// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.hashing;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * A WritableSequentialData implementation that writes all the data into a given MessageDigest instance.
 */
public class WritableMessageDigest implements WritableSequentialData {
    private final MessageDigest digest;

    /**
     * A fake position that we keep increasing as we add data. We have to maintain it because there's code
     * in ProtoWriterTools that checks the position before and after writing data, and errors out if the position
     * hasn't changed as expected. Both `reset()` and `digest()` reset the position to zero.
     */
    private long position = 0;

    /**
     * Constructor.
     * @param digest a MessageDigest object to write data to
     */
    public WritableMessageDigest(@NonNull final MessageDigest digest) {
        this.digest = Objects.requireNonNull(digest);
    }

    /**
     * A delegate for the wrapped MessageDigest.reset() method.
     */
    public void reset() {
        position = 0;
        digest.reset();
    }

    /**
     * A delegate for the wrapped MessageDigest.digest() method.
     */
    public byte[] digest() {
        position = 0;
        return digest.digest();
    }

    /**
     * A delegate for the wrapped {@link MessageDigest#digest(byte[], int, int)} method.
     *
     * <p>The digest is written into {@code buf} starting at {@code offset}. The length used is
     * {@link MessageDigest#getDigestLength()}, and {@link #position()} is reset to {@code 0},
     * matching the behavior of {@link #digest()}.
     *
     * @param buf the destination buffer for the digest bytes
     * @param offset the offset in {@code buf} where digest bytes are written
     * @throws RuntimeException if writing the digest into the destination buffer fails
     */
    public void digestInto(final byte[] buf, final int offset) {
        position = 0;
        try {
            digest.digest(buf, offset, digest.getDigestLength());
        } catch (final java.security.DigestException e) {
            throw new RuntimeException("Failed to write digest into buffer", e);
        }
    }

    @Override
    public void writeByte(byte b) throws BufferOverflowException, UncheckedIOException {
        digest.update(b);
        position += 1;
    }

    @Override
    public void writeBytes(@NonNull byte[] src) throws BufferOverflowException, UncheckedIOException {
        digest.update(src);
        position += src.length;
    }

    @Override
    public void writeBytes(@NonNull byte[] src, int offset, int length)
            throws BufferOverflowException, UncheckedIOException {
        digest.update(src, offset, length);
        position += length;
    }

    @Override
    public void writeBytes(@NonNull ByteBuffer src) throws BufferOverflowException, UncheckedIOException {
        position += src.remaining();
        digest.update(src);
    }

    @Override
    public void writeBytes(@NonNull BufferedData src) throws BufferOverflowException, UncheckedIOException {
        position += src.remaining();
        src.writeTo(digest);
    }

    @Override
    public void writeBytes(@NonNull RandomAccessData src) throws BufferOverflowException, UncheckedIOException {
        position += src.length();
        src.writeTo(digest);
    }

    /**
     * This WritableSequentialData implementation feeds an underlying MessageDigest object, so there's not a concept
     * of a capacity here, and this method always returns Long.MAX_VALUE.
     * @return Long.MAX_VALUE
     */
    @Override
    public long capacity() {
        return Long.MAX_VALUE;
    }

    @Override
    public long position() {
        return position;
    }

    /**
     * This WritableSequentialData implementation feeds an underlying MessageDigest object, so there's not a concept
     * of a limit here, and this method always returns Long.MAX_VALUE.
     * @return Long.MAX_VALUE
     */
    @Override
    public long limit() {
        return Long.MAX_VALUE;
    }

    /**
     * This WritableSequentialData implementation feeds an underlying MessageDigest object, so there's not a concept
     * of a limit here, and this method is a no-op.
     */
    @Override
    public void limit(long limit) {}

    @Override
    public void skip(long count) throws UncheckedIOException {
        position += count;
    }
}
