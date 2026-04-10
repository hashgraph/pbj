// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;

/// A buffer backed by a MemorySegment. It is a [BufferedSequentialData] (and therefore contains
/// a "position" cursor into the data), a [ReadableSequentialData] (and therefore can be read from),
/// a [WritableSequentialData] (and therefore can be written to), and a [RandomAccessData]
/// (and therefore can be accessed at any position).
///
/// This class implements all the same APIs and interfaces that the BufferedData does, and it is a drop-in
/// replacement for the latter. Applications should use interfaces that this class implements instead of using
/// a concrete implementation type.
public final class MemoryData
        implements BufferedSequentialData, ReadableSequentialData, WritableSequentialData, RandomAccessData {
    // --------------------------------------------------------------
    // INTERNAL STATE

    /// The backing MemorySegment
    private final MemorySegment segment;

    /// The current read/write position.
    private long position = 0;

    /// The current limit (aka max position to read/write at.)
    private long limit;

    // --------------------------------------------------------------
    // FACTORY METHODS AND CONSTRUCTOR

    /// Wraps an external MemorySegment of any kind (heap, native, mapped, etc.).
    @NonNull
    public static MemoryData wrap(@NonNull final MemorySegment segment) {
        return new MemoryData(segment);
    }

    /// Wraps an external byte array. Applications are responsible for not modifying the content of the array
    /// directly after this method is called.
    @NonNull
    public static MemoryData wrap(@NonNull final byte[] array) {
        return new MemoryData(MemorySegment.ofArray(array));
    }

    /// Wraps a slice of an external byte array. Applications are responsible for not modifying the content of the array
    /// directly after this method is called.
    @NonNull
    public static MemoryData wrap(@NonNull final byte[] array, final int offset, final int len) {
        final MemorySegment ms1 = MemorySegment.ofArray(array);
        final MemorySegment ms2 = ms1.asSlice(offset, len);
        return new MemoryData(MemorySegment.ofArray(array).asSlice(offset, len));
    }

    /// Wraps a slice of an external ByteBuffer respecting its current position/limit.
    /// Applications are responsible for not modifying the content of the ByteBuffer
    /// directly after this method is called.
    @NonNull
    public static MemoryData wrap(@NonNull final ByteBuffer buffer) {
        return new MemoryData(MemorySegment.ofBuffer(buffer));
    }

    /// Allocates a new byte[] in heap and wraps it.
    @NonNull
    public static MemoryData allocate(final int size) {
        return new MemoryData(MemorySegment.ofArray(new byte[size]));
    }

    /// Convenience method to allocate a new native segment in a new auto Arena.
    /// In most cases, applications would want to use a custom Arena to manage the life-cycle
    /// of their MemorySegment instances more precisely, and then use the
    /// `MemoryData.wrap(MemorySegment)` factory method instead of this one.
    @NonNull
    public static MemoryData allocateOffHeap(final int size) {
        return new MemoryData(Arena.ofAuto().allocate(size));
    }

    private MemoryData(@NonNull final MemorySegment segment) {
        this.segment = segment;
        this.limit = segment.byteSize();
    }

    // --------------------------------------------------------------
    // Object

    /// toString that outputs data in buffer in bytes.
    /// @return nice debug output of buffer contents
    @Override
    public String toString() {
        // build string
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[");
        for (int i = 0; i < limit; i++) {
            int v = segment.get(ValueLayout.JAVA_BYTE, i) & 0xFF;
            sb.append(v);
            if (i < (limit - 1)) sb.append(',');
        }
        sb.append(']');
        return sb.toString();
    }

    // --------------------------------------------------------------
    // SequentialData

    @Override
    public long capacity() {
        return segment.byteSize();
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public long limit() {
        return limit;
    }

    @Override
    public void limit(final long limit) {
        this.limit = Math.clamp(limit, position(), capacity());
    }

    @Override
    public void skip(final long count) {
        if (count > remaining()) {
            throw new BufferUnderflowException();
        }
        if (count <= 0) {
            return;
        }
        position += count;
    }

    // --------------------------------------------------------------
    // BufferedSequentialData

    @Override
    public void position(long position) {
        if (position > capacity()) {
            throw new BufferUnderflowException();
        }
        if (position < 0) {
            throw new IllegalArgumentException("position cannot be negative, got " + position);
        }
        this.position = position;
    }

    @Override
    public void flip() {
        limit = position;
        position = 0;
    }

    @Override
    public void reset() {
        limit = capacity();
        position = 0;
    }

    @Override
    public void resetPosition() {
        position = 0;
    }

    // --------------------------------------------------------------
    // ReadableSequentialData

    @Override
    public byte readByte() throws BufferUnderflowException {
        if (position >= limit) {
            throw new BufferUnderflowException();
        }
        return segment.get(ValueLayout.JAVA_BYTE, position++);
    }

    @NonNull
    @Override
    public MemoryData view(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }

        if (length > remaining()) {
            throw new BufferUnderflowException();
        }

        final var pos = Math.toIntExact(position());
        final var buf = slice(pos, length);
        position((long) pos + length);
        return buf;
    }

    @NonNull
    @Override // to match BufferedData semantics and return Bytes.EMPTY
    public Bytes readBytes(final int length) {
        if (length < 0) throw new IllegalArgumentException("Length cannot be negative");
        if (length == 0) return Bytes.EMPTY;
        if (remaining() < length) throw new BufferUnderflowException();

        final var bytes = getBytes(position(), length);
        position = position + length;
        return bytes;
    }

    // --------------------------------------------------------------
    // WritableSequentialData

    @Override
    public void writeByte(final byte b) throws BufferOverflowException {
        if (position >= limit) {
            throw new BufferOverflowException();
        }
        segment.set(ValueLayout.JAVA_BYTE, position++, b);
    }

    // --------------------------------------------------------------
    // RandomAccessData

    @Override
    public long length() {
        return limit;
    }

    @Override
    public byte getByte(final long offset) {
        if (offset >= limit) {
            throw new BufferUnderflowException();
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative, got " + offset);
        }
        return segment.get(ValueLayout.JAVA_BYTE, offset);
    }

    @Override // to follow BufferedData semantics and not modify the dst position
    public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
        final var len = Math.min(dst.remaining(), length() - offset);
        dst.put(dst.position(), segment.asByteBuffer(), Math.toIntExact(offset), Math.toIntExact(len));
        return len;
    }

    @Override // to follow BufferedData semantics and not modify the dst position
    public long getBytes(final long offset, @NonNull final BufferedData dst) {
        final var len = Math.min(dst.remaining(), length() - offset);
        dst.buffer.put(dst.buffer.position(), segment.asByteBuffer(), Math.toIntExact(offset), Math.toIntExact(len));
        return len;
    }

    @Override
    @NonNull
    public MemoryData slice(final long offset, final long length) {
        return MemoryData.wrap(segment.asSlice(offset, length));
    }

    @Override
    public void writeTo(@NonNull OutputStream outStream) {
        try {
            final WritableByteChannel channel = Channels.newChannel(outStream);
            channel.write(segment.asByteBuffer().limit(Math.toIntExact(limit)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeTo(@NonNull OutputStream outStream, int offset, int length) {
        if (offset > limit - length) {
            throw new BufferUnderflowException();
        }
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("offset/length cannot be negative, got " + offset + "/" + length);
        }
        try {
            final WritableByteChannel channel = Channels.newChannel(outStream);
            channel.write(segment.asByteBuffer().position(offset).limit(Math.toIntExact(offset + length)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeTo(@NonNull MessageDigest digest) {
        digest.update(segment.asByteBuffer().limit(Math.toIntExact(limit)));
    }
}
