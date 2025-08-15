// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * Non-thread-safe SHA-256 implementation of HashFunction. Takes the lower 32 bits of the hash as integer.
 */
public class Sha256 {
    private static MessageDigest sha256;
    private static byte[] hash = new byte[32]; // SHA-256 produces a 32-byte hash

    static {
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SHA-256", e);
        }
    }

    public static int hash32(byte[] data, int offset, int len) {
        sha256.update(data, offset, len);
        try {
            sha256.digest(hash, 0, hash.length);
        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
        return ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16) | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
    }
}
