// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static com.hedera.pbj.runtime.io.buffer.NonCryptographicHashing.hash32;
import static com.hedera.pbj.runtime.io.buffer.NonCryptographicHashing.hash64;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Non-Cryptographic Hash Test")
class NonCryptographicHashTest {

    /**
     * This test does not attempt to verify statistical properties of the hash functions.
     * Its purpose is to ensure that none of the methods cause a crash.
     */
    @DisplayName("Test hash32")
    @Test
    void testHash32() {
        final long seed = 842025;
        final Random random = new Random(seed);

        assertDoesNotThrow(() -> {
            hash32(random.nextInt());
            hash32(random.nextInt(), random.nextInt());
            hash32(random.nextInt(), random.nextInt(), random.nextInt());
            hash32(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash32(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());

            hash32(random.nextLong());
            hash32(random.nextLong(), random.nextLong());
            hash32(random.nextLong(), random.nextLong(), random.nextLong());
            hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());

            for (int i = 0; i < 100; i++) {
                final byte[] bytes = new byte[i];
                hash32(bytes);

                final String string = randomString(random, i);
                hash32(string);
            }
        });
    }

    /**
     * This test does not attempt to verify statistical properties of the hash functions.
     * Its purpose is to ensure that none of the methods cause a crash.
     */
    @DisplayName("Test hash64")
    @Test
    void testHash64() {
        final long seed = 842025;
        final Random random = new Random(seed);

        assertDoesNotThrow(() -> {
            hash64(random.nextInt());
            hash64(random.nextInt(), random.nextInt());
            hash64(random.nextInt(), random.nextInt(), random.nextInt());
            hash64(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash64(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());

            hash64(random.nextLong());
            hash64(random.nextLong(), random.nextLong());
            hash64(random.nextLong(), random.nextLong(), random.nextLong());
            hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());

            for (int i = 0; i < 100; i++) {
                final byte[] bytes = new byte[i];
                hash64(bytes);

                final String string = randomString(random, i);
                hash64(string);
            }
        });
    }

    @DisplayName("Hashes Are Not Degenerate 32")
    @Test
    void hashesAreNonDegenerate32() {
        final long seed = 842025;
        final Random random = new Random(seed);

        assertNotEquals(0, hash32(0));
        assertNotEquals(0, hash32(0, 0));
        assertNotEquals(0, hash32(0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertNotEquals(0, hash32(random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));

        for (int i = 0; i < 100; i++) {
            final byte[] bytes = new byte[i];
            assertNotEquals(0, hash32(bytes), "Hashes should be non-degenerate");

            final String string = randomString(random, i);
            assertNotEquals(0, hash32(string), "Hashes should be non-degenerate");
        }
    }

    @DisplayName("Hashes Are Not Degenerate 64")
    @Test
    void hashesAreNonDegenerate64() {
        final long seed = 842025;
        final Random random = new Random(seed);

        assertNotEquals(0, hash64(0));
        assertNotEquals(0, hash64(0, 0));
        assertNotEquals(0, hash64(0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertNotEquals(0, hash64(random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));

        for (int i = 0; i < 100; i++) {
            final byte[] bytes = new byte[i];
            assertNotEquals(0, hash64(bytes), "Hashes should be non-degenerate");

            final String string = randomString(random, i);
            assertNotEquals(0, hash64(string), "Hashes should be non-degenerate");
        }
    }

    public static @NonNull String randomString(@NonNull final Random random, final int length) {
        final int LEFT_LIMIT = 48; // numeral '0'
        final int RIGHT_LIMIT = 122; // letter 'z'

        return random.ints(LEFT_LIMIT, RIGHT_LIMIT + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

}
