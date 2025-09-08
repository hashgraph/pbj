// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.utf8;

import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_SURROGATE;
import static java.lang.Character.isSurrogatePair;
import static java.lang.Character.toCodePoint;

import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * UTF8 Tools based on java standard library
 */
@SuppressWarnings("DuplicatedCode")
public final class Utf8ToolsV0 {
    public static int encodedLength(final String in) {
        return in.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    public static String decodeUtf8(byte[] in, int offset, int length) {
        return new String(in, offset, length, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static int encodeUtf8(String in, byte[] out, int offset) {
        byte[] b = in.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(b, 0, out, offset, b.length);
        return b.length;
    }
}
