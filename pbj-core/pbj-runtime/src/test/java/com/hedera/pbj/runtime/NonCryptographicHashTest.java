// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.NonCryptographicHashing.hash64;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Non-Cryptographic Hash Test")
class NonCryptographicHashTest {
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
            hash64(random.nextLong());

            for (int i = 0; i < 100; i++) {
                final byte[] bytes = new byte[i];
                hash64(bytes);
            }
        });
    }


    @DisplayName("Hashes Are Not Degenerate 64")
    @Test
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
