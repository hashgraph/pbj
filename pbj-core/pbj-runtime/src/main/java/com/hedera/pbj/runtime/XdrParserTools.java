// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Static utility class with all XDR read and validation methods.
 *
 * <p>XDR (External Data Representation, RFC 4506) uses big-endian byte order throughout,
 * with 4-byte alignment. All variable-length data is padded to 4-byte boundaries.
 *
 * <p>Methods that IDE thinks are unused may be used in generated code by the PBJ compiler.
 */
@SuppressWarnings({"DuplicatedCode", "unused"})
public final class XdrParserTools {

    /** Instance should never be created */
    private XdrParserTools() {}

    /**
     * Read an XDR {@code int} (signed 32-bit, big-endian) from input.
     *
     * @param in the input to read from
     * @return the read int
     */
    public static int readInt(final ReadableSequentialData in) {
        return in.readInt();
    }

    /**
     * Read an XDR {@code hyper} (signed 64-bit, big-endian) from input.
     *
     * @param in the input to read from
     * @return the read long
     */
    public static long readHyper(final ReadableSequentialData in) {
        return in.readLong();
    }

    /**
     * Read an XDR {@code float} (IEEE 754 single-precision, big-endian) from input.
     *
     * @param in the input to read from
     * @return the read float
     */
    public static float readFloat(final ReadableSequentialData in) {
        return in.readFloat();
    }

    /**
     * Read an XDR {@code double} (IEEE 754 double-precision, big-endian) from input.
     *
     * @param in the input to read from
     * @return the read double
     */
    public static double readDouble(final ReadableSequentialData in) {
        return in.readDouble();
    }

    /**
     * Read an XDR {@code bool} from input. XDR encodes booleans as a 4-byte int where
     * {@code 0} means false and {@code 1} means true. Any other value is an error.
     *
     * @param in the input to read from
     * @return the read boolean
     * @throws ParseException if the value is not 0 or 1
     */
    public static boolean readBool(final ReadableSequentialData in) throws ParseException {
        final int value = in.readInt();
        if (value != 0 && value != 1) {
            throw new ParseException("XDR: bool must be 0 or 1, got " + value);
        }
        return value == 1;
    }

    /**
     * Read an XDR variable-length {@code string} from input. The encoding is:
     * 4-byte length (unsigned int) + UTF-8 data bytes + 0-3 zero padding bytes to
     * reach the next 4-byte boundary.
     *
     * @param in the input to read from
     * @param maxSize the maximum allowed string length in bytes
     * @return the read String
     * @throws ParseException if the length is negative, exceeds maxSize, or padding is non-zero
     */
    public static String readString(final ReadableSequentialData in, final int maxSize) throws ParseException {
        final int length = in.readInt();
        if (length < 0) {
            throw new ParseException("XDR: negative length");
        }
        if (length > maxSize) {
            throw new ParseException("XDR: length exceeds maxSize");
        }
        final ByteBuffer bb = ByteBuffer.allocate(length);
        in.readBytes(bb);
        final byte[] bytes = bb.array();
        final String result = new String(bytes, StandardCharsets.UTF_8);
        readAndValidatePadding(in, length);
        return result;
    }

    /**
     * Read an XDR variable-length {@code opaque} from input. The encoding is:
     * 4-byte length (unsigned int) + raw bytes + 0-3 zero padding bytes to
     * reach the next 4-byte boundary.
     *
     * @param in the input to read from
     * @param maxSize the maximum allowed opaque length in bytes
     * @return the read Bytes
     * @throws ParseException if the length is negative, exceeds maxSize, or padding is non-zero
     */
    public static Bytes readOpaque(final ReadableSequentialData in, final int maxSize) throws ParseException {
        final int length = in.readInt();
        if (length < 0) {
            throw new ParseException("XDR: negative length");
        }
        if (length > maxSize) {
            throw new ParseException("XDR: length exceeds maxSize");
        }
        final Bytes result = in.readBytes(length);
        readAndValidatePadding(in, length);
        return result;
    }

    /**
     * Read an XDR {@code enum} value from input. Returns the raw signed int value;
     * enum validation is the responsibility of the caller.
     *
     * @param in the input to read from
     * @return the raw enum int value
     */
    public static int readEnum(final ReadableSequentialData in) {
        return in.readInt();
    }

    /**
     * Read an XDR optional presence flag from input. The encoding is a 4-byte int where
     * {@code 1} means present and {@code 0} means absent. Any other value is an error.
     *
     * @param in the input to read from
     * @return true if the optional value is present, false otherwise
     * @throws ParseException if the value is not 0 or 1
     */
    public static boolean readPresence(final ReadableSequentialData in) throws ParseException {
        final int value = in.readInt();
        if (value != 0 && value != 1) {
            throw new ParseException("XDR: presence flag must be 0 or 1, got " + value);
        }
        return value == 1;
    }

    /**
     * Read and validate XDR padding bytes after variable-length data. Computes the number
     * of padding bytes as {@code (4 - (dataLength % 4)) % 4} and reads each byte, throwing
     * a {@link ParseException} if any padding byte is non-zero.
     *
     * @param in the input to read from
     * @param dataLength the number of data bytes that preceded the padding
     * @throws ParseException if any padding byte is non-zero
     */
    public static void readAndValidatePadding(final ReadableSequentialData in, final int dataLength)
            throws ParseException {
        final int paddingCount = (4 - (dataLength % 4)) % 4;
        for (int i = 0; i < paddingCount; i++) {
            final byte padByte = in.readByte();
            if (padByte != 0) {
                throw new ParseException("XDR: non-zero padding byte at position " + i);
            }
        }
    }
}
