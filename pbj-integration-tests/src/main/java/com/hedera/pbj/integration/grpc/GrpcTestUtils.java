// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.grpc;

import com.hedera.pbj.grpc.client.helidon.PbjGrpcClient;
import com.hedera.pbj.grpc.client.helidon.PbjGrpcClientConfig;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.WebClient;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/** Misc utils for GRPC tests. */
public class GrpcTestUtils {
    public static final PortsAllocator PORTS = new PortsAllocator(14100, 15200);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    public static final Options PROTO_OPTIONS =
            new Options(Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC);

    /** sleep() for non-blocking GRPC calls to wait for results to arrive. */
    public static void sleep(final GrpcClient grpcClient) {
        // Wait a tad longer than the read timeout:
        LockSupport.parkUntil(System.currentTimeMillis()
                + ((PbjGrpcClient) grpcClient)
                        .getConfig()
                        .readTimeout()
                        .plusSeconds(2)
                        .toMillis());
    }

    public static GrpcClient createGrpcClient(final int port, final ServiceInterface.RequestOptions requestOptions) {
        return createGrpcClient(port, requestOptions, GrpcCompression.IDENTITY, GrpcCompression.getDecompressorNames());
    }

    public static GrpcClient createGrpcClient(
            final int port,
            final ServiceInterface.RequestOptions requestOptions,
            String encoding,
            Set<String> acceptEncodings) {
        final Tls tls = Tls.builder().enabled(false).build();
        final WebClient webClient =
                WebClient.builder().baseUri("http://localhost:" + port).tls(tls).build();

        // If the requestOptions doesn't have an authority, provide one based on the port
        final Optional<String> authority =
                requestOptions.authority().isPresent() ? requestOptions.authority() : Optional.of("localhost:" + port);

        final PbjGrpcClientConfig config = new PbjGrpcClientConfig(
                READ_TIMEOUT, tls, authority, requestOptions.contentType(), encoding, acceptEncodings);

        return new PbjGrpcClient(webClient, config);
    }
}
