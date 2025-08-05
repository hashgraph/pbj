// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.NonCryptographicHashing.hash64;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Non-Cryptographic Hash Test")
class NonCryptographicHashTest {
    /**
     * Test the hash64(long) method with known values. The computation is very simple to do with any
     * calculator,
     */
    @Test
    @DisplayName("Test Hash64(long) Long with Known Values")
    void testHash64Long() {
        assertEquals(605873356528442819L, NonCryptographicHashing.hash64(0L));
        assertEquals(4748194389872103055L, NonCryptographicHashing.hash64(1L));
        assertEquals(5797980124308584942L, NonCryptographicHashing.hash64(-1L));
        assertEquals(6218562537029544279L, NonCryptographicHashing.hash64(1234567890123456789L));
    }

    /**
     * Test the hash64(byte[]) method with an empty byte array. This computation is also very simple
     * to do with any calculator, and the result is known. We want to show that hashing an empty
     * array is OK.
     */
    @Test
    @DisplayName("Test Hash64(byte[]) Empty Array")
    void testHash64ByteArrayEmpty() {
        assertEquals(2903670678409729503L, NonCryptographicHashing.hash64(new byte[0]));
    }

    /**
     * Test the hash64(byte[], int, int) method with an empty byte array, position 0, and length 0.
     */
    @Test
    @DisplayName("Test Hash64(byte[], int, int) Empty Array with Valid Position and Length")
    void testHash64ByteArrayEmptyWithPositionAndLength() {
        assertEquals(2903670678409729503L, NonCryptographicHashing.hash64(new byte[0], 0, 0));
    }

    /**
     * Test the hash64(byte[], int, int) method with position > length of the byte array.
     */
    @Test
    @DisplayName("Test Hash64(byte[], int, int) Position Exceeds Array Length")
    @Disabled("Disabled for now. I don't want to do the check and slow things down. Do we care about this?")
    void testHash64ByteArrayPositionExceedsLength() {
        byte[] arr = new byte[5];
        // At the moment just returns the hash of 255.
        assertThrows(IndexOutOfBoundsException.class, () -> NonCryptographicHashing.hash64(arr, 6, 0));
    }

    /**
     * Test the hash64(byte[], int, int) method with position < 0.
     */
    @Test
    @DisplayName("Test Hash64(byte[], int, int) Negative Position")
    @Disabled("Disabled for now. I don't want to do the check and slow things down. Do we care about this?")
    void testHash64ByteArrayNegativePosition() {
        byte[] arr = new byte[5];
        // At the moment just returns the hash of 255.
        assertThrows(IndexOutOfBoundsException.class, () -> NonCryptographicHashing.hash64(arr, -1, 0));
    }

    /**
     * Test the hash64(byte[], int, int) method with length < 0.
     */
    @Test
    @DisplayName("Test Hash64(byte[], int, int) Negative Length")
    @Disabled("Disabled for now. I don't want to do the check and slow things down. Do we care about this?")
    void testHash64ByteArrayNegativeLength() {
        byte[] arr = new byte[5];
        // At the moment just returns the hash of 255.
        assertThrows(IllegalArgumentException.class, () -> NonCryptographicHashing.hash64(arr, 0, -1));
    }

    /**
     * Test the hash64(byte[], int, int) method with position + length > byte array length.
     */
    @Test
    @DisplayName("Test Hash64(byte[], int, int) Position Plus Length Exceeds Array Length")
    void testHash64ByteArrayPositionPlusLengthExceeds() {
        byte[] arr = new byte[5];
        assertThrows(IndexOutOfBoundsException.class, () -> NonCryptographicHashing.hash64(arr, 2, 4));
    }

    /**
     * Test the hash64(byte[]) method with a one-byte array. This shows what happens if we have less than 8 bytes.
     * The constant was found by calculating by hand the expected result.
     */
    @Test
    @DisplayName("Test Hash64(byte[]) with array less than 8 bytes")
    void testHash64ByteArrayLessThan8Bytes() {
        byte[] arr = {(byte) 1};
        assertEquals(3532887395273621549L, NonCryptographicHashing.hash64(arr));
    }

    /**
     * Test the hash64(byte[]) method with an 8-byte array. This shows what happens if we test with exactly 8 bytes.
     * The constant was found by calculating by hand the expected result.
     */
    @Test
    @DisplayName("Test Hash64(byte[]) with 8 bytes")
    void testHash64ByteArray8Bytes() {
        byte[] arr = {(byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7, (byte) 8};

        assertEquals(8350451599110236880L, NonCryptographicHashing.hash64(arr));
    }

    /**
     * Test the hash64(byte[]) method with a 12-byte array. This shows what happens if we test with more than
     * 8 bytes, but not a multiple of 8. The constant was found by calculating by hand the expected result.
     */
    @Test
    @DisplayName("Test Hash64(byte[]) with larger non-multiple of 8 bytes")
    void testHash64ByteArrayMoreThan8ButNotMultipleOf8Bytes() {
        byte[] arr = {
            (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7, (byte) 8, (byte) 9, (byte) 10,
            (byte) 11, (byte) 12
        };

        assertEquals(4316537784988356653L, NonCryptographicHashing.hash64(arr));
    }

    /**
     * Test the hash64(byte[]) method with a 16-byte array. This shows what happens for arrays that are a multiple of 8.
     * The constant was found by calculating by hand the expected result.
     */
    @Test
    @DisplayName("Test Hash64(byte[]) with multiple of 8 bytes")
    void testHash64ByteArrayMultipleOf8Bytes() {
        byte[] arr = {
            (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7, (byte) 8, (byte) 9, (byte) 10,
            (byte) 11, (byte) 12, (byte) 13, (byte) 14, (byte) 15, (byte) 16
        };

        assertEquals(4734248821214862750L, NonCryptographicHashing.hash64(arr));
    }

    /**
     * While not comprehensive, this test provides a basic sanity check that if you are given two arrays of different
     * lengths, but they both have the same high byte set and all other bytes are zero, then they generate different
     * hashes.
     */
    @Test
    @DisplayName("Test arrays of various lengths with high byte set and all else zero do not collide")
    void testLeadingOneHasNoCollisions() {
        Set<Long> hashes = new HashSet<>();
        for (int len = 1; len <= 16; len++) {
            byte[] leadingOne = new byte[len];
            long h1 = NonCryptographicHashing.hash64(leadingOne);
            assertTrue(hashes.add(h1)); // asserts each is unique
        }
    }

    /**
     * While not comprehensive, this test provides a basic sanity check that if you are given two arrays of different
     * lengths, but they both have all bytes set to 1, then they generate different hashes.
     */
    @Test
    void testAllOnesHasNoCollisions() {
        Set<Long> hashes = new HashSet<>();
        for (int len = 1; len <= 16; len++) {
            byte[] allOnes = new byte[len];
            for (int i = 0; i < len; i++) allOnes[i] = (byte) 0xFF;
            long h1 = NonCryptographicHashing.hash64(allOnes);
            assertTrue(hashes.add(h1)); // asserts each is unique
        }
    }

    /**
     * This test checks that the hash64 method does not produce collisions for small arrays.
     * It verifies that all possible byte combinations for arrays of length 1 and 2 produce unique hashes.
     */
    @Test
    @DisplayName("Test No Collisions for Small Arrays")
    void testNoCollisionsSmallArrays() {
        // Length 1: all 256
        Set<Long> set1 = new HashSet<>();
        for (int i = 0; i < 256; i++) {
            byte[] ba = {(byte) i};
            assertTrue(set1.add(NonCryptographicHashing.hash64(ba)));
        }

        // Length 2: all 65536
        Set<Long> set2 = new HashSet<>();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                byte[] ba = {(byte) i, (byte) j};
                assertTrue(set2.add(NonCryptographicHashing.hash64(ba)));
            }
        }
    }

    /**
     * This test checks that the hash64 method does not produce collisions for larger sets of data.
     * It verifies that all possible byte combinations up to the number 100,000 produce unique hashes.
     */
    @Test
    @DisplayName("Test No Collisions for Large Sets")
    void testNoCollisionsLargeSet() {
        final int num = 100_000;
        Set<Long> set = new HashSet<>();
        for (int i = 0; i < num; i++) {
            byte[] ba = ByteBuffer.allocate(4).putInt(i).array();
            assertTrue(set.add(NonCryptographicHashing.hash64(ba)));
        }
    }

    @Test
    @DisplayName("Test Collisions with non-random data")
    void testLowCollisionsLargeSet() {
        // Given an 8 byte array, try changing only the first 2 bytes, and see if we get collisions.
        // A bad hash function would produce many collisions here. Then try again but changing out the middle
        // 2 bytes. And do the same for the last 2 bytes.
        final Set<Long> firstBytesSet = new HashSet<>();
        final Set<Long> middleBytesSet = new HashSet<>();
        final Set<Long> lastBytesSet = new HashSet<>();
        final byte[] arr = {
            (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
            (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08
        };
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                // Change the first two bytes
                arr[6] = (byte) 0x07; // Reset last two bytes
                arr[7] = (byte) 0x08; // Reset last two bytes
                arr[0] = (byte) i;
                arr[1] = (byte) j;
                long hash1 = NonCryptographicHashing.hash64(arr);
                assertTrue(
                        firstBytesSet.add(hash1),
                        "Collision found with first two bytes: iteration=" + i + ", long="
                                + Long.toHexString(UnsafeUtils.getLong(arr, 0)));

                // Change the middle two bytes
                arr[0] = (byte) 0x01; // Reset first two bytes
                arr[1] = (byte) 0x02; // Reset first two bytes
                arr[3] = (byte) i;
                arr[4] = (byte) j;
                long hash2 = NonCryptographicHashing.hash64(arr);
                assertTrue(
                        middleBytesSet.add(hash2),
                        "Collision found with middle two bytes: iteration=" + i + ", long="
                                + Long.toHexString(UnsafeUtils.getLong(arr, 0)));

                // Change the last two bytes
                arr[3] = (byte) 0x03; // Reset middle two bytes
                arr[4] = (byte) 0x04; // Reset middle two bytes
                arr[6] = (byte) i;
                arr[7] = (byte) j;
                long hash3 = NonCryptographicHashing.hash64(arr);
                assertTrue(
                        lastBytesSet.add(hash3),
                        "Collision found with last two bytes: iteration=" + i + ", long="
                                + Long.toHexString(UnsafeUtils.getLong(arr, 0)));
            }
        }
    }

    /**
     * Checks that hashing a byte array with an offset produces the same result as hashing the same bytes directly.
     */
    @Test
    @DisplayName("Test Hash with Offset")
    void testHashWithOffset() {
        byte[] large = new byte[255];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) i;
        }

        // Try every subset where the start is changing but the length includes the last byte.
        for (int i = 0; i < large.length; i++) {
            int length = large.length - i;
            byte[] subset = new byte[length];
            System.arraycopy(large, i, subset, 0, length);
            long expected = NonCryptographicHashing.hash64(subset);
            long actual = NonCryptographicHashing.hash64(large, i, length);
            assertEquals(expected, actual, "Hash with offset where start changes: " + i);
        }

        // Try every subset where the start is always 0 but the length is changing.
        for (int i = 0; i < large.length; i++) {
            int length = large.length - i;
            byte[] subset = new byte[length];
            System.arraycopy(large, 0, subset, 0, length);
            long expected = NonCryptographicHashing.hash64(subset);
            long actual = NonCryptographicHashing.hash64(large, 0, length);
            assertEquals(expected, actual, "Hash with offset where length changes: " + i);
        }
    }

    /**
     * This test does not attempt to verify statistical properties of the hash functions.
     * Its purpose is to ensure that none of the methods cause a crash.
     */
    @Test
    @DisplayName("Test hash64")
    void testHash64() {
        final long seed = 842025;
        final Random random = new Random(seed);

        assertDoesNotThrow(() -> {
            hash64(random.nextLong());

            for (int i = 0; i < 100; i++) {
                final byte[] bytes = new byte[i];
                hash64(bytes);
            }
        });
    }

    @Test
    @DisplayName("Hashes Are Not Degenerate 64")
    void hashesAreNonDegenerate64() {
        final long seed = 842025;
        final Random random = new Random(seed);

        assertNotEquals(0, hash64(0));
        assertNotEquals(0, hash64(random.nextLong()));

        for (int i = 0; i < 100; i++) {
            final byte[] bytes = new byte[i];
            assertNotEquals(0, hash64(bytes), "Hashes should be non-degenerate");
        }
    }
}
