// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import com.hedera.pbj.grpc.client.helidon.PbjGrpcClient;
import com.hedera.pbj.grpc.client.helidon.PbjGrpcClientConfig;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.WebClient;
import java.time.Duration;
import java.util.Optional;

/** Misc utils for GRPC tests. */
public class GrpcTestUtils {
    public static final PortsAllocator PORTS = new PortsAllocator(8666, 10666);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    public static final Options PROTO_OPTIONS =
            new Options(Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC);

    /** sleep() for non-blocking GRPC calls to wait for results to arrive. */
    public static void sleep(final GrpcClient grpcClient) {
        // Wait a tad longer than the read timeout:
        try {
            Thread.sleep(((PbjGrpcClient) grpcClient).getConfig().readTimeout().plusSeconds(2));
        } catch (InterruptedException e) {
        }
    }

    public static GrpcClient createGrpcClient(final int port, final ServiceInterface.RequestOptions requestOptions) {
        final Tls tls = Tls.builder().enabled(false).build();
        final WebClient webClient =
                WebClient.builder().baseUri("http://localhost:" + port).tls(tls).build();

        final PbjGrpcClientConfig config =
                new PbjGrpcClientConfig(READ_TIMEOUT, tls, requestOptions.authority(), requestOptions.contentType());

        return new PbjGrpcClient(webClient, config);
    }
}
