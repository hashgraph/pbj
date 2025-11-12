// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import java.util.Random;

/**
 * Random string generator.
 */
public class RandomString {
    // A random seed to try and keep tests deterministic
    private final Random random = new Random(9247525184L);
    private final int length;

    /**
     * Create a new RandomString with a given `length`.
     * @param length the length of strings to be generated
     */
    public RandomString(int length) {
        this.length = length;
    }

    /**
     * Generate a new random string.
     * The current implementation uses ASCII characters space through ~.
     * @return a random string of the configured `length`
     */
    public String nextString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) (random.nextInt(127 - 32) + 32));
        }
        return sb.toString();
    }
}
