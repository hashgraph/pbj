// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.utf8;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * UTF8 tools based on protobuf standard library, so we are byte for byte identical
 */
public final class Utf8ToolsV4 {

    // ---- Internal fast-path plumbing ---------------------------------------------------------

    // Compact Strings: coder == 0 (LATIN1), 1 (UTF16)
    private static final byte CODER_LATIN1 = 0;
    private static final byte CODER_UTF16  = 1;

    // Guard: if these are non-null, we can fast-path on String internals.
    private static final VarHandle STRING_VALUE_VH;
    private static final VarHandle STRING_CODER_VH;
    private static final boolean   HAS_STRING_VH;

    static {
        VarHandle v = null, c = null;
        boolean ok = false;
        try {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
            v = l.findVarHandle(String.class, "value", byte[].class);
            c = l.findVarHandle(String.class, "coder", byte.class);
            ok = (v != null && c != null);
        } catch (Throwable ignore) {
            ignore.printStackTrace();
            ok = false;
        }
        STRING_VALUE_VH = v;
        STRING_CODER_VH = c;
        HAS_STRING_VH   = ok;
    }

    // Helpers
    private static byte[] stringValueBytes(String s) { return (byte[]) STRING_VALUE_VH.get(s); }
    private static byte   stringCoder(String s)      { return (byte)   STRING_CODER_VH.get(s); }

    // ---- Public API --------------------------------------------------------------------------

    /** Strict UTF-8 decode. Throws IOException on malformed sequences. */
    public static String decodeUtf8(final byte[] in, final int offset, final int length) throws IOException {
        if ((offset | length) < 0 || offset + length > in.length) {
            throw new IndexOutOfBoundsException("decodeUtf8: bad offset/length");
        }
        final int end = offset + length;

        // ASCII run fast path
        int i = offset;
        while (i < end && in[i] >= 0) i++;
        if (i == end) {
            char[] chars = new char[length];
            for (int p = 0; p < length; p++) chars[p] = (char) (in[offset + p] & 0x7F);
            return new String(chars);
        }

        // Count UTF-16 units (strict validation)
        final int charCount = countUtf16UnitsStrict(in, offset, end);
        final char[] out = new char[charCount];

        // Decode
        int outPos = 0;
        i = offset;
        while (i < end) {
            int b0 = in[i++] & 0xFF;
            if (b0 < 0x80) {
                out[outPos++] = (char) b0;
                while (i < end && in[i] >= 0) out[outPos++] = (char) (in[i++] & 0x7F);
                continue;
            }
            if ((b0 & 0xE0) == 0xC0) {
                if (i >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80) throw bad();
                int cp = ((b0 & 0x1F) << 6) | (b1 & 0x3F);
                if (cp < 0x80 || b0 < 0xC2) throw bad(); // overlong
                out[outPos++] = (char) cp;
                continue;
            }
            if ((b0 & 0xF0) == 0xE0) {
                if (i + 1 >= end) throw bad();
                int b1 = in[i++] & 0xFF, b2 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) throw bad();
                if (b0 == 0xE0 && b1 < 0xA0) throw bad();
                if (b0 == 0xED && b1 >= 0xA0) throw bad();
                int cp = ((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
                if (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF)) throw bad();
                out[outPos++] = (char) cp;
                continue;
            }
            if ((b0 & 0xF8) == 0xF0) {
                if (i + 2 >= end) throw bad();
                int b1 = in[i++] & 0xFF, b2 = in[i++] & 0xFF, b3 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) throw bad();
                if (b0 == 0xF0 && b1 < 0x90) throw bad();
                if (b0 > 0xF4 || (b0 == 0xF4 && b1 > 0x8F)) throw bad();
                int cp = ((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
                if (cp < 0x10000 || cp > 0x10FFFF) throw bad();
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
     * Encodes {@code in} to UTF-8 into {@code out} at {@code offset}.
     * Returns bytes written. Throws if insufficient space or malformed surrogates.
     * Uses a VarHandle fast path for String LATIN1 (Compact String) when available.
     */
    public static int encodeUtf8(final String in, final byte[] out, final int offset) throws IOException {
        Objects.requireNonNull(in, "in");
        // Try the internal LATIN1 fast path
        if (HAS_STRING_VH && stringCoder(in) == CODER_LATIN1) {
            final byte[] v = stringValueBytes(in);
            // Quick ASCII check; if all ASCII, we can memcpy directly.
            int i = 0, n = v.length;
            while (i < n && (v[i] & 0x80) == 0) i++;
            if (i == n) {
                // Pure ASCII: exact size == n
                if (out.length - offset < n)
                    throw new IOException("encodeUtf8: insufficient space (ASCII path)");
                System.arraycopy(v, 0, out, offset, n);
                return n;
            }
            // Mixed Latin-1: encode in one pass. Worst-case length = n (ASCII) + 2*(non-ascii bytes)
            // Exact length is computed below (without allocating).
            return encodeLatin1ToUtf8(v, out, offset);
        }
        // Portable path (handles UTF16 coder as well)
        return encodePortable(in, out, offset);
    }

    /**
     * Returns the exact UTF-8 byte length for {@code str}. Validates surrogate pairing.
     * Uses internal LATIN1 fast path if available.
     */
    public static int encodedLength(final String str) throws IOException {
        if (HAS_STRING_VH && stringCoder(str) == CODER_LATIN1) {
            return encodedLengthLatin1(stringValueBytes(str));
        }
        // Portable path for UTF16 (or if internals not available)
        final int n = str.length();
        int len = 0;
        int i = 0;

        // ASCII prefix
        while (i < n && str.charAt(i) <= 0x7F) { len++; i++; }
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
                i++; len += 4;
            } else if (Character.isLowSurrogate(c)) {
                throw new IOException("encodedLength: unpaired low surrogate");
            } else {
                len += 3;
            }
        }
        return len;
    }

    // ---- Internal fast-path (LATIN1 String.value) --------------------------------------------

    /** Exact UTF-8 length for a LATIN1 byte[] (no surrogates exist in LATIN1). */
    private static int encodedLengthLatin1(final byte[] latin1) {
        int ascii = 0, hi = 0; // count ASCII vs high bytes
        for (byte b : latin1) {
            if ((b & 0x80) == 0) ascii++; else hi++;
        }
        // ASCII -> 1 byte; high bytes (0x80..0xFF) -> 2-byte UTF-8
        return ascii + (hi << 1);
    }

    /** Encodes a LATIN1 byte[] directly to UTF-8. Returns bytes written. */
    private static int encodeLatin1ToUtf8(final byte[] latin1, final byte[] out, final int offset) {
        int pos = offset;
        int i = 0, n = latin1.length;

        // ASCII run
        while (i < n && (latin1[i] & 0x80) == 0) {
            out[pos++] = latin1[i++];
            while (i < n && (latin1[i] & 0x80) == 0) {
                out[pos++] = latin1[i++];
            }
            // then fall into non-ASCII handling if applicable
        }

        while (i < n) {
            int b = latin1[i++] & 0xFF;
            if ((b & 0x80) == 0) {
                // ASCII
                out[pos++] = (byte) b;
            } else {
                // LATIN1 0x80..0xFF -> two-byte UTF-8: 0xC2/0xC3 prefix depending on top bit of 0x80..0xFF
                // Values 0x80..0xBF => 0xC2 xx ; 0xC0..0xFF => 0xC3 (b - 0x40)
                if (b < 0xC0) {
                    out[pos++] = (byte) 0xC2;
                    out[pos++] = (byte) b;
                } else {
                    out[pos++] = (byte) 0xC3;
                    out[pos++] = (byte) (b - 0x40); // (b & 0x3F) | 0x80
                }
            }
        }
        return pos - offset;
    }

    // ---- Portable encode (UTF-16 String) -----------------------------------------------------

    private static int encodePortable(final String in, final byte[] out, final int offset) throws IOException {
        int pos = offset;
        final int n = in.length();
        int i = 0;

        // ASCII fast path
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
            out[pos++] = (byte) (0xE0 | (c >>> 12));
            out[pos++] = (byte) (0x80 | ((c >>> 6) & 0x3F));
            out[pos++] = (byte) (0x80 | (c & 0x3F));
            i++;
        }
        return pos - offset;
    }

    // ---- Strict counting for decode ----------------------------------------------------------

    private static int countUtf16UnitsStrict(final byte[] in, final int offset, final int end) throws IOException {
        int i = offset, count = 0;
        while (i < end) {
            int b0 = in[i++] & 0xFF;
            if (b0 < 0x80) {
                count += 1;
                while (i < end && in[i] >= 0) { i++; count++; }
                continue;
            }
            if ((b0 & 0xE0) == 0xC0) {
                if (i >= end) throw bad();
                int b1 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80) throw bad();
                if (b0 < 0xC2) throw bad(); // overlong
                count += 1;
                continue;
            }
            if ((b0 & 0xF0) == 0xE0) {
                if (i + 1 >= end) throw bad();
                int b1 = in[i++] & 0xFF, b2 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) throw bad();
                if (b0 == 0xE0 && b1 < 0xA0) throw bad();
                if (b0 == 0xED && b1 >= 0xA0) throw bad();
                count += 1;
                continue;
            }
            if ((b0 & 0xF8) == 0xF0) {
                if (i + 2 >= end) throw bad();
                int b1 = in[i++] & 0xFF, b2 = in[i++] & 0xFF, b3 = in[i++] & 0xFF;
                if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) throw bad();
                if (b0 == 0xF0 && b1 < 0x90) throw bad();
                if (b0 > 0xF4 || (b0 == 0xF4 && b1 > 0x8F)) throw bad();
                count += 2; // surrogate pair
                continue;
            }
            throw bad();
        }
        return count;
    }

    private static IOException bad() { return new IOException("Malformed UTF-8"); }


    public static void main(String[] args) throws IOException {
        encodeUtf8("hello", new byte[10], 0);
    }
}