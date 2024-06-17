package pbj.interop;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import greeter.GreeterGrpc;
import grpc.testing.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.testing.integration.Payload;
import io.grpc.testing.integration.SimpleRequest;
import io.grpc.testing.integration.TestServiceGrpc;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class InteropTest {
    private static ManagedChannel CHANNEL;

//    @BeforeAll
//    static void setup() {
//        // Set up the server
//        WebServer.builder()
//                .port(8080)
//                .addRouting(PbjRouting.builder()
//                        .service(new TestServiceImpl()))
//                .build()
//                .start();
//
//        CHANNEL = ManagedChannelBuilder.forAddress("localhost", 8080)
//                .usePlaintext()
//                .build();
//
//    }
//
//    // https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md#empty_unary
//    @Test
//    void empty_unary() {
//        final var stub = TestServiceGrpc.newBlockingStub(CHANNEL);
//        final var reply = stub.emptyCall(Empty.getDefaultInstance());
//        assertThat(reply).isEqualTo(Empty.getDefaultInstance());
//
//    }
//
//    // https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md#cacheable_unary
//    @Test
//    @Disabled("Not implemented")
//    void cacheable_unary() {
//    }
//
//    // https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md#large_unary
//    @Test
//    void large_unary() throws IOException {
//        final var stub = TestServiceGrpc.newBlockingStub(CHANNEL);
//        final var request = SimpleRequest.newBuilder()
//                .setResponseSize(314159)
//                .setPayload(Payload.newBuilder()
//                        .setBody(createEmptyByteString(314159))
//                        .build())
//                .build();
//
//        final var reply = stub.unaryCall(request);
//
//        try (final var byteStream = getClass().getResourceAsStream("large_271828.bytes")) {
//            assertThat(byteStream).isNotNull();
//            final var bytes = byteStream.readAllBytes();
//            final var expected = ByteString.copyFrom(bytes);
//            assertThat(reply.getPayload().getBody()).isEqualTo(expected);
//        }
//    }
//
//    private ByteString createEmptyByteString(int size) {
//        final var output = ByteString.newOutput(size);
//        for (int i = 0; i < size; i++) {
//            output.write(0);
//        }
//        return output.toByteString();
//    }
}
