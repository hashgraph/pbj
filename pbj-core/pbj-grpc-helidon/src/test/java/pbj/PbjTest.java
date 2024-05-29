package pbj;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.pbj.grpc.helidon.GrpcStatus;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PbjTest {
    private static final MediaType APPLICATION_GRPC = HttpMediaType.create("application/grpc");
    private static final MediaType APPLICATION_GRPC_PROTO = HttpMediaType.create("application/grpc+proto");
    private static final MediaType APPLICATION_GRPC_JSON = HttpMediaType.create("application/grpc+json");
    private static final MediaType APPLICATION_RANDOM = HttpMediaType.create("application/random");
    private static WebClient CLIENT;
    private static final String SUBMIT_MESSAGE_PATH = "/proto.ConsensusService/submitMessage";

    @BeforeAll
    static void setup() {
        // Set up the server
        WebServer.builder()
                .port(8080)
                .addRouting(PbjRouting.builder().service(new ConsensusServiceImpl()))
                .build()
                .start();

        CLIENT = WebClient.builder()
                .baseUri("http://localhost:8080")
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
    //
    // TESTS:
    //   - Verify the path is case-sensitive
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void badCaseOnPathIsNotFound() {
        try (var response = CLIENT.post()
                .protocolId("h2")
                .contentType(APPLICATION_GRPC_PROTO)
                .path(SUBMIT_MESSAGE_PATH.toUpperCase())
                .submit(messageBytes(Transaction.DEFAULT))) {
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
    //
    // TESTS:
    //   - Verify that only POST is supported
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @ValueSource(strings = { "GET", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE" })
    void mustUsePost(final String methodName) {
        try (var response = CLIENT.method(Method.create(methodName))
                .protocolId("h2")
                .contentType(APPLICATION_GRPC_PROTO)
                .path(SUBMIT_MESSAGE_PATH)
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
    //   - Verify that the server responds with 415 when the Content-Type is not specified
    //   - Verify that the server responds with 415 when the Content-Type is not "application/grpc"
    //   - Verify that both "application/grpc+proto" and "application/grpc+json" are accepted
    //   - Verify that if "application/grpc" is used, it defaults to "application/grpc+proto"
    //   - Verify that the response is encoded as JSON when "application/grpc+json" is used
    //   - Verify that the response is encoded as protobuf when "application/grpc+proto" or "application/grpc" is used
    //   - Verify that a custom encoding "application/grpc+custom" can work
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void contentTypeMustBeSet() {
        try (var response = CLIENT.post()
                .protocolId("h2")
                .path(SUBMIT_MESSAGE_PATH)
                .submit(messageBytes(Transaction.DEFAULT))) {

            assertThat(response.status().code()).isEqualTo(415);
        }
    }

    @Test
    void contentTypeMustStartWithApplicationGrpc() {
        try (var response = CLIENT.post()
                .protocolId("h2")
                .path(SUBMIT_MESSAGE_PATH)
                .contentType(APPLICATION_RANDOM)
                .submit(messageBytes(Transaction.DEFAULT))) {

            assertThat(response.status().code()).isEqualTo(415);
        }
    }

    @Test
    void contentTypeCanBeJSON() {
        try (var response = CLIENT.post()
                .protocolId("h2")
                .path(SUBMIT_MESSAGE_PATH)
                .contentType(APPLICATION_GRPC_JSON)
                .submit(messageBytesJson(Transaction.DEFAULT))) {

            // TODO Assert that the response is also encoded as JSON
            assertThat(response.status().code()).isEqualTo(200);
            assertThat(response.headers().contentType().orElseThrow().text()).isEqualTo("application/grpc+json");
        }
    }

    @Test
    void contentTypeWithoutProtoDefaultsToProto() {

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
    //   - TBD
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

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Compression
    //
    // SPEC:
    //
    // The repeated sequence of Length-Prefixed-Message items is delivered in DATA frames
    //
    //  - Length-Prefixed-Message → Compressed-Flag Message-Length Message
    //  - Compressed-Flag → 0 / 1 # encoded as 1 byte unsigned integer
    //  - Message-Length → {length of Message} # encoded as 4 byte unsigned integer (big endian)
    //  - Message → *{binary octet}
    //
    // A Compressed-Flag value of 1 indicates that the binary octet sequence of Message is compressed using the
    // mechanism declared by the Message-Encoding header. A value of 0 indicates that no encoding of Message bytes has
    // occurred. Compression contexts are NOT maintained over message boundaries, implementations must create a new
    // context for each message in the stream. If the Message-Encoding header is omitted then the Compressed-Flag must
    // be 0.
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Unary Method Calls
    //
    // TESTS:
    //   - A correct unary call should return a 200 OK response with a gRPC status of OK
    //   - A correct unary call to a failed method should return a 200 OK response with an error code
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    void unaryCall() throws Exception {
        try (var response = CLIENT.post()
                .protocolId("h2")
                .contentType(APPLICATION_GRPC_PROTO)
                .path(SUBMIT_MESSAGE_PATH)
                .submit(messageBytes(Transaction.DEFAULT))) {

            assertThat(response.status().code()).isEqualTo(200);
            assertThat(response.headers().get(GrpcStatus.STATUS_NAME)).isEqualTo(GrpcStatus.OK);

            final var rsd = new ReadableStreamingData(response.inputStream());
            assertThat(rsd.readByte()).isEqualTo((byte) 0); // No Compression (we didn't ask for it)

            final var responseLength = (int) rsd.readUnsignedInt();
            assertThat(responseLength).isPositive();

            final var responseData = new byte[responseLength];
            rsd.readBytes(responseData);
            final var txr = TransactionResponse.PROTOBUF.parse(Bytes.wrap(responseData));

            assertThat(txr).isEqualTo(TransactionResponse.DEFAULT);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Server Streaming Method Calls
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Client Streaming Method Calls
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Bidi Streaming Calls
    //
    // TESTS:
    //   - TBD
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Utility methods
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private byte[] messageBytes(Transaction tx) {
        final var data = Transaction.PROTOBUF.toBytes(tx).toByteArray();
        final var out = new ByteArrayOutputStream();
        final WritableStreamingData wsd = new WritableStreamingData(out);
        wsd.writeByte((byte) 0);
        wsd.writeUnsignedInt(data.length);
        wsd.writeBytes(data);
        return out.toByteArray();
    }

    private byte[] messageBytesJson(Transaction tx) {
        final var data = Transaction.JSON.toBytes(tx).toByteArray();
        final var out = new ByteArrayOutputStream();
        final WritableStreamingData wsd = new WritableStreamingData(out);
        wsd.writeByte((byte) 0);
        wsd.writeUnsignedInt(data.length);
        wsd.writeBytes(data);
        return out.toByteArray();
    }

    private static final class ConsensusServiceImpl implements ConsensusService {
        @Override
        public TransactionResponse createTopic(Transaction tx) {
            // TODO Test when one of these returns null!!
            System.out.println("Creating topic");
            return TransactionResponse.DEFAULT;
        }

        @Override
        public TransactionResponse updateTopic(Transaction tx) {
            System.out.println("Updating topic");
            return TransactionResponse.DEFAULT;
        }

        @Override
        public TransactionResponse deleteTopic(Transaction tx) {
            System.out.println("Deleting topic");
            return TransactionResponse.DEFAULT;
        }

        @Override
        public TransactionResponse submitMessage(Transaction tx) {
            System.out.println("Submitting message");
            return TransactionResponse.DEFAULT;
        }

        @Override
        public Response getTopicInfo(Query q) {
            System.out.println("Getting topic info");
            return Response.DEFAULT;
        }
    }
}
