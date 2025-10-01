// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static java.lang.Character.*;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * UTF8 tools based on protobuf standard library, so we are byte for byte identical
 */
public final class Utf8Tools {

    public static byte[] toUtf8Bytes(final String string) {
        return string.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static List<byte[]> toUtf8Bytes(final List<String> strings) {
        return strings.stream()
                .map(s -> {
                    return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                })
                .toList();
    }

    public static List<byte[]> toUtf8Bytes(final String... strings) {
        return Arrays.stream(strings)
                .map(s -> {
                    return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                })
                .toList();
    }

    public static String toUtf8String(final byte[] bytes) {
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static List<String> toUtf8String(final List<byte[]> bytesList) {
        return bytesList.stream()
                .map(bytes -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                .toList();
    }

    /**
     * Returns the number of bytes in the UTF-8-encoded form of {@code sequence}. For a string, this
     * method is equivalent to {@code string.getBytes(UTF_8).length}, but is more efficient in both
     * time and space.
     *
     * @throws IllegalArgumentException if {@code sequence} contains ill-formed UTF-16 (unpaired
     *     surrogates)
     */
    static int encodedLength(final CharSequence sequence) throws IOException {
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

    /**
     * Encodes the input character sequence to a {@link WritableSequentialData} using the same algorithm as protoc, so we are
     * byte for byte the same.
     */
    static void encodeUtf8(final CharSequence in, final WritableSequentialData out) throws IOException {
        final int inLength = in.length();
        for (int inIx = 0; inIx < inLength; ++inIx) {
            final char c = in.charAt(inIx);
            if (c < 0x80) {
                // One byte (0xxx xxxx)
                out.writeByte((byte) c);
            } else if (c < 0x800) {
                // Two bytes (110x xxxx 10xx xxxx)

                // Benchmarks show put performs better than putShort here (for HotSpot).
                out.writeByte2((byte) (0xC0 | (c >>> 6)), (byte) (0x80 | (0x3F & c)));
            } else if (c < MIN_SURROGATE || MAX_SURROGATE < c) {
                // Three bytes (1110 xxxx 10xx xxxx 10xx xxxx)
                // Maximum single-char code point is 0xFFFF, 16 bits.

                // Benchmarks show put performs better than putShort here (for HotSpot).
                out.writeByte3(
                        (byte) (0xE0 | (c >>> 12)), (byte) (0x80 | (0x3F & (c >>> 6))), (byte) (0x80 | (0x3F & c)));
            } else {
                // Four bytes (1111 xxxx 10xx xxxx 10xx xxxx 10xx xxxx)
                // Minimum code point represented by a surrogate pair is 0x10000, 17 bits, four UTF-8 bytes
                final char low;
                if (inIx + 1 == inLength || !isSurrogatePair(c, (low = in.charAt(++inIx)))) {
                    throw new MalformedProtobufException("Unpaired surrogate at index " + inIx + " of " + inLength);
                }
                int codePoint = toCodePoint(c, low);
                out.writeByte4(
                        (byte) ((0xF << 4) | (codePoint >>> 18)),
                        (byte) (0x80 | (0x3F & (codePoint >>> 12))),
                        (byte) (0x80 | (0x3F & (codePoint >>> 6))),
                        (byte) (0x80 | (0x3F & codePoint)));
            }
        }
    }

    /**
     * Encodes the input character sequence to a byte array using the same algorithm as protoc, so we are byte for
     * byte the same. Returns the number of bytes written.
     *
     * @param out    The byte array to write to
     * @param offset The offset in the byte array to start writing at
     * @param in     The input character sequence to encode
     * @return The number of bytes written
     * @throws MalformedUtf8Exception if the input contains unpaired surrogates
     */
    public static int encodeUtf8(@NonNull final byte[] out, final int offset, final String in) {
        int utf16Length = in.length();
        int i = 0;
        int j = offset;
        // Designed to take advantage of
        // https://wiki.openjdk.org/display/HotSpot/RangeCheckElimination
        for (char c; i < utf16Length && (c = in.charAt(i)) < 0x80; i++) {
            out[j + i] = (byte) c;
        }
        if (i == utf16Length) {
            return j + utf16Length - offset;
        }
        j += i;
        for (char c; i < utf16Length; i++) {
            c = in.charAt(i);
            if (c < 0x80) {
                out[j++] = (byte) c;
            } else if (c < 0x800) { // 11 bits, two UTF-8 bytes
                out[j++] = (byte) ((0xF << 6) | (c >>> 6));
                out[j++] = (byte) (0x80 | (0x3F & c));
            } else if ((c < Character.MIN_SURROGATE || Character.MAX_SURROGATE < c)) {
                // Maximum single-char code point is 0xFFFF, 16 bits, three UTF-8 bytes
                out[j++] = (byte) ((0xF << 5) | (c >>> 12));
                out[j++] = (byte) (0x80 | (0x3F & (c >>> 6)));
                out[j++] = (byte) (0x80 | (0x3F & c));
            } else {
                // Minimum code point represented by a surrogate pair is 0x10000, 17 bits,
                // four UTF-8 bytes
                final char low;
                if (i + 1 == in.length() || !Character.isSurrogatePair(c, (low = in.charAt(++i)))) {
                    throw new MalformedUtf8Exception("Unpaired surrogate at index " + (i - 1) + " of " + utf16Length);
                }
                int codePoint = Character.toCodePoint(c, low);
                out[j++] = (byte) ((0xF << 4) | (codePoint >>> 18));
                out[j++] = (byte) (0x80 | (0x3F & (codePoint >>> 12)));
                out[j++] = (byte) (0x80 | (0x3F & (codePoint >>> 6)));
                out[j++] = (byte) (0x80 | (0x3F & codePoint));
            }
        }
        return j - offset;
    }
}
