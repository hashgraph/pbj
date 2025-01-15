// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.test;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.CharBuffer;

/**
 * <p>A {@code WritableSequentialData} backed by a {@link CharBuffer}. It only supports writing UTF8 strings.
 */
public class CharBufferToWritableSequentialData implements WritableSequentialData, ReadableSequentialData {
    private final CharBuffer charBuffer;

    public CharBufferToWritableSequentialData(CharBuffer charBuffer) {
        this.charBuffer = charBuffer;
    }

    @Override
    public long capacity() {
        return charBuffer.capacity();
    }

    @Override
    public long position() {
        return charBuffer.position();
    }

    @Override
    public long limit() {
        return charBuffer.limit();
    }

    @Override
    public void limit(long limit) {
        charBuffer.limit((int) limit);
    }

    @Override
    public void skip(long count) {
        if (count > charBuffer.remaining()) {
            throw new BufferUnderflowException();
        }
        if (count <= 0) {
            return;
        }
        // Note: skip() should be relative. But it's okay, this is a test implementation.
        charBuffer.position((int) count);
    }

    @Override
    public void writeByte(byte b) throws UncheckedIOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Write a string as UTF8 bytes to this {@link WritableSequentialData}.
     *
     * @param value The string to write, can not be null
     */
    @Override
    public void writeUTF8(@NonNull String value) {
        charBuffer.put(value);
    }

    @Override
    public byte readByte() {
        throw new UnsupportedOperationException();
    }


}
