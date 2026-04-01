// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common.compression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ZstdGrpcTransformerTest {
    // A helper assertion that also prints entire arrays in addition to the default first mismatching index only
    public static void assertArrayEquals(byte[] expected, byte[] actual) {
        Assertions.assertArrayEquals(
                expected,
                actual,
                () -> "Expected:\n" + Arrays.toString(expected) + "\nbut got:\n" + Arrays.toString(actual) + "\n");
    }

    @Test
    void testRegistration() {
        final String name = "ZstdGrpcTransformerTestRegistrationTest".toLowerCase();

        assertFalse(GrpcCompression.getCompressorNames().contains(name));
        assertFalse(GrpcCompression.getDecompressorNames().contains(name));

        final ZstdGrpcTransformer zstdGrpcTransformer = new ZstdGrpcTransformer();
        zstdGrpcTransformer.register(name);

        assertEquals(zstdGrpcTransformer, GrpcCompression.getCompressor(name));
        assertEquals(zstdGrpcTransformer, GrpcCompression.getDecompressor(name));
    }

    @Test
    void testTransformation() {
        final byte[] data = new byte[] {55, -37, 3, 0, 87, 57, 17};
        final byte[] expectedCompressedData =
                new byte[] {40, -75, 47, -3, 0, 88, 56, 0, 0, 55, -37, 3, 0, 87, 57, 17, 1, 0, 0};

        final ZstdGrpcTransformer zstdGrpcTransformer = new ZstdGrpcTransformer();

        final Bytes compressedData = zstdGrpcTransformer.compress(Bytes.wrap(data));
        assertArrayEquals(expectedCompressedData, compressedData.toByteArray());

        final Bytes decompressedData = zstdGrpcTransformer.decompress(compressedData);
        assertArrayEquals(data, decompressedData.toByteArray());
    }
}
