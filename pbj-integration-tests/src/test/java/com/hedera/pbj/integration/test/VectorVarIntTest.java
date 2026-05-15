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

    /// A refactored copy from VarIntByteArrayReadBench.vector_fastXOR.
    private long readVarInt_fastXOR(byte[] bytes, int pos, boolean zigZag) {
        final int limit = Math.min(bytes.length, pos + 10);

        fastpath:
        {
            if (pos < limit) {
                int vi;
                if ((vi = bytes[pos++]) >= 0) {
                    return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
                } else if (pos + 9 == limit) {
                    // Fast path w/o any limit checks if we have 9 more bytes
                    if ((vi ^= bytes[pos++] << 7) < 0) {
                        vi ^= (~0 << 7);
                        return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
                    }

                    if ((vi ^= bytes[pos++] << 14) >= 0) {
                        vi ^= ((~0 << 7) ^ (~0 << 14));
                        return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
                    }

                    if ((vi ^= bytes[pos++] << 21) < 0) {
                        vi ^= ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                        return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
                    }

                    long vl = vi;
                    if ((vl ^= (long) bytes[pos++] << 28) >= 0L) {
                        vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28));
                        return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                    }

                    if ((vl ^= (long) bytes[pos++] << 35) < 0L) {
                        vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35));
                        return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                    }

                    if ((vl ^= (long) bytes[pos++] << 42) >= 0L) {
                        vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42));
                        return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                    }

                    if ((vl ^= (long) bytes[pos++] << 49) < 0L) {
                        vl ^= ((~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49));
                        return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                    }

                    if ((vl ^= (long) bytes[pos++] << 56) >= 0L) {
                        vl ^= ((~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49)
                                ^ (~0L << 56));
                        return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                    }

                    if ((vl ^= (long) bytes[pos++] << 63) >= 0L) {
                        vl ^= ((~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49)
                                ^ (~0L << 56)
                                ^ (~0L << 63));
                        return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                    }
                }
            }
        }

        slowpath:
        {
            // Slower path because this is an array/buffer, and we have less than 9 (or even 10) bytes ahead
            if (pos >= limit) break slowpath;

            // Since the above check is false, the pos was incremented in the fastpath above.
            // This byte is in CPU L1 cache, so this should be fast. Also, this is a slowpath anyway.
            int vi = bytes[pos - 1];
            if ((vi ^= bytes[pos++] << 7) < 0) {
                vi ^= (~0 << 7);
                return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
            }
            if (pos >= limit) break slowpath;

            if ((vi ^= bytes[pos++] << 14) >= 0) {
                vi ^= ((~0 << 7) ^ (~0 << 14));
                return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
            }
            if (pos >= limit) break slowpath;

            if ((vi ^= bytes[pos++] << 21) < 0) {
                vi ^= ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
            }
            if (pos >= limit) break slowpath;

            long vl = vi;
            if ((vl ^= (long) bytes[pos++] << 28) >= 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) bytes[pos++] << 35) < 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) throw new DataEncodingException("Malformed var int");

            if ((vl ^= (long) bytes[pos++] << 42) >= 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) bytes[pos++] << 49) < 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) bytes[pos++] << 56) >= 0L) {
                vl ^= ((~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) bytes[pos++] << 63) >= 0L) {
                vl ^= ((~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56)
                        ^ (~0L << 63));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
        }

        throw new DataEncodingException("Malformed var int");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testVectorVarInt_fastXOR(boolean zigZag) {
        final byte[] bytes = new byte[64];
        final BufferedData bd = BufferedData.wrap(bytes);
        final Random random = new Random(457639854);

        for (int i = 0; i < 10 * 1024 * 1024; i++) {
            final int val = random.nextInt();
            Arrays.fill(bytes, (byte) 0);
            bd.writeVarInt(val, zigZag);

            assertEquals(val, readVarInt_fastXOR(bytes, 0, zigZag));

            bd.reset();
        }
    }
}
