// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.grpc.client.helidon.PbjGrpcClient;
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
import java.net.InetSocketAddress;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pbj.integration.tests.pbj.integration.tests.TCPIPInfoProviderInterface;
import pbj.integration.tests.pbj.integration.tests.TCPIPReply;
import pbj.integration.tests.pbj.integration.tests.TCPIPRequest;

public class TCPIPInfoProviderTest {
    private static final String TEST_NAME = "TCPIPInfoProviderTest Name";

    private static PortsAllocator.Port PORT;
    private static ServerHandle SERVER;
    private static GrpcClient GRPC_CLIENT;

    public static class TCPIPInfoProviderService implements TCPIPInfoProviderInterface {
        @Override
        public @NonNull TCPIPReply provideTCPIP(
                @NonNull TCPIPRequest request, ServiceInterface.RequestOptions requestOptions) {
            final InetSocketAddress inetSocketAddress = (InetSocketAddress) requestOptions.remoteAddress();
            return TCPIPReply.newBuilder()
                    .name(request.name())
                    .host(inetSocketAddress.getHostString())
                    .port(inetSocketAddress.getPort())
                    .numOfCertificates(requestOptions
                            .remoteCertificateChain()
                            .map(certs -> certs.length)
                            .orElse(-1))
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
        SERVER = ServerHandle.start(PORT.port(), new TCPIPInfoProviderService(), serviceConfig);
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
    void testTCPIPInfoProvider() {
        TCPIPInfoProviderInterface.TCPIPInfoProviderClient client =
                new TCPIPInfoProviderInterface.TCPIPInfoProviderClient(GRPC_CLIENT, GrpcTestUtils.PROTO_OPTIONS);

        final TCPIPReply reply =
                client.provideTCPIP(TCPIPRequest.newBuilder().name(TEST_NAME).build());

        assertEquals(TEST_NAME, reply.name());
        assertEquals("127.0.0.1", reply.host());

        PbjGrpcClient pbjGrpcClient = (PbjGrpcClient) GRPC_CLIENT;
        InetSocketAddress localAddress = (InetSocketAddress) pbjGrpcClient.getLocalAddress();
        assertEquals(localAddress.getPort(), reply.port());

        assertEquals(-1, reply.numOfCertificates());
    }
}
