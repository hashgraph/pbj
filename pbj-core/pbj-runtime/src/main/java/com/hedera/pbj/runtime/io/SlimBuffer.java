// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SlimBuffer {
    ReadableSequentialData input;

    public SlimBuffer(ReadableSequentialData input) {
        this.input = input;
    }

    public boolean hasRemaining() {
        return input.hasRemaining();
    }

    public long remaining() {
        return input.remaining();
    }

    public long limit() {
        return input.limit();
    }

    public void limit(long limit) {
        input.limit(limit);
    }

    public long position() {
        return input.position();
    }

    public void skip(long count) throws UncheckedIOException {
        input.skip(count);
    }

    public int readVarInt(final boolean zigZag) {
        return input.readVarInt(zigZag);
    }

    public long readVarLong(final boolean zigZag) throws BufferUnderflowException, UncheckedIOException {
        return input.readVarLong(zigZag);
    }

    public Bytes readVarLongBytes() throws BufferUnderflowException, UncheckedIOException {
        return input.readVarLongBytes();
    }

    public long readBytes(@NonNull final byte[] dst) throws UncheckedIOException {
        return input.readBytes(dst);
    }

    public @NonNull Bytes readBytes(final int length) throws BufferUnderflowException, UncheckedIOException {
        return input.readBytes(length);
    }

    public long readBytes(@NonNull final ByteBuffer dst) throws UncheckedIOException {
        return input.readBytes(dst);
    }

    public int readInt() throws BufferUnderflowException, UncheckedIOException {
        return input.readInt();
    }

    public int readInt(@NonNull final ByteOrder byteOrder) throws BufferUnderflowException, UncheckedIOException {
        return input.readInt(byteOrder);
    }

    public long readLong() throws BufferUnderflowException, UncheckedIOException {
        return input.readLong();
    }

    public long readLong(@NonNull final ByteOrder byteOrder) throws BufferUnderflowException, UncheckedIOException {
        return input.readLong(byteOrder);
    }

    public float readFloat() throws BufferUnderflowException, UncheckedIOException {
        return input.readFloat();
    }

    public float readFloat(@NonNull final ByteOrder byteOrder) throws BufferUnderflowException, UncheckedIOException {
        return input.readFloat(byteOrder);
    }

    public double readDouble() throws BufferUnderflowException, UncheckedIOException {
        return input.readDouble();
    }

    public double readDouble(@NonNull final ByteOrder byteOrder) throws BufferUnderflowException, UncheckedIOException {
        return input.readDouble(byteOrder);
    }

    public InputStream asInputStream() {
        return input.asInputStream();
    }

    public void test() {
        input.readDouble();
    }
}
