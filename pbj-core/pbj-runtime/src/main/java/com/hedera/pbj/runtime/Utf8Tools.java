// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static java.lang.Character.*;

import com.hedera.pbj.runtime.io.SlimWriter;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * UTF8 tools based on protobuf standard library, so we are byte for byte identical
 */
public final class Utf8Tools {

    static int encodedLength(String sz) {
        if (sz == null) return 0;
        int count = 0;
        for (int i = 0; i < sz.length(); i++) {
            char c = sz.charAt(i);
            if (c < 0x80) {
                count += 1;
            } else if (c < 0x800) {
                count += 2;
            } else if (c < 0xD800 || c >= 0xE000) {
                count += 3;
            } else if (c <= 0xDBFF) { // high surrogate: D800–DBFF
                if (i + 1 >= sz.length()) return 0; // throw new IOException("Lone high surrogate at index " + i);
                char low = sz.charAt(i + 1);
                if (low < 0xDC00 || low > 0xDFFF)
                    return 0; // throw new IOException("Invalid low surrogate at index " + (i + 1));
                i++; // consume the low surrogate
                count += 4;
            } else {
                return 0; // throw new IOException("Lone low surrogate at index " + i);  // DC00–DFFF with no preceding
                // high
            }
        }
        return count;
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

    // doesn't write out the length
    static void encodeUtf8(String in, SlimWriter out) throws IOException {
        int inLength = in.length();
        for (int i = 0; i < inLength; ++i) {
            char c = in.charAt(i);
            if (c < 0x80) {
                out.writeByte((byte) c);
            } else if (c < 0x800) {
                out.writeByte2((byte) (0xC0 | (c >>> 6)), (byte) (0x80 | (0x3F & c)));
            } else if (c < MIN_SURROGATE || MAX_SURROGATE < c) {
                out.writeByte3(
                        (byte) (0xE0 | (c >>> 12)), (byte) (0x80 | (0x3F & (c >>> 6))), (byte) (0x80 | (0x3F & c)));
            } else {
                char low;
                if (i + 1 == inLength || !isSurrogatePair(c, (low = in.charAt(++i)))) {
                    throw new MalformedProtobufException("Unpaired surrogate at index " + i + " of " + inLength);
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

    static void WriteUTF8(String in, SlimWriter out) throws IOException {
        int inLength = in.length();
        if (inLength > 0x7F) {
            WriteUTF8_2byte(in, out);
            return;
        }
        // fast path 1 byte tag case
        out.reserveRel(0x7F * 4 + 2); // worse case size
        int pos = out.position();
        out.placehold(1);
        encodeUtf8(in, out);
        int endPos = out.position();
        int utf8Len = endPos - pos - 1;
        if (utf8Len <= 0x7F) {
            out.writeAt(pos, (byte) utf8Len);
        } else {
            out.reinsertVarInt(pos);
        }
    }

    private static void WriteUTF8_2byte(String in, SlimWriter out) throws IOException {
        // buffer is 16k, string is UTF16, so worst case is len*3.
        // 5460 was picked bc its (16k - 2byte tag) / 3 byte worse case
        if (in.length() > 5460) {
            // Can't fit in buffer, todo check if we'll grow anyways
            // I don't think anything hits this case?
            // These two lines counts the length then write the length, making this 2 pass
            out.writeVarIntNoZZ(ProtoWriterTools.sizeOfStringNoTag(in));
            Utf8Tools.encodeUtf8(in, out);
            return;
        }
        out.reserveRel(in.length() * 3 + 2);
        int pos = out.position();
        out.placehold(2);
        Utf8Tools.encodeUtf8(in, out);
        int utf8Len = out.position() - pos - 2;
        out.writeAt(pos, (byte) ((utf8Len & 0x7F) | 0x80));
        out.writeAt(pos + 1, (byte) (utf8Len >>> 7));
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
