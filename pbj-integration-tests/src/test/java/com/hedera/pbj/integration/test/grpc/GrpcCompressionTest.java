// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
}
