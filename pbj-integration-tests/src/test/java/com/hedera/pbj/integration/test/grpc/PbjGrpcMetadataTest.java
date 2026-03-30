// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.grpc.helidon.PbjGrpcServiceConfig;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.webserver.WebServer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

public class PbjGrpcMetadataTest {
    private static final String DATA_NAME = "test-greeter-metadata-header-name";

    private static PortsAllocator.Port PORT;
    private static ServerHandle SERVER;
    private static GrpcClient GRPC_CLIENT;

    record OptionsWithMetadata(Optional<String> authority, String contentType, Map<String, String> metadata)
            implements ServiceInterface.RequestOptions {}

    public static class MetadataGreeterService implements GreeterInterface {
        @Override
        public HelloReply sayHello(HelloRequest request, RequestOptions requestOptions) {
            return HelloReply.newBuilder()
                    .message("Hello " + request.name()
                            + (requestOptions.metadata().containsKey(DATA_NAME)
                                    ? (" " + requestOptions.metadata().get(DATA_NAME))
                                    : ""))
                    .build();
        }
    }

    private record ServerHandle(WebServer server) implements AutoCloseable {
        @Override
        public void close() {
            server.stop();
        }

        static ServerHandle start(
                final int port, final ServiceInterface service, final PbjGrpcServiceConfig serviceConfig) {
            final int maxPayloadSize = 20 * 1024 * 1024;
            final PbjConfig pbjConfig = PbjConfig.builder()
                    .name("pbj")
                    .maxMessageSizeBytes(maxPayloadSize)
                    .build();
            return new ServerHandle(WebServer.builder()
                    .port(port)
                    .addProtocol(pbjConfig)
                    .addRouting(PbjRouting.builder().service(service, serviceConfig))
                    .maxPayloadSize(maxPayloadSize)
                    .build()
                    .start());
        }
    }

    @BeforeAll
    static void setup() {
        final PbjGrpcServiceConfig serviceConfig =
                new PbjGrpcServiceConfig(GrpcCompression.IDENTITY, Set.of(GrpcCompression.IDENTITY));
        PORT = GrpcTestUtils.PORTS.acquire();
        SERVER = ServerHandle.start(PORT.port(), new MetadataGreeterService(), serviceConfig);
        GRPC_CLIENT = GrpcTestUtils.createGrpcClient(
                PORT.port(),
                GrpcTestUtils.PROTO_OPTIONS,
                GrpcCompression.IDENTITY,
                Set.of(GrpcCompression.IDENTITY),
                Codec.DEFAULT_MAX_SIZE,
                Codec.DEFAULT_MAX_SIZE * 5);
    }

    @AfterAll
    static void teardown() {
        GRPC_CLIENT.close();
        SERVER.close();
        PORT.close();
    }

    @Test
    void testNoMetadata() {
        // Use default options, so there's no any metadata
        GreeterInterface.GreeterClient client =
                new GreeterInterface.GreeterClient(GRPC_CLIENT, GrpcTestUtils.PROTO_OPTIONS);

        final HelloReply reply =
                client.sayHello(HelloRequest.newBuilder().name("name").build());

        assertEquals("Hello name", reply.message());
    }

    @Test
    void testWithEmptyMetadata() {
        OptionsWithMetadata options =
                new OptionsWithMetadata(Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC, Map.of());
        GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, options);

        final HelloReply reply =
                client.sayHello(HelloRequest.newBuilder().name("name").build());

        assertEquals("Hello name", reply.message());
    }

    @Test
    void testWithRandomMetadata() {
        OptionsWithMetadata options = new OptionsWithMetadata(
                Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC, Map.of("random-header", "data"));
        GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, options);

        final HelloReply reply =
                client.sayHello(HelloRequest.newBuilder().name("name").build());

        assertEquals("Hello name", reply.message());
    }

    @Test
    void testWithTestMetadata() {
        OptionsWithMetadata options = new OptionsWithMetadata(
                Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC, Map.of(DATA_NAME, "the data"));
        GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, options);

        final HelloReply reply =
                client.sayHello(HelloRequest.newBuilder().name("name").build());

        assertEquals("Hello name the data", reply.message());
    }

    @Test
    void testWithTestMetadataPerRequest() {
        GreeterInterface.GreeterClient client =
                new GreeterInterface.GreeterClient(GRPC_CLIENT, GrpcTestUtils.PROTO_OPTIONS);

        OptionsWithMetadata options = new OptionsWithMetadata(
                Optional.empty(),
                ServiceInterface.RequestOptions.APPLICATION_GRPC,
                Map.of(DATA_NAME, "per-request data"));
        final HelloReply reply =
                client.sayHello(HelloRequest.newBuilder().name("name").build(), options);

        assertEquals("Hello name per-request data", reply.message());
    }
}
