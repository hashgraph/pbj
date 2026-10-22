// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common.compression;

import com.hedera.pbj.runtime.grpc.GrpcCompression;

/** Installs the optional compression algorithms shared by PBJ transports before negotiation starts. */
public final class GrpcCompressionRegistry {
    static {
        final ZstdGrpcTransformer zstd = new ZstdGrpcTransformer();
        // Keep application-provided implementations when a transport initializes after application setup.
        GrpcCompression.registerCompressorIfAbsent("zstd", zstd);
        GrpcCompression.registerDecompressorIfAbsent("zstd", zstd);
    }

    private GrpcCompressionRegistry() {}

    /**
     * Installs the shared algorithms, such as zstd, the first time it is called. Later calls do nothing, and an
     * implementation already registered under the same name is never replaced.
     */
    public static void initialize() {}
}
