// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

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
    // CONSTANTS
    private static final ValueLayout.OfInt JAVA_INT_BIG_ENDIAN = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt JAVA_INT_BIG_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt JAVA_INT_LITTLE_ENDIAN =
            ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfInt JAVA_INT_LITTLE_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfLong JAVA_LONG_BIG_ENDIAN =
            ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong JAVA_LONG_BIG_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong JAVA_LONG_LITTLE_ENDIAN =
            ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfLong JAVA_LONG_LITTLE_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfFloat JAVA_FLOAT_BIG_ENDIAN =
            ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat JAVA_FLOAT_BIG_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat JAVA_FLOAT_LITTLE_ENDIAN =
            ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfFloat JAVA_FLOAT_LITTLE_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfDouble JAVA_DOUBLE_BIG_ENDIAN =
            ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble JAVA_DOUBLE_BIG_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble JAVA_DOUBLE_LITTLE_ENDIAN =
            ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfDouble JAVA_DOUBLE_LITTLE_ENDIAN_UNALIGNED =
            ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

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

    @Override
    public int readUnsignedByte() throws BufferUnderflowException {
        if (position >= limit) {
            throw new BufferUnderflowException();
        }
        return Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, position++));
    }

    @Override
    public long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength)
            throws UncheckedIOException {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }
        final int length = Math.min(maxLength, Math.toIntExact(remaining()));
        if (length == 0) {
            return 0;
        }
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, position, dst, offset, length);
        position += length;
        return length;
    }

    @Override
    public long readBytes(@NonNull final ByteBuffer dst) throws UncheckedIOException {
        final var length = Math.min(dst.remaining(), remaining());
        if (length == 0) {
            return 0;
        }
        // MemorySegment.ofBuffer() captures the current position/limit of the buffer, so dstOffset is 0:
        MemorySegment.copy(segment, position, MemorySegment.ofBuffer(dst), 0, length);
        position += length;
        return length;
    }

    @Override
    public long readBytes(@NonNull final BufferedData dst) throws UncheckedIOException {
        final var length = Math.min(dst.remaining(), remaining());
        if (length == 0) {
            return 0;
        }
        // MemorySegment.ofBuffer() captures the current position/limit of the buffer, so dstOffset is 0:
        MemorySegment.copy(segment, position, MemorySegment.ofBuffer(dst.buffer), 0, length);
        position += length;
        return length;
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

    @Override
    public int readInt() throws BufferUnderflowException {
        if (remaining() < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final int num = segment.get(determineIntLayout(ByteOrder.BIG_ENDIAN, position), position);
        position += Integer.BYTES;
        return num;
    }

    @Override
    public int readInt(@NonNull final ByteOrder byteOrder) throws BufferUnderflowException {
        if (remaining() < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final int num = segment.get(determineIntLayout(byteOrder, position), position);
        position += Integer.BYTES;
        return num;
    }

    @Override
    public long readLong() throws BufferUnderflowException, UncheckedIOException {
        if (remaining() < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final long num = segment.get(determineLongLayout(ByteOrder.BIG_ENDIAN, position), position);
        position += Long.BYTES;
        return num;
    }

    @Override
    public long readLong(@NonNull final ByteOrder byteOrder) throws BufferUnderflowException {
        if (remaining() < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final long num = segment.get(determineLongLayout(byteOrder, position), position);
        position += Long.BYTES;
        return num;
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

    @Override
    public void writeByte2(byte b1, byte b2) {
        if (remaining() < 2) {
            throw new BufferOverflowException();
        }
        segment.set(ValueLayout.JAVA_BYTE, position++, b1);
        segment.set(ValueLayout.JAVA_BYTE, position++, b2);
    }

    @Override
    public void writeByte3(byte b1, byte b2, byte b3) {
        if (remaining() < 3) {
            throw new BufferOverflowException();
        }
        segment.set(ValueLayout.JAVA_BYTE, position++, b1);
        segment.set(ValueLayout.JAVA_BYTE, position++, b2);
        segment.set(ValueLayout.JAVA_BYTE, position++, b3);
    }

    @Override
    public void writeByte4(byte b1, byte b2, byte b3, byte b4) {
        if (remaining() < 4) {
            throw new BufferOverflowException();
        }
        segment.set(ValueLayout.JAVA_BYTE, position++, b1);
        segment.set(ValueLayout.JAVA_BYTE, position++, b2);
        segment.set(ValueLayout.JAVA_BYTE, position++, b3);
        segment.set(ValueLayout.JAVA_BYTE, position++, b4);
    }

    @Override
    public void writeBytes(@NonNull final byte[] src, final int offset, final int length)
            throws BufferOverflowException, UncheckedIOException {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }
        if (remaining() < length) {
            throw new BufferOverflowException();
        }

        MemorySegment.copy(MemorySegment.ofArray(src), offset, segment, position, length);
        position += length;
    }

    @Override
    public void writeBytes(@NonNull final ByteBuffer src) throws BufferOverflowException, UncheckedIOException {
        final int srcRemaining = src.remaining();
        if (remaining() < srcRemaining) {
            throw new BufferOverflowException();
        }

        MemorySegment.copy(MemorySegment.ofBuffer(src), 0, segment, position, srcRemaining);
        position += srcRemaining;
    }

    @Override
    public void writeBytes(@NonNull final BufferedData src) throws BufferOverflowException, UncheckedIOException {
        final long srcRemaining = src.remaining();
        if (remaining() < srcRemaining) {
            throw new BufferOverflowException();
        }

        MemorySegment.copy(MemorySegment.ofBuffer(src.buffer), 0, segment, position, srcRemaining);
        position += srcRemaining;
    }

    @Override
    public void writeBytes(@NonNull final RandomAccessData src) throws BufferOverflowException, UncheckedIOException {
        if (src instanceof Bytes buf) {
            if ((limit() - position()) < src.length()) {
                throw new BufferOverflowException();
            }
            buf.writeTo(segment, position);
            position += buf.length();
        } else if (src instanceof MemoryData md) {
            if ((limit() - position()) < md.segment.byteSize()) {
                throw new BufferOverflowException();
            }
            MemorySegment.copy(md.segment, 0, segment, position, md.segment.byteSize());
            position += md.segment.byteSize();
        } else {
            WritableSequentialData.super.writeBytes(src);
        }
    }

    @Override
    public int writeBytes(@NonNull final InputStream src, final int maxLength) {
        if (segment.heapBase().isPresent() && segment.heapBase().get() instanceof byte[] array) {
            Objects.requireNonNull(src);
            if (maxLength < 0) {
                throw new IllegalArgumentException("The length must be >= 0");
            }

            // If the length is zero, then we have nothing to read
            if (maxLength == 0) {
                return 0;
            }

            // We are going to read from the input stream up to either "len" or the number of bytes
            // remaining in this DataOutput, whichever is lesser.
            final long numBytesToRead = Math.min(maxLength, remaining());
            if (numBytesToRead == 0) {
                return 0;
            }

            try {
                int totalBytesRead = 0;
                while (totalBytesRead < numBytesToRead) {
                    int bytesRead = src.read(
                            array,
                            Math.toIntExact(segment.address() + position),
                            (int) numBytesToRead - totalBytesRead);
                    if (bytesRead == -1) {
                        return totalBytesRead;
                    }
                    position += bytesRead;
                    totalBytesRead += bytesRead;
                }
                return totalBytesRead;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return WritableSequentialData.super.writeBytes(src, maxLength);
        }
    }

    @Override
    public void writeInt(final int value) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Integer.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineIntLayout(ByteOrder.BIG_ENDIAN, position), position, value);
        position += Integer.BYTES;
    }

    @Override
    public void writeInt(final int value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Integer.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineIntLayout(byteOrder, position), position, value);
        position += Integer.BYTES;
    }

    @Override
    public void writeLong(final long value) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Long.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineLongLayout(ByteOrder.BIG_ENDIAN, position), position, value);
        position += Long.BYTES;
    }

    @Override
    public void writeLong(final long value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Long.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineLongLayout(byteOrder, position), position, value);
        position += Long.BYTES;
    }

    @Override
    public void writeFloat(final float value) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Float.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineFloatLayout(ByteOrder.BIG_ENDIAN, position), position, value);
        position += Float.BYTES;
    }

    @Override
    public void writeFloat(final float value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Float.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineFloatLayout(byteOrder, position), position, value);
        position += Float.BYTES;
    }

    @Override
    public void writeDouble(final double value) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Double.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineDoubleLayout(ByteOrder.BIG_ENDIAN, position), position, value);
        position += Double.BYTES;
    }

    @Override
    public void writeDouble(final double value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Double.BYTES) {
            throw new BufferOverflowException();
        }
        segment.set(determineDoubleLayout(byteOrder, position), position, value);
        position += Double.BYTES;
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

    @Override
    public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        final var len = Math.min(maxLength, length() - offset);

        final Optional<Object> heapBaseOptional = segment.heapBase();
        if (heapBaseOptional.isPresent() && heapBaseOptional.get() instanceof byte[] array) {
            System.arraycopy(array, Math.toIntExact(segment.address() + offset), dst, dstOffset, Math.toIntExact(len));
        } else {
            MemorySegment.copy(segment, offset, MemorySegment.ofArray(dst).asSlice(dstOffset, len), 0, len);
        }
        return len;
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

    @Override
    public int getInt(final long offset) {
        checkOffset(offset, length());
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineIntLayout(ByteOrder.BIG_ENDIAN, offset), offset);
    }

    @Override
    public int getInt(final long offset, @NonNull final ByteOrder byteOrder) {
        checkOffset(offset, length());
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineIntLayout(byteOrder, offset), offset);
    }

    @Override
    public long getLong(final long offset) {
        checkOffset(offset, length());
        if ((length() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineLongLayout(ByteOrder.BIG_ENDIAN, offset), offset);
    }

    @Override
    public long getLong(final long offset, @NonNull final ByteOrder byteOrder) {
        checkOffset(offset, length());
        if ((length() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineLongLayout(byteOrder, offset), offset);
    }

    @Override
    public float getFloat(final long offset) {
        checkOffset(offset, length());
        if ((length() - offset) < Float.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineFloatLayout(ByteOrder.BIG_ENDIAN, offset), offset);
    }

    @Override
    public float getFloat(final long offset, @NonNull final ByteOrder byteOrder) {
        checkOffset(offset, length());
        if ((length() - offset) < Float.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineFloatLayout(byteOrder, offset), offset);
    }

    @Override
    public double getDouble(final long offset) {
        checkOffset(offset, length());
        if ((length() - offset) < Double.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineDoubleLayout(ByteOrder.BIG_ENDIAN, offset), offset);
    }

    @Override
    public double getDouble(final long offset, @NonNull final ByteOrder byteOrder) {
        checkOffset(offset, length());
        if ((length() - offset) < Double.BYTES) {
            throw new BufferUnderflowException();
        }
        return segment.get(determineDoubleLayout(byteOrder, offset), offset);
    }

    @Override
    public boolean contains(final long offset, @NonNull final byte[] bytes) {
        checkOffset(offset, length());
        if (length() - offset < bytes.length) {
            return false;
        }
        return MemorySegment.mismatch(
                        segment, offset, offset + bytes.length, MemorySegment.ofArray(bytes), 0, bytes.length)
                == -1;
    }

    @Override
    public boolean contains(final long offset, @NonNull final RandomAccessData data) {
        if (length() == 0) {
            return data.length() == 0;
        }

        checkOffset(offset, length());

        if (length() - offset < data.length()) {
            return false;
        }

        return switch (data) {
            case MemoryData otherData -> {
                final int dataLength = Math.toIntExact(data.length());
                yield MemorySegment.mismatch(segment, offset, offset + dataLength, otherData.segment, 0, dataLength)
                        == -1;
            }
            case BufferedData bd -> {
                final int dataLength = Math.toIntExact(data.length());
                yield MemorySegment.mismatch(
                                segment, offset, offset + dataLength, MemorySegment.ofBuffer(bd.buffer), 0, dataLength)
                        == -1;
            }
            case Bytes bytes -> {
                final int dataLength = Math.toIntExact(data.length());
                yield MemorySegment.mismatch(
                                segment, offset, offset + dataLength, bytes.toMemorySegment(), 0, dataLength)
                        == -1;
            }
            default -> BufferedSequentialData.super.contains(offset, data);
        };
    }

    // --------------------------------------------------------------
    // Utilities

    private ValueLayout.OfInt determineIntLayout(final ByteOrder byteOrder, final long offset) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return segment.maxByteAlignment() >= JAVA_INT_BIG_ENDIAN.byteAlignment()
                            && ((offset & (Integer.BYTES - 1)) == 0L)
                    ? JAVA_INT_BIG_ENDIAN
                    : JAVA_INT_BIG_ENDIAN_UNALIGNED;
        }
        return segment.maxByteAlignment() >= JAVA_INT_LITTLE_ENDIAN.byteAlignment()
                        && ((offset & (Integer.BYTES - 1)) == 0L)
                ? JAVA_INT_LITTLE_ENDIAN
                : JAVA_INT_LITTLE_ENDIAN_UNALIGNED;
    }

    private ValueLayout.OfLong determineLongLayout(final ByteOrder byteOrder, final long offset) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return segment.maxByteAlignment() >= JAVA_LONG_BIG_ENDIAN.byteAlignment()
                            && ((offset & (Long.BYTES - 1)) == 0L)
                    ? JAVA_LONG_BIG_ENDIAN
                    : JAVA_LONG_BIG_ENDIAN_UNALIGNED;
        }
        return segment.maxByteAlignment() >= JAVA_LONG_LITTLE_ENDIAN.byteAlignment()
                        && ((offset & (Long.BYTES - 1)) == 0L)
                ? JAVA_LONG_LITTLE_ENDIAN
                : JAVA_LONG_LITTLE_ENDIAN_UNALIGNED;
    }

    private ValueLayout.OfFloat determineFloatLayout(final ByteOrder byteOrder, final long offset) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return segment.maxByteAlignment() >= JAVA_FLOAT_BIG_ENDIAN.byteAlignment()
                            && ((offset & (Float.BYTES - 1)) == 0L)
                    ? JAVA_FLOAT_BIG_ENDIAN
                    : JAVA_FLOAT_BIG_ENDIAN_UNALIGNED;
        }
        return segment.maxByteAlignment() >= JAVA_FLOAT_LITTLE_ENDIAN.byteAlignment()
                        && ((offset & (Float.BYTES - 1)) == 0L)
                ? JAVA_FLOAT_LITTLE_ENDIAN
                : JAVA_FLOAT_LITTLE_ENDIAN_UNALIGNED;
    }

    private ValueLayout.OfDouble determineDoubleLayout(final ByteOrder byteOrder, final long offset) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return segment.maxByteAlignment() >= JAVA_DOUBLE_BIG_ENDIAN.byteAlignment()
                            && ((offset & (Double.BYTES - 1)) == 0L)
                    ? JAVA_DOUBLE_BIG_ENDIAN
                    : JAVA_DOUBLE_BIG_ENDIAN_UNALIGNED;
        }
        return segment.maxByteAlignment() >= JAVA_DOUBLE_LITTLE_ENDIAN.byteAlignment()
                        && ((offset & (Double.BYTES - 1)) == 0L)
                ? JAVA_DOUBLE_LITTLE_ENDIAN
                : JAVA_DOUBLE_LITTLE_ENDIAN_UNALIGNED;
    }
}
