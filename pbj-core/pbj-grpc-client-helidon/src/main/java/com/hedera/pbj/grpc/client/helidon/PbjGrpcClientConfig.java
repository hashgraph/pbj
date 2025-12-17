// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.runtime.grpc.GrpcCompression;
import io.helidon.common.tls.Tls;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

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
        String contentType,

        /**
         * Default GRPC encoding for sending data to remote peers, e.g. "identity", "gzip", etc.
         * Note that the encoding must be registered as a `Compressor` with `GrpcCompression` to actually be supported,
         * otherwise "identity" is used by default.
         * If the remote peer doesn't support this encoding, then the sender is free to choose another one supported
         * by both the remote peer and the `GrpcCompression`.
         */
        String encoding,

        /**
         * Accepted GRPC encodings for receiving data from remote peers which typically refer to compression algorithms,
         * e.g. "identity", "gzip", etc.
         * Note that the encoding must be registered as a `Decompressor` with `GrpcCompression` to actually be supported.
         */
        Set<String> acceptEncodings) {

    public PbjGrpcClientConfig(Duration readTimeout, Tls tls, Optional<String> authority, String contentType) {
        this(
                readTimeout,
                tls,
                authority,
                contentType,
                GrpcCompression.IDENTITY,
                GrpcCompression.getDecompressorNames());
    }
}
