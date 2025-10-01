// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.utf8;

import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_SUPPLEMENTARY_CODE_POINT;
import static java.lang.Character.MIN_SURROGATE;
import static java.lang.Character.isSurrogatePair;
import static java.lang.Character.toCodePoint;

import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import java.io.IOException;

/**
 * UTF8 tools based on protobuf standard library, so we are byte for byte identical
 */
@SuppressWarnings("DuplicatedCode")
public final class Utf8ToolsV1 {

    /**
     * Returns the number of bytes in the UTF-8-encoded form of {@code sequence}. For a string, this
     * method is equivalent to {@code string.getBytes(UTF_8).length}, but is more efficient in both
     * time and space.
     *
     * @throws IllegalArgumentException if {@code sequence} contains ill-formed UTF-16 (unpaired
     *     surrogates)
     */
    static int encodedLength(final String sequence) throws IOException {
        if (sequence == null) {
            return 0;
        }
        // Warning to maintainers: this implementation is highly optimized.
        int utf16Length = sequence.length();
        int utf8Length = utf16Length;
        int i = 0;

        // This loop optimizes for pure ASCII.
        while (i < utf16Length && sequence.charAt(i) < 0x80) {
            i++;
        }

        // This loop optimizes for chars less than 0x800.
        for (; i < utf16Length; i++) {
            char c = sequence.charAt(i);
            if (c < 0x800) {
                utf8Length += ((0x7f - c) >>> 31); // branch free!
            } else {
                utf8Length += encodedLengthGeneral(sequence, i);
                break;
            }
        }

        if (utf8Length < utf16Length) {
            // Necessary and sufficient condition for overflow because of maximum 3x expansion
            throw new IllegalArgumentException("UTF-8 length does not fit in int: " + (utf8Length + (1L << 32)));
        }
        return utf8Length;
    }

    private static int encodedLengthGeneral(final CharSequence sequence, final int start) throws IOException {
        int utf16Length = sequence.length();
        int utf8Length = 0;
        for (int i = start; i < utf16Length; i++) {
            char c = sequence.charAt(i);
            if (c < 0x800) {
                utf8Length += (0x7f - c) >>> 31; // branch free!
            } else {
                utf8Length += 2;
                // jdk7+: if (Character.isSurrogate(c)) {
                if (Character.MIN_SURROGATE <= c && c <= Character.MAX_SURROGATE) {
                    // Check that we have a well-formed surrogate pair.
                    int cp = Character.codePointAt(sequence, i);
                    if (cp < MIN_SUPPLEMENTARY_CODE_POINT) {
                        throw new MalformedProtobufException("Unpaired surrogate at index " + i + " of " + utf16Length);
                    }
                    i++;
                }
            }
        }
        return utf8Length;
    }

    public static String decodeUtf8(byte[] in, int offset, int length) {
        return new String(in, offset, length, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Encodes the input character sequence to a {@link WritableSequentialData} using the same algorithm as protoc, so we are
     * byte for byte the same.
     */
    static void encodeUtf8(final String in, byte[] out, int off) throws IOException {
        final int inLength = in.length();
        for (int inIx = 0; inIx < inLength; ++inIx) {
            final char c = in.charAt(inIx);
            if (c < 0x80) {
                // One byte (0xxx xxxx)
                out[off++] = (byte) c;
            } else if (c < 0x800) {
                // Two bytes (110x xxxx 10xx xxxx)

                // Benchmarks show put performs better than putShort here (for HotSpot).
                out[off++] = (byte) (0xC0 | (c >>> 6));
                out[off++] = (byte) (0x80 | (0x3F & c));
            } else if (c < MIN_SURROGATE || MAX_SURROGATE < c) {
                // Three bytes (1110 xxxx 10xx xxxx 10xx xxxx)
                // Maximum single-char code point is 0xFFFF, 16 bits.

                // Benchmarks show put performs better than putShort here (for HotSpot).
                out[off++] = (byte) (0xE0 | (c >>> 12));
                out[off++] = (byte) (0x80 | (0x3F & (c >>> 6)));
                out[off++] = (byte) (0x80 | (0x3F & c));
            } else {
                // Four bytes (1111 xxxx 10xx xxxx 10xx xxxx 10xx xxxx)
                // Minimum code point represented by a surrogate pair is 0x10000, 17 bits, four UTF-8 bytes
                final char low;
                if (inIx + 1 == inLength || !isSurrogatePair(c, (low = in.charAt(++inIx)))) {
                    throw new MalformedProtobufException("Unpaired surrogate at index " + inIx + " of " + inLength);
                }
                int codePoint = toCodePoint(c, low);
                out[off++] = (byte) ((0xF << 4) | (codePoint >>> 18));
                out[off++] = (byte) (0x80 | (0x3F & (codePoint >>> 12)));
                out[off++] = (byte) (0x80 | (0x3F & (codePoint >>> 6)));
                out[off++] = (byte) (0x80 | (0x3F & codePoint));
            }
        }
    }
}
