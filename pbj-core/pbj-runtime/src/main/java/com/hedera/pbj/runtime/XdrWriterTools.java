// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;

/**
 * Static helper methods for writing XDR-encoded data (RFC 4506).
 *
 * <p>All methods write in big-endian (network) byte order. All XDR values are 4-byte aligned;
 * variable-length data (strings, opaque) is padded to the next 4-byte boundary with zero bytes.
 */
@SuppressWarnings("unused")
public final class XdrWriterTools {

    /** Instance should never be created */
    private XdrWriterTools() {}

    // ================================================================================================================
    // WRITE METHODS

    /**
     * Write a signed 32-bit XDR {@code int} (4 bytes, big-endian).
     *
     * @param out the output stream
     * @param value the value to write
     */
    public static void writeInt(@NonNull final WritableSequentialData out, final int value) {
        out.writeInt(value);
    }

    /**
     * Write a signed 64-bit XDR {@code hyper} (8 bytes, big-endian).
     *
     * @param out the output stream
     * @param value the value to write
     */
    public static void writeHyper(@NonNull final WritableSequentialData out, final long value) {
        out.writeLong(value);
    }

    /**
     * Write an IEEE 754 single-precision XDR {@code float} (4 bytes, big-endian).
     *
     * @param out the output stream
     * @param value the value to write
     */
    public static void writeFloat(@NonNull final WritableSequentialData out, final float value) {
        out.writeFloat(value);
    }

    /**
     * Write an IEEE 754 double-precision XDR {@code double} (8 bytes, big-endian).
     *
     * @param out the output stream
     * @param value the value to write
     */
    public static void writeDouble(@NonNull final WritableSequentialData out, final double value) {
        out.writeDouble(value);
    }

    /**
     * Write an XDR {@code bool} (4 bytes: 0 for false, 1 for true).
     *
     * @param out the output stream
     * @param value the value to write
     */
    public static void writeBool(@NonNull final WritableSequentialData out, final boolean value) {
        out.writeInt(value ? 1 : 0);
    }

    /**
     * Write an XDR {@code string}: 4-byte UTF-8 byte length, UTF-8 bytes, then zero-padding
     * to the next 4-byte boundary.
     *
     * @param out the output stream
     * @param value the string to write (must not be null)
     */
    public static void writeString(@NonNull final WritableSequentialData out, @NonNull final String value) {
        final byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.writeBytes(utf8);
        writePadding(out, utf8.length);
    }

    /**
     * Write XDR variable-length {@code opaque} data: 4-byte byte length, raw bytes, then
     * zero-padding to the next 4-byte boundary.
     *
     * @param out the output stream
     * @param value the bytes to write (must not be null)
     */
    public static void writeOpaque(@NonNull final WritableSequentialData out, @NonNull final Bytes value) {
        final int len = (int) value.length();
        out.writeInt(len);
        value.writeTo(out);
        writePadding(out, len);
    }

    /**
     * Write an XDR {@code enum} value as a 4-byte signed int (the proto ordinal).
     *
     * @param out the output stream
     * @param protoOrdinal the proto ordinal value of the enum constant
     */
    public static void writeEnum(@NonNull final WritableSequentialData out, final int protoOrdinal) {
        out.writeInt(protoOrdinal);
    }

    /**
     * Write an XDR optional-field presence flag (4 bytes: 0 for absent, 1 for present).
     *
     * @param out the output stream
     * @param present {@code true} if the field is present
     */
    public static void writePresence(@NonNull final WritableSequentialData out, final boolean present) {
        out.writeInt(present ? 1 : 0);
    }

    /**
     * Write zero-padding bytes to align {@code dataLength} bytes to the next 4-byte boundary.
     * Writes {@code (4 - (dataLength % 4)) % 4} zero bytes.
     *
     * @param out the output stream
     * @param dataLength the number of data bytes already written
     */
    public static void writePadding(@NonNull final WritableSequentialData out, final int dataLength) {
        final int pad = paddingSize(dataLength);
        for (int i = 0; i < pad; i++) {
            out.writeByte((byte) 0);
        }
    }

    // ================================================================================================================
    // SIZE MEASUREMENT METHODS

    /**
     * Compute the XDR encoded size of a string: 4-byte length prefix + UTF-8 bytes + padding.
     *
     * @param value the string to measure (must not be null)
     * @return the total encoded size in bytes
     */
    public static int sizeOfString(@NonNull final String value) {
        final int utf8Len = value.getBytes(StandardCharsets.UTF_8).length;
        return 4 + utf8Len + paddingSize(utf8Len);
    }

    /**
     * Compute the XDR encoded size of variable-length opaque data: 4-byte length prefix + data + padding.
     *
     * @param value the bytes to measure (must not be null)
     * @return the total encoded size in bytes
     */
    public static int sizeOfOpaque(@NonNull final Bytes value) {
        final int len = (int) value.length();
        return 4 + len + paddingSize(len);
    }

    /**
     * Compute the XDR encoded size of variable-length opaque data given a known byte length.
     *
     * @param dataLength the number of data bytes
     * @return the total encoded size in bytes
     */
    public static int sizeOfOpaque(final int dataLength) {
        return 4 + dataLength + paddingSize(dataLength);
    }

    /**
     * Compute the number of padding bytes needed to align {@code dataLength} bytes to a 4-byte boundary.
     *
     * @param dataLength the number of data bytes
     * @return number of zero padding bytes (0, 1, 2, or 3)
     */
    public static int paddingSize(final int dataLength) {
        return (4 - (dataLength % 4)) % 4;
    }
}
