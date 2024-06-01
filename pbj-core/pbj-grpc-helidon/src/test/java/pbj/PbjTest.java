package pbj;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.util.JsonFormat;
import com.hedera.pbj.grpc.helidon.GrpcStatus;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import greeter.GreeterGrpc;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PbjTest {
    private static final MediaType APPLICATION_GRPC_PROTO = HttpMediaType.create("application/grpc+proto");
    private static final MediaType APPLICATION_GRPC_JSON = HttpMediaType.create("application/grpc+json");
    private static final MediaType APPLICATION_GRPC_STRING = HttpMediaType.create("application/grpc+string");
    private static final MediaType APPLICATION_RANDOM = HttpMediaType.create("application/random");
    private static Http2Client CLIENT;
    private static final String SAY_HELLO_PATH = "/greeter.Greeter/sayHello";
    private static final String SAY_HELLO_CLIENT_STREAM_PATH = "/greeter.Greeter/sayHelloStreamRequest";

    private static final HelloRequest SIMPLE_REQUEST = HelloRequest.newBuilder()
            .setName("PBJ")
            .build();

    private static final HelloReply SIMPLE_REPLY = HelloReply.newBuilder()
            .setMessage("Hello PBJ")
            .build();

    private static ManagedChannel CHANNEL;

    @BeforeAll
    static void setup() {
        // Set up the server
        WebServer.builder()
                .port(8080)
                .addRouting(PbjRouting.builder()
                        .service(new GreeterServiceImpl()))
                .build()
                .start();

        CLIENT = Http2Client.builder()
                .baseUri("http://localhost:8080")
                .build();

        CHANNEL = ManagedChannelBuilder.forAddress("localhost", 8080)
                .usePlaintext()
                .build();

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // HTTP2 Path
    //
    // SPEC:
    //
    // Path is case-sensitive. Some gRPC implementations may allow the Path format shown above to be overridden, but
    // this functionality is strongly discouraged. gRPC does not go out of its way to break users that are using this
    // kind of override, but we do not actively support it, and some functionality (e.g., service config support) will
    // not work when the path is not of the form shown above.
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** Verify the path is case-sensitive */
    @Test
    void badCaseOnPathIsNotFound() {
        try (var response = CLIENT.post()
                .contentType(APPLICATION_GRPC_PROTO)
                .path(SAY_HELLO_PATH.toUpperCase())
                .submit(messageBytes(SIMPLE_REQUEST))) {
            assertThat(response.status().code()).isEqualTo(200);
            assertThat(response.headers().get(GrpcStatus.STATUS_NAME)).isEqualTo(GrpcStatus.NOT_FOUND);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // HTTP2 Method
    //
    // SPEC:
    //
    // Only POST can be used for gRPC calls.
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** Verify that only POST is supported */
    @ParameterizedTest
    @ValueSource(strings = { "GET", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE" })
    void mustUsePost(final String methodName) {
        try (var response = CLIENT.method(Method.create(methodName))
                .contentType(APPLICATION_GRPC_PROTO)
                .path(SAY_HELLO_PATH)
                .request()) {

            // This is consistent with existing behavior on Helidon, but I would have expected the response code
            // to be 405 Method Not Allowed instead. See PbjProtocolSelector for the check for POST.
            assertThat(response.status().code()).isEqualTo(404);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Content-Type
    //
    // SPEC:
    //
    // If Content-Type does not begin with "application/grpc", gRPC servers SHOULD respond with HTTP status of 415
    // (Unsupported Media Type). This will prevent other HTTP/2 clients from interpreting a gRPC error response, which
    // uses status 200 (OK), as successful.
    //
    // TESTS:
    //   -
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** Verify that the server responds with 415 when the Content-Type is not specified */
    @Test
    void contentTypeMustBeSet() {
        try (var response = CLIENT.post()
                .path(SAY_HELLO_PATH)
                .submit(messageBytes(SIMPLE_REQUEST))) {

            assertThat(response.status().code()).isEqualTo(415);
        }
    }

    /** Verify that the server responds with 415 when the Content-Type does not start with "application/grpc" */
    @Test
    void contentTypeMustStartWithApplicationGrpc() {
        try (var response = CLIENT.post()
                .path(SAY_HELLO_PATH)
                .contentType(APPLICATION_RANDOM)
                .submit(messageBytes(SIMPLE_REQUEST))) {

            assertThat(response.status().code()).isEqualTo(415);
        }
    }

    /** Verify that "application/grpc+json" requests are accepted */
    @Test
    void contentTypeCanBeJSON() {
        try (var response = CLIENT.post()
                .path(SAY_HELLO_PATH)
                .contentType(APPLICATION_GRPC_JSON)
                .submit(messageBytesJson(SIMPLE_REQUEST))) {

            assertThat(response.status().code()).isEqualTo(200);
            assertThat(response.headers().contentType().orElseThrow().text())
                    .isEqualTo("application/grpc+json");

            final var reply = decodeJsonReply(new ReadableStreamingData(response.inputStream()));
            assertThat(reply).isEqualTo(SIMPLE_REPLY);
        }
    }

    /** Verify that "application/grpc+proto" and "application/grpc" both support protobuf encoding */
    @ParameterizedTest
    @ValueSource(strings = { "application/grpc+proto", "application/grpc" })
    void contentTypeCanBeProtobuf(final String contentType) {
        try (var response = CLIENT.post()
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
        try (var response = CLIENT.post()
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

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // METADATA
    //
    // SPEC:
    //
    // Custom-Metadata is an arbitrary set of key-value pairs defined by the application layer. Header names starting
    // with "grpc-" but not listed here are reserved for future GRPC use and should not be used by applications as
    // Custom-Metadata.
    //
    // Note that HTTP2 does not allow arbitrary octet sequences for header values so binary header values must be
    // encoded using Base64 as per https://tools.ietf.org/html/rfc4648#section-4. Implementations MUST accept padded
    // and un-padded values and should emit un-padded values. Applications define binary headers by having their names
    // end with "-bin". Runtime libraries use this suffix to detect binary headers and properly apply base64 encoding &
    // decoding as headers are sent and received.
    //
    // Custom-Metadata header order is not guaranteed to be preserved except for values with duplicate header names.
    // Duplicate header names may have their values joined with "," as the delimiter and be considered semantically
    // equivalent. Implementations must split Binary-Headers on "," before decoding the Base64-encoded values.
    //
    // ASCII-Value should not have leading or trailing whitespace. If it contains leading or trailing whitespace, it
    // may be stripped. The ASCII-Value character range defined is stricter than HTTP. Implementations must not error
    // due to receiving an invalid ASCII-Value that's a valid field-value in HTTP, but the precise behavior is not
    // strictly defined: they may throw the value away or accept the value. If accepted, care must be taken to make
    // sure that the application is permitted to echo the value back as metadata. For example, if the metadata is
    // provided to the application as a list in a request, the application should not trigger an error by providing
    // that same list as the metadata in the response.
    //
    // TESTS:
    //   - Not implemented
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Request-Headers
    //
    // SPEC:
    //
    // Servers may limit the size of Request-Headers, with a default of 8 KiB suggested. Implementations are encouraged
    // to compute total header size like HTTP/2's SETTINGS_MAX_HEADER_LIST_SIZE: the sum of all header fields, for each
    // field the sum of the uncompressed field name and value lengths plus 32, with binary values' lengths being
    // post-Base64.
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Verify that the server responds with grpc-accept-encoding and UNIMPLEMENTED if unsupported compression schemes
     * are used.
     */
    @ParameterizedTest
    @ValueSource(strings = { "gzip", "deflate", "random" })
    void compressionNotSupported(final String grpcEncoding) {
        try (var response = CLIENT.post()
                .contentType(APPLICATION_GRPC_PROTO)
                .path(SAY_HELLO_PATH)
                .header(HeaderNames.create("grpc-encoding"), grpcEncoding)
                .submit(messageBytes(SIMPLE_REQUEST))) {

            assertThat(response.status().code()).isEqualTo(200);
            assertThat(response.headers().get(GrpcStatus.STATUS_NAME).values()).isEqualTo(GrpcStatus.UNIMPLEMENTED.values());
            assertThat(response.headers().get(HeaderNames.create("grpc-accept-encoding")).get()).isEqualTo("identity");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Unary Method Calls
    //
    // TESTS:
    //   - A correct unary call should return a 200 OK response with a gRPC status of OK
    //   - A correct unary call to a failed method should return a 200 OK response with an error code
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Intentionally uses grpc.io to verify compatibility
    @Test
    void unaryCall() {
        final var stub = GreeterGrpc.newBlockingStub(CHANNEL);
        final var reply = stub.sayHello(SIMPLE_REQUEST);
        assertThat(reply.getMessage()).isEqualTo("Hello PBJ");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Server Streaming Method Calls
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void streamingServer() {
        final var stub = GreeterGrpc.newBlockingStub(CHANNEL);
        final var replies = stub.sayHelloStreamReply(SIMPLE_REQUEST);
        final var messages = new ArrayList<HelloReply>();
        replies.forEachRemaining(messages::add);
        assertThat(messages)
                .hasSize(10)
                .allMatch(reply -> reply.getMessage().equals("Hello!"));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Client Streaming Method Calls
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void streamingClient() throws InterruptedException {
        final var latch = new CountDownLatch(1);
        final var response = new AtomicReference<HelloReply>();
        final var requestObserver = GreeterGrpc.newStub(CHANNEL).sayHelloStreamRequest(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(HelloReply helloReply) {
                response.set(helloReply);
            }

            @Override
            public void onError(Throwable throwable) {
                // TODO Test fail
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

        latch.await(1, TimeUnit.MINUTES);

        assertThat(response.get()).isEqualTo(HelloReply.newBuilder()
                .setMessage("Hello Alice, Bob, Carol")
                .build());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Bidi Streaming Calls
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void streamingBidi() throws InterruptedException {
        final var latch = new CountDownLatch(1);
        final var response = new ArrayList<HelloReply>();
        final var requestObserver = GreeterGrpc.newStub(CHANNEL).sayHelloStreamBidi(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(HelloReply helloReply) {
                response.add(helloReply);
            }

            @Override
            public void onError(Throwable throwable) {
                // TODO Test fail
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

        latch.await(1, TimeUnit.MINUTES);

        assertThat(response)
                .hasSize(3)
                .allMatch(reply -> reply.getMessage().startsWith("Hello"));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Utility methods
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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
        @Override
        public HelloReply sayHello(HelloRequest request) {
            return HelloReply.newBuilder()
                    .setMessage("Hello " + request.getName())
                    .build();
        }

        // Streams of stuff coming from the client, with a single response.
        @Override
        public Flow.Subscriber<? super HelloRequest> sayHelloStreamRequest(Flow.Subscriber<? super HelloReply> replies) {
            final var names = new ArrayList<String>();
            return new Flow.Subscriber<>() {
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
                    final var reply = HelloReply.newBuilder()
                            .setMessage("Hello " + String.join(", ", names))
                            .build();
                    replies.onNext(reply);
                    replies.onComplete();
                }
            };
        }

        @Override
        public void sayHelloStreamReply(HelloRequest request, Flow.Subscriber<? super HelloReply> replies) {
            for (int i = 0; i < 10; i++) {
                replies.onNext(HelloReply.newBuilder()
                        .setMessage("Hello!")
                        .build());
            }

            replies.onComplete();
        }

        @Override
        public Flow.Subscriber<? super HelloRequest> sayHelloStreamBidi(Flow.Subscriber<? super HelloReply> replies) {
            // Here we receive info from the client. In this case, it is a stream of requests with names.
            // We will respond with a stream of replies.
            return new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    replies.onNext(HelloReply.newBuilder()
                            .setMessage("Hello " + item.getName())
                            .build());
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
}
