// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * A simple implementation of RandomAccessData used to test default methods in the interface.
 */
public class RandomAccessArray implements RandomAccessData {

    private final byte[] array;

    /**
     * Create a new instance that wraps the given array.
     *
     * @param array the array to wrap
     */
    public RandomAccessArray(final byte[] array) {
        this.array = array;
    }

    @Override
    public long length() {
        return array.length;
    }

    @Override
    public byte getByte(final long offset) {
        return array[Math.toIntExact(offset)];
    }

    @Override
    public long writeTo(@NonNull final OutputStream outStream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long writeTo(@NonNull final OutputStream outStream, final long offset, final long length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long writeTo(@NonNull MessageDigest digest) {
        digest.update(array);
        return array.length;
    }

    @Override
    public long writeTo(@NonNull MessageDigest digest, final long offset, final long length) {
        digest.update(array, Math.toIntExact(offset), Math.toIntExact(offset + length));
        return length;
    }

    @Override
    public long writeTo(@NonNull final RandomAccessData dst, final long dstOffset) {
        return dst.putBytes(dstOffset, array);
    }
}
