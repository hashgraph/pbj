// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common.compression;

import com.hedera.pbj.runtime.grpc.GrpcCompression;

/** Installs the optional compression algorithms shared by PBJ transports before negotiation starts. */
public final class GrpcCompressionRegistry {
    static {
        final ZstdGrpcTransformer zstd = new ZstdGrpcTransformer();
        // Keep application-provided implementations when a transport initializes after application setup.
        if (GrpcCompression.getCompressor("zstd") == null) {
            GrpcCompression.registerCompressor("zstd", zstd);
        }
        if (GrpcCompression.getDecompressor("zstd") == null) {
            GrpcCompression.registerDecompressor("zstd", zstd);
        }
    }

    private GrpcCompressionRegistry() {}

    /** Initializes the shared algorithms once. Repeated calls do not replace registered implementations. */
    public static void initialize() {
        // Initialization is performed by the JVM before this method can be invoked.
    }
}
