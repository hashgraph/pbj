// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common.compression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

class GrpcCompressionRegistryTest {
    @Test
    void initializationInstallsZstdWithoutReplacingItOnLaterCalls() {
        GrpcCompressionRegistry.initialize();
        final GrpcCompression.Compressor compressor = GrpcCompression.getCompressor("zstd");
        final GrpcCompression.Decompressor decompressor = GrpcCompression.getDecompressor("zstd");
        assertNotNull(compressor);
        assertNotNull(decompressor);
        final Bytes message = Bytes.wrap("available before any native gRPC request");
        assertEquals(message, decompressor.decompress(compressor.compress(message)));

        GrpcCompressionRegistry.initialize();
        assertSame(compressor, GrpcCompression.getCompressor("zstd"));
        assertSame(decompressor, GrpcCompression.getDecompressor("zstd"));
    }
}
