// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Base test class for testing all types of {@link SequentialData} classes. */
public abstract class SequentialTestBase {

    @NonNull
    protected abstract SequentialData sequence();

    @NonNull
    protected abstract SequentialData eofSequence();

    @NonNull
    protected byte[] asBytes(
            @NonNull final Consumer<ByteBuffer> c, @NonNull final ByteOrder order) {
        final var buf = ByteBuffer.allocate(1000).order(order);
        c.accept(buf);
        buf.flip();
        final var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }

    @NonNull
    protected byte[] asBytes(@NonNull final Consumer<ByteBuffer> c) {
        return asBytes(c, BIG_ENDIAN);
    }

    @Nested
    @DisplayName("limit()")
    final class LimitTest {
        @Test
        @DisplayName("Setting the limit to be negative clamps to the position")
        void setNegativeLimit() {
            // Given a sequence
            final var seq = sequence();
            // When the limit is made negative
            seq.limit(-1);
            // Then the limit is clamped to the position
            assertThat(seq.limit()).isEqualTo(seq.position());
            // And now there will be no remaining bytes since the limit is equal to the position
            assertThat(seq.hasRemaining()).isFalse();
            assertThat(seq.remaining()).isZero();
        }

        @Test
        @DisplayName("Setting the limit to be less than the position clamps to the position")
        void clampToPosition() {
            // Given a sequence where the position is not 0 and not capacity
            final var seq = sequence();
            seq.skip(2);
            // When we set the limit to be less than the position
            seq.limit(seq.position() - 1);
            // Then the limit is clamped to the position
            assertThat(seq.limit()).isEqualTo(seq.position());
            // And now there will be no remaining bytes since the limit is equal to the position
            assertThat(seq.hasRemaining()).isFalse();
            assertThat(seq.remaining()).isZero();
        }

        @Test
        @DisplayName("Setting the limit between position and capacity works")
        void limit() {
            // Given a sequence
            final var seq = sequence();
            // When we set the limit to be between the position and capacity
            final var limit = seq.capacity() - seq.position() / 2;
            seq.limit(limit);
            // Then the limit is set
            assertThat(seq.limit()).isEqualTo(limit);
            // And there are still bytes remaining, and equal the difference between the limit and
            // the position
            assertThat(seq.hasRemaining()).isTrue();
            assertThat(seq.remaining()).isEqualTo(limit - seq.position());
        }

        @Test
        @DisplayName("Setting the limit to be greater than the capacity clamps to the capacity")
        void clampToCapacity() {
            // Given a sequence (assuming capacity is less than Long.MAX_VALUE)
            final var seq = sequence();
            assumeTrue(
                    seq.capacity() < Long.MAX_VALUE, "This test does not make sense for streams");
            // When we set the limit to be larger than the capacity
            seq.limit(seq.capacity() + 1);
            // Then the limit is clamped to the capacity
            assertThat(seq.limit()).isEqualTo(seq.capacity());
            // And there are still bytes remaining, and equal the difference between the capacity
            // and the position
            assertThat(seq.hasRemaining()).isTrue();
            assertThat(seq.remaining()).isEqualTo(seq.capacity() - seq.position());
        }
    }

    @Nested
    @DisplayName("skip()")
    final class SkipTest {
        @ParameterizedTest
        @CsvSource({
            "-1, 0", // skip -1 bytes, limit is 5, so clamp to 0
            "0, 0", // skip 0 bytes, limit is 5, so clamp to 0
            "3, 3", // skip 3 bytes, limit is 5, so clamp to 3
            "5, 5"
        }) // skip 5 bytes, limit is 5, so clamp to 5
        @DisplayName("Skipping relative to the limit will clamp at limit")
        void skipping(long skip, long expected) {
            // Given a sequence, and some number of bytes to skip
            final var seq = sequence();
            // When we set the limit to be between the position and capacity, and we skip those
            // bytes
            seq.limit(5);
            seq.skip(skip);
            // Then the position matches the number of bytes actually skipped, taking into account
            // whether the number of bytes skipped was clamped due to encountering the limit
            // or not (The "expected" arg tells us where we should have landed after skipping bytes)
            assertThat(seq.position()).isEqualTo(expected);
            assertThat(seq.remaining()).isEqualTo(5 - expected);
        }

        @ParameterizedTest
        @CsvSource({"7"}) // skip 7 bytes, limit is 5, so throw on skip()
        @DisplayName("Skipping beyond the limit will throw")
        void skippingAndThrowing(long skip) {
            // Given a sequence, and some number of bytes to skip
            final var seq = sequence();
            // When we set the limit to be between the position and capacity, and we skip those
            // bytes
            seq.limit(5);
            assertThatThrownBy(() -> seq.skip(skip))
                    .isInstanceOfAny(BufferUnderflowException.class, BufferOverflowException.class);
        }
    }

    @Nested
    @DisplayName("writeByte()")
    final class WriteByteTest {
        //        @Test
        //        @DisplayName("Writing a byte to an empty sequence throws BufferOverflowException")
        //        void writeToEmptyDataThrows() {
        //            // Given an empty sequence
        //            final var seq = emptySequence();
        //            // When we try to read a byte, then we get a BufferOverflowException
        //            assertThatThrownBy(() -> seq.writeByte((byte)
        // 1)).isInstanceOf(BufferOverflowException.class);
        //        }
        //
        //        @Test
        //        @DisplayName("Reading a byte from a full read sequence throws
        // BufferUnderflowException")
        //        void readFromFullyReadDataThrows() {
        //            // Given a fully read sequence
        //            final var seq = fullyUsedSequence();
        //            // When we try to read a byte, then we get a BufferUnderflowException
        //
        // assertThatThrownBy(seq::readByte).isInstanceOf(BufferUnderflowException.class);
        //        }
        //
        //        @Test
        //        @DisplayName("Reading a byte past the limit throws BufferUnderflowException")
        //        void readPastLimit() {
        //            // Given a sequence of bytes with a limit where position == limit
        //            final var seq = sequence(TEST_BYTES);
        //            seq.limit(5);
        //            seq.skip(5);
        //            // When we try to read a byte, then we get a BufferUnderflowException
        //
        // assertThatThrownBy(seq::readByte).isInstanceOf(BufferUnderflowException.class);
        //        }
        //
        //        @Test
        //        @DisplayName("Reading bytes from beginning to end")
        //        void read() {
        //            // Given a sequence of bytes
        //            final var seq = sequence(TEST_BYTES);
        //            // When we read each byte, then we get the expected byte
        //            for (byte testByte : TEST_BYTES) {
        //                final var pos = seq.position();
        //                assertThat(seq.hasRemaining()).isTrue();
        //                assertThat(seq.readByte()).isEqualTo(testByte);
        //                assertThat(seq.position()).isEqualTo(pos + 1);
        //            }
        //            // And when we get to the end, there is no longer anything to be read
        //            assertThat(seq.hasRemaining()).isFalse();
        //            assertThat(seq.remaining()).isZero();
        //        }
    }

    //    @Nested
    //    @DisplayName("readUnsignedByte()")
    //    final class ReadUnsignedByteTest {
    //        @Test
    //        @DisplayName("Reading an unsigned byte from an empty sequence throws
    // BufferUnderflowException")
    //        void readFromEmptyDataThrows() {
    //            // Given an empty sequence
    //            final var seq = emptySequence();
    //            // When we try to read an unsigned byte, then we get a BufferUnderflowException
    //
    // assertThatThrownBy(seq::readUnsignedByte).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an unsigned byte from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows() {
    //            // Given a fully read sequence
    //            final var seq = fullyUsedSequence();
    //            // When we try to read an unsigned byte, then we get a BufferUnderflowException
    //
    // assertThatThrownBy(seq::readUnsignedByte).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an unsigned byte past the limit throws
    // BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            seq.skip(5);
    //            // When we try to read an unsigned byte, then we get a BufferUnderflowException
    //
    // assertThatThrownBy(seq::readUnsignedByte).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an unsigned byte")
    //        void read() {
    //            // Given a sequence of bytes (with a single byte that could be interpreted as
    // negative if signed)
    //            final var seq = sequence(new byte[] { (byte) 0b1110_0011 });
    //            // When we read the byte, then we get the expected byte and move the position
    // forward by a single byte
    //            final var pos = seq.position();
    //            assertThat(seq.readUnsignedByte()).isEqualTo(0b1110_0011);
    //            assertThat(seq.position()).isEqualTo(pos + 1);
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("readBytes()")
    //    final class ReadBytesTest {
    //        @Test
    //        @DisplayName("Reading bytes with a null dst throws NullPointerException")
    //        void readNullDstThrows() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //
    //            // When we try to read bytes using a null byte array, then we get a
    // NullPointerException
    //            //noinspection DataFlowIssue
    //            assertThatThrownBy(() -> seq.readBytes((byte[])
    // null)).isInstanceOf(NullPointerException.class);
    //            //noinspection DataFlowIssue
    //            assertThatThrownBy(() -> seq.readBytes(null, 0,
    // 10)).isInstanceOf(NullPointerException.class);
    //
    //            // When we try to read bytes using a null ByteBuffer, then we get a
    // NullPointerException
    //            //noinspection DataFlowIssue
    //            assertThatThrownBy(() -> seq.readBytes((ByteBuffer)
    // null)).isInstanceOf(NullPointerException.class);
    //
    //            // When we try to read bytes using a null BufferedData, then we get a
    // NullPointerException
    //            //noinspection DataFlowIssue
    //            assertThatThrownBy(() -> seq.readBytes((BufferedData)
    // null)).isInstanceOf(NullPointerException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes with a negative offset throws IndexOutOfBoundsException")
    //        void negativeOffsetThrows() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we try to read bytes using a byte array with a negative offset, then we
    // get an IndexOutOfBoundsException
    //            assertThatThrownBy(() -> seq.readBytes(new byte[10], -1,
    // 10)).isInstanceOf(IndexOutOfBoundsException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes with an offset that is too large throws
    // IndexOutOfBoundsException")
    //        void tooLargeOffsetThrows() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we try to read bytes using a byte array with an offset that is too large,
    //            // then we get an IndexOutOfBoundsException
    //            assertThatThrownBy(() -> seq.readBytes(new byte[10], 11, 10))
    //                    .isInstanceOf(IndexOutOfBoundsException.class);
    //            // When we try to read bytes using a byte array with an offset + maxLength that is
    // too large,
    //            // then we get an IndexOutOfBoundsException
    //            assertThatThrownBy(() -> seq.readBytes(new byte[10], 9, 2))
    //                    .isInstanceOf(IndexOutOfBoundsException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes with a negative length throws IllegalArgumentException")
    //        void negativeLengthThrows() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we try to read bytes using a byte array with a negative length, then we
    // get an IllegalArgumentException
    //            assertThatThrownBy(() -> seq.readBytes(new byte[10], 0,
    // -1)).isInstanceOf(IllegalArgumentException.class);
    //            assertThatThrownBy(() ->
    // seq.readBytes(-1)).isInstanceOf(IllegalArgumentException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes from an empty sequence is a no-op")
    //        void readFromEmptyDataIsNoOp() {
    //            // Given an empty sequence
    //            final var seq = emptySequence();
    //
    //            // When we try to read bytes using a byte array, then we get nothing read
    //            assertThat(seq.readBytes(new byte[10])).isZero();
    //            assertThat(seq.readBytes(new byte[10], 0, 2)).isZero();
    //
    //            // When we try to read bytes using a ByteBuffer, then we get nothing read
    //            final var byteBuffer = ByteBuffer.allocate(10);
    //            assertThat(seq.readBytes(byteBuffer)).isZero();
    //
    //            // When we try to read bytes using a BufferedData, then we get nothing read
    //            final var bufferedData = BufferedData.allocate(10);
    //            assertThat(seq.readBytes(bufferedData)).isZero();
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes from a fully read sequence is a no-op")
    //        void readFromFullyReadDataIsNoOp() {
    //            // Given a fully read sequence
    //            final var seq = fullyUsedSequence();
    //
    //            // When we try to read bytes using a byte array, then we get nothing read
    //            assertThat(seq.readBytes(new byte[10])).isZero();
    //            assertThat(seq.readBytes(new byte[10], 0, 2)).isZero();
    //
    //            // When we try to read bytes using a ByteBuffer, then we get nothing read
    //            final var byteBuffer = ByteBuffer.allocate(10);
    //            assertThat(seq.readBytes(byteBuffer)).isZero();
    //
    //            // When we try to read bytes using a BufferedData, then we get nothing read
    //            final var bufferedData = BufferedData.allocate(10);
    //            assertThat(seq.readBytes(bufferedData)).isZero();
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes where there is nothing remaining because we are at the
    // limit is a no-op")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            seq.skip(5);
    //
    //            // When we try to read bytes using a byte array, then we get nothing read
    //            assertThat(seq.readBytes(new byte[10])).isZero();
    //            assertThat(seq.readBytes(new byte[10], 0, 2)).isZero();
    //
    //            // When we try to read bytes using a ByteBuffer, then we get nothing read
    //            final var byteBuffer = ByteBuffer.allocate(10);
    //            assertThat(seq.readBytes(byteBuffer)).isZero();
    //
    //            // When we try to read bytes using a BufferedData, then we get nothing read
    //            final var bufferedData = BufferedData.allocate(10);
    //            assertThat(seq.readBytes(bufferedData)).isZero();
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array where the dst has length of 0")
    //        void readZeroDstByteArray() {
    //            // Given a sequence of bytes and an empty destination byte array
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = new byte[0];
    //            final var pos = seq.position();
    //            // When we try to read bytes into the dst, then the position does not change,
    //            // and the destination array is empty
    //            assertThat(seq.readBytes(dst)).isZero();
    //            assertThat(seq.position()).isEqualTo(pos);
    //            assertThat(dst).isEmpty();
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array with offset and length where the dst
    // has length of 0")
    //        void readZeroDstByteArrayWithOffset() {
    //            // Given a sequence of bytes and a destination byte array
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = new byte[10];
    //            final var pos = seq.position();
    //            // When we try to read bytes into the dst but with a 0 length, then the position
    // does not change,
    //            // and the destination array is empty
    //            assertThat(seq.readBytes(dst, 5,0)).isZero();
    //            assertThat(seq.position()).isEqualTo(pos);
    //            assertThat(dst).containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst ByteBuffer where the dst has length of 0")
    //        void readZeroDstByteBuffer() {
    //            // Given a sequence of bytes and an empty destination ByteBuffer
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = ByteBuffer.allocate(0);
    //            final var pos = seq.position();
    //            // When we try to read bytes into the dst, then the position does not change,
    //            // and the destination buffer is empty
    //            assertThat(seq.readBytes(dst)).isZero();
    //            assertThat(seq.position()).isEqualTo(pos);
    //            assertThat(dst.position()).isZero();
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst BufferedData where the dst has length of 0")
    //        void readZeroDstBufferedData() {
    //            // Given a sequence of bytes and an empty destination BufferedData
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = BufferedData.allocate(0);
    //            final var pos = seq.position();
    //            // When we try to read bytes into the dst, then the position does not change,
    //            // and the destination buffer is empty
    //            assertThat(seq.readBytes(dst)).isZero();
    //            assertThat(seq.position()).isEqualTo(pos);
    //            assertThat(dst.position()).isZero();
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array where the dst is smaller than the
    // sequence")
    //        void readSmallerDstByteArray() {
    //            // Given a sequence of bytes and a destination byte array
    //            final var seq = sequence(TEST_BYTES);
    //            // When we try reading into the dst (twice, once from the beginning and once in
    // the middle)
    //            for (int i = 0; i < 2; i++) {
    //                final var dst = new byte[5];
    //                final var pos = seq.position();
    //                final var subset = Arrays.copyOfRange(TEST_BYTES, (int) pos, (int) pos + 5);
    //                assertThat(seq.readBytes(dst)).isEqualTo(5);
    //                // Then the dst is filled with the bytes from the sequence, and the position
    // is updated
    //                assertThat(dst).isEqualTo(subset);
    //                assertThat(seq.position()).isEqualTo(pos + 5);
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array with offset where the dst is smaller
    // than the sequence")
    //        void readSmallerDstByteArrayWithOffset() {
    //            final var seq = sequence(TEST_BYTES);
    //            // Do twice, so we read once from sequence at the beginning and once in the middle
    //            for (int i = 0; i < 2; i++) {
    //                final var dst = new byte[10];
    //                final var pos = seq.position();
    //                final var subset = Arrays.copyOfRange(TEST_BYTES, (int) pos, (int) pos + 5);
    //                assertThat(seq.readBytes(dst, 3, 5)).isEqualTo(5);
    //                assertThat(Arrays.copyOfRange(dst, 3, 8)).isEqualTo(subset);
    //                assertThat(seq.position()).isEqualTo(pos + 5);
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst ByteBuffer where the dst is smaller than the
    // sequence")
    //        void readSmallerDstByteBuffer() {
    //            final var seq = sequence(TEST_BYTES);
    //            for (int i = 0; i < 2; i++) {
    //                final var dst = ByteBuffer.allocate(5);
    //                final var pos = seq.position();
    //                final var subset = Arrays.copyOfRange(TEST_BYTES, (int) pos, (int) pos + 5);
    //                assertThat(seq.readBytes(dst)).isEqualTo(5);
    //                assertThat(dst.array()).isEqualTo(subset);
    //                assertThat(seq.position()).isEqualTo(pos + 5);
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst ByteBuffer with offset where the dst is smaller
    // than the sequence")
    //        void readSmallerDstByteBufferWithOffset() {
    //            final var seq = sequence(TEST_BYTES);
    //            for (int i = 0; i < 2; i++) {
    //                final var dst = ByteBuffer.allocate(10);
    //                dst.position(5);
    //                final var pos = seq.position();
    //                final var subset = Arrays.copyOfRange(TEST_BYTES, (int) pos, (int) pos + 5);
    //                assertThat(seq.readBytes(dst)).isEqualTo(5);
    //                assertThat(dst.slice(5, 5)).isEqualTo(ByteBuffer.wrap(subset));
    //                assertThat(seq.position()).isEqualTo(pos + 5);
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst BufferedData where the dst is smaller than the
    // sequence")
    //        void readSmallerDstBufferedData() {
    //            final var seq = sequence(TEST_BYTES);
    //            for (int i = 0; i < 2; i++) {
    //                final var dst = BufferedData.allocate(5);
    //                final var pos = seq.position();
    //                final var subset = Arrays.copyOfRange(TEST_BYTES, (int) pos, (int) pos + 5);
    //                assertThat(seq.readBytes(dst)).isEqualTo(5);
    //                assertThat(dst).isEqualTo(BufferedData.wrap(subset));
    //                assertThat(seq.position()).isEqualTo(pos + 5);
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst BufferedData with offset where the dst is
    // smaller than the sequence")
    //        void readSmallerDstBufferedDataWithOffset() {
    //            final var seq = sequence(TEST_BYTES);
    //            for (int i = 0; i < 2; i++) {
    //                final var dst = BufferedData.allocate(10);
    //                dst.position(5);
    //                final var pos = seq.position();
    //                final var subset = Arrays.copyOfRange(TEST_BYTES, (int) pos, (int) pos + 5);
    //                assertThat(seq.readBytes(dst)).isEqualTo(5);
    //                assertThat(dst.slice(5, 5)).isEqualTo(BufferedData.wrap(subset));
    //                assertThat(seq.position()).isEqualTo(pos + 5);
    //            }
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array where the dst is the same length as
    // the sequence")
    //        void readDstByteArray() {
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = new byte[TEST_BYTES.length];
    //            final var pos = seq.position();
    //            assertThat(seq.readBytes(dst)).isEqualTo(TEST_BYTES.length);
    //            assertThat(dst).isEqualTo(TEST_BYTES);
    //            assertThat(seq.position()).isEqualTo(pos + TEST_BYTES.length);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array with offset where the dst is the
    // same length as the sequence")
    //        void readDstByteArrayWithOffset() {
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = new byte[TEST_BYTES.length + 10];
    //            final var pos = seq.position();
    //            assertThat(seq.readBytes(dst, 5, TEST_BYTES.length)).isEqualTo(TEST_BYTES.length);
    //            assertThat(Arrays.copyOfRange(dst, 5, 5 +
    // TEST_BYTES.length)).isEqualTo(TEST_BYTES);
    //            assertThat(seq.position()).isEqualTo(pos + TEST_BYTES.length);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst ByteBuffer where the dst is the same length as
    // the sequence")
    //        void readDstByteBuffer() {
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = ByteBuffer.allocate(TEST_BYTES.length);
    //            final var pos = seq.position();
    //            assertThat(seq.readBytes(dst)).isEqualTo(TEST_BYTES.length);
    //            assertThat(dst.array()).isEqualTo(TEST_BYTES);
    //            assertThat(seq.position()).isEqualTo(pos + TEST_BYTES.length);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst ByteBuffer with offset where the dst is the
    // same length as the sequence")
    //        void readDstByteBufferWithOffset() {
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = ByteBuffer.allocate(TEST_BYTES.length + 10);
    //            final var pos = seq.position();
    //            dst.position(5);
    //            dst.limit(TEST_BYTES.length + 5);
    //            assertThat(seq.readBytes(dst)).isEqualTo(TEST_BYTES.length);
    //            assertThat(dst.slice(5,
    // TEST_BYTES.length)).isEqualTo(ByteBuffer.wrap(TEST_BYTES));
    //            assertThat(seq.position()).isEqualTo(pos + TEST_BYTES.length);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst BufferedData where the dst is the same length
    // as the sequence")
    //        void readDstBufferedData() {
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = BufferedData.allocate(TEST_BYTES.length);
    //            final var pos = seq.position();
    //            assertThat(seq.readBytes(dst)).isEqualTo(TEST_BYTES.length);
    //            assertThat(dst).isEqualTo(BufferedData.wrap(TEST_BYTES));
    //            assertThat(seq.position()).isEqualTo(pos + TEST_BYTES.length);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst BufferedData with offset where the dst is the
    // same length as the sequence")
    //        void readDstBufferedDataWithOffset() {
    //            final var seq = sequence(TEST_BYTES);
    //            final var dst = BufferedData.allocate(TEST_BYTES.length + 10);
    //            final var pos = seq.position();
    //            dst.position(5);
    //            dst.limit(TEST_BYTES.length + 5);
    //            assertThat(seq.readBytes(dst)).isEqualTo(TEST_BYTES.length);
    //            assertThat(dst.slice(5,
    // TEST_BYTES.length)).isEqualTo(BufferedData.wrap(TEST_BYTES));
    //            assertThat(seq.position()).isEqualTo(pos + TEST_BYTES.length);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array where the dst is larger than the
    // sequence")
    //        void readLargerDstByteArray() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we read the bytes into a larger byte array
    //            final var arr = new byte[TEST_BYTES.length + 1];
    //            assertThat(seq.readBytes(arr)).isEqualTo(TEST_BYTES.length);
    //            // Then the sequence is exhausted and the array is filled starting at index 0
    //            assertThat(seq.remaining()).isZero();
    //            assertThat(seq.hasRemaining()).isFalse();
    //            assertThat(arr).startsWith(TEST_BYTES);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst byte array with offset where the dst is larger
    // than the sequence")
    //        void readLargerDstByteArrayWithOffset() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we read the bytes into a larger byte array with an offset
    //            final var arr = new byte[TEST_BYTES.length + 10];
    //            assertThat(seq.readBytes(arr, 5, TEST_BYTES.length +
    // 1)).isEqualTo(TEST_BYTES.length);
    //            // Then the sequence is exhausted and the array is filled starting at index 5
    //            assertThat(seq.remaining()).isZero();
    //            assertThat(seq.hasRemaining()).isFalse();
    //            assertThat(Arrays.copyOfRange(arr, 5, TEST_BYTES.length + 5
    // )).containsExactly(TEST_BYTES);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst ByteBuffer where the dst is larger than the
    // sequence")
    //        void readLargerDstByteBuffer() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we read the bytes into a larger buffer
    //            final var buffer = ByteBuffer.allocate(TEST_BYTES.length + 1);
    //            assertThat(seq.readBytes(buffer)).isEqualTo(TEST_BYTES.length);
    //            // Then the sequence is exhausted and the buffer is filled starting at index 0
    //            assertThat(seq.remaining()).isZero();
    //            assertThat(seq.hasRemaining()).isFalse();
    //            assertThat(buffer.array()).startsWith(TEST_BYTES);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst ByteBuffer with offset where the dst is larger
    // than the sequence")
    //        void readLargerDstByteBufferWithOffset() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we read the bytes into a larger buffer with an offset
    //            final var buffer = ByteBuffer.allocate(TEST_BYTES.length + 10);
    //            buffer.position(5);
    //            buffer.limit(5 + TEST_BYTES.length + 1);
    //            assertThat(seq.readBytes(buffer)).isEqualTo(TEST_BYTES.length);
    //            // Then the sequence is exhausted and the buffer is filled starting at index 5
    //            assertThat(seq.remaining()).isZero();
    //            assertThat(seq.hasRemaining()).isFalse();
    //            assertThat(Arrays.copyOfRange(buffer.array(), 5, TEST_BYTES.length + 5
    // )).containsExactly(TEST_BYTES);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst BufferedData where the dst is larger than the
    // sequence")
    //        void readLargerDstBufferedData() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we read the bytes into a larger buffer
    //            final var buffer = BufferedData.allocate(TEST_BYTES.length + 1);
    //            assertThat(seq.readBytes(buffer)).isEqualTo(TEST_BYTES.length);
    //            // Then the sequence is exhausted and the buffer is filled starting at index 0
    //            assertThat(seq.remaining()).isZero();
    //            assertThat(seq.hasRemaining()).isFalse();
    //            assertThat(buffer.slice(0,
    // TEST_BYTES.length)).isEqualTo(BufferedData.wrap(TEST_BYTES));
    //        }
    //
    //        @Test
    //        @DisplayName("Reading bytes into a dst BufferedData with offset where the dst is
    // larger than the sequence")
    //        void readLargerDstBufferedDataWithOffset() {
    //            // Given a sequence of bytes
    //            final var seq = sequence(TEST_BYTES);
    //            // When we read the bytes into a larger buffer with an offset
    //            final var buffer = BufferedData.allocate(TEST_BYTES.length + 10);
    //            buffer.position(5);
    //            buffer.limit(5 + TEST_BYTES.length + 1);
    //            assertThat(seq.readBytes(buffer)).isEqualTo(TEST_BYTES.length);
    //            // Then the sequence is exhausted and the buffer is filled starting at index 5
    //            assertThat(seq.remaining()).isZero();
    //            assertThat(seq.hasRemaining()).isFalse();
    //            assertThat(buffer.slice(5,
    // TEST_BYTES.length)).isEqualTo(BufferedData.wrap(TEST_BYTES));
    //        }
    //
    //        @ParameterizedTest(name = "offset={0}, length={1}")
    //        @CsvSource({
    //                "-1, 1", // Negative offset
    //                "100, 10", // Offset larger than the dst array size
    //                "5, 10", // Offset+Length larger than the dst array size
    //        })
    //        @DisplayName("Reading bytes where the dst offset and length are bad")
    //        void badOffsetLength(int offset, int length) {
    //            final var seq = sequence(TEST_BYTES);
    //            assertThatThrownBy(() -> seq.readBytes(new byte[10], offset, length))
    //                    .isInstanceOf(IndexOutOfBoundsException.class);
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("view()")
    //    final class ViewTest {
    //        @Test
    //        @DisplayName("Negative length throws IllegalArgumentException")
    //        void negativeLength() {
    //            final var seq = sequence(TEST_BYTES);
    //            assertThatThrownBy(() ->
    // seq.view(-1)).isInstanceOf(IllegalArgumentException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Length that is greater than remaining throws BufferUnderflowException")
    //        @Disabled("This has to be tested on the buffer level only, because for a Stream, the
    // limit is too big")
    //        void lengthGreaterThanRemaining() {
    //            // TODO Move to buffer tests
    //            final var seq = sequence(TEST_BYTES);
    //            seq.skip(1);
    //            assertThatThrownBy(() ->
    // seq.view(TEST_BYTES.length)).isInstanceOf(BufferUnderflowException.class);
    //            assertThatThrownBy(() ->
    // seq.view(Integer.MAX_VALUE)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Creating a view past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            seq.skip(5);
    //            // When we try to create a view with a length past the limit, then we get a
    // BufferUnderflowException
    //            assertThatThrownBy(() ->
    // seq.view(6)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Length is zero (OK, empty sequence)")
    //        void lengthIsZero() {
    //            final var seq = sequence(TEST_BYTES);
    //            assertThat(seq.view(0).remaining()).isZero();
    //        }
    //
    //        @Test
    //        @DisplayName("Length + Position is less than limit (OK)")
    //        void lengthPlusPositionIsLessThanLimit() {
    //            final var seq = sequence(TEST_BYTES);
    //            seq.skip(5);
    //            final var view = seq.view(10);
    //
    //            assertThat(view.remaining()).isEqualTo(10);
    //            assertThat(view.readBytes(10)).isEqualTo(Bytes.wrap(TEST_BYTES).slice(5, 10));
    //        }
    //
    //        @Test
    //        @DisplayName("Length + Position is the limit (OK)")
    //        void lengthPlusPositionIsTheLimit() {
    //            // Given a sequence of bytes where the position is 10 bytes from the end
    //            final var seq = sequence(TEST_BYTES);
    //            final var startIndex = TEST_BYTES.length - 10;
    //            assertThat(seq.skip(startIndex)).isEqualTo(16);
    //            assertThat(seq.position()).isEqualTo(16);
    //            // When we create a view with a length of 10 bytes
    //            final var view = seq.view(10);
    //            // Then we get the last 10 bytes of the sequence, AND it advances the position by
    // that many bytes.
    //            assertThat(seq.position()).isEqualTo(26);
    //            // The view, when read, will have all 10 of its bytes
    //            assertThat(view.remaining()).isEqualTo(10);
    //            final var bytes = view.readBytes(10);
    //            assertThat(view.position()).isEqualTo(10);
    //            // And those bytes will be the last 10 bytes of the sequence
    //            assertThat(bytes).isEqualTo(Bytes.wrap(TEST_BYTES).slice(startIndex, 10));
    //        }
    //
    //        @Test
    //        @DisplayName("Get sub-sequence of a sub-sequence")
    //        void subSequenceOfSubSequence() {
    //            final var seq = sequence(TEST_BYTES);
    //            final var subSeq = seq.view(10);
    //            final var subSubSeq = subSeq.view(5);
    //            assertThat(subSubSeq.remaining()).isEqualTo(5);
    //            assertThat(subSubSeq.readBytes(5)).isEqualTo(Bytes.wrap(TEST_BYTES).slice(0, 5));
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("readInt()")
    //    final class ReadIntTest {
    //        @Test
    //        @DisplayName("Reading an int from an empty sequence throws BufferUnderflowException")
    //        void readFromEmptyDataThrows() {
    //            final var seq = emptySequence();
    //            assertThatThrownBy(seq::readInt).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an int from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows() {
    //            final var seq = fullyUsedSequence();
    //            assertThatThrownBy(seq::readInt).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an int past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            // When we try to read an int, then we get a BufferUnderflowException
    //            seq.skip(4); // Only 1 byte left, not enough
    //            assertThatThrownBy(seq::readInt).isInstanceOf(BufferUnderflowException.class);
    //            seq.skip(1); // No bytes left, not enough
    //            assertThatThrownBy(seq::readInt).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an int when less than 4 bytes are available throws
    // BufferUnderflowException")
    //        void readInsufficientDataThrows() {
    //            for (int i = 0; i < 3; i++) {
    //                final var seq = sequence(new byte[i]);
    //                assertThatThrownBy(seq::readInt).isInstanceOf(BufferUnderflowException.class);
    //            }
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(ints = {Integer.MIN_VALUE, -8, -1, 0, 1, 8, Integer.MAX_VALUE})
    //        @DisplayName("Reading an int")
    //        void read(int value) {
    //            // Given a sequence with exactly 1 integer of data
    //            final var seq = sequence(asBytes(c -> c.putInt(value)));
    //            final var pos = seq.position();
    //            // When we read an int, then it is the same as the one we wrote, and the position
    // has moved forward
    //            // by 4 bytes
    //            assertThat(seq.readInt()).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(ints = {Integer.MIN_VALUE, -8, -1, 0, 1, 8, Integer.MAX_VALUE})
    //        @DisplayName("Reading an int in Little Endian")
    //        void readLittleEndian(int value) {
    //            final var seq = sequence(asBytes(c -> c.putInt(value), LITTLE_ENDIAN));
    //            final var pos = seq.position();
    //            assertThat(seq.readInt(LITTLE_ENDIAN)).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(ints = {Integer.MIN_VALUE, -8, -1, 0, 1, 8, Integer.MAX_VALUE})
    //        @DisplayName("Reading an int in Big Endian")
    //        void readBigEndian(int value) {
    //            final var seq = sequence(asBytes(c -> c.putInt(value), BIG_ENDIAN));
    //            final var pos = seq.position();
    //            assertThat(seq.readInt(BIG_ENDIAN)).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a mixture of big and little endian data")
    //        void readMixedEndian() {
    //            final var seq = sequence(asBytes(c -> {
    //                c.order(BIG_ENDIAN);
    //                c.putInt(0x01020304);
    //                c.order(LITTLE_ENDIAN);
    //                c.putInt(0x05060708);
    //                c.order(BIG_ENDIAN);
    //                c.putInt(0x090A0B0C);
    //                c.order(LITTLE_ENDIAN);
    //                c.putInt(0x0D0E0F10);
    //            }));
    //            assertThat(seq.readInt()).isEqualTo(0x01020304);
    //            assertThat(seq.readInt(LITTLE_ENDIAN)).isEqualTo(0x05060708);
    //            assertThat(seq.readInt()).isEqualTo(0x090A0B0C);
    //            assertThat(seq.readInt(LITTLE_ENDIAN)).isEqualTo(0x0D0E0F10);
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("readUnsignedInt()")
    //    final class ReadUnsignedIntTest {
    //        @Test
    //        @DisplayName("Reading an unsigned int from an empty sequence throws
    // BufferUnderflowException")
    //        void readFromEmptyDataThrows() {
    //            final var seq = emptySequence();
    //
    // assertThatThrownBy(seq::readUnsignedInt).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an unsigned int from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows() {
    //            final var seq = fullyUsedSequence();
    //
    // assertThatThrownBy(seq::readUnsignedInt).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an unsigned int past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            // When we try to read an unsigned int, then we get a BufferUnderflowException
    //            seq.skip(4); // Only 1 byte left, not enough
    //
    // assertThatThrownBy(seq::readUnsignedInt).isInstanceOf(BufferUnderflowException.class);
    //            seq.skip(1); // No bytes left, not enough
    //
    // assertThatThrownBy(seq::readUnsignedInt).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading an unsigned int when less than 4 bytes are available throws
    // BufferUnderflowException")
    //        void readInsufficientDataThrows() {
    //            for (int i = 0; i < 3; i++) {
    //                final var seq = sequence(new byte[i]);
    //
    // assertThatThrownBy(seq::readUnsignedInt).isInstanceOf(BufferUnderflowException.class);
    //            }
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(longs = {0x00FFFFFFFFL, 0, 1, 8, 0x007FFFFFFFL})
    //        @DisplayName("Reading an unsigned int")
    //        void read(long value) {
    //            final var seq = sequence(asBytes(c -> c.putInt((int) value)));
    //            final var pos = seq.position();
    //            assertThat(seq.readUnsignedInt()).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(longs = {0x00FFFFFFFFL, 0, 1, 8, 0x007FFFFFFFL})
    //        @DisplayName("Reading an unsigned int in Little Endian")
    //        void readLittleEndian(long value) {
    //            final var seq = sequence(asBytes(c -> c.putInt((int) value), LITTLE_ENDIAN));
    //            final var pos = seq.position();
    //            assertThat(seq.readUnsignedInt(LITTLE_ENDIAN)).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(longs = {0x00FFFFFFFFL, 0, 1, 8, 0x007FFFFFFFL})
    //        @DisplayName("Reading an unsigned int in Big Endian")
    //        void readBigEndian(long value) {
    //            final var seq = sequence(asBytes(c -> c.putInt((int) value), BIG_ENDIAN));
    //            final var pos = seq.position();
    //            assertThat(seq.readUnsignedInt(BIG_ENDIAN)).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a mixture of big and little endian data")
    //        void readMixedEndian() {
    //            final var seq = sequence(asBytes(c -> {
    //                c.order(BIG_ENDIAN);
    //                c.putInt(0x91020304);
    //                c.order(LITTLE_ENDIAN);
    //                c.putInt(0x95060708);
    //                c.order(BIG_ENDIAN);
    //                c.putInt(0x990A0B0C);
    //                c.order(LITTLE_ENDIAN);
    //                c.putInt(0x9D0E0F10);
    //            }));
    //            assertThat(seq.readUnsignedInt()).isEqualTo(0x91020304L);
    //            assertThat(seq.readUnsignedInt(LITTLE_ENDIAN)).isEqualTo(0x95060708L);
    //            assertThat(seq.readUnsignedInt()).isEqualTo(0x990A0B0CL);
    //            assertThat(seq.readUnsignedInt(LITTLE_ENDIAN)).isEqualTo(0x9D0E0F10L);
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("readLong()")
    //    final class ReadLongTest {
    //        @Test
    //        @DisplayName("Reading a long from an empty sequence throws BufferUnderflowException")
    //        void readFromEmptyDataThrows() {
    //            final var seq = emptySequence();
    //            assertThatThrownBy(seq::readLong).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a long from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows() {
    //            final var seq = fullyUsedSequence();
    //            assertThatThrownBy(seq::readLong).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a long past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            // When we try to read a long, then we get a BufferUnderflowException
    //            seq.skip(4); // Only 1 byte left, not enough
    //            assertThatThrownBy(seq::readLong).isInstanceOf(BufferUnderflowException.class);
    //            seq.skip(1); // No bytes left, not enough
    //            assertThatThrownBy(seq::readLong).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a long when less than 4 bytes are available throws
    // BufferUnderflowException")
    //        void readInsufficientDataThrows() {
    //            for (int i = 0; i < 7; i++) {
    //                final var seq = sequence(new byte[i]);
    //
    // assertThatThrownBy(seq::readLong).isInstanceOf(BufferUnderflowException.class);
    //            }
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(longs = {Long.MIN_VALUE, -8, -1, 0, 1, 8, Long.MAX_VALUE})
    //        @DisplayName("Reading a long")
    //        void read(long value) {
    //            final var seq = sequence(asBytes(c -> c.putLong(value)));
    //            final var pos = seq.position();
    //            assertThat(seq.readLong()).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 8);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(longs = {Long.MIN_VALUE, -8, -1, 0, 1, 8, Long.MAX_VALUE})
    //        @DisplayName("Reading a long in Little Endian")
    //        void readLittleEndian(long value) {
    //            final var seq = sequence(asBytes(c -> c.putLong(value), LITTLE_ENDIAN));
    //            final var pos = seq.position();
    //            assertThat(seq.readLong(LITTLE_ENDIAN)).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 8);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(longs = {Long.MIN_VALUE, -8, -1, 0, 1, 8, Long.MAX_VALUE})
    //        @DisplayName("Reading a long in Big Endian")
    //        void readBigEndian(long value) {
    //            final var seq = sequence(asBytes(c -> c.putLong(value), BIG_ENDIAN));
    //            final var pos = seq.position();
    //            assertThat(seq.readLong(BIG_ENDIAN)).isEqualTo(value);
    //            assertThat(seq.position()).isEqualTo(pos + 8);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a mixture of big and little endian data")
    //        void readMixedEndian() {
    //            final var seq = sequence(asBytes(c -> {
    //                c.order(BIG_ENDIAN);
    //                c.putLong(0x0102030405060708L);
    //                c.order(LITTLE_ENDIAN);
    //                c.putLong(0x05060708090A0B0CL);
    //                c.order(BIG_ENDIAN);
    //                c.putLong(0x990A0B0C0D0E0F10L);
    //                c.order(LITTLE_ENDIAN);
    //                c.putLong(0x9D0E0F1011121314L);
    //            }));
    //            assertThat(seq.readLong()).isEqualTo(0x0102030405060708L);
    //            assertThat(seq.readLong(LITTLE_ENDIAN)).isEqualTo(0x05060708090A0B0CL);
    //            assertThat(seq.readLong()).isEqualTo(0x990A0B0C0D0E0F10L);
    //            assertThat(seq.readLong(LITTLE_ENDIAN)).isEqualTo(0x9D0E0F1011121314L);
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("readFloat()")
    //    final class ReadFloatTest {
    //        @Test
    //        @DisplayName("Reading a float from an empty sequence throws BufferUnderflowException")
    //        void readFromEmptyDataThrows() {
    //            final var seq = emptySequence();
    //            assertThatThrownBy(seq::readFloat).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a float from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows() {
    //            final var seq = fullyUsedSequence();
    //            assertThatThrownBy(seq::readFloat).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a float past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            // When we try to read a float, then we get a BufferUnderflowException
    //            seq.skip(4); // Only 1 byte left, not enough
    //            assertThatThrownBy(seq::readFloat).isInstanceOf(BufferUnderflowException.class);
    //            seq.skip(1); // No bytes left, not enough
    //            assertThatThrownBy(seq::readFloat).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a float when less than 4 bytes are available throws
    // BufferUnderflowException")
    //        void readInsufficientDataThrows() {
    //            for (int i = 0; i < 3; i++) {
    //                final var seq = sequence(new byte[i]);
    //
    // assertThatThrownBy(seq::readFloat).isInstanceOf(BufferUnderflowException.class);
    //            }
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -8.2f,
    // -1.3f, 0, 1.4f, 8.5f, Float.MAX_VALUE, Float.POSITIVE_INFINITY})
    //        @DisplayName("Reading a float")
    //        void read(float value) {
    //            final var seq = sequence(asBytes(c -> c.putFloat(value)));
    //            final var pos = seq.position();
    //            final var readFloat = seq.readFloat();
    //            if (Float.isNaN(value)) {
    //                assertThat(readFloat).isNaN();
    //            } else {
    //                assertThat(readFloat).isEqualTo(value);
    //            }
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -8.2f,
    // -1.3f, 0, 1.4f, 8.5f, Float.MAX_VALUE, Float.POSITIVE_INFINITY})
    //        @DisplayName("Reading a float in Little Endian")
    //        void readLittleEndian(float value) {
    //            final var seq = sequence(asBytes(c -> c.putFloat(value), LITTLE_ENDIAN));
    //            final var pos = seq.position();
    //            final var readFloat = seq.readFloat(LITTLE_ENDIAN);
    //            if (Float.isNaN(value)) {
    //                assertThat(readFloat).isNaN();
    //            } else {
    //                assertThat(readFloat).isEqualTo(value);
    //            }
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -8.2f,
    // -1.3f, 0, 1.4f, 8.5f, Float.MAX_VALUE, Float.POSITIVE_INFINITY})
    //        @DisplayName("Reading a float in Big Endian")
    //        void readBigEndian(float value) {
    //            final var seq = sequence(asBytes(c -> c.putFloat(value), BIG_ENDIAN));
    //            final var pos = seq.position();
    //            final var readFloat = seq.readFloat(BIG_ENDIAN);
    //            if (Float.isNaN(value)) {
    //                assertThat(readFloat).isNaN();
    //            } else {
    //                assertThat(readFloat).isEqualTo(value);
    //            }
    //            assertThat(seq.position()).isEqualTo(pos + 4);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a mixture of big and little endian data")
    //        void readMixedEndian() {
    //            final var seq = sequence(asBytes(c -> {
    //                c.putFloat(0x01020304);
    //                c.order(LITTLE_ENDIAN);
    //                c.putFloat(0x05060708);
    //                c.order(BIG_ENDIAN);
    //                c.putFloat(0x990A0B0C);
    //                c.order(LITTLE_ENDIAN);
    //                c.putFloat(0x9D0E0F10);
    //            }));
    //            assertThat(seq.readFloat()).isEqualTo(0x01020304);
    //            assertThat(seq.readFloat(LITTLE_ENDIAN)).isEqualTo(0x05060708);
    //            assertThat(seq.readFloat()).isEqualTo(0x990A0B0C);
    //            assertThat(seq.readFloat(LITTLE_ENDIAN)).isEqualTo(0x9D0E0F10);
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("readDouble()")
    //    final class ReadDoubleTest {
    //        @Test
    //        @DisplayName("Reading a double from an empty sequence throws
    // BufferUnderflowException")
    //        void readFromEmptyDataThrows() {
    //            final var seq = emptySequence();
    //            assertThatThrownBy(seq::readDouble).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a double from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows() {
    //            final var seq = fullyUsedSequence();
    //            assertThatThrownBy(seq::readDouble).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a double past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            // When we try to read a double, then we get a BufferUnderflowException
    //            seq.skip(4); // Only 1 byte left, not enough
    //            assertThatThrownBy(seq::readDouble).isInstanceOf(BufferUnderflowException.class);
    //            seq.skip(1); // No bytes left, not enough
    //            assertThatThrownBy(seq::readDouble).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a double when less than 4 bytes are available throws
    // BufferUnderflowException")
    //        void readInsufficientDataThrows() {
    //            for (int i = 0; i < 7; i++) {
    //                final var seq = sequence(new byte[i]);
    //
    // assertThatThrownBy(seq::readDouble).isInstanceOf(BufferUnderflowException.class);
    //            }
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -8.2f,
    // -1.3f, 0, 1.4f, 8.5f, Double.MAX_VALUE, Double.POSITIVE_INFINITY})
    //        @DisplayName("Reading a double")
    //        void read(double value) {
    //            final var seq = sequence(asBytes(c -> c.putDouble(value)));
    //            final var pos = seq.position();
    //            final var readDouble = seq.readDouble();
    //            if (Double.isNaN(value)) {
    //                assertThat(readDouble).isNaN();
    //            } else {
    //                assertThat(readDouble).isEqualTo(value);
    //            }
    //            assertThat(seq.position()).isEqualTo(pos + 8);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -8.2f,
    // -1.3f, 0, 1.4f, 8.5f, Double.MAX_VALUE, Double.POSITIVE_INFINITY})
    //        @DisplayName("Reading a double in Little Endian")
    //        void readLittleEndian(double value) {
    //            final var seq = sequence(asBytes(c -> c.putDouble(value), LITTLE_ENDIAN));
    //            final var pos = seq.position();
    //            final var readDouble = seq.readDouble(LITTLE_ENDIAN);
    //            if (Double.isNaN(value)) {
    //                assertThat(readDouble).isNaN();
    //            } else {
    //                assertThat(readDouble).isEqualTo(value);
    //            }
    //            assertThat(seq.position()).isEqualTo(pos + 8);
    //        }
    //
    //        @ParameterizedTest(name = "value={0}")
    //        @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -8.2f,
    // -1.3f, 0, 1.4f, 8.5f, Double.MAX_VALUE, Double.POSITIVE_INFINITY})
    //        @DisplayName("Reading a double in Big Endian")
    //        void readBigEndian(double value) {
    //            final var seq = sequence(asBytes(c -> c.putDouble(value), BIG_ENDIAN));
    //            final var pos = seq.position();
    //            final var readDouble = seq.readDouble(BIG_ENDIAN);
    //            if (Double.isNaN(value)) {
    //                assertThat(readDouble).isNaN();
    //            } else {
    //                assertThat(readDouble).isEqualTo(value);
    //            }
    //            assertThat(seq.position()).isEqualTo(pos + 8);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a mixture of big and little endian data")
    //        void readMixedEndian() {
    //            final var seq = sequence(asBytes(c -> {
    //                c.putDouble(0x9102030405060708L);
    //                c.order(LITTLE_ENDIAN);
    //                c.putDouble(0x990A0B0C0D0E0F10L);
    //                c.order(BIG_ENDIAN);
    //                c.putDouble(0x1112131415161718L);
    //                c.order(LITTLE_ENDIAN);
    //                c.putDouble(0x191A1B1C1D1E1F20L);
    //            }));
    //            assertThat(seq.readDouble()).isEqualTo(0x9102030405060708L);
    //            assertThat(seq.readDouble(LITTLE_ENDIAN)).isEqualTo(0x990A0B0C0D0E0F10L);
    //            assertThat(seq.readDouble()).isEqualTo(0x1112131415161718L);
    //            assertThat(seq.readDouble(LITTLE_ENDIAN)).isEqualTo(0x191A1B1C1D1E1F20L);
    //        }
    //    }
    //    @Nested
    //    @DisplayName("readVarInt()")
    //    final class ReadVarIntTest {
    //        @ParameterizedTest
    //        @ValueSource(booleans = {false, true})
    //        @DisplayName("Reading a varint from an empty sequence throws
    // BufferUnderflowException")
    //        void readFromEmptyDataThrows(final boolean zigZag) {
    //            final var seq = emptySequence();
    //            assertThatThrownBy(() ->
    // seq.readVarInt(zigZag)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @ParameterizedTest
    //        @ValueSource(booleans = {false, true})
    //        @DisplayName("Reading a varint from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows(final boolean zigZag) {
    //            final var seq = fullyUsedSequence();
    //            assertThatThrownBy(() ->
    // seq.readVarInt(zigZag)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a varint past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            seq.skip(5);
    //            // When we try to read a varint, then we get a BufferUnderflowException
    //            assertThatThrownBy(() ->
    // seq.readVarInt(false)).isInstanceOf(BufferUnderflowException.class);
    //            assertThatThrownBy(() ->
    // seq.readVarInt(true)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @ParameterizedTest
    //        @ValueSource(booleans = {false, true})
    //        @DisplayName("Reading a varint when less than 4 bytes are available throws
    // BufferUnderflowException")
    //        void readInsufficientDataThrows(final boolean zigZag) {
    //            final var seq = sequence(new byte[] { (byte) 0b10101100 });
    //            assertThatThrownBy(() ->
    // seq.readVarLong(zigZag)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a varint")
    //        void read() {
    //            final var seq = sequence(new byte[] { (byte) 0b10101100, 0b00000010 });
    //            final var pos = seq.position();
    //            final var value = seq.readVarInt(false);
    //            assertThat(value).isEqualTo(300);
    //            assertThat(seq.position()).isEqualTo(pos + 2);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a varint with zig zag encoding")
    //        void readZigZag() {
    //            final var seq = sequence(new byte[] { (byte) 0b10101101, 0b00000010 });
    //            final var pos = seq.position();
    //            final var value = seq.readVarInt(true);
    //            assertThat(value).isEqualTo(-151);
    //            assertThat(seq.position()).isEqualTo(pos + 2);
    //        }
    //    }
    //
    //    @Nested
    //    @DisplayName("readVarLong()")
    //    final class ReadVarLongTest {
    //        @ParameterizedTest
    //        @ValueSource(booleans = {false, true})
    //        @DisplayName("Reading a varlong from an empty sequence throws
    // BufferUnderflowException")
    //        void readFromEmptyDataThrows(final boolean zigZag) {
    //            final var seq = emptySequence();
    //            assertThatThrownBy(() ->
    // seq.readVarLong(zigZag)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @ParameterizedTest
    //        @ValueSource(booleans = {false, true})
    //        @DisplayName("Reading a varlong from a full read sequence throws
    // BufferUnderflowException")
    //        void readFromFullyReadDataThrows(final boolean zigZag) {
    //            final var seq = fullyUsedSequence();
    //            assertThatThrownBy(() ->
    // seq.readVarLong(zigZag)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Reading a varlong past the limit throws BufferUnderflowException")
    //        void readPastLimit() {
    //            // Given a sequence of bytes with a limit where position == limit
    //            final var seq = sequence(TEST_BYTES);
    //            seq.limit(5);
    //            seq.skip(5);
    //            // When we try to read a varlong, then we get a BufferUnderflowException
    //            assertThatThrownBy(() ->
    // seq.readVarLong(false)).isInstanceOf(BufferUnderflowException.class);
    //            assertThatThrownBy(() ->
    // seq.readVarLong(true)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @ParameterizedTest
    //        @ValueSource(booleans = {false, true})
    //        @DisplayName("Reading a varlong when less than 4 bytes are available throws
    // BufferUnderflowException")
    //        void readInsufficientDataThrows(final boolean zigZag) {
    //            final var seq = sequence(new byte[] { (byte) 0b10101100 });
    //            assertThatThrownBy(() ->
    // seq.readVarLong(zigZag)).isInstanceOf(BufferUnderflowException.class);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a varlong")
    //        void read() {
    //            final var seq = sequence(new byte[] { (byte) 0b10101100, 0b00000010 });
    //            final var pos = seq.position();
    //            final var value = seq.readVarLong(false);
    //            assertThat(value).isEqualTo(300);
    //            assertThat(seq.position()).isEqualTo(pos + 2);
    //        }
    //
    //        @Test
    //        @DisplayName("Read a varlong with zig zag encoding")
    //        void readZigZag() {
    //            final var seq = sequence(new byte[] { (byte) 0b10101101, 0b00000010 });
    //            final var pos = seq.position();
    //            final var value = seq.readVarLong(true);
    //            assertThat(value).isEqualTo(-151);
    //            assertThat(seq.position()).isEqualTo(pos + 2);
    //        }
    //    }
}
