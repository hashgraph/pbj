// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GrpcCompressionTest {
    @Test
    void gzipAcceptsTheExactDecodedLimitAndRejectsOneByteMore() {
        final Bytes payload = Bytes.wrap(new byte[16_384]);
        final Bytes compressed = GrpcCompression.getCompressor("gzip").compress(payload);
        final GrpcCompression.Decompressor decompressor = GrpcCompression.getDecompressor("gzip");
        assertEquals(payload, GrpcCompression.decompress(decompressor, compressed, 16_384));
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.decompress(decompressor, compressed, 16_383));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
    }

    @Test
    void gzipRejectsHighlyCompressedMessages() {
        final Bytes compressed = GrpcCompression.getCompressor("gzip").compress(Bytes.wrap(new byte[8 * 1024 * 1024]));
        final GrpcException failure = assertThrows(
                GrpcException.class,
                () -> GrpcCompression.decompress(GrpcCompression.getDecompressor("gzip"), compressed, 10 * 1024));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
    }

    @Test
    void gzipHandlesAnEmptyMessageWithZeroLimit() {
        final Bytes compressed = GrpcCompression.getCompressor("gzip").compress(Bytes.EMPTY);
        assertEquals(Bytes.EMPTY, GrpcCompression.decompress(GrpcCompression.getDecompressor("gzip"), compressed, 0));
    }

    @Test
    void readsAtMostOneByteBeyondTheDecodedLimit() throws Exception {
        final AtomicInteger bytesRead = new AtomicInteger();
        final InputStream decoded = new ByteArrayInputStream(new byte[1024 * 1024]) {
            @Override
            public synchronized int read(final byte[] bytes, final int offset, final int length) {
                final int read = super.read(bytes, offset, length);
                if (read > 0) {
                    bytesRead.addAndGet(read);
                }
                return read;
            }
        };
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.readDecompressed(decoded, 10_000));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
        assertEquals(10_001, bytesRead.get());
    }

    @Test
    void rejectsLegacyCustomDecompressorsWithoutCallingTheUnboundedMethod() {
        final AtomicBoolean called = new AtomicBoolean();
        final GrpcCompression.Decompressor legacy = bytes -> {
            called.set(true);
            return Bytes.wrap(new byte[1024 * 1024]);
        };
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.decompress(legacy, Bytes.EMPTY, 100));
        assertEquals(GrpcStatus.UNIMPLEMENTED, failure.status());
        assertFalse(called.get());
    }

    @Test
    void rejectsCustomDecompressorsThatReturnMoreThanTheRequestedBound() {
        final GrpcCompression.Decompressor invalid = new GrpcCompression.Decompressor() {
            @Override
            public Bytes decompress(final Bytes bytes) {
                throw new AssertionError("Unbounded method must not be called");
            }

            @Override
            public Bytes decompress(final Bytes bytes, final int maxOutputSize) {
                return Bytes.wrap(new byte[maxOutputSize + 1]);
            }
        };
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.decompress(invalid, Bytes.EMPTY, 100));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
    }

    @Test
    void identityDoesNotAcceptAMessageMarkedCompressed() {
        final GrpcException failure = assertThrows(
                GrpcException.class,
                () -> GrpcCompression.decompress(
                        GrpcCompression.getDecompressor(GrpcCompression.IDENTITY), Bytes.EMPTY, 100));
        assertEquals(GrpcStatus.INTERNAL, failure.status());
    }
}
