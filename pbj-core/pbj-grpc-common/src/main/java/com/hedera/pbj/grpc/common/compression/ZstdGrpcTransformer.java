// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common.compression;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A GRPC Compressor/Decompressor for zstd.
 * @param level zstd compression level, -5 to 22; 3 is the default.
 */
public record ZstdGrpcTransformer(int level) implements GrpcCompression.GrpcTransformer {
    public ZstdGrpcTransformer() {
        this(3);
    }

    /**
     * Register the transformer with PBJ GrpcCompression
     * @param name the name of the encoding.
     */
    public void register(String name) {
        GrpcCompression.registerCompressor(name, this);
        GrpcCompression.registerDecompressor(name, this);
    }

    @Override
    public Bytes compress(Bytes bytes) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZstdOutputStream zos = new ZstdOutputStream(baos, level)) {
            bytes.writeTo(zos);
            zos.flush();
            zos.close();
            return Bytes.wrap(baos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Bytes decompress(Bytes bytes) {
        return decompress(bytes, Integer.MAX_VALUE);
    }

    @Override
    public Bytes decompress(final Bytes bytes, final int maxOutputSize) {
        if (maxOutputSize < 0) {
            throw new IllegalArgumentException("maxOutputSize must not be negative");
        }
        // A stream's advertised window can itself demand substantial native memory. Permit the ordinary zstd
        // streaming window (up to 8 MiB), or the configured message limit if larger, but never an arbitrary window.
        final int windowLog = Math.max(23, 32 - Integer.numberOfLeadingZeros(Math.max(1, maxOutputSize) - 1));
        try (ZstdInputStream zis = new ZstdInputStream(bytes.toInputStream()).setLongMax(windowLog)) {
            return GrpcCompression.readDecompressed(zis, maxOutputSize);
        } catch (final ZstdIOException e) {
            if (e.getErrorCode() == Zstd.errFrameParameterWindowTooLarge()) {
                throw new GrpcException(GrpcStatus.RESOURCE_EXHAUSTED, "Compressed message window exceeds limit", e);
            }
            throw new UncheckedIOException(e);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
