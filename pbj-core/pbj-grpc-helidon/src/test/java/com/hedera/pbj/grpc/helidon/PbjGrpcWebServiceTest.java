// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.util.JsonFormat;
import com.hedera.pbj.grpc.common.Base64ChunkDecoder;
import com.hedera.pbj.grpc.common.GrpcFramer;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.GreeterGrpc;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.grpc.ManagedChannelBuilder;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests {@link PbjGrpcWebService} end to end, over HTTP/1.1 and HTTP/2, on a server that also serves native gRPC on
 * the same port.
 */
class PbjGrpcWebServiceTest {
    private static final String ALLOWED_ORIGIN = "https://allowed.example";
    private static final String OTHER_ORIGIN = "https://other.example";
    private static final String SAY_HELLO = "/greeter.Greeter/sayHello";
    private static final String STREAM_REPLY = "/greeter.Greeter/sayHelloStreamReply";
    private static final String STREAM_REQUEST = "/greeter.Greeter/sayHelloStreamRequest";
    private static final String STREAM_BIDI = "/greeter.Greeter/sayHelloStreamBidi";
    private static final String GRPC_WEB = "application/grpc-web";
    private static final String GRPC_WEB_PROTO = "application/grpc-web+proto";
    private static final String GRPC_WEB_TEXT = "application/grpc-web-text";
    private static final String GRPC_WEB_TEXT_PROTO = "application/grpc-web-text+proto";
    private static final int MAX_MESSAGE_SIZE = 1000;

    private static final HelloRequest REQUEST =
            HelloRequest.newBuilder().setName("PBJ").build();

    private static final AtomicReference<GreeterService> SERVICE = new AtomicReference<>();

    private static WebServer server;
    private static HttpClient http1Client;
    private static Http2Client http2Client;

    @BeforeAll
    static void setup() {
        final var proxy = new GreeterProxy();
        server = WebServer.builder()
                .port(0)
                .addRouting(PbjRouting.builder().service(proxy))
                .routing(routing -> routing.register(PbjGrpcWebService.builder(proxy)
                        .allowedOrigins(ALLOWED_ORIGIN)
                        .allowedHeaders("x-custom")
                        .maxMessageSizeBytes(MAX_MESSAGE_SIZE)
                        .build()))
                .build()
                .start();
        http1Client =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        http2Client = Http2Client.builder()
                .baseUri("http://localhost:" + server.port())
                .protocolConfig(
                        Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                .build();
    }

    @AfterAll
    static void teardown() {
        http1Client.close();
        server.stop();
    }

    @BeforeEach
    void resetService() {
        SERVICE.set(new GreeterServiceImpl());
    }

    /** Which HTTP version a test sends its requests with. */
    enum Transport {
        HTTP_1_1,
        HTTP_2
    }

    @Nested
    class Unary {
        @ParameterizedTest
        @EnumSource(Transport.class)
        void binaryMode(final Transport transport) throws Exception {
            final var response = post(transport, SAY_HELLO, GRPC_WEB_PROTO, frame(REQUEST), Map.of());

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.header("content-type")).isEqualTo(GRPC_WEB_PROTO);
            assertThat(response.replies()).containsExactly(reply("Hello PBJ"));
            assertThat(response.trailers()).containsEntry("grpc-status", "0");
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        void textMode(final Transport transport) throws Exception {
            final var body = Base64.getEncoder().encode(frame(REQUEST));
            final var response = post(transport, SAY_HELLO, GRPC_WEB_TEXT, body, Map.of());

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.header("content-type")).isEqualTo(GRPC_WEB_TEXT_PROTO);
            assertThat(response.replies()).containsExactly(reply("Hello PBJ"));
            assertThat(response.trailers()).containsEntry("grpc-status", "0");
        }

        @Test
        void bareContentTypeIsTreatedAsProtobuf() throws Exception {
            final var options = new AtomicReference<ServiceInterface.RequestOptions>();
            SERVICE.set(new GreeterServiceImpl() {
                @Override
                @NonNull
                public Pipeline<? super Bytes> open(
                        @NonNull final Method method,
                        @NonNull final RequestOptions opts,
                        @NonNull final Pipeline<? super Bytes> replies) {
                    options.set(opts);
                    return super.open(method, opts, replies);
                }
            });

            final var response =
                    post(Transport.HTTP_1_1, SAY_HELLO, GRPC_WEB, frame(REQUEST), Map.of("x-custom", "custom-value"));

            assertThat(response.header("content-type")).isEqualTo(GRPC_WEB_PROTO);
            assertThat(response.replies()).containsExactly(reply("Hello PBJ"));
            assertThat(options.get().contentType()).isEqualTo(GRPC_WEB_PROTO);
            assertThat(options.get().isProtobuf()).isTrue();
            assertThat(options.get().maxMessageSizeBytes()).isEqualTo(MAX_MESSAGE_SIZE);
            assertThat(options.get().metadata()).containsEntry("x-custom", "custom-value");
        }

        @Test
        void jsonMode() throws Exception {
            final var json = JsonFormat.printer().print(REQUEST).getBytes(StandardCharsets.UTF_8);
            final var body = GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, Bytes.wrap(json));
            final var response = post(Transport.HTTP_1_1, SAY_HELLO, "application/grpc-web+json", body, Map.of());

            assertThat(response.header("content-type")).isEqualTo("application/grpc-web+json");
            assertThat(response.messages()).hasSize(1);
            final var builder = HelloReply.newBuilder();
            JsonFormat.parser().merge(new String(response.messages().getFirst(), StandardCharsets.UTF_8), builder);
            assertThat(builder.build()).isEqualTo(reply("Hello PBJ"));
        }

        @Test
        void compressedRequest() throws Exception {
            final var body = GrpcFramer.frameMessage(Bytes.wrap(REQUEST.toByteArray()), "gzip");
            final var response =
                    post(Transport.HTTP_1_1, SAY_HELLO, GRPC_WEB_PROTO, body, Map.of("grpc-encoding", "gzip"));

            assertThat(response.replies()).containsExactly(reply("Hello PBJ"));
            assertThat(response.trailers()).containsEntry("grpc-status", "0");
        }
    }

    @Nested
    class ServerStreaming {
        @ParameterizedTest
        @EnumSource(Transport.class)
        void streamsEveryReplyThenTheTrailers(final Transport transport) throws Exception {
            final var response = post(transport, STREAM_REPLY, GRPC_WEB_PROTO, frame(REQUEST), Map.of());

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.replies()).hasSize(10).containsOnly(reply("Hello!"));
            assertThat(response.trailers()).containsEntry("grpc-status", "0");
        }

        @Test
        void textModeEncodesEachFrame() throws Exception {
            final var body = Base64.getEncoder().encode(frame(REQUEST));
            final var response = post(Transport.HTTP_2, STREAM_REPLY, GRPC_WEB_TEXT_PROTO, body, Map.of());

            assertThat(response.replies()).hasSize(10).containsOnly(reply("Hello!"));
            assertThat(response.trailers()).containsEntry("grpc-status", "0");
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        void errorAfterRepliesGoesInTheTrailerFrame(final Transport transport) throws Exception {
            SERVICE.set(new GreeterServiceImpl() {
                @Override
                public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
                    replies.onNext(reply("one"));
                    replies.onNext(reply("two"));
                    replies.onError(new GrpcException(GrpcStatus.ABORTED, "stream broke"));
                }
            });

            final var response = post(transport, STREAM_REPLY, GRPC_WEB_PROTO, frame(REQUEST), Map.of());

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.replies()).containsExactly(reply("one"), reply("two"));
            assertThat(response.trailers())
                    .containsEntry("grpc-status", "" + GrpcStatus.ABORTED.ordinal())
                    .containsEntry("grpc-message", "stream%20broke");
        }

        @Test
        void repliesFromAnotherThreadAreWritten() throws Exception {
            SERVICE.set(new GreeterServiceImpl() {
                @Override
                public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
                    Thread.ofVirtual().start(() -> {
                        for (int i = 0; i < 5; i++) {
                            replies.onNext(reply("async " + i));
                        }
                        replies.onComplete();
                    });
                }
            });

            final var response = post(Transport.HTTP_1_1, STREAM_REPLY, GRPC_WEB_PROTO, frame(REQUEST), Map.of());

            assertThat(response.replies()).hasSize(5).first().isEqualTo(reply("async 0"));
            assertThat(response.trailers()).containsEntry("grpc-status", "0");
        }
    }

    @Nested
    class Errors {
        @ParameterizedTest
        @EnumSource(Transport.class)
        void errorBeforeAnyReplyIsTrailersOnly(final Transport transport) throws Exception {
            final var service = new GreeterServiceImpl();
            service.errorToThrow = GrpcStatus.PERMISSION_DENIED;
            SERVICE.set(service);

            final var response = post(transport, SAY_HELLO, GRPC_WEB_PROTO, frame(REQUEST), Map.of());

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.header("grpc-status")).isEqualTo("" + GrpcStatus.PERMISSION_DENIED.ordinal());
            assertThat(response.body()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        void clientStreamingIsUnimplemented(final Transport transport) throws Exception {
            for (final var path : List.of(STREAM_REQUEST, STREAM_BIDI)) {
                final var response = post(transport, path, GRPC_WEB_PROTO, frame(REQUEST), Map.of());

                assertThat(response.status()).isEqualTo(200);
                assertThat(response.header("grpc-status")).isEqualTo("" + GrpcStatus.UNIMPLEMENTED.ordinal());
                assertThat(response.body()).isEmpty();
            }
        }

        @Test
        void unsupportedContentTypeIs415() throws Exception {
            final var response = post(Transport.HTTP_1_1, SAY_HELLO, "application/json", frame(REQUEST), Map.of());

            assertThat(response.status()).isEqualTo(415);
            assertThat(response.header("grpc-status")).isEqualTo("" + GrpcStatus.INVALID_ARGUMENT.ordinal());
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        void messageLargerThanTheMaximumIsRejected(final Transport transport) throws Exception {
            final var large = HelloRequest.newBuilder()
                    .setName("x".repeat(MAX_MESSAGE_SIZE))
                    .build();
            final var response = post(transport, SAY_HELLO, GRPC_WEB_PROTO, frame(large), Map.of());

            assertThat(response.header("grpc-status")).isEqualTo("" + GrpcStatus.INVALID_ARGUMENT.ordinal());
        }

        @Test
        void invalidBase64IsRejected() throws Exception {
            final var body = "not*base64".getBytes(StandardCharsets.US_ASCII);
            final var response = post(Transport.HTTP_1_1, SAY_HELLO, GRPC_WEB_TEXT, body, Map.of());

            assertThat(response.header("grpc-status")).isEqualTo("" + GrpcStatus.INVALID_ARGUMENT.ordinal());
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        void deadlineExceeded(final Transport transport) throws Exception {
            SERVICE.set(new GreeterServiceImpl() {
                @Override
                public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
                    // Never reply, never complete
                }
            });

            final long start = System.nanoTime();
            final var response =
                    post(transport, STREAM_REPLY, GRPC_WEB_PROTO, frame(REQUEST), Map.of("grpc-timeout", "200m"));

            assertThat(response.header("grpc-status")).isEqualTo("" + GrpcStatus.DEADLINE_EXCEEDED.ordinal());
            assertThat(System.nanoTime() - start).isLessThan(10_000_000_000L);
        }
    }

    @Nested
    class Cors {
        @Test
        void preflightFromAnAllowedOrigin() throws Exception {
            final var response = http1Client.send(
                    HttpRequest.newBuilder(uri(SAY_HELLO))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type,x-grpc-web")
                            .build(),
                    HttpResponse.BodyHandlers.discarding());

            assertThat(response.statusCode()).isEqualTo(204);
            final var headers = response.headers();
            assertThat(headers.firstValue("access-control-allow-origin")).hasValue(ALLOWED_ORIGIN);
            assertThat(headers.firstValue("access-control-allow-methods")).hasValue("POST, OPTIONS");
            assertThat(headers.firstValue("access-control-allow-headers"))
                    .hasValue("content-type, x-grpc-web, x-user-agent, grpc-timeout, x-custom");
            assertThat(headers.firstValue("access-control-max-age")).isPresent();
            assertThat(headers.firstValue("vary")).hasValue("Origin");
        }

        @Test
        void preflightFromAnotherOriginIsForbidden() throws Exception {
            final var response = http1Client.send(
                    HttpRequest.newBuilder(uri(SAY_HELLO))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", OTHER_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .build(),
                    HttpResponse.BodyHandlers.discarding());

            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.headers().firstValue("access-control-allow-origin"))
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        void callFromAnAllowedOriginExposesTheStatusHeaders(final Transport transport) throws Exception {
            final var response =
                    post(transport, SAY_HELLO, GRPC_WEB_PROTO, frame(REQUEST), Map.of("origin", ALLOWED_ORIGIN));

            assertThat(response.header("access-control-allow-origin")).isEqualTo(ALLOWED_ORIGIN);
            assertThat(response.header("access-control-expose-headers")).isEqualTo("grpc-status, grpc-message");
            assertThat(response.replies()).containsExactly(reply("Hello PBJ"));
        }

        @Test
        void callFromAnotherOriginGetsNoCorsHeaders() throws Exception {
            final var response =
                    post(Transport.HTTP_1_1, SAY_HELLO, GRPC_WEB_PROTO, frame(REQUEST), Map.of("origin", OTHER_ORIGIN));

            assertThat(response.header("access-control-allow-origin")).isNull();
        }
    }

    @Nested
    class SharedPort {
        @Test
        void nativeGrpcStillWorks() throws Exception {
            final var channel = ManagedChannelBuilder.forAddress("localhost", server.port())
                    .usePlaintext()
                    .build();
            try {
                final var reply = GreeterGrpc.newBlockingStub(channel).sayHello(REQUEST);
                assertThat(reply).isEqualTo(reply("Hello PBJ"));
            } finally {
                channel.shutdownNow();
            }
        }

        @Test
        void otherHttp2PostsAreStillClaimedByNativeGrpc() throws Exception {
            // A native gRPC request on HTTP/2 must not reach the HttpRouting, even for a path the web service serves
            try (var response = http2Client
                    .post()
                    .path(SAY_HELLO)
                    .contentType(HttpMediaType.create("application/grpc+proto"))
                    .submit(frame(REQUEST))) {
                assertThat(response.status().code()).isEqualTo(200);
                assertThat(response.headers().contentType().orElseThrow().text())
                        .isEqualTo("application/grpc+proto");
            }
        }
    }

    // Utility methods

    private static URI uri(final String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static HelloReply reply(final String message) {
        return HelloReply.newBuilder().setMessage(message).build();
    }

    private static byte[] frame(final HelloRequest request) {
        return GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, Bytes.wrap(request.toByteArray()));
    }

    private static WebResponse post(
            final Transport transport,
            final String path,
            final String contentType,
            final byte[] body,
            final Map<String, String> headers)
            throws Exception {
        final var responseHeaders = new HashMap<String, String>();
        final int status;
        final byte[] responseBody;
        if (transport == Transport.HTTP_1_1) {
            final var request = HttpRequest.newBuilder(uri(path))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .header("content-type", contentType)
                    .header("x-grpc-web", "1");
            headers.forEach(request::header);
            final var response = http1Client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
            status = response.statusCode();
            response.headers()
                    .map()
                    .forEach((name, values) -> responseHeaders.put(name.toLowerCase(Locale.ROOT), values.getFirst()));
            responseBody = response.body();
        } else {
            final var request = http2Client
                    .post()
                    .path(path)
                    .contentType(HttpMediaType.create(contentType))
                    .header(HeaderNames.create("x-grpc-web"), "1");
            headers.forEach((name, value) -> request.header(HeaderNames.create(name), value));
            try (var response = request.submit(body)) {
                status = response.status().code();
                response.headers()
                        .forEach(header -> responseHeaders.put(header.name().toLowerCase(Locale.ROOT), header.get()));
                responseBody = response.entity().inputStream().readAllBytes();
            }
        }
        final var contentTypeHeader = responseHeaders.getOrDefault("content-type", "");
        return WebResponse.parse(status, responseHeaders, responseBody, contentTypeHeader.startsWith(GRPC_WEB_TEXT));
    }

    /** A parsed gRPC-Web response. */
    private record WebResponse(
            int status, Map<String, String> headers, byte[] body, List<byte[]> messages, Map<String, String> trailers) {

        static WebResponse parse(
                final int status, final Map<String, String> headers, final byte[] body, final boolean textMode) {
            byte[] data = body;
            if (textMode) {
                final var decoder = new Base64ChunkDecoder();
                data = decoder.decode(body, 0, body.length);
                decoder.finish();
            }
            final var messages = new ArrayList<byte[]>();
            final var trailers = new HashMap<String, String>();
            int position = 0;
            while (position < data.length) {
                final int flags = data[position] & 0xFF;
                final int length = ((data[position + 1] & 0xFF) << 24)
                        | ((data[position + 2] & 0xFF) << 16)
                        | ((data[position + 3] & 0xFF) << 8)
                        | (data[position + 4] & 0xFF);
                final var payload = new byte[length];
                System.arraycopy(data, position + 5, payload, 0, length);
                position += 5 + length;
                if ((flags & GrpcFramer.TRAILERS_FLAG) != 0) {
                    assertThat(position).as("the trailer frame must be last").isEqualTo(data.length);
                    for (final var line : new String(payload, StandardCharsets.US_ASCII).split("\r\n")) {
                        final int colon = line.indexOf(':');
                        trailers.put(
                                line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                                line.substring(colon + 1).trim());
                    }
                } else {
                    assertThat(flags).isEqualTo(GrpcFramer.UNCOMPRESSED_FLAG);
                    assertThat(trailers)
                            .as("no message may follow the trailers")
                            .isEmpty();
                    messages.add(payload);
                }
            }
            return new WebResponse(status, headers, body, messages, trailers);
        }

        String header(final String name) {
            return headers.get(name);
        }

        List<HelloReply> replies() throws Exception {
            final var replies = new ArrayList<HelloReply>();
            for (final var message : messages) {
                replies.add(HelloReply.parseFrom(message));
            }
            return replies;
        }
    }

    /** Delegates to {@link #SERVICE}, so each test can change the behaviour of the service. */
    private static final class GreeterProxy implements GreeterService {
        @Override
        public HelloReply sayHello(HelloRequest request) {
            return SERVICE.get().sayHello(request);
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamRequest(Pipeline<? super HelloReply> replies) {
            return SERVICE.get().sayHelloStreamRequest(replies);
        }

        @Override
        public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
            SERVICE.get().sayHelloStreamReply(request, replies);
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamBidi(Pipeline<? super HelloReply> replies) {
            return SERVICE.get().sayHelloStreamBidi(replies);
        }

        @Override
        @NonNull
        public Pipeline<? super Bytes> open(
                @NonNull Method method, @NonNull RequestOptions options, @NonNull Pipeline<? super Bytes> replies) {
            return SERVICE.get().open(method, options, replies);
        }
    }
}
