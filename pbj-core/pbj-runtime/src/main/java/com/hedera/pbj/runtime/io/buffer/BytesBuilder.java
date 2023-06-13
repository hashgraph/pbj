package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A utility class tp allow building immutable {@link Bytes} objects
 */

public class BytesBuilder {
    /**
     * Appends a {@link Bytes} object to another {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes1 The first {@link Bytes} object to append to.
     * @param bytes2 The second {@link Bytes} object to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    public static @NonNull Bytes appendBytes(@NonNull final Bytes bytes1, @NonNull final Bytes bytes2) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes1.length() + (int)bytes2.length())];
        bytes1.getBytes(0, newBytes, 0, (int)bytes1.length());
        bytes2.getBytes(0, newBytes, (int)bytes1.length(), (int)bytes2.length());
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends a {@link RandomAccessData} object to another {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes1 The first {@link Bytes} object to append to.
     * @param data The second {@link RandomAccessData} object to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    public static @NonNull Bytes appendBytes(@NonNull final Bytes bytes1, @NonNull final RandomAccessData data) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes1.length() + (int)data.length())];
        bytes1.getBytes(0, newBytes, 0, (int)bytes1.length());
        data.getBytes(0, newBytes, (int)bytes1.length(), (int)data.length());
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends a String to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param str The String to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    public static @NonNull Bytes appendString(@NonNull final Bytes bytes, @NonNull final String str) {
        return appendString(bytes, str, StandardCharsets.UTF_8);
    }

    /**
     * Appends a String to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param str The String to append.
     * @param charSet The character set to use for converting to bytes.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    public static @NonNull Bytes appendString(
            @NonNull final Bytes bytes, @NonNull final String str, @NonNull final Charset charSet) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] strBytes = str.getBytes(charSet);
        byte[] newBytes = new byte[(int)(bytes.length() + strBytes.length)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        System.arraycopy(strBytes, 0, newBytes, (int)bytes.length(), strBytes.length);
        return Bytes.wrap(newBytes);
    }
    /**
     * Appends a byte to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The first {@link Bytes} object to append to.
     * @param b The byte to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendByte(@NonNull final Bytes bytes, final byte b) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes.length() + 1)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        newBytes[(int)bytes.length()] = b;
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends an int to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param i The int to be appended
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendInt(@NonNull final Bytes bytes, final int i) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes.length() + 4)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        newBytes[(int)bytes.length()] = (byte)(i >>> 24);
        newBytes[(int)bytes.length() + 1] = (byte)(i >>> 16);
        newBytes[(int)bytes.length() + 2] = (byte)(i >>> 8);
        newBytes[(int)bytes.length() + 3] = (byte)(i);
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends an int to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param i The int to be appended
     * @param byteOrder teh order of the bytes in int.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    public static @NonNull Bytes appendInt(@NonNull final Bytes bytes, final int i , @NonNull final ByteOrder byteOrder) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes.length() + 4)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return appendInt(bytes, i);
        } else {
            bytes.getBytes(0, newBytes, 0, (int)bytes.length());
            newBytes[(int)bytes.length()] = (byte)(i);
            newBytes[(int)bytes.length() + 1] = (byte)(i >>> 8);
            newBytes[(int)bytes.length() + 2] = (byte)(i >>> 16);
            newBytes[(int)bytes.length() + 3] = (byte)(i >>> 24);
            return Bytes.wrap(newBytes);
        }
    }

    /**
     * Appends an unsigned int (long) to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param i The int to be appended
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    public static @NonNull Bytes appendUnsignedInt(@NonNull final Bytes bytes, final long i) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes.length() + 4)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        newBytes[(int)bytes.length()] = (byte)(i >>> 24);
        newBytes[(int)bytes.length() + 1] = (byte)(i >>> 16);
        newBytes[(int)bytes.length() + 2] = (byte)(i >>> 8);
        newBytes[(int)bytes.length() + 3] = (byte)(i);
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends an unsigned int (long) to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param i The int to be appended
     * @param byteOrder teh order of the bytes in int.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendUnsignedInt(@NonNull final Bytes bytes, final long i , @NonNull final ByteOrder byteOrder) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes.length() + 4)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return appendUnsignedInt(bytes, i);
        } else {
            newBytes[(int)bytes.length()] = (byte)(i);
            newBytes[(int)bytes.length() + 1] = (byte)(i >>> 8);
            newBytes[(int)bytes.length() + 2] = (byte)(i >>> 16);
            newBytes[(int)bytes.length() + 3] = (byte)(i >>> 24);
            return Bytes.wrap(newBytes);
        }
    }

    /**
     * Appends a long to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param l The long to be appended
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendLong(@NonNull final Bytes bytes, final long l) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes.length() + 8)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        newBytes[(int)bytes.length()] = (byte)(l >>> 56);
        newBytes[(int)bytes.length() + 1] = (byte)(l >>> 48);
        newBytes[(int)bytes.length() + 2] = (byte)(l >>> 40);
        newBytes[(int)bytes.length() + 3] = (byte)(l >>> 32);
        newBytes[(int)bytes.length() + 4] = (byte)(l >>> 24);
        newBytes[(int)bytes.length() + 5] = (byte)(l >>> 16);
        newBytes[(int)bytes.length() + 6] = (byte)(l >>> 8);
        newBytes[(int)bytes.length() + 7] = (byte)(l);
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends a long to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param l The long to be appended
     * @param byteOrder teh order of the bytes in int.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendLong(@NonNull final Bytes bytes, final long l , @NonNull final ByteOrder byteOrder) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(bytes.length() + 8)];
        bytes.getBytes(0, newBytes, 0, (int)bytes.length());
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return appendLong(bytes, l);
        } else {
            newBytes[(int)bytes.length()] = (byte)(l);
            newBytes[(int)bytes.length() + 1] = (byte)(l >>> 8);
            newBytes[(int)bytes.length() + 2] = (byte)(l >>> 16);
            newBytes[(int)bytes.length() + 3] = (byte)(l >>> 24);
            newBytes[(int)bytes.length() + 4] = (byte)(l >>> 32);
            newBytes[(int)bytes.length() + 5] = (byte)(l >>> 40);
            newBytes[(int)bytes.length() + 6] = (byte)(l >>> 48);
            newBytes[(int)bytes.length() + 7] = (byte)(l >>> 56);
            return Bytes.wrap(newBytes);
        }
    }

    /**
     * Appends a float to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param f The float to be appended
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendFloat(@NonNull final Bytes bytes, final float f) {
        return appendInt(bytes, Float.floatToIntBits(f));
    }

    /**
     * Appends a float to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param f The float to be appended
     * @param byteOrder teh order of the bytes in int.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendFloat(@NonNull final Bytes bytes, final float f , @NonNull final ByteOrder byteOrder) {
        return appendInt(bytes, Float.floatToIntBits(f), byteOrder);
    }

    /**
     * Appends a double to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param d The double to be appended
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendDouble(@NonNull final Bytes bytes, final double d) {
        return appendLong(bytes, Double.doubleToLongBits(d));
    }

    /**
     * Appends a double to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param d The double to be appended
     * @param byteOrder teh order of the bytes in int.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendDouble(@NonNull final Bytes bytes, final double d, @NonNull final ByteOrder byteOrder) {
        return appendLong(bytes, Double.doubleToLongBits(d), byteOrder);
    }

    /**
     * Appends an int as VarInt to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param i The int to append as VarInt
     * @param zigZag Whether to use zigZag format
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendVarInt(@NonNull final Bytes bytes, final int i, final boolean zigZag) {
        return appendVarLong(bytes, i, zigZag);
    }

    /**
     * Appends a long as VarLong to a {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append to.
     * @param l The long to append as VarInt
     * @param zigZag Whether to use zigZag format
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length
     */
    public static @NonNull Bytes appendVarLong(@NonNull final Bytes bytes, long l, final boolean zigZag) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        int bytesLen = (int) bytes.length();
        byte[] newBytes = new byte[bytesLen + 10];
        bytes.getBytes(0, newBytes, 0, bytesLen);
        int index = bytesLen;
        if (zigZag) {
            l = (l << 1) ^ (l >> 63);
        }
        while (true) {
            if ((l & ~0x7FL) == 0) {
                newBytes[index++] = (byte)l;
                return Bytes.wrap(newBytes, 0, index);
            } else {
                newBytes[index++] = (byte) (((int) l & 0x7F) | 0x80);
                l >>>= 7;
            }
        }
    }
}
