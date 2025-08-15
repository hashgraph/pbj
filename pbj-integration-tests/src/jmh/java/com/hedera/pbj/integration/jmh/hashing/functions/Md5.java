// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * Non-thread-safe MD5 implementation of HashFunction. Takes the lower 32 bits of the hash as integer.
 */
public class Md5 {
    private static MessageDigest md5;
    private static byte[] hash;

    static {
        try {
            md5 = MessageDigest.getInstance("MD5");
            hash = new byte[md5.getDigestLength()];
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MD5", e);
        }
    }

    public static int hash32(byte[] data, int offset, int len) {
        md5.update(data, offset, len);
        try {
            md5.digest(hash, 0, hash.length);
        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
        return ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16) | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
    }
}
