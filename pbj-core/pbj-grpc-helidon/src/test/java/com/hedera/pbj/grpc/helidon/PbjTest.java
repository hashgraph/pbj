/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.protobuf.util.JsonFormat;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.GreeterGrpc;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PbjTest {
    private static final MediaType APPLICATION_GRPC_PROTO =
            HttpMediaType.create("application/grpc+proto");
    private static final MediaType APPLICATION_GRPC_JSON =
            HttpMediaType.create("application/grpc+json");
    private static final MediaType APPLICATION_GRPC_STRING =
            HttpMediaType.create("application/grpc+string");
    private static final MediaType APPLICATION_RANDOM = HttpMediaType.create("application/random");
    private static final String SAY_HELLO_PATH = "/greeter.Greeter/sayHello";

    private static final HelloRequest SIMPLE_REQUEST =
            HelloRequest.newBuilder().setName("PBJ").build();

    private static final HelloReply SIMPLE_REPLY =
            HelloReply.newBuilder().setMessage("Hello PBJ").build();

    private static WebServer SERVER;
    private static Http2Client CLIENT;
    private static ManagedChannel CHANNEL;
    private static GreeterProxy PROXY;

    @BeforeAll
    static void setup() {
        // The proxy is used to switch out the service implementation for each test
        PROXY = new GreeterProxy();

        // Set up the server
        SERVER =
                WebServer.builder()
                        .port(8080)
                        .addRouting(PbjRouting.builder().service(PROXY))
                        .build()
                        .start();

        CLIENT = Http2Client.builder().baseUri("http://localhost:8080").build();

        CHANNEL = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
    }

    @AfterAll
    static void teardown() {
        CHANNEL.shutdown();
        SERVER.stop();
    }

    @BeforeEach
    void setupEachRun() {
        PROXY.svc = new GreeterServiceImpl();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // HTTP2 Tests
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    class Http2Tests {
        /**
         * Verify the path is case-sensitive.
         *
         * <p>SPEC:
         *
         * <pre>
         * Path is case-sensitive. Some gRPC implementations may allow the Path format shown above to be overridden,
         * but this functionality is strongly discouraged. gRPC does not go out of its way to break users that are using
         * this kind of override, but we do not actively support it, and some functionality (e.g., service config
         * support) will not work when the path is not of the form shown above.
         * </pre>
         */
        @Test
        void badCaseOnPathIsNotFound() {
            try (var response =
                    CLIENT.post()
                            .contentType(APPLICATION_GRPC_PROTO)
                            .path(SAY_HELLO_PATH.toUpperCase())
                            .submit(messageBytes(SIMPLE_REQUEST))) {
                assertThat(response.status().code()).isEqualTo(200);
                assertThat(grpcStatus(response)).isEqualTo(GrpcStatus.NOT_FOUND);
            }
        }

        /**
         * Verify that only POST is supported.
         *
         * <p>SPEC:
         *
         * <pre>
         * Only POST can be used for gRPC calls.
         * </pre>
         */
        @ParameterizedTest
        @ValueSource(strings = {"GET", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE"})
        void mustUsePost(final String methodName) {
            try (var response =
                    CLIENT.method(Method.create(methodName))
                            .contentType(APPLICATION_GRPC_PROTO)
                            .path(SAY_HELLO_PATH)
                            .request()) {

                // This is consistent with existing behavior on Helidon, but I would have expected
                // the response code
                // to be 405 Method Not Allowed instead. See PbjProtocolSelector for the check for
                // POST.
                assertThat(response.status().code()).isEqualTo(404);
            }
        }
    }

    @Nested
    class ContentTypeTests {
        /**
         * Verify that the server responds with 415 when the Content-Type is not specified
         *
         * <p>Spec:
         *
         * <pre>
         * If Content-Type does not begin with "application/grpc", gRPC servers SHOULD respond with HTTP status of 415
         * (Unsupported Media Type). This will prevent other HTTP/2 clients from interpreting a gRPC error response, which
         * uses status 200 (OK), as successful.
         * </pre>
         */
        @Test
        void contentTypeMustBeSet() {
            try (var response =
                    CLIENT.post().path(SAY_HELLO_PATH).submit(messageBytes(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(415);
            }
        }

        /**
         * Verify that the server responds with 415 when the Content-Type does not start with
         * "application/grpc"
         */
        @Test
        void contentTypeMustStartWithApplicationGrpc() {
            try (var response =
                    CLIENT.post()
                            .path(SAY_HELLO_PATH)
                            .contentType(APPLICATION_RANDOM)
                            .submit(messageBytes(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(415);
            }
        }

        /** Verify that "application/grpc+json" requests are accepted */
        @Test
        void contentTypeCanBeJSON() {
            try (var response =
                    CLIENT.post()
                            .path(SAY_HELLO_PATH)
                            .contentType(APPLICATION_GRPC_JSON)
                            .submit(messageBytesJson(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(200);
                assertThat(response.headers().contentType().orElseThrow().text())
                        .isEqualTo("application/grpc+json");

                final var reply =
                        decodeJsonReply(new ReadableStreamingData(response.inputStream()));
                assertThat(reply).isEqualTo(SIMPLE_REPLY);
            }
        }

        /**
         * Verify that "application/grpc+proto" and "application/grpc" both support protobuf
         * encoding
         */
        @ParameterizedTest
        @ValueSource(strings = {"application/grpc+proto", "application/grpc"})
        void contentTypeCanBeProtobuf(final String contentType) {
            try (var response =
                    CLIENT.post()
                            .path(SAY_HELLO_PATH)
                            .contentType(MediaTypes.create(contentType))
                            .submit(messageBytes(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(200);
                assertThat(response.headers().contentType().orElseThrow().text())
                        .isEqualTo(contentType);

                final var tx = decodeReply(new ReadableStreamingData(response.inputStream()));
                assertThat(tx).isEqualTo(SIMPLE_REPLY);
            }
        }

        /** Verify that a custom suffix of the content type is supported */
        @Test
        void contentTypeCanBeCustom() throws IOException {
            try (var response =
                    CLIENT.post()
                            .path(SAY_HELLO_PATH)
                            .contentType(APPLICATION_GRPC_STRING)
                            .submit(messageBytes("dude".getBytes(StandardCharsets.UTF_8)))) {

                assertThat(response.status().code()).isEqualTo(200);
                assertThat(response.headers().contentType().orElseThrow().text())
                        .isEqualTo(APPLICATION_GRPC_STRING.text());

                // The first five bytes are framing -- compression + length
                final var data = response.inputStream().readAllBytes();
                assertThat(new String(data, 5, data.length - 5, StandardCharsets.UTF_8))
                        .isEqualTo("Hello dude");
            }
        }
    }

    @Nested
    class GrpcEncodingTests {
        /**
         * If the client sets "grpc-accept-encoding" such that it does NOT include any values
         * supported by the server, then the server should return IDENTITY.
         */
        @Test
        void acceptEncodingExcludesAllSupportedEncodings() {
            try (var response =
                    CLIENT.post()
                            .contentType(APPLICATION_GRPC_PROTO)
                            .path(SAY_HELLO_PATH)
                            .header(HeaderNames.create("grpc-accept-encoding"), "gzip, deflate")
                            .submit(messageBytes(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(200);
                response.entity().consume();
                assertThat(grpcStatus(response)).isEqualTo(GrpcStatus.OK);
                assertThat(response.headers().get(HeaderNames.create("grpc-encoding")).get())
                        .isEqualTo("identity");
                assertThat(response.headers().get(HeaderNames.create("grpc-accept-encoding")).get())
                        .isEqualTo("identity");
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // METADATA
    //
    // SPEC:
    //
    // Custom-Metadata is an arbitrary set of key-value pairs defined by the application layer.
    // Header names starting with "grpc-" but not listed here are reserved for future GRPC use
    // and should not be used by applications as Custom-Metadata.
    //
    // Note that HTTP2 does not allow arbitrary octet sequences for header values so binary header
    // values must be encoded using Base64 as per https://tools.ietf.org/html/rfc4648#section-4.
    // Implementations MUST accept padded and un-padded values and should emit un-padded values.
    // Applications define binary headers by having their names end with "-bin". Runtime libraries
    // use this suffix to detect binary headers and properly apply base64 encoding & decoding as
    // headers are sent and received.
    //
    // Custom-Metadata header order is not guaranteed to be preserved except for values with
    // duplicate header names. Duplicate header names may have their values joined with "," as the
    // delimiter and be considered semantically equivalent. Implementations must split
    // Binary-Headers on "," before decoding the Base64-encoded values.
    //
    // ASCII-Value should not have leading or trailing whitespace. If it contains leading or
    // trailing whitespace, it may be stripped. The ASCII-Value character range defined is stricter
    // than HTTP. Implementations must not error due to receiving an invalid ASCII-Value that's a
    // valid field-value in HTTP, but the precise behavior is not strictly defined: they may throw
    // the value away or accept the value. If accepted, care must be taken to make sure that the
    // application is permitted to echo the value back as metadata. For example, if the metadata is
    // provided to the application as a list in a request, the application should not trigger an
    // error by providing that same list as the metadata in the response.
    //
    // TESTS:
    //   - Not implemented
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Nested
    class MetadataTests {}

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Request-Headers
    //
    // SPEC:
    //
    // Servers may limit the size of Request-Headers, with a default of 8 KiB suggested.
    // Implementations are encouraged to compute total header size like HTTP/2's
    // SETTINGS_MAX_HEADER_LIST_SIZE: the sum of all header fields, for each field the
    // sum of the uncompressed field name and value lengths plus 32, with binary values'
    // lengths being post-Base64.
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Nested
    class CompressionTests {
        /**
         * Verify that the server responds with grpc-accept-encoding and UNIMPLEMENTED for
         * unsupported compression schemes.
         */
        @ParameterizedTest
        @ValueSource(strings = {"gzip", "deflate", "random"})
        void compressionNotSupported(final String grpcEncoding) {
            try (var response =
                    CLIENT.post()
                            .contentType(APPLICATION_GRPC_PROTO)
                            .path(SAY_HELLO_PATH)
                            .header(HeaderNames.create("grpc-encoding"), grpcEncoding)
                            .submit(messageBytes(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(200);
                assertThat(grpcStatus(response)).isEqualTo(GrpcStatus.UNIMPLEMENTED);
                assertThat(response.headers().get(HeaderNames.create("grpc-accept-encoding")).get())
                        .isEqualTo("identity");
            }
        }

        /**
         * Verify that an explicit call uses IDENTITY, and that the response headers includes the
         * grpc-accept-encoding header with a value of "identity".
         */
        @Test
        void identityIfNotSpecified() {
            try (var response =
                    CLIENT.post()
                            .contentType(APPLICATION_GRPC_PROTO)
                            .path(SAY_HELLO_PATH)
                            .submit(messageBytes(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(200);
                response.entity().consume();
                assertThat(grpcStatus(response)).isEqualTo(GrpcStatus.OK);
                assertThat(response.headers().get(HeaderNames.create("grpc-accept-encoding")).get())
                        .isEqualTo("identity");
            }
        }

        /**
         * Verify that an explicit call uses IDENTITY, and that the response headers includes the
         * grpc-accept-encoding header with a value of "identity".
         */
        @Test
        void identityIfSpecified() {
            try (var response =
                    CLIENT.post()
                            .contentType(APPLICATION_GRPC_PROTO)
                            .path(SAY_HELLO_PATH)
                            .header(HeaderNames.create("grpc-encoding"), "identity")
                            .submit(messageBytes(SIMPLE_REQUEST))) {

                assertThat(response.status().code()).isEqualTo(200);
                response.entity().consume();
                assertThat(grpcStatus(response)).isEqualTo(GrpcStatus.OK);
                assertThat(response.headers().get(HeaderNames.create("grpc-accept-encoding")).get())
                        .isEqualTo("identity");
            }
        }
    }

    @Nested
    class DeadlineTests {
        @Test
        void deadlineExceeded() {
            PROXY.svc =
                    new GreeterAdapter() {
                        @Override
                        public HelloReply sayHello(HelloRequest request) {
                            try {
                                // This should be plenty of time. Shouldn't be flaky...
                                Thread.sleep(Duration.ofSeconds(1));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }

                            return HelloReply.newBuilder()
                                    .setMessage("Hello " + request.getName())
                                    .build();
                        }
                    };

            final var stub =
                    GreeterGrpc.newBlockingStub(CHANNEL)
                            .withDeadline(Deadline.after(1, TimeUnit.NANOSECONDS));

            try {
                //noinspection ResultOfMethodCallIgnored
                stub.sayHello(SIMPLE_REQUEST);
                fail("This line should never be reached");
            } catch (StatusRuntimeException e) {
                assertThat(e.getStatus().getCode()).isEqualTo(Status.DEADLINE_EXCEEDED.getCode());
            }
        }
    }

    @Nested
    class AuthenticationTests {}

    @Nested
    class UnaryTests {
        // Intentionally uses grpc.io to verify compatibility
        @Test
        void unaryCall() {
            final var stub = GreeterGrpc.newBlockingStub(CHANNEL);
            final var reply = stub.sayHello(SIMPLE_REQUEST);
            assertThat(reply.getMessage()).isEqualTo("Hello PBJ");
        }

        /**
         * Given a handler that throws exceptions, make sure the call fails accordingly. Note that I
         * don't throw the OK code, if that is thrown, the call actually terminates with success,
         * since that is the success code!
         *
         * @param grpcStatusCode the code
         */
        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
        void exceptionThrownDuringHandling(final int grpcStatusCode) {
            PROXY.svc =
                    new GreeterAdapter() {
                        @Override
                        public HelloReply sayHello(HelloRequest request) {
                            throw new GrpcException(GrpcStatus.values()[grpcStatusCode]);
                        }
                    };

            final var stub = GreeterGrpc.newBlockingStub(CHANNEL);

            try {
                //noinspection ResultOfMethodCallIgnored
                stub.sayHello(SIMPLE_REQUEST);
                fail("An exception should have been thrown");
            } catch (StatusRuntimeException ex) {
                assertThat(ex.getStatus().getCode().value()).isEqualTo(grpcStatusCode);
            }
        }

        @Test
        void exceptionThrownWhileOpening() {
            // Try this list of exceptions
            final var exceptions =
                    List.of(
                            new GrpcException(GrpcStatus.UNKNOWN),
                            new RuntimeException("Error opening"));

            for (final var ex : exceptions) {
                PROXY.svc =
                        new GreeterAdapter() {
                            @Override
                            @NonNull
                            public Pipeline<? super Bytes> open(
                                    @NonNull Method method,
                                    @NonNull RequestOptions options,
                                    @NonNull Flow.Subscriber<? super Bytes> replies) {
                                throw ex;
                            }
                        };

                final var stub = GreeterGrpc.newBlockingStub(CHANNEL);

                try {
                    //noinspection ResultOfMethodCallIgnored
                    stub.sayHello(SIMPLE_REQUEST);
                    fail("An exception should have been thrown");
                } catch (StatusRuntimeException e) {
                    assertThat(e.getStatus().getCode().value())
                            .isEqualTo(GrpcStatus.UNKNOWN.ordinal());
                }
            }
        }
    }

    @Nested
    class StreamingServerTests {
        @Test
        void streamingServer() {
            final var stub = GreeterGrpc.newBlockingStub(CHANNEL);
            final var replies = stub.sayHelloStreamReply(SIMPLE_REQUEST);
            final var messages = new ArrayList<HelloReply>();
            replies.forEachRemaining(messages::add);
            assertThat(messages).hasSize(10).allMatch(reply -> reply.getMessage().equals("Hello!"));
        }
    }

    @Nested
    class StreamingClientTests {
        @Test
        void streamingClient() throws InterruptedException {
            final var latch = new CountDownLatch(1);
            final var response = new AtomicReference<HelloReply>();
            final var requestObserver =
                    GreeterGrpc.newStub(CHANNEL)
                            .sayHelloStreamRequest(
                                    new StreamObserver<>() {
                                        @Override
                                        public void onNext(HelloReply helloReply) {
                                            response.set(helloReply);
                                        }

                                        @Override
                                        public void onError(Throwable throwable) {
                                            // FUTURE: Test this failure condition
                                            System.err.println("Error: " + throwable.getMessage());
                                        }

                                        @Override
                                        public void onCompleted() {
                                            latch.countDown();
                                        }
                                    });

            requestObserver.onNext(HelloRequest.newBuilder().setName("Alice").build());
            requestObserver.onNext(HelloRequest.newBuilder().setName("Bob").build());
            requestObserver.onNext(HelloRequest.newBuilder().setName("Carol").build());
            requestObserver.onCompleted();

            assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();

            assertThat(response.get())
                    .isEqualTo(
                            HelloReply.newBuilder().setMessage("Hello Alice, Bob, Carol").build());
        }
    }

    @Nested
    class BidiStreamingTests {
        @Test
        void streamingBidi() throws InterruptedException {
            final var latch = new CountDownLatch(1);
            final var response = new ArrayList<HelloReply>();
            final var requestObserver =
                    GreeterGrpc.newStub(CHANNEL)
                            .sayHelloStreamBidi(
                                    new StreamObserver<>() {
                                        @Override
                                        public void onNext(HelloReply helloReply) {
                                            response.add(helloReply);
                                        }

                                        @Override
                                        public void onError(Throwable throwable) {
                                            latch.countDown();
                                            fail("Encountered unexpected exception", throwable);
                                        }

                                        @Override
                                        public void onCompleted() {
                                            latch.countDown();
                                        }
                                    });

            requestObserver.onNext(HelloRequest.newBuilder().setName("Alice").build());
            requestObserver.onNext(HelloRequest.newBuilder().setName("Bob").build());
            requestObserver.onNext(HelloRequest.newBuilder().setName("Carol").build());
            requestObserver.onCompleted();

            //noinspection ResultOfMethodCallIgnored
            latch.await(1, TimeUnit.MINUTES);

            assertThat(response)
                    .hasSize(3)
                    .allMatch(reply -> reply.getMessage().startsWith("Hello"));
        }
    }

    @Nested
    class ConcurrencyTests {
        private static final int NUM_CONCURRENT = 10;
        private static final int NUM_REQUESTS = 100_000;
        private final ConcurrentLinkedQueue<AssertionError> failures =
                new ConcurrentLinkedQueue<>();
        private final CountDownLatch latch = new CountDownLatch(NUM_REQUESTS);
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicInteger nextClientId = new AtomicInteger(0);
        private final BlockingDeque<ManagedChannel> channels = new LinkedBlockingDeque<>();

        @BeforeEach
        void setup() {
            // Create a pool of channels. I want to have many, many, unique concurrent calls, but
            // there is a practical limit to the number of concurrent channels. If the deque is
            // empty, there are no available channels.
            for (int i = 0; i < NUM_CONCURRENT; i++) {
                final var channel =
                        ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();

                channels.offer(channel);
            }
        }

        @AfterEach
        void teardown() {
            channels.forEach(ManagedChannel::shutdownNow);
            channels.forEach(
                    c -> {
                        try {
                            c.awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        // FUTURE Try to test a bad client that sends multiple messages for a unary call

        @Test
        @Disabled(
                "This test passes locally but fails in CI. More work is needed to see why. It is"
                        + " timing dependent.")
        void manyConcurrentUnaryCalls() throws InterruptedException {
            // For each virtual client, execute the query and get the reply. Put the reply here in
            // this map. The key
            // is the unique ID of the client (integer), and the value is the reply.
            for (int i = 0; i < NUM_CONCURRENT; i++) {
                executor.submit(new TestClient(nextClientId.getAndIncrement()));
            }

            // It should take less than 20 seconds
            assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();

            // If there were any failures, throw them.
            assertThat(failures).isEmpty();
        }

        private final class TestClient implements Runnable {
            private final int clientId;

            TestClient(int clientId) {
                this.clientId = clientId;
            }

            @Override
            public void run() {
                try {
                    final var channel = channels.takeFirst();
                    try {
                        final var stub = GreeterGrpc.newFutureStub(channel);
                        final var request =
                                HelloRequest.newBuilder().setName("" + clientId).build();
                        final var future = stub.sayHello(request);
                        final var reply = future.get(10, TimeUnit.SECONDS);
                        if (!reply.getMessage().equals("Hello " + clientId)) {
                            failures.offer(new AssertionError("Failed " + clientId));
                        }

                        final var id = nextClientId.getAndIncrement();
                        if (id < NUM_REQUESTS) {
                            executor.submit(new TestClient(id));
                        }
                    } catch (Exception e) {
                        // If some random exception occurs, just reschedule this task for later
                        // execution.
                        executor.submit(this);
                    } finally {
                        channels.offer(channel);
                    }
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    latch.countDown();
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Utility methods
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private GrpcStatus grpcStatus(Http2ClientResponse response) {
        try {
            return grpcStatus(response.headers());
        } catch (NoSuchElementException e) {
            return grpcStatus(response.trailers());
        }
    }

    private GrpcStatus grpcStatus(Headers headers) {
        final var grpcStatus = headers.get(GrpcHeaders.GRPC_STATUS);
        final var ordinal = Integer.parseInt(grpcStatus.values());
        return GrpcStatus.values()[ordinal];
    }

    private HelloReply decodeReply(ReadableStreamingData rsd) {
        try {
            assertThat(rsd.readByte()).isEqualTo((byte) 0); // No Compression
            final var responseLength = (int) rsd.readUnsignedInt();
            final var responseData = new byte[responseLength];
            rsd.readBytes(responseData);
            return HelloReply.parseFrom(responseData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HelloReply decodeJsonReply(ReadableStreamingData rsd) {
        try {
            assertThat(rsd.readByte()).isEqualTo((byte) 0); // No Compression
            final var responseLength = (int) rsd.readUnsignedInt();
            final var responseData = new byte[responseLength];
            rsd.readBytes(responseData);
            final var builder = HelloReply.newBuilder();
            JsonFormat.parser().merge(new String(responseData, StandardCharsets.UTF_8), builder);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] messageBytes(byte[] data) {
        final var out = new ByteArrayOutputStream();
        final WritableStreamingData wsd = new WritableStreamingData(out);
        wsd.writeByte((byte) 0);
        wsd.writeUnsignedInt(data.length);
        wsd.writeBytes(data);
        return out.toByteArray();
    }

    private byte[] messageBytes(HelloRequest req) {
        final var data = req.toByteArray();
        return messageBytes(data);
    }

    private byte[] messageBytesJson(HelloRequest req) {
        try {
            final var data = JsonFormat.printer().print(req).getBytes(StandardCharsets.UTF_8);
            return messageBytes(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class GreeterServiceImpl implements GreeterService {
        GrpcStatus errorToThrow = null;

        @Override
        public HelloReply sayHello(HelloRequest request) {
            if (errorToThrow != null) {
                throw new GrpcException(errorToThrow);
            }

            return HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
        }

        // Streams of stuff coming from the client, with a single response.
        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamRequest(
                Flow.Subscriber<? super HelloReply> replies) {
            final var names = new ArrayList<String>();
            return new Pipeline<>() {
                @Override
                public void clientEndStreamReceived() {
                    onComplete();
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    names.add(item.getName());
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    final var reply =
                            HelloReply.newBuilder()
                                    .setMessage("Hello " + String.join(", ", names))
                                    .build();
                    replies.onNext(reply);
                    replies.onComplete();
                }
            };
        }

        @Override
        public void sayHelloStreamReply(
                HelloRequest request, Flow.Subscriber<? super HelloReply> replies) {
            for (int i = 0; i < 10; i++) {
                replies.onNext(HelloReply.newBuilder().setMessage("Hello!").build());
            }

            replies.onComplete();
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamBidi(
                Flow.Subscriber<? super HelloReply> replies) {
            // Here we receive info from the client. In this case, it is a stream of requests with
            // names. We will respond with a stream of replies.
            return new Pipeline<>() {
                @Override
                public void clientEndStreamReceived() {
                    onComplete();
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    replies.onNext(
                            HelloReply.newBuilder().setMessage("Hello " + item.getName()).build());
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    replies.onComplete();
                }
            };
        }
    }

    private interface GreeterAdapter extends GreeterService {
        @Override
        default HelloReply sayHello(HelloRequest request) {
            return null;
        }

        @Override
        default Pipeline<? super HelloRequest> sayHelloStreamRequest(
                Flow.Subscriber<? super HelloReply> replies) {
            return null;
        }

        @Override
        default void sayHelloStreamReply(
                HelloRequest request, Flow.Subscriber<? super HelloReply> replies) {}

        @Override
        default Pipeline<? super HelloRequest> sayHelloStreamBidi(
                Flow.Subscriber<? super HelloReply> replies) {
            return null;
        }
    }

    private static class GreeterProxy implements GreeterService {
        GreeterService svc;

        @Override
        @NonNull
        public HelloReply sayHello(HelloRequest request) {
            return svc.sayHello(request);
        }

        @Override
        @NonNull
        public Pipeline<? super HelloRequest> sayHelloStreamRequest(
                Flow.Subscriber<? super HelloReply> replies) {
            return svc.sayHelloStreamRequest(replies);
        }

        @Override
        public void sayHelloStreamReply(
                HelloRequest request, Flow.Subscriber<? super HelloReply> replies) {
            svc.sayHelloStreamReply(request, replies);
        }

        @Override
        @NonNull
        public Pipeline<? super HelloRequest> sayHelloStreamBidi(
                Flow.Subscriber<? super HelloReply> replies) {
            return svc.sayHelloStreamBidi(replies);
        }

        @Override
        @NonNull
        public Pipeline<? super Bytes> open(
                @NonNull Method method,
                @NonNull RequestOptions options,
                @NonNull Flow.Subscriber<? super Bytes> replies) {
            return svc.open(method, options, replies);
        }
    }
}
