// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import io.helidon.common.tls.Tls;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuration for PBJ GRPC client.
 */
public record PbjGrpcClientConfig(
        /** A read timeout. Duration.ofSeconds(10) is a good default. */
        Duration readTimeout,
        /** TLS configuration. */
        Tls tls,
        /** An optional authority string. */
        Optional<String> authority,
        /** A content type, such as "application/grpc+proto" or "application/grpc+json". */
        String contentType) {}
