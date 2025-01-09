// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * BufferedData subclass for instances backed by a byte array. Provides slightly more optimized
 * versions of several methods to get / read / write bytes using {@link System#arraycopy} and direct
 * array reads / writes.
 */
final class ByteArrayBufferedData extends BufferedData {

    // Backing byte array
    private final byte[] array;

    // This data buffer's offset into the backing array. See ByteBuffer.arrayOffset() for details
    private final int arrayOffset;

    ByteArrayBufferedData(final ByteBuffer buffer) {
        super(buffer);
        if (!buffer.hasArray()) {
            throw new IllegalArgumentException(
                    "Cannot create a ByteArrayBufferedData over a buffer with no array");
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public byte getByte(final long offset) {
        checkOffset(offset, length());
        return array[Math.toIntExact(arrayOffset + offset)];
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(
            final long offset,
            @NonNull final byte[] dst,
            final int dstOffset,
            final int maxLength) {
        validateLen(maxLength);
        checkOffset(offset);
        final long len = Math.min(maxLength, length() - offset);
        checkOffsetToRead(offset, length(), len);
        if (len == 0) {
            return 0;
        }
        System.arraycopy(
                array, Math.toIntExact(arrayOffset + offset), dst, dstOffset, Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
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
                array,
                Math.toIntExact(arrayOffset + offset),
                dstArr,
                dstArrOffset + dstPos,
                Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int getVarInt(final long offset, final boolean zigZag) {
        return (int) getVar(Math.toIntExact(offset), zigZag);
    }

    /** {@inheritDoc} */
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
        throw (i == 10)
                ? new DataEncodingException("Malformed var int")
                : new BufferUnderflowException();
    }

    /** {@inheritDoc} */
    @Override
    public byte readByte() {
        if (remaining() == 0) {
            throw new BufferUnderflowException();
        }
        final int pos = buffer.position();
        final byte res = array[arrayOffset + pos];
        buffer.position(pos + 1);
        return res;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
        System.arraycopy(
                array, arrayOffset + pos, dstArr, dstArrOffset + dstPos, Math.toIntExact(len));
        buffer.position(Math.toIntExact(pos + len));
        dst.position(Math.toIntExact(dstPos + len));
        return len;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int readVarInt(final boolean zigZag) {
        return (int) readVar(zigZag);
    }

    /** {@inheritDoc} */
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
        throw (i == 10)
                ? new DataEncodingException("Malformed var int")
                : new BufferUnderflowException();
    }

    /** {@inheritDoc} */
    @Override
    public void writeByte(final byte b) {
        validateCanWrite(1);
        final int pos = buffer.position();
        array[arrayOffset + pos] = b;
        buffer.position(pos + 1);
    }

    @Override
    public void writeByte2(final byte b1, final byte b2) {
        validateCanWrite(2);
        final int pos = buffer.position();
        array[arrayOffset + pos] = b1;
        array[arrayOffset + pos + 1] = b2;
        buffer.position(pos + 2);
    }

    @Override
    public void writeByte3(final byte b1, final byte b2, final byte b3) {
        validateCanWrite(3);
        final int pos = buffer.position();
        array[arrayOffset + pos] = b1;
        array[arrayOffset + pos + 1] = b2;
        array[arrayOffset + pos + 2] = b3;
        buffer.position(pos + 3);
    }

    @Override
    public void writeByte4(final byte b1, final byte b2, final byte b3, final byte b4) {
        validateCanWrite(4);
        final int pos = buffer.position();
        array[arrayOffset + pos] = b1;
        array[arrayOffset + pos + 1] = b2;
        array[arrayOffset + pos + 2] = b3;
        array[arrayOffset + pos + 3] = b4;
        buffer.position(pos + 4);
    }

    /** {@inheritDoc} */
    @Override
    public void writeBytes(@NonNull final byte[] src, final int offset, final int len) {
        validateLen(len);
        validateCanWrite(len);
        final int pos = buffer.position();
        System.arraycopy(src, offset, array, arrayOffset + pos, len);
        buffer.position(pos + len);
    }

    /** {@inheritDoc} */
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
        System.arraycopy(
                srcArr, srcArrOffset + srcPos, array, arrayOffset + pos, Math.toIntExact(len));
        src.position(Math.toIntExact(srcPos + len));
        buffer.position(Math.toIntExact(pos + len));
    }

    /** {@inheritDoc} */
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
                int bytesRead =
                        src.read(array, pos + arrayOffset, (int) numBytesToRead - totalBytesRead);
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
}
