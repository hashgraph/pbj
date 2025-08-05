// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Versions of the traditional Java-style hashing algorithms with different multiplier constants. The 31 constant is
 * what is used in JDK hashCode() methods, while 255 and 256 are interesting alternatives.
 */
public class JavaStyleHashing {
    public static int hash31(@NonNull final byte[] bytes, int start, int length) {
        int h = 1;
        for (int i = length - 1; i >= start; i--) {
            h = 31 * h + bytes[i];
        }
        return h;
    }

    public static int hash255(@NonNull final byte[] bytes, int start, int length) {
        int h = 1;
        for (int i = length - 1; i >= start; i--) {
            h = 255 * h + bytes[i];
        }
        return h;
    }

    public static int hash256(@NonNull final byte[] bytes, int start, int length) {
        int h = 1;
        for (int i = length - 1; i >= start; i--) {
            h = 256 * h + bytes[i];
        }
        return h;
    }
}
