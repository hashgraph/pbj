// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.utf8;

import java.io.IOException;

/**
 * UTF8 tools based on protobuf standard library, so we are byte for byte identical
 */
public final class Utf8ToolsV3 {

    // ----------------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------------

    /** Strict UTF-8 decode. Throws IOException on malformed sequences. */
    public static String decodeUtf8(final byte[] in, final int offset, final int length) throws IOException {
        if ((offset | length) < 0 || offset + length > in.length) {
            throw new IndexOutOfBoundsException("decodeUtf8: bad offset/length");
        }
        final int end = offset + length;

        // Fast ASCII-only pass; if all ASCII, build String directly.
        int i = offset;
        while (i < end && (in[i] >= 0)) i++;
        if (i == end) {
            // All ASCII
            char[] chars = new char[length];
            for (int p = 0; p < length; p++) {
                chars[p] = (char) (in[offset + p] & 0x7F);
            }
            return new String(chars);
        }

        // Two-pass: count UTF-16 code units, then decode into a single char[].
        final int charCount = countUtf16UnitsStrict(in, offset, end);
        final char[] out = new char[charCount];

        // Decode pass
        int outPos = 0;
        i = offset;
        while (i < end) {
            int b0 = in[i++] & 0xFF;
            if (b0 < 0x80) {
                out[outPos++] = (char) b0;
                // try to chew a few ASCII in a tight loop
                while (i < end && (in[i] >= 0)) {
                    out[outPos++] = (char) (in[i++] & 0x7F);
                }
                continue;
            }
            if ((b0 & 0xE0) == 0xC0) { // 2-byte
                if (i >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80) throw bad();
                int cp = ((b0 & 0x1F) << 6) | (b1 & 0x3F);
                // reject overlongs: must be >= 0x80; also b0 >= 0xC2
                if (cp < 0x80 || b0 < 0xC2) throw bad();
                out[outPos++] = (char) cp;
                continue;
            }
            if ((b0 & 0xF0) == 0xE0) { // 3-byte
                if (i + 1 >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                int b2 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) throw bad();

                // E0: b1 >= 0xA0 to avoid overlong; ED: b1 <= 0x9F to avoid surrogates
                if (b0 == 0xE0 && b1 < 0xA0) throw bad();
                if (b0 == 0xED && b1 >= 0xA0) throw bad();

                int cp = ((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
                if (cp >= 0xD800 && cp <= 0xDFFF) throw bad(); // no UTF-8-encoded surrogates
                if (cp < 0x800) throw bad(); // overlong (should have been 2-byte)
                out[outPos++] = (char) cp;
                continue;
            }
            if ((b0 & 0xF8) == 0xF0) { // 4-byte
                if (i + 2 >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                int b2 = in[i++] & 0xFF;
                int b3 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) throw bad();

                // F0: b1 >= 0x90 (avoid overlong); F4: b1 <= 0x8F (max U+10FFFF); F5..FF invalid
                if (b0 == 0xF0 && b1 < 0x90) throw bad();
                if (b0 > 0xF4 || (b0 == 0xF4 && b1 > 0x8F)) throw bad();

                int cp = ((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
                if (cp < 0x10000 || cp > 0x10FFFF) throw bad();

                // Encode as surrogate pair in UTF-16
                int hi = ((cp - 0x10000) >>> 10) + 0xD800;
                int lo = ((cp - 0x10000) & 0x3FF) + 0xDC00;
                out[outPos++] = (char) hi;
                out[outPos++] = (char) lo;
                continue;
            }
            throw bad();
        }

        return new String(out);
    }

    /**
     * Encodes {@code in} to UTF-8 into {@code out} starting at {@code offset}.
     * Returns the number of bytes written. Throws IOException if {@code out}
     * does not have enough space or on invalid surrogate usage.
     */
    public static int encodeUtf8(final String in, final byte[] out, final int offset) throws IOException {
        if (in == null) throw new NullPointerException("in");
        if (offset < 0 || offset > out.length) throw new IndexOutOfBoundsException("encodeUtf8: bad offset");

        final int need = encodedLength(in); // also validates surrogate pairs
        if (out.length - offset < need) {
            throw new IOException("encodeUtf8: insufficient space: need=" + need + " have=" + (out.length - offset));
        }

        int pos = offset;
        final int n = in.length();
        int i = 0;

        // ASCII fast path (eat a run)
        while (i < n) {
            char c = in.charAt(i);
            if (c <= 0x7F) {
                out[pos++] = (byte) c;
                i++;
                while (i < n) {
                    char d = in.charAt(i);
                    if (d > 0x7F) break;
                    out[pos++] = (byte) d;
                    i++;
                }
                continue;
            }

            if (c <= 0x7FF) {
                out[pos++] = (byte) (0xC0 | (c >>> 6));
                out[pos++] = (byte) (0x80 | (c & 0x3F));
                i++;
                continue;
            }

            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= n) throw new IOException("encodeUtf8: unpaired high surrogate at end");
                char d = in.charAt(i + 1);
                if (!Character.isLowSurrogate(d)) throw new IOException("encodeUtf8: unpaired high surrogate");
                int cp = Character.toCodePoint(c, d);
                // 4-byte
                out[pos++] = (byte) (0xF0 | (cp >>> 18));
                out[pos++] = (byte) (0x80 | ((cp >>> 12) & 0x3F));
                out[pos++] = (byte) (0x80 | ((cp >>> 6) & 0x3F));
                out[pos++] = (byte) (0x80 | (cp & 0x3F));
                i += 2;
                continue;
            }

            if (Character.isLowSurrogate(c)) {
                throw new IOException("encodeUtf8: unpaired low surrogate");
            }

            // 3-byte (BMP non-surrogate)
            out[pos++] = (byte) (0xE0 | (c >>> 12));
            out[pos++] = (byte) (0x80 | ((c >>> 6) & 0x3F));
            out[pos++] = (byte) (0x80 | (c & 0x3F));
            i++;
        }
        return pos - offset;
    }

    // ----------------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------------

    /** Computes the exact number of UTF-8 bytes required for {@code str}. Validates surrogate pairing. */
    public static int encodedLength(final String str) throws IOException {
        final int n = str.length();
        int len = 0;
        int i = 0;

        // Fast ASCII prefix
        while (i < n) {
            char c = str.charAt(i);
            if (c > 0x7F) break;
            len++;
            i++;
            // nibble a few ASCII in a burst
            while (i < n) {
                char d = str.charAt(i);
                if (d > 0x7F) break;
                len++;
                i++;
            }
        }

        while (i < n) {
            char c = str.charAt(i++);
            if (c <= 0x7F) {
                len += 1;
            } else if (c <= 0x7FF) {
                len += 2;
            } else if (Character.isHighSurrogate(c)) {
                if (i >= n) throw new IOException("encodedLength: unpaired high surrogate at end");
                char d = str.charAt(i);
                if (!Character.isLowSurrogate(d)) throw new IOException("encodedLength: unpaired high surrogate");
                i++; // consume pair
                len += 4;
            } else if (Character.isLowSurrogate(c)) {
                throw new IOException("encodedLength: unpaired low surrogate");
            } else {
                len += 3;
            }
        }
        return len;
    }

    /** Counts UTF-16 code units produced by decoding strict UTF-8 in in[offset..end). */
    private static int countUtf16UnitsStrict(final byte[] in, final int offset, final int end) throws IOException {
        int i = offset;
        int count = 0;

        while (i < end) {
            int b0 = in[i++] & 0xFF;
            if (b0 < 0x80) {
                count += 1;
                // run of ASCII
                while (i < end && (in[i] >= 0)) {
                    i++;
                    count++;
                }
                continue;
            }

            if ((b0 & 0xE0) == 0xC0) { // 2-byte
                if (i >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80) throw bad();
                int cp = ((b0 & 0x1F) << 6) | (b1 & 0x3F);
                if (cp < 0x80 || b0 < 0xC2) throw bad(); // overlong / invalid
                count += 1;
                continue;
            }

            if ((b0 & 0xF0) == 0xE0) { // 3-byte
                if (i + 1 >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                int b2 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) throw bad();
                if (b0 == 0xE0 && b1 < 0xA0) throw bad(); // overlong
                if (b0 == 0xED && b1 >= 0xA0) throw bad(); // surrogate range
                int cp = ((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
                if (cp < 0x800) throw bad(); // overlong
                if (cp >= 0xD800 && cp <= 0xDFFF) throw bad(); // encoded surrogate
                count += 1;
                continue;
            }

            if ((b0 & 0xF8) == 0xF0) { // 4-byte
                if (i + 2 >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                int b2 = in[i++] & 0xFF;
                int b3 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) throw bad();
                if (b0 == 0xF0 && b1 < 0x90) throw bad(); // overlong
                if (b0 > 0xF4 || (b0 == 0xF4 && b1 > 0x8F)) throw bad(); // > U+10FFFF
                count += 2; // surrogate pair in UTF-16
                continue;
            }

            throw bad();
        }
        return count;
    }

    private static IOException bad() {
        return new IOException("Malformed UTF-8 input");
    }
}
