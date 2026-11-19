// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static java.lang.Character.*;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * UTF8 tools based on protobuf standard library, so we are byte for byte identical
 */
public final class Utf8Tools {

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

    /**
     * Decode UTF-8 bytes into a String, rejecting malformed input exactly as the JDK decoder does in REPORT mode, without
     * the decoder's buffers: all-ASCII input becomes a compact Latin-1 string, and anything else is validated, then
     * decoded.
     *
     * @param in the bytes
     * @param offset the index of the first byte
     * @param length the number of bytes
     * @return the string
     * @throws MalformedProtobufException if the bytes are not well-formed UTF-8
     */
    public static String decodeUtf8Strict(@NonNull final byte[] in, final int offset, final int length)
            throws MalformedProtobufException {
        if (length == 0) {
            return "";
        }
        final int end = offset + length;
        for (int i = offset; i < end; i++) {
            if (in[i] < 0) {
                if (!isWellFormedUtf8(in, i, end)) {
                    throw new MalformedProtobufException("Malformed UTF-8 string encountered");
                }
                return new String(in, offset, length, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return new String(in, offset, length, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    /**
     * Whether {@code in[offset, end)} is well-formed UTF-8: no stray continuation bytes, overlong forms, surrogates,
     * code points over U+10FFFF or truncated sequences. The same rules as the JDK decoder in REPORT mode.
     *
     * @param in the bytes
     * @param offset the index of the first byte to check
     * @param end the index after the last byte
     * @return true if well-formed
     */
    public static boolean isWellFormedUtf8(@NonNull final byte[] in, final int offset, final int end) {
        int i = offset;
        while (i < end) {
            final int c = in[i] & 0xFF;
            if (c < 0x80) {
                i++;
            } else if (c < 0xC2) {
                // a continuation byte, or an overlong two-byte form
                return false;
            } else if (c < 0xE0) {
                if (i + 1 >= end || (in[i + 1] & 0xC0) != 0x80) return false;
                i += 2;
            } else if (c < 0xF0) {
                if (i + 2 >= end) return false;
                final int c1 = in[i + 1] & 0xFF;
                if ((c1 & 0xC0) != 0x80 || (in[i + 2] & 0xC0) != 0x80) return false;
                // overlong forms, and the surrogates U+D800 to U+DFFF
                if ((c == 0xE0 && c1 < 0xA0) || (c == 0xED && c1 >= 0xA0)) return false;
                i += 3;
            } else if (c < 0xF5) {
                if (i + 3 >= end) return false;
                final int c1 = in[i + 1] & 0xFF;
                if ((c1 & 0xC0) != 0x80 || (in[i + 2] & 0xC0) != 0x80 || (in[i + 3] & 0xC0) != 0x80) return false;
                // overlong forms, and code points over U+10FFFF
                if ((c == 0xF0 && c1 < 0x90) || (c == 0xF4 && c1 >= 0x90)) return false;
                i += 4;
            } else {
                return false;
            }
        }
        return true;
    }
}
