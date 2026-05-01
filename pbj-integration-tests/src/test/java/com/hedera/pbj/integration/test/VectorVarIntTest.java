// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// A test to verify the correctness of the new varint reading algorithm.
public class VectorVarIntTest {

    /// A refactored copy from VarIntByteArrayReadBench.vector_zigZag.
    private long readVarInt(byte[] bytes, int pos, boolean zigZag) {
        byte b = bytes[pos];
        if ((b & 0x80) == 0) {
            return zigZag ? (b >>> 1) ^ -(b & 1) : b;
        }

        byte lim = (byte) -Math.min(bytes.length - pos - 1, 9);
        long v = b & 0x7f;

        byte s = (byte) (((lim & 0x80) >>> 7) & 0xFF);

        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= b << 7;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= (b & 0x7f) << 7;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= b << 14;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= (b & 0x7f) << 14;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= b << 21;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= (b & 0x7f) << 21;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= (long) b << 28;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 28;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= (long) b << 35;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 35;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= (long) b << 42;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 42;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= (long) b << 49;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 49;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= (long) b << 56;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 56;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) {
            v |= (long) b << 63;
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }

        throw new DataEncodingException("Malformed var int");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testVectorVarInt(boolean zigZag) {
        final byte[] bytes = new byte[64];
        final BufferedData bd = BufferedData.wrap(bytes);
        final Random random = new Random(457639854);

        for (int i = 0; i < 10 * 1024 * 1024; i++) {
            final int val = random.nextInt();
            Arrays.fill(bytes, (byte) 0);
            bd.writeVarInt(val, zigZag);

            assertEquals(val, readVarInt(bytes, 0, zigZag));

            bd.reset();
        }
    }
}
