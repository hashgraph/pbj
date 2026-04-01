// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common.compression;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
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
        byte[] buffer = new byte[1024];
        try (ZstdInputStream zis = new ZstdInputStream(bytes.toInputStream());
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int len;
            while ((len = zis.read(buffer, 0, buffer.length)) > 0) {
                baos.write(buffer, 0, len);
            }
            return Bytes.wrap(baos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
