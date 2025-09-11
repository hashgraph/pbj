// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * BufferedData subclass for instances backed by direct byte buffers. Provides slightly more optimized
 * versions of several methods to get / read / write bytes using {@link UnsafeUtils} methods.
 */
final class DirectBufferedData extends BufferedData {

    DirectBufferedData(final ByteBuffer buffer) {
        super(buffer);
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Cannot create a DirectBufferedData over a heap byte buffer");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        validateLen(maxLength);
        final long len = Math.min(maxLength, length() - offset);
        checkOffsetToRead(offset, length(), len);
        if (len == 0) {
            return 0;
        }
        checkOffsetToWrite(dstOffset, dst.length, len);
        UnsafeUtils.getDirectBufferToArray(buffer, offset, dst, dstOffset, Math.toIntExact(len));
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
        checkOffset(offset, length());
        final long len = Math.min(length() - offset, dst.remaining());
        final byte[] dstArr = dst.array();
        final int dstPos = dst.position();
        final int dstArrOffset = dst.arrayOffset();
        UnsafeUtils.getDirectBufferToArray(buffer, offset, dstArr, dstArrOffset + dstPos, Math.toIntExact(len));
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
        UnsafeUtils.getDirectBufferToArray(buffer, offset, res, 0, Math.toIntExact(len));
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
        checkOffset(offset, length());

        int rem = Math.toIntExact(buffer.limit() - offset);
        if (rem > 10) {
            rem = 10;
        }

        long value = 0;

        int i = 0;
        while (i != rem) {
            final byte b = UnsafeUtils.getDirectBufferByte(buffer, offset + i);
            value |= (long) (b & 0x7F) << (i * 7);
            i++;
            if (b >= 0) {
                buffer.position(offset + i);
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        throw (i == 10) ? new DataEncodingException("Malformed var int") : new BufferUnderflowException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readBytes(@NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        validateLen(maxLength);
        final long len = Math.min(maxLength, remaining());
        final int pos = buffer.position();
        validateCanRead(pos, len);
        if (len == 0) {
            return 0;
        }
        if ((dstOffset < 0) || (dst.length - dstOffset < len)) {
            throw new IndexOutOfBoundsException();
        }
        UnsafeUtils.getDirectBufferToArray(buffer, pos, dst, dstOffset, Math.toIntExact(len));
        buffer.position(Math.toIntExact(pos + len));
        return len;
    }

    @Override
    public long readBytes(@NonNull final ByteBuffer dst) {
        final long len = Math.min(remaining(), dst.remaining());
        final int pos = buffer.position();
        final int dstPos = dst.position();
        if (dst.hasArray()) {
            final byte[] dstArr = dst.array();
            final int dstArrOffset = dst.arrayOffset();
            UnsafeUtils.getDirectBufferToArray(buffer, pos, dstArr, dstArrOffset + dstPos, Math.toIntExact(len));
            buffer.position(Math.toIntExact(pos + len));
            dst.position(Math.toIntExact(dstPos + len));
            return len;
        } else if (dst.isDirect()) {
            UnsafeUtils.getDirectBufferToDirectBuffer(buffer, pos, dst, dstPos, Math.toIntExact(len));
            buffer.position(Math.toIntExact(pos + len));
            dst.position(Math.toIntExact(dstPos + len));
            return len;
        } else {
            return super.readBytes(dst);
        }
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
        final byte[] res = new byte[len];
        UnsafeUtils.getDirectBufferToArray(buffer, pos, res, 0, len);
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
        int rem = buffer.remaining();
        if (rem > 10) {
            rem = 10;
        }

        long value = 0;

        int i = 0;
        while (i != rem) {
            final byte b = UnsafeUtils.getDirectBufferByteNoChecks(buffer, pos + i);
            value |= (long) (b & 0x7F) << (i * 7);
            i++;
            if (b >= 0) {
                buffer.position(pos + i);
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        throw (i == 10) ? new DataEncodingException("") : new BufferUnderflowException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final byte[] src, final int offset, final int len) {
        Objects.requireNonNull(src);
        validateLen(len);
        checkOffsetToRead(offset, src.length, len);
        validateCanWrite(len);
        final int pos = buffer.position();
        UnsafeUtils.putByteArrayToDirectBuffer(buffer, pos, src, offset, len);
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
        final int len = src.remaining();
        validateCanWrite(len);
        final int pos = buffer.position();
        final byte[] srcArr = src.array();
        final int srcPos = src.position();
        final int srcArrOffset = src.arrayOffset();
        UnsafeUtils.putByteArrayToDirectBuffer(buffer, pos, srcArr, srcArrOffset + srcPos, len);
        buffer.position(pos + len);
        src.position(srcPos + len);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Optimized implementation for DirectBufferedData that avoids byte-by-byte comparison
     * by using bulk memory operations when possible.
     */
    @Override
    public boolean contains(final long offset, @NonNull final byte[] bytes) {
        checkOffset(offset, length());

        /* Check if the number of bytes between offset and length is shorter
         * than the bytes we're matching */
        if (length() - offset < bytes.length) {
            // No way we could have a match -> return false.
            return false;
        }

        // For small byte arrays, use the default implementation to avoid overhead
        if (bytes.length <= 8) {
            return super.contains(offset, bytes);
        }

        // For larger byte arrays, use bulk comparison via UnsafeUtils
        final byte[] tempArray = new byte[bytes.length];
        UnsafeUtils.getDirectBufferToArray(buffer, offset, tempArray, 0, bytes.length);
        return Arrays.equals(tempArray, bytes);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Optimized implementation for DirectBufferedData that avoids byte-by-byte comparison
     * when both data sources are DirectBufferedData instances.
     */
    @Override
    public boolean contains(final long offset, @NonNull final RandomAccessData data) {
        // If this data is empty, return true if only the incoming data is empty too.
        if (length() == 0) {
            return data.length() == 0;
        }

        checkOffset(offset, length());

        if (length() - offset < data.length()) {
            return false;
        }

        // Fast path: if the other data is also DirectBufferedData, we can use bulk comparison
        if (data instanceof DirectBufferedData otherDirectData) {
            // For small data, use the default implementation to avoid overhead
            if (data.length() <= 8) {
                return super.contains(offset, data);
            }

            // Extract both data ranges into temporary arrays and compare
            final int dataLength = Math.toIntExact(data.length());
            final byte[] thisArray = new byte[dataLength];
            final byte[] otherArray = new byte[dataLength];

            UnsafeUtils.getDirectBufferToArray(buffer, offset, thisArray, 0, dataLength);
            UnsafeUtils.getDirectBufferToArray(otherDirectData.buffer, 0, otherArray, 0, dataLength);

            return Arrays.equals(thisArray, otherArray);
        }

        // Fall back to the default implementation for other RandomAccessData types
        return super.contains(offset, data);
    }
}
