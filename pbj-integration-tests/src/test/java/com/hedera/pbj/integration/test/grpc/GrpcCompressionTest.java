// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.grpc.helidon.PbjGrpcServiceConfig;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.helidon.webserver.WebServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pbj.integration.tests.GreeterGrpc;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

public class GrpcCompressionTest {

    @Test
    void testPbjGrpcClientCompressionWithGoogleGrpcServer() {
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle server = new GoogleProtobufGrpcServerGreeterHandle(port.port())) {
            server.start();
            server.setSayHello(request ->
                    HelloReply.newBuilder().message("Hello " + request.name()).build());

            try (final GrpcClient grpcClient =
                    GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS, "gzip", Set.of("gzip"))) {
                final GreeterInterface.GreeterClient client =
                        new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

                final HelloRequest request =
                        HelloRequest.newBuilder().name("test name").build();

                // NOTE: Google GRPC server will send replies using the "identity" encoding despite us only accepting
                // "gzip", even though it reports that it supports gzip in its headers. Installing a CompressionRegistry
                // when initializing the server doesn't help. Increasing the message size doesn't help too.
                // But that's fine because this test primarily verifies that the PBJ GRPC Client can send a request
                // using the "gzip" encoding.
                // While there's no public API to verify that, a debugging println at PbjGrpcCall.sendRequest()
                // actually shows that the request is gzip'd. Further, a correct reply that we receive here proves
                // that the Google server was able to parse our gzip'd request:
                final HelloReply reply = client.sayHello(request);

                assertEquals("Hello test name", reply.message());
            }
        }
    }

    @Test
    void testPbjGrpcServerCompressionWithGoogleGrpcClient() {
        WebServer server = null;
        ManagedChannel channel = null;
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle handle = new PbjGrpcServerGreeterHandle(port.port())) {
            // Note that we bypass the default handle.start() and only use the ServiceInterface side of the handle
            // because we need to modify how the PbjRouting is created:
            server = WebServer.builder()
                    .port(port.port())
                    .addRouting(PbjRouting.builder().service(handle, new PbjGrpcServiceConfig("gzip", Set.of("gzip"))))
                    .maxPayloadSize(10000)
                    .build()
                    .start();
            handle.setSayHello(request ->
                    HelloReply.newBuilder().message("Hello " + request.name()).build());

            channel = ManagedChannelBuilder.forAddress("localhost", port.port())
                    .usePlaintext()
                    .build();

            // The client speaks gzip, so our server decompresses the requests.
            // Our server speaks gzip as well as configured above, so the client will decompress replies.
            final GreeterGrpc.GreeterBlockingStub stub =
                    GreeterGrpc.newBlockingStub(channel).withCompression("gzip");
            final pbj.integration.tests.HelloReply reply = stub.sayHello(pbj.integration.tests.HelloRequest.newBuilder()
                    .setName("PBJ")
                    .build());
            assertEquals("Hello PBJ", reply.getMessage());

        } finally {
            if (channel != null) {
                channel.shutdown();
            }
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void testPbjGrpcServerCompressionWithPbjClient() {
        WebServer server = null;
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle handle = new PbjGrpcServerGreeterHandle(port.port())) {
            // Note that we bypass the default handle.start() and only use the ServiceInterface side of the handle
            // because we need to modify how the PbjRouting is created:
            server = WebServer.builder()
                    .port(port.port())
                    .addRouting(PbjRouting.builder().service(handle, new PbjGrpcServiceConfig("gzip", Set.of("gzip"))))
                    .maxPayloadSize(10000)
                    .build()
                    .start();
            handle.setSayHello(request ->
                    HelloReply.newBuilder().message("Hello " + request.name()).build());

            try (final GrpcClient grpcClient =
                    GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS, "gzip", Set.of("gzip"))) {
                final GreeterInterface.GreeterClient client =
                        new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

                final HelloRequest request =
                        HelloRequest.newBuilder().name("test name").build();

                // Here, both PBJ client and server speak gzip, so they compress/decompress both requests and replies:
                final HelloReply reply = client.sayHello(request);

                assertEquals("Hello test name", reply.message());
            }

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
}
