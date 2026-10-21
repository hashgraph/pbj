// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.pbj.grpc.common.Base64ChunkDecoder;
import com.hedera.pbj.grpc.common.GrpcFramer;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientRequest;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real-server regressions for malformed requests and server-owned resource limits. */
@Timeout(10)
class PbjGrpcWebServiceHardeningTest {
    private static final String PATH = "/hardening.Test/Call";
    private static final String CONTENT_TYPE = "application/grpc-web+proto";
    private static final byte[] EMPTY_MESSAGE = new byte[5];

    enum Transport {
        HTTP_1_1,
        HTTP_2
    }

    static Stream<Arguments> malformedRequests() {
        return Arrays.stream(Transport.values()).flatMap(transport -> Stream.of(
                        ServiceInterface.MethodType.UNARY,
                        ServiceInterface.MethodType.SERVER_STREAMING,
                        ServiceInterface.MethodType.UNKNOWN)
                .flatMap(type -> Stream.of(
                                new byte[0],
                                new byte[] {0},
                                new byte[] {0, 0, 0, 0, 3, 1},
                                new byte[10],
                                new byte[5000],
                                new byte[] {0, 0, 0, 0, 0, 0})
                        .map(body -> Arguments.of(transport, type, body))));
    }

    @ParameterizedTest
    @MethodSource("malformedRequests")
    void rejectsMissingTruncatedAndMultipleMessagesBeforeDispatch(
            final Transport transport, final ServiceInterface.MethodType type, final byte[] body) throws Exception {
        final TestService service = new TestService(type, (bytes, replies) -> replies.onComplete());
        try (final RunningServer server = new RunningServer(service, builder -> {})) {
            final Response response = server.post(transport, CONTENT_TYPE, body, Map.of());
            assertThat(response.grpcStatus()).isEqualTo(GrpcStatus.INVALID_ARGUMENT.ordinal());
            assertThat(service.calls).hasValue(0);
            assertThat(service.failed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rejectsDuplicateMessagesBeforeClosingAnUnreadStalledBody() throws Exception {
        final TestService service = echoService();
        try (final RunningServer server = new RunningServer(service, builder -> {});
                final Socket socket = new Socket("localhost", server.server.port())) {
            socket.setSoTimeout(2000);
            socket.getOutputStream()
                    .write(("POST " + PATH + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + CONTENT_TYPE
                                    + "\r\nContent-Length: 1000\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(new byte[10]);
            socket.getOutputStream().flush();
            final String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertThat(response).contains("grpc-status: 3\r\n");
            assertThat(service.calls).hasValue(0);
        }
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void acceptsExactlyOneEmptyMessage(final Transport transport) throws Exception {
        final TestService service = echoService();
        try (final RunningServer server = new RunningServer(service, builder -> {})) {
            assertThat(server.post(transport, CONTENT_TYPE, EMPTY_MESSAGE, Map.of())
                            .grpcStatus())
                    .isZero();
            assertThat(service.calls).hasValue(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "99999999H", "invalid"})
    void serverTimeoutBoundsMissingOversizedAndInvalidClientTimeouts(final String timeout) throws Exception {
        final TestService service =
                new TestService(ServiceInterface.MethodType.SERVER_STREAMING, (bytes, replies) -> {});
        try (final RunningServer server =
                new RunningServer(service, builder -> builder.defaultTimeout(Duration.ofMillis(100))
                        .maxTimeout(Duration.ofMillis(150)))) {
            final Map<String, String> headers = timeout.isEmpty() ? Map.of() : Map.of("grpc-timeout", timeout);
            assertThat(server.post(Transport.HTTP_1_1, CONTENT_TYPE, EMPTY_MESSAGE, headers)
                            .grpcStatus())
                    .isEqualTo(GrpcStatus.DEADLINE_EXCEEDED.ordinal());
            assertThat(service.failed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void cancelledWorkerKeepsAdmissionUntilItsCallbackReturnsAndErrorIsSerialized() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean active = new AtomicBoolean();
        final AtomicBoolean overlap = new AtomicBoolean();
        final AtomicBoolean echo = new AtomicBoolean();
        final TestService service = new TestService(ServiceInterface.MethodType.UNARY, (bytes, replies) -> {
            if (echo.get()) {
                replies.onComplete();
                return;
            }
            active.set(true);
            entered.countDown();
            try {
                while (release.getCount() != 0) {
                    try {
                        release.await();
                    } catch (final InterruptedException ignored) {
                        // Simulate an application that does not cooperate with interruption.
                    }
                }
            } finally {
                active.set(false);
            }
        });
        service.onError = error -> overlap.set(active.get());
        try (final RunningServer server = new RunningServer(service, builder -> builder.maxConcurrentCalls(1)
                .defaultTimeout(Duration.ofMillis(150))
                .maxTimeout(Duration.ofMillis(150)))) {
            final HttpRequest request = server.request(CONTENT_TYPE, EMPTY_MESSAGE, Map.of());
            final java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> first =
                    server.http1.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(Response.of(first.get(3, TimeUnit.SECONDS)).grpcStatus())
                        .isEqualTo(GrpcStatus.DEADLINE_EXCEEDED.ordinal());
                assertThat(server.post(Transport.HTTP_1_1, CONTENT_TYPE, EMPTY_MESSAGE, Map.of())
                                .grpcStatus())
                        .isEqualTo(GrpcStatus.RESOURCE_EXHAUSTED.ordinal());
                assertThat(service.failed.getCount()).isEqualTo(1);
                assertThat(overlap).isFalse();
            } finally {
                echo.set(true);
                release.countDown();
            }
            assertThat(service.failed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(overlap).isFalse();
            Response next = null;
            for (int attempt = 0; attempt < 30; attempt++) {
                next = server.post(Transport.HTTP_1_1, CONTENT_TYPE, EMPTY_MESSAGE, Map.of());
                if (next.grpcStatus() == 0) {
                    break;
                }
                Thread.sleep(10);
            }
            assertThat(next.grpcStatus()).isZero();
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cookiesRequireOptInAndTransportHeadersAreExcluded(final boolean cookies) throws Exception {
        final TestService service = echoService();
        try (final RunningServer server = new RunningServer(service, builder -> builder.forwardCookies(cookies))) {
            assertThat(server.post(
                                    Transport.HTTP_1_1,
                                    CONTENT_TYPE,
                                    EMPTY_MESSAGE,
                                    Map.of(
                                            "cookie",
                                            "session=example",
                                            "authorization",
                                            "Bearer example",
                                            "origin",
                                            "https://example.test",
                                            "x-custom",
                                            "custom-value"))
                            .grpcStatus())
                    .isZero();
            final Map<String, String> metadata = service.options.get().metadata();
            assertThat(metadata)
                    .containsEntry("authorization", "Bearer example")
                    .containsEntry("x-custom", "custom-value");
            assertThat(metadata).doesNotContainKeys("host", "origin", "content-type", "content-length", "connection");
            if (cookies) {
                assertThat(metadata).containsEntry("cookie", "session=example");
            } else {
                assertThat(metadata).doesNotContainKey("cookie");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "https://example.test/",
                "https://example.test/path",
                "https://user@example.test",
                "https://example.test?q=1",
                "https://example.test#fragment",
                "file:///example",
                "https://example.test:99999"
            })
    void invalidOrOpaqueOriginsFailAtConfiguration(final String origin) {
        assertThatThrownBy(() -> PbjGrpcWebService.builder(echoService()).allowedOrigins(origin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void originConfigurationNormalizesCaseAndDefaultPortsAndVariesRejectedResponses() throws Exception {
        try (final RunningServer server =
                new RunningServer(echoService(), builder -> builder.allowedOrigins("HTTPS://EXAMPLE.TEST:443"))) {
            final Response allowed = server.post(
                    Transport.HTTP_1_1, CONTENT_TYPE, EMPTY_MESSAGE, Map.of("origin", "https://example.test"));
            assertThat(allowed.header("access-control-allow-origin")).contains("https://example.test");
            final Response rejected = server.post(
                    Transport.HTTP_1_1, CONTENT_TYPE, EMPTY_MESSAGE, Map.of("origin", "https://other.test"));
            assertThat(rejected.header("access-control-allow-origin")).isEmpty();
            assertThat(rejected.header("vary")).contains("Origin");
            final HttpResponse<byte[]> preflight = server.http1.send(
                    HttpRequest.newBuilder(server.uri())
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("origin", "https://other.test")
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(preflight.statusCode()).isEqualTo(403);
            assertThat(preflight.headers().firstValue("vary")).contains("Origin");
        }
    }

    @Test
    void wildcardPreflightDoesNotEnableCredentials() throws Exception {
        try (final RunningServer server = new RunningServer(echoService(), builder -> builder.allowedOrigins("*"))) {
            final HttpResponse<byte[]> response = server.http1.send(
                    HttpRequest.newBuilder(server.uri())
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("origin", "https://example.test")
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.headers().firstValue("access-control-allow-origin"))
                    .contains("*");
            assertThat(response.headers().firstValue("access-control-allow-credentials"))
                    .isEmpty();
        }
    }

    static Stream<Arguments> compressedRequests() {
        return Arrays.stream(Transport.values())
                .flatMap(transport -> Stream.of("gzip", "zstd").map(encoding -> Arguments.of(transport, encoding)));
    }

    @ParameterizedTest
    @MethodSource("compressedRequests")
    void webOnlyServerSupportsCompressionAndEnforcesExpandedLimit(final Transport transport, final String encoding)
            throws Exception {
        final TestService service = echoService();
        try (final RunningServer server = new RunningServer(service, builder -> builder.maxMessageSizeBytes(2000))) {
            final byte[] small = GrpcFramer.frameMessage(Bytes.wrap("hello"), encoding);
            assertThat(server.post(transport, CONTENT_TYPE, small, Map.of("grpc-encoding", encoding))
                            .grpcStatus())
                    .isZero();
            final byte[] bomb = GrpcFramer.frameMessage(Bytes.wrap(new byte[1024 * 1024]), encoding);
            assertThat(bomb.length).isLessThan(2000);
            assertThat(server.post(transport, CONTENT_TYPE, bomb, Map.of("grpc-encoding", encoding))
                            .grpcStatus())
                    .isEqualTo(GrpcStatus.RESOURCE_EXHAUSTED.ordinal());
            assertThat(service.calls).hasValue(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/grpc-web+json", "application/grpc-web-text+json"})
    void jsonTransferNegotiationPreservesJsonCodec(final String type) throws Exception {
        final TestService service = echoService();
        try (final RunningServer server = new RunningServer(service, builder -> {})) {
            final byte[] json = GrpcFramer.frame(0, Bytes.wrap("{}"));
            final byte[] body = type.contains("text") ? Base64.getEncoder().encode(json) : json;
            final Response implicit = server.post(Transport.HTTP_1_1, type, body, Map.of());
            assertThat(implicit.header("content-type")).contains(type);
            assertThat(service.options.get().isJson()).isTrue();
            final Response response =
                    server.post(Transport.HTTP_1_1, type, body, Map.of("accept", "application/grpc-web-text+json"));
            assertThat(response.header("content-type")).contains("application/grpc-web-text+json");
            assertThat(service.options.get().isJson()).isTrue();
            try (final Base64ChunkDecoder decoder = new Base64ChunkDecoder()) {
                final byte[] decoded = decoder.decode(response.body(), 0, response.body().length);
                assertThat(decoded).startsWith(json);
            }
        }
    }

    @Test
    void invalidResourceLimitsFailAtConfiguration() {
        assertThatThrownBy(() -> PbjGrpcWebService.builder(echoService()).maxConcurrentCalls(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PbjGrpcWebService.builder(echoService()).maxTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PbjGrpcWebService.builder(echoService()).defaultTimeout(Duration.ofSeconds(Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TestService echoService() {
        return new TestService(ServiceInterface.MethodType.UNARY, (bytes, replies) -> {
            replies.onNext(bytes);
            replies.onComplete();
        });
    }

    private static final class TestService implements ServiceInterface {
        private final MethodType type;
        private final BiConsumer<Bytes, Pipeline<? super Bytes>> handler;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<RequestOptions> options = new AtomicReference<>();
        private final CountDownLatch failed = new CountDownLatch(1);
        private Consumer<Throwable> onError = error -> {};

        private TestService(final MethodType type, final BiConsumer<Bytes, Pipeline<? super Bytes>> handler) {
            this.type = type;
            this.handler = handler;
        }

        @Override
        public String serviceName() {
            return "Test";
        }

        @Override
        public String fullName() {
            return "hardening.Test";
        }

        @Override
        public List<Method> methods() {
            return List.of(new Method() {
                @Override
                public String name() {
                    return "Call";
                }

                @Override
                public MethodType type() {
                    return type;
                }
            });
        }

        @Override
        public Pipeline<? super Bytes> open(
                final Method method, final RequestOptions opts, final Pipeline<? super Bytes> replies) {
            options.set(opts);
            return new Pipeline<Bytes>() {
                @Override
                public void onSubscribe(final Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(final Bytes bytes) {
                    calls.incrementAndGet();
                    handler.accept(bytes, replies);
                }

                @Override
                public void onError(final Throwable error) {
                    onError.accept(error);
                    failed.countDown();
                }

                @Override
                public void onComplete() {}
            };
        }
    }

    private static final class RunningServer implements AutoCloseable {
        private final WebServer server;
        private final HttpClient http1 =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        private final Http2Client http2;

        private RunningServer(final TestService service, final Consumer<PbjGrpcWebService.Builder> config) {
            final PbjGrpcWebService.Builder builder = PbjGrpcWebService.builder(service);
            config.accept(builder);
            server = WebServer.builder()
                    .port(0)
                    .routing(routing -> routing.register(builder.build()))
                    .build()
                    .start();
            http2 = Http2Client.builder()
                    .baseUri("http://localhost:" + server.port())
                    .protocolConfig(Http2ClientProtocolConfig.builder()
                            .priorKnowledge(true)
                            .build())
                    .build();
        }

        private URI uri() {
            return URI.create("http://localhost:" + server.port() + PATH);
        }

        private HttpRequest request(final String type, final byte[] body, final Map<String, String> headers) {
            final HttpRequest.Builder builder = HttpRequest.newBuilder(uri())
                    .timeout(Duration.ofSeconds(3))
                    .header("content-type", type)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(builder::header);
            return builder.build();
        }

        private Response post(
                final Transport transport, final String type, final byte[] body, final Map<String, String> headers)
                throws Exception {
            if (transport == Transport.HTTP_1_1) {
                return Response.of(http1.send(request(type, body, headers), HttpResponse.BodyHandlers.ofByteArray()));
            }
            final Http2ClientRequest request =
                    http2.post().path(PATH).readTimeout(Duration.ofSeconds(3)).contentType(HttpMediaType.create(type));
            headers.forEach((name, value) -> request.header(HeaderNames.create(name), value));
            try (final Http2ClientResponse response = request.submit(body)) {
                final Map<String, List<String>> values = response.headers().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                header -> header.name().toLowerCase(Locale.ROOT), header -> List.of(header.values())));
                return new Response(values, response.entity().inputStream().readAllBytes());
            }
        }

        @Override
        public void close() {
            http1.close();
            server.stop();
        }
    }

    private record Response(Map<String, List<String>> headers, byte[] body) {
        private static Response of(final HttpResponse<byte[]> response) {
            return new Response(response.headers().map(), response.body());
        }

        private Optional<String> header(final String name) {
            final List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
        }

        private int grpcStatus() {
            if (header("grpc-status").isPresent()) {
                return Integer.parseInt(header("grpc-status").get());
            }
            int position = 0;
            while (position < body.length) {
                final int flags = body[position] & 0xff;
                final int length =
                        java.nio.ByteBuffer.wrap(body, position + 1, 4).getInt();
                if ((flags & 0x80) != 0) {
                    final String trailers = new String(body, position + 5, length, StandardCharsets.US_ASCII);
                    for (final String line : trailers.split("\r\n")) {
                        if (line.startsWith("grpc-status: ")) {
                            return Integer.parseInt(line.substring(13));
                        }
                    }
                }
                position += 5 + length;
            }
            throw new AssertionError("Missing gRPC status");
        }
    }
}
