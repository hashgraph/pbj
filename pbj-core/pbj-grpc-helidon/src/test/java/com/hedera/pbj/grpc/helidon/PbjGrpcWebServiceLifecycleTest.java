// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.Pipelines;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.common.context.Contexts;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import java.io.BufferedReader;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises deadlines across service initialization, synchronous dispatch, and request-body reads. */
@Timeout(8)
class PbjGrpcWebServiceLifecycleTest {
    private static final String PATH = "/lifecycle.Test/Call";
    private static final String CONTENT_TYPE = "application/grpc-web+proto";
    private static final String TIMEOUT = "200m";

    enum Transport {
        HTTP_1_1,
        HTTP_2
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void deadlineDuringOpenInterruptsWorkerAndCleansUpLatePipeline(final Transport transport) throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final RecordingPipeline requests = new RecordingPipeline(null);
        final TestService service = new TestService(replies -> {
            entered.countDown();
            try {
                release.await();
            } catch (final InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return requests;
        });

        try (final TestServer server = new TestServer(service)) {
            final FutureTask<Response> response = server.postAsync(transport);
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertDeadline(response.get(3, TimeUnit.SECONDS));
                assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(requests.errorDelivered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(requests.errors).hasValue(1);
                assertThat(requests.lastError.get()).isInstanceOf(GrpcException.class);
                assertThat(((GrpcException) requests.lastError.get()).status()).isEqualTo(GrpcStatus.DEADLINE_EXCEEDED);
            } finally {
                release.countDown();
                response.cancel(true);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void deadlineDuringSynchronousUnaryDispatchInterruptsWorker(final Transport transport) throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final AtomicReference<RecordingPipeline> requestPipeline = new AtomicReference<RecordingPipeline>();
        final TestService service = new TestService(replies -> {
            final Pipeline<? super Bytes> unary = Pipelines.<Bytes, Bytes>unary()
                    .mapRequest(bytes -> bytes)
                    .method(bytes -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (final InterruptedException e) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                            throw e;
                        }
                        return Bytes.EMPTY;
                    })
                    .mapResponse(bytes -> bytes)
                    .respondTo(replies)
                    .build();
            final RecordingPipeline requests = new RecordingPipeline(unary);
            requestPipeline.set(requests);
            return requests;
        });

        try (final TestServer server = new TestServer(service)) {
            final FutureTask<Response> response = server.postAsync(transport);
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertDeadline(response.get(3, TimeUnit.SECONDS));
                assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
                final RecordingPipeline requests = requestPipeline.get();
                assertThat(requests.errorDelivered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(requests.errors).hasValue(1);
            } finally {
                release.countDown();
                response.cancel(true);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void lateCallbacksCannotReplaceDeadlineOrWriteToFinishedResponse(final Transport transport) throws Exception {
        final AtomicReference<Pipeline<? super Bytes>> replies = new AtomicReference<Pipeline<? super Bytes>>();
        final RecordingPipeline requests = new RecordingPipeline(null);
        final TestService service = new TestService(responses -> {
            replies.set(responses);
            return requests;
        });

        try (final TestServer server = new TestServer(service)) {
            assertDeadline(server.postAsync(transport).get(3, TimeUnit.SECONDS));
            assertThat(requests.errorDelivered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests.errorMarker.get()).isSameAs(server.marker);
            assertThatCode(() -> {
                        replies.get().onNext(Bytes.wrap("late"));
                        replies.get().onComplete();
                        replies.get().onError(new GrpcException(GrpcStatus.INTERNAL));
                    })
                    .doesNotThrowAnyException();
            assertThat(requests.errors).hasValue(1);
        }
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void errorDuringServiceOpenCompletesWithUnknown(final Transport transport) throws Exception {
        final TestService service = new TestService(replies -> {
            throw new AssertionError("Service initialization failed");
        });

        try (final TestServer server = new TestServer(service)) {
            final Response response = server.postAsync(transport).get(3, TimeUnit.SECONDS);
            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.grpcStatus()).isEqualTo(GrpcStatus.UNKNOWN.ordinal());
        }
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void deadlineCleanupCanReleaseABlockedAsynchronousResponseWrite(final Transport transport) throws Exception {
        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch releaseWrite = new CountDownLatch(1);
        final AtomicReference<Thread> publisher = new AtomicReference<Thread>();
        final AtomicReference<RecordingPipeline> requests = new AtomicReference<RecordingPipeline>();
        final TestService service = new TestService(ServiceInterface.MethodType.SERVER_STREAMING, replies -> {
            final Pipeline<? super Bytes> stream = Pipelines.<Bytes, Bytes>serverStreaming()
                    .mapRequest(bytes -> bytes)
                    .method((request, responses) ->
                            publisher.set(Thread.ofVirtual().start(() -> responses.onNext(Bytes.EMPTY))))
                    .mapResponse(bytes -> bytes)
                    .respondTo(replies)
                    .build();
            final RecordingPipeline pipeline = new RecordingPipeline(stream, releaseWrite::countDown);
            requests.set(pipeline);
            return pipeline;
        });
        final UnaryOperator<OutputStream> streamFilter = out -> new FilterOutputStream(out) {
            @Override
            public void write(final byte[] bytes, final int offset, final int length) throws IOException {
                writing.countDown();
                try {
                    if (!releaseWrite.await(3, TimeUnit.SECONDS)) {
                        throw new IOException("Cancellation did not release the blocked response write");
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                out.write(bytes, offset, length);
            }
        };

        try (final TestServer server = new TestServer(service, streamFilter)) {
            final FutureTask<Response> response = server.postAsync(transport);
            try {
                assertThat(writing.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(requests.get().errorDelivered.await(2, TimeUnit.SECONDS))
                        .isTrue();
                assertDeadline(response.get(3, TimeUnit.SECONDS));
                assertThat(requests.get().errors).hasValue(1);
                assertThat(requests.get().errorMarker.get()).isSameAs(server.marker);
            } finally {
                releaseWrite.countDown();
                response.cancel(true);
                if (publisher.get() != null) {
                    publisher.get().join(1_000);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void serviceOpenAndUnaryDispatchSeeTheRequestContext(final Transport transport) throws Exception {
        final AtomicReference<RequestMarker> dispatchMarker = new AtomicReference<RequestMarker>();
        final TestService service = new TestService(replies -> Pipelines.<Bytes, Bytes>unary()
                .mapRequest(bytes -> bytes)
                .method(bytes -> {
                    dispatchMarker.set(currentMarker());
                    return Bytes.EMPTY;
                })
                .mapResponse(bytes -> bytes)
                .respondTo(replies)
                .build());

        try (final TestServer server = new TestServer(service)) {
            final Response response = server.postAsync(transport).get(3, TimeUnit.SECONDS);
            assertThat(response.grpcStatus()).isEqualTo(GrpcStatus.OK.ordinal());
            assertThat(service.openMarker.get()).isSameAs(server.marker);
            assertThat(dispatchMarker.get()).isSameAs(server.marker);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {CONTENT_TYPE, "application/grpc-web-text+proto"})
    void deadlineDuringStalledHttp1BodySendsStatusBeforeClosingConnection(final String contentType) throws Exception {
        final RecordingPipeline requests = new RecordingPipeline(null);
        try (final TestServer server = new TestServer(new TestService(replies -> requests));
                final Socket socket = new Socket("localhost", server.server.port())) {
            socket.setSoTimeout(3_000);
            socket.getOutputStream()
                    .write(("POST " + PATH + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + contentType
                                    + "\r\ngrpc-timeout: " + TIMEOUT + "\r\nContent-Length: 5\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            // Leave a frame unfinished without half-closing the socket: the body read must hit the RPC deadline.
            socket.getOutputStream().write(contentType.contains("text") ? 'A' : 0);
            socket.getOutputStream().flush();
            final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reader.readLine()).isEqualTo("HTTP/1.1 200 OK");
            final StringBuilder headers = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                headers.append(line.toLowerCase(java.util.Locale.ROOT)).append('\n');
            }
            assertThat(headers.toString()).contains("grpc-status: 4\n").contains("connection: close\n");
            assertThat(reader.read()).isEqualTo(-1);
            assertThat(requests.errorDelivered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests.errors).hasValue(1);
        }
    }

    @Test
    void deadlineAfterInitialReplyFinishesTrailersWhileHttp1BodyIsStillStalled() throws Exception {
        final AtomicReference<Pipeline<? super Bytes>> replies = new AtomicReference<Pipeline<? super Bytes>>();
        final RecordingPipeline requests = new RecordingPipeline(new Pipeline<Bytes>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final Bytes bytes) {
                replies.get().onNext(Bytes.EMPTY);
            }

            @Override
            public void onError(final Throwable error) {}

            @Override
            public void onComplete() {}
        });
        final TestService service = new TestService(ServiceInterface.MethodType.SERVER_STREAMING, responses -> {
            replies.set(responses);
            return requests;
        });

        try (final TestServer server = new TestServer(service);
                final Socket socket = new Socket("localhost", server.server.port())) {
            socket.setSoTimeout(3_000);
            socket.getOutputStream()
                    .write(("POST " + PATH + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + CONTENT_TYPE
                                    + "\r\ngrpc-timeout: " + TIMEOUT + "\r\nContent-Length: 10\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            // The first complete frame triggers a reply, but the second declared frame never arrives.
            socket.getOutputStream().write(new byte[5]);
            socket.getOutputStream().flush();
            final String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertThat(response).startsWith("HTTP/1.1 200 OK\r\n").contains("grpc-status: 4\r\n");
            assertThat(requests.errorDelivered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests.errors).hasValue(1);
        }
    }

    @Test
    void responseWriteFailureTerminatesCallEvenWhenProducerOnlyCatchesTheFailure() throws Exception {
        final AtomicReference<Pipeline<? super Bytes>> replies = new AtomicReference<Pipeline<? super Bytes>>();
        final CountDownLatch firstReply = new CountDownLatch(1);
        final RecordingPipeline requests = new RecordingPipeline(new Pipeline<Bytes>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final Bytes bytes) {
                replies.get().onNext(Bytes.EMPTY);
                firstReply.countDown();
            }

            @Override
            public void onError(final Throwable error) {}

            @Override
            public void onComplete() {}
        });
        final TestService service = new TestService(ServiceInterface.MethodType.SERVER_STREAMING, responses -> {
            replies.set(responses);
            return requests;
        });

        try (final TestServer server = new TestServer(service)) {
            try (final Socket socket = new Socket("localhost", server.server.port())) {
                socket.setSoTimeout(3_000);
                socket.setSoLinger(true, 0);
                socket.getOutputStream()
                        .write(("POST " + PATH + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + CONTENT_TYPE
                                        + "\r\nContent-Length: 5\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().write(new byte[5]);
                socket.getOutputStream().flush();
                final BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                assertThat(reader.readLine()).isEqualTo("HTTP/1.1 200 OK");
                assertThat(firstReply.await(2, TimeUnit.SECONDS)).isTrue();
            }

            final CountDownLatch failedWrite = new CountDownLatch(1);
            final Thread producer = Thread.ofVirtual().start(() -> {
                try {
                    // The peer reset the socket. Keep writing until that reset reaches a server write.
                    final Bytes message = Bytes.wrap(new byte[64 * 1024]);
                    for (int i = 0; i < 64; i++) {
                        replies.get().onNext(message);
                    }
                } catch (final RuntimeException e) {
                    // Deliberately do not call onError/onComplete: transport cleanup must be sufficient.
                    failedWrite.countDown();
                }
            });
            try {
                assertThat(failedWrite.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(requests.errorDelivered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(requests.errors).hasValue(1);
            } finally {
                producer.interrupt();
                producer.join(1_000);
            }
        }
    }

    private static void assertDeadline(final Response response) {
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.grpcStatus()).isEqualTo(GrpcStatus.DEADLINE_EXCEEDED.ordinal());
    }

    private static RequestMarker currentMarker() {
        return Contexts.context()
                .flatMap(context -> context.get(RequestMarker.class))
                .orElse(null);
    }

    private static final class RequestMarker {}

    private static final class TestService implements ServiceInterface {
        private final Function<Pipeline<? super Bytes>, Pipeline<? super Bytes>> opener;
        private final MethodType methodType;
        private final AtomicReference<RequestMarker> openMarker = new AtomicReference<>();

        private TestService(final Function<Pipeline<? super Bytes>, Pipeline<? super Bytes>> opener) {
            this(MethodType.UNARY, opener);
        }

        private TestService(
                final MethodType methodType, final Function<Pipeline<? super Bytes>, Pipeline<? super Bytes>> opener) {
            this.methodType = methodType;
            this.opener = opener;
        }

        @Override
        public String serviceName() {
            return "Test";
        }

        @Override
        public String fullName() {
            return "lifecycle.Test";
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
                    return methodType;
                }
            });
        }

        @Override
        public Pipeline<? super Bytes> open(
                final Method method, final RequestOptions opts, final Pipeline<? super Bytes> responses) {
            openMarker.set(currentMarker());
            return opener.apply(responses);
        }
    }

    private static final class RecordingPipeline implements Pipeline<Bytes> {
        private final Pipeline<? super Bytes> delegate;
        private final CountDownLatch errorDelivered = new CountDownLatch(1);
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicReference<Throwable> lastError = new AtomicReference<>();
        private final AtomicReference<RequestMarker> errorMarker = new AtomicReference<>();
        private final Runnable errorAction;

        private RecordingPipeline(final Pipeline<? super Bytes> delegate) {
            this(delegate, () -> {});
        }

        private RecordingPipeline(final Pipeline<? super Bytes> delegate, final Runnable errorAction) {
            this.delegate = delegate;
            this.errorAction = errorAction;
        }

        @Override
        public void onSubscribe(final Flow.Subscription subscription) {
            if (delegate != null) {
                delegate.onSubscribe(subscription);
            }
        }

        @Override
        public void onNext(final Bytes bytes) {
            if (delegate != null) {
                delegate.onNext(bytes);
            }
        }

        @Override
        public void onError(final Throwable error) {
            errors.incrementAndGet();
            lastError.set(error);
            errorMarker.set(currentMarker());
            try {
                errorAction.run();
                if (delegate != null) {
                    delegate.onError(error);
                }
            } finally {
                errorDelivered.countDown();
            }
        }

        @Override
        public void onComplete() {
            if (delegate != null) {
                delegate.onComplete();
            }
        }

        @Override
        public void clientEndStreamReceived() {
            if (delegate != null) {
                delegate.clientEndStreamReceived();
            }
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final RequestMarker marker = new RequestMarker();
        private final WebServer server;
        private final HttpClient http1Client =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        private final Http2Client http2Client;

        private TestServer(final ServiceInterface service) {
            this(service, null);
        }

        private TestServer(final ServiceInterface service, final UnaryOperator<OutputStream> streamFilter) {
            server = WebServer.builder()
                    .port(0)
                    .routing(routing -> {
                        routing.any((request, response) -> {
                            request.context().register(marker);
                            if (streamFilter != null) {
                                response.streamFilter(streamFilter);
                            }
                            response.next();
                        });
                        routing.register(PbjGrpcWebService.of(service));
                    })
                    .build()
                    .start();
            http2Client = Http2Client.builder()
                    .baseUri("http://localhost:" + server.port())
                    .protocolConfig(Http2ClientProtocolConfig.builder()
                            .priorKnowledge(true)
                            .build())
                    .build();
        }

        private FutureTask<Response> postAsync(final Transport transport) {
            final FutureTask<Response> task = new FutureTask<>(() -> {
                if (transport == Transport.HTTP_1_1) {
                    final HttpRequest request = HttpRequest.newBuilder(
                                    URI.create("http://localhost:" + server.port() + PATH))
                            .timeout(Duration.ofSeconds(3))
                            .header("content-type", CONTENT_TYPE)
                            .header("grpc-timeout", TIMEOUT)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[5]))
                            .build();
                    final HttpResponse<byte[]> response =
                            http1Client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    return new Response(
                            response.statusCode(), response.headers().firstValue("grpc-status"), response.body());
                }
                try (final Http2ClientResponse response = http2Client
                        .post()
                        .path(PATH)
                        .readTimeout(Duration.ofSeconds(3))
                        .contentType(HttpMediaType.create(CONTENT_TYPE))
                        .header(HeaderNames.create("grpc-timeout"), TIMEOUT)
                        .submit(new byte[5])) {
                    return new Response(
                            response.status().code(),
                            response.headers().first(HeaderNames.create("grpc-status")),
                            response.entity().inputStream().readAllBytes());
                }
            });
            Thread.ofVirtual().start(task);
            return task;
        }

        @Override
        public void close() {
            http1Client.close();
            server.stop();
        }
    }

    private record Response(int httpStatus, Optional<String> statusHeader, byte[] body) {
        private int grpcStatus() {
            if (statusHeader.isPresent()) {
                return Integer.parseInt(statusHeader.get());
            }
            final ByteBuffer frames = ByteBuffer.wrap(body);
            while (frames.hasRemaining()) {
                final int flags = frames.get() & 0xff;
                final byte[] payload = new byte[frames.getInt()];
                frames.get(payload);
                if ((flags & 0x80) != 0) {
                    for (final String line : new String(payload, StandardCharsets.US_ASCII).split("\r\n")) {
                        if (line.startsWith("grpc-status: ")) {
                            return Integer.parseInt(line.substring("grpc-status: ".length()));
                        }
                    }
                }
            }
            throw new AssertionError("Response has no grpc-status");
        }
    }
}
