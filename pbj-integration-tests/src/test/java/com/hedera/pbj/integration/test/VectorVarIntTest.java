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
        final int limit = Math.min(bytes.length, pos + 10);
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        byte b;
        long v = (b = bytes[pos++]) & 0x7F;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7F) << 7;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7F) << 14;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7F) << 21;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7FL) << 28;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7FL) << 35;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7FL) << 42;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7FL) << 49;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        v |= ((b = bytes[pos++]) & 0x7FL) << 56;
        if ((b & 0x80) == 0) {
            return zigZag ? (v >>> 1) ^ -(v & 1) : v;
        }
        if (pos >= limit) throw new DataEncodingException("Malformed var int");

        b = bytes[pos++];
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
