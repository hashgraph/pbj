package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
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
        validateCanRead(offset, len);
        if (len == 0) {
            return 0;
        }
        if ((dstOffset < 0) || (dst.length - dstOffset < len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
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
        validateCanRead(offset, len);
        final byte[] res = new byte[Math.toIntExact(len)];
        UnsafeUtils.getDirectBufferToArray(buffer, offset, res, 0, Math.toIntExact(len));
        return Bytes.wrap(res);
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
            throw new ArrayIndexOutOfBoundsException();
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
    public void writeBytes(@NonNull final byte[] src, final int offset, final int len) {
        Objects.requireNonNull(src);
        if ((offset < 0) || (offset > src.length - len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        validateLen(len);
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
}
