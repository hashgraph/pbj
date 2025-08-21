// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static com.hedera.pbj.runtime.JsonTools.*;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import sun.misc.Unsafe;

/**
 * BufferedData subclass for instances backed by a byte array. Provides slightly more optimized
 * versions of several methods to get / read / write bytes using {@link System#arraycopy} and
 * direct array reads / writes.
 */
final class UnsafeByteArrayBufferedData extends BufferedData {
    /** Unsafe instance for direct memory access */
    private static final Unsafe UNSAFE;
    /** Field offset of the byte[] class */
    private static final int BYTE_ARRAY_BASE_OFFSET;
    /** Lookup table for hex digits */
    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    /** The byte representation of long minimum value */
    private static final byte[] MIN_LONG_VALUE = Long.toString(Long.MIN_VALUE).getBytes(StandardCharsets.US_ASCII);

    /* Get the Unsafe instance and the byte array base offset */
    static {
        try {
            final Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    // Backing byte array
    private final byte[] array;

    // This data buffer's offset into the backing array. See ByteBuffer.arrayOffset() for details
    private final int arrayOffset;

    UnsafeByteArrayBufferedData(final ByteBuffer buffer) {
        super(buffer);
        if (!buffer.hasArray()) {
            throw new IllegalArgumentException("Cannot create a ByteArrayBufferedData over a buffer with no array");
        }
        this.array = buffer.array();
        this.arrayOffset = buffer.arrayOffset();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[");
        for (int i = 0; i < buffer.limit(); i++) {
            int v = array[arrayOffset + i] & 0xFF;
            sb.append(v);
            if (i < (buffer.limit() - 1)) {
                sb.append(',');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final long offset, @NonNull final byte[] bytes) {
        checkOffset(offset, length());

        final int len = bytes.length;
        if (length() - offset < len) {
            return false;
        }

        final int fromThisIndex = Math.toIntExact(arrayOffset + offset);
        final int fromToIndex = fromThisIndex + len;
        return Arrays.equals(array, fromThisIndex, fromToIndex, bytes, 0, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getByte(final long offset) {
        checkOffset(offset, length());
        return UNSAFE.getByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + offset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        validateLen(maxLength);
        checkOffset(offset);
        final long len = Math.min(maxLength, length() - offset);
        checkOffsetToRead(offset, length(), len);
        if (len == 0) {
            return 0;
        }
        System.arraycopy(array, Math.toIntExact(arrayOffset + offset), dst, dstOffset, Math.toIntExact(len));
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
        if (!dst.hasArray()) {
            return super.getBytes(offset, dst);
        }
        final long len = Math.min(length() - offset, dst.remaining());
        checkOffsetToRead(offset, length(), len);
        final byte[] dstArr = dst.array();
        final int dstPos = dst.position();
        final int dstArrOffset = dst.arrayOffset();
        System.arraycopy(
                array, Math.toIntExact(arrayOffset + offset), dstArr, dstArrOffset + dstPos, Math.toIntExact(len));
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bytes getBytes(final long offset, final long len) {
        validateLen(len);
        if (len == 0) {
            return Bytes.EMPTY;
        }
        checkOffsetToRead(offset, length(), len);
        final byte[] res = new byte[Math.toIntExact(len)];
        System.arraycopy(array, Math.toIntExact(arrayOffset + offset), res, 0, res.length);
        return Bytes.wrap(res);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVarInt(final long offset, final boolean zigZag) {
        return (int) getVar(Math.toIntExact(offset), zigZag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getVarLong(final long offset, final boolean zigZag) {
        return getVar(Math.toIntExact(offset), zigZag);
    }

    private long getVar(final int offset, final boolean zigZag) {
        checkOffset(offset, buffer.limit());

        final int readOffset = arrayOffset + offset;
        int rem = buffer.limit() - offset;
        if (rem > 10) {
            rem = 10;
        }

        long value = 0;

        int i = 0;
        while (i != rem) {
            final byte b = UnsafeUtils.getArrayByteNoChecks(array, readOffset + i);
            value |= (long) (b & 0x7F) << (i * 7);
            i++;
            if (b >= 0) {
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        throw (i == 10) ? new DataEncodingException("Malformed var int") : new BufferUnderflowException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte readByte() {
        if (remaining() == 0) {
            throw new BufferUnderflowException();
        }
        final int pos = buffer.position();
        final byte res = UNSAFE.getByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos);
        buffer.position(pos + 1);
        return res;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readBytes(@NonNull byte[] dst, int offset, int maxLength) {
        validateLen(maxLength);
        final var len = Math.toIntExact(Math.min(maxLength, remaining()));
        if (len == 0) {
            return 0;
        }
        final int pos = buffer.position();
        System.arraycopy(array, arrayOffset + pos, dst, offset, len);
        buffer.position(pos + len);
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readBytes(@NonNull final ByteBuffer dst) {
        if (!dst.hasArray()) {
            return super.readBytes(dst);
        }
        final long len = Math.min(remaining(), dst.remaining());
        final int pos = buffer.position();
        final byte[] dstArr = dst.array();
        final int dstPos = dst.position();
        final int dstArrOffset = dst.arrayOffset();
        System.arraycopy(array, arrayOffset + pos, dstArr, dstArrOffset + dstPos, Math.toIntExact(len));
        buffer.position(Math.toIntExact(pos + len));
        dst.position(Math.toIntExact(dstPos + len));
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bytes readBytes(final int len) {
        validateLen(len);
        final int pos = buffer.position();
        validateCanRead(pos, len);
        if (len == 0) {
            return Bytes.EMPTY;
        }
        final byte[] res = new byte[len];
        System.arraycopy(array, arrayOffset + pos, res, 0, len);
        buffer.position(pos + len);
        return Bytes.wrap(res);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readVarInt(final boolean zigZag) {
        return (int) readVar(zigZag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readVarLong(final boolean zigZag) {
        return readVar(zigZag);
    }

    private long readVar(final boolean zigZag) {
        final int pos = buffer.position();
        final int offset = arrayOffset + pos;
        int rem = buffer.remaining();
        if (rem > 10) {
            rem = 10;
        }

        long value = 0;

        int i = 0;
        while (i != rem) {
            final byte b = UnsafeUtils.getArrayByteNoChecks(array, offset + i);
            value |= (long) (b & 0x7F) << (i * 7);
            i++;
            if (b >= 0) {
                buffer.position(pos + i);
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        throw (i == 10) ? new DataEncodingException("Malformed var int") : new BufferUnderflowException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(final byte b) {
        validateCanWrite(1);
        final int pos = buffer.position();
        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos, b);
        buffer.position(pos + 1);
    }

    @Override
    public void writeByte2(final byte b1, final byte b2) {
        validateCanWrite(2);
        int pos = buffer.position();
        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b1);
        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b2);
        buffer.position(pos);
    }

    @Override
    public void writeByte3(final byte b1, final byte b2, final byte b3) {
        validateCanWrite(3);
        int pos = buffer.position();
        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b1);
        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b2);
        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b3);
        buffer.position(pos);
    }
//
//    @Override
//    public void writeByte4(final byte b1, final byte b2, final byte b3, final byte b4) {
//        validateCanWrite(4);
//        int pos = buffer.position();
//        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b1);
//        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b2);
//        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b3);
//        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos++, b4);
//        buffer.position(pos);
//    }
    // TODO not interesting at least on mac this is just as fast as the above
    @Override
    public void writeByte4(final byte b1, final byte b2, final byte b3, final byte b4) {
        buffer.put(new byte[] {b1, b2, b3, b4});
    }

    /** {@inheritDoc} */
    @Override
    public void writeBytes(@NonNull final byte[] src, final int offset, final int len) {
        validateLen(len);
        validateCanWrite(len);
        if(offset < 0) {
            throw new IndexOutOfBoundsException("offset must be >= 0");
        }
        if (src.length < offset + len) {
            throw new IndexOutOfBoundsException("Source array is too short for the specified offset and length");
        }
        final int pos = buffer.position();
        UNSAFE.copyMemory(src, BYTE_ARRAY_BASE_OFFSET + offset, array, BYTE_ARRAY_BASE_OFFSET + arrayOffset + pos, len);
        buffer.position(pos + len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final ByteBuffer src) {
        if (!src.hasArray()) {
            super.writeBytes(src);
            return;
        }
        final long len = src.remaining();
        validateCanWrite(len);
        final int pos = buffer.position();
        final byte[] srcArr = src.array();
        final int srcArrOffset = src.arrayOffset();
        final int srcPos = src.position();
        System.arraycopy(srcArr, srcArrOffset + srcPos, array, arrayOffset + pos, Math.toIntExact(len));
        src.position(Math.toIntExact(srcPos + len));
        buffer.position(Math.toIntExact(pos + len));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int writeBytes(@NonNull final InputStream src, final int maxLength) {
        // Check for a bad length or a null src
        Objects.requireNonNull(src);
        validateLen(maxLength);

        // If the length is zero, then we have nothing to read
        if (maxLength == 0) {
            return 0;
        }

        // We are going to read from the input stream up to either "len" or the number of bytes
        // remaining in this buffer, whichever is lesser.
        final long numBytesToRead = Math.min(maxLength, remaining());
        if (numBytesToRead == 0) {
            return 0;
        }

        try {
            int pos = buffer.position();
            int totalBytesRead = 0;
            while (totalBytesRead < numBytesToRead) {
                int bytesRead = src.read(array, pos + arrayOffset, (int) numBytesToRead - totalBytesRead);
                if (bytesRead == -1) {
                    buffer.position(pos);
                    return totalBytesRead;
                }
                pos += bytesRead;
                totalBytesRead += bytesRead;
            }
            buffer.position(pos);
            return totalBytesRead;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeJsonString(@NonNull String value, boolean quoted) {
        int offset = buffer.position();
        final int len = value.length();
        validateCanWrite(len * 6L + 2); //  TODO Worst-case scenario for UTF-8 encoding, is there better estimate?
        if (quoted) array[arrayOffset + offset++] = '"';
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            // Escape control chars and JSON specials
            switch (c) {
                case '"':  array[arrayOffset + offset++] = '\\'; array[arrayOffset + offset++] = '"';  continue;
                case '\\': array[arrayOffset + offset++] = '\\'; array[arrayOffset + offset++] = '\\'; continue;
                case '\b': array[arrayOffset + offset++] = '\\'; array[arrayOffset + offset++] = 'b';  continue;
                case '\f': array[arrayOffset + offset++] = '\\'; array[arrayOffset + offset++] = 'f';  continue;
                case '\n': array[arrayOffset + offset++] = '\\'; array[arrayOffset + offset++] = 'n';  continue;
                case '\r': array[arrayOffset + offset++] = '\\'; array[arrayOffset + offset++] = 'r';  continue;
                case '\t': array[arrayOffset + offset++] = '\\'; array[arrayOffset + offset++] = 't';  continue;
            }

            if (c < 0x20) {
                // Control character – use \ u00XX
                array[arrayOffset + offset++] = '\\';
                array[arrayOffset + offset++] = 'u';
                array[arrayOffset + offset++] = '0';
                array[arrayOffset + offset++] = '0';
                array[arrayOffset + offset++] = HEX[c >> 4];
                array[arrayOffset + offset++] = HEX[c & 0xF];
            } else {
                // Proper UTF-8 encoding for extended characters
                offset = encodeUtf8(c, value, i, offset);
            }
        }
        if (quoted) array[arrayOffset + offset++] = '"';
        buffer.position(offset);
    }

    private int encodeUtf8(char c, String value, int i, int offset) {
        if (c < 0x80) {
            array[arrayOffset + offset++] = (byte) c;
        } else if (c < 0x800) {
            array[arrayOffset + offset++] = (byte) (0b11000000 | (c >> 6));
            array[arrayOffset + offset++] = (byte) (0b10000000 | (c & 0b00111111));
        } else if (Character.isSurrogate(c)) {
            int cp = Character.toCodePoint(c, value.charAt(++i));
            array[arrayOffset + offset++] = (byte) (0b11110000 | (cp >> 18));
            array[arrayOffset + offset++] = (byte) (0b10000000 | ((cp >> 12) & 0b00111111));
            array[arrayOffset + offset++] = (byte) (0b10000000 | ((cp >> 6) & 0b00111111));
            array[arrayOffset + offset++] = (byte) (0b10000000 | (cp & 0b00111111));
        } else {
            array[arrayOffset + offset++] = (byte) (0b11100000 | (c >> 12));
            array[arrayOffset + offset++] = (byte) (0b10000000 | ((c >> 6) & 0b00111111));
            array[arrayOffset + offset++] = (byte) (0b10000000 | (c & 0b00111111));
        }
        return offset;
    }

    @Override
    public void writeJsonLong(final long value, boolean quoted) {
        int offset = buffer.position();
        validateCanWrite(20); // Worst-case scenario for a long value, quoted or not
        final int baseOffset = BYTE_ARRAY_BASE_OFFSET + arrayOffset;
        if (quoted) UNSAFE.putByte(array, baseOffset + offset++, QUOTE);
        // Handle zero explicitly
        if (value == 0) {
            UNSAFE.putByte(array, baseOffset + offset++, _0);
        } else if (value == Long.MIN_VALUE)  {
            // Special case for Long.MIN_VALUE(-9223372036854775808) to avoid overflow
            UNSAFE.copyMemory(MIN_LONG_VALUE, BYTE_ARRAY_BASE_OFFSET, array, baseOffset + offset, MIN_LONG_VALUE.length);
            offset += MIN_LONG_VALUE.length;
        } else {
            long v = value;
            if (v < 0) {
                UNSAFE.putByte(array, baseOffset + offset++, MINUS);
                v = -v;
            }
            // count the number of digits in the long value, assumes all values are positive
            // Fast digit count calculation
            final int digitCount = (v < 10L) ? 1 :
                (v < 100L) ? 2 :
                (v < 1000L) ? 3 :
                (v < 10000L) ? 4 :
                (v < 100000L) ? 5 :
                (v < 1000000L) ? 6 :
                (v < 10000000L) ? 7 :
                (v < 100000000L) ? 8 :
                (v < 1000000000L) ? 9 :
                (v < 10000000000L) ? 10 :
                (v < 100000000000L) ? 11 :
                (v < 1000000000000L) ? 12 :
                (v < 10000000000000L) ? 13 :
                (v < 100000000000000L) ? 14 :
                (v < 1000000000000000L) ? 15 :
                (v < 10000000000000000L) ? 16 :
                (v < 100000000000000000L) ? 17 :
                (v < 1000000000000000000L) ? 18 : 19;
            // Now write them in reverse order
            long tmp = v;
            for (int i = digitCount-1; i >= 0; i--) {
                UNSAFE.putByte(array, baseOffset + offset + i, (byte) ('0' + (tmp % 10)));
                tmp /= 10;
            }
            offset += digitCount;
        }
        if (quoted) UNSAFE.putByte(array, baseOffset + offset++, QUOTE);
        buffer.position(offset);
    }

    @Override
    public void writeBase64(@NonNull Bytes dataToEncode) {
        final int offset = buffer.position();
        final int writenBytes = dataToEncode.writeBase64To(array, offset);
        buffer.position(offset + writenBytes);
    }
}
