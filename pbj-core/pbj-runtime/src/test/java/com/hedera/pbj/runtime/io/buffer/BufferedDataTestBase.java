// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableSequentialTestBase;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.WritableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class BufferedDataTestBase {

    // FUTURE Test that "view" shows the updated data when it is changed on the fly
    // FUTURE Verify capacity is never negative (it can't be, maybe nothing to test here)

    @NonNull
    protected abstract BufferedData allocate(final int size);

    @NonNull
    protected abstract BufferedData wrap(final byte[] arr);

    @NonNull
    protected abstract BufferedData wrap(final byte[] arr, final int offset, final int len);

    @Test
    @DisplayName("allocated data length test")
    void allocateLength() {
        final var buf = allocate(12);
        assertThat(buf.length()).isEqualTo(12);
    }

    @Test
    @DisplayName("wrapped data length test")
    void wrapLength() {
        final var buf = wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        assertThat(buf.position()).isEqualTo(0);
        assertThat(buf.length()).isEqualTo(8);
    }

    @Test
    @DisplayName("wrapped data with offset length test")
    void wrapOffsetLength() {
        final var buf = wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7}, 1, 3);
        assertThat(buf.length()).isEqualTo(4);
    }

    @Test
    @DisplayName("wrapped sliced data length test")
    void wrapSliceLength() {
        final var buf = wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7}).slice(1, 3);
        assertThat(buf.length()).isEqualTo(3);
    }

    @Test
    @DisplayName("toString() is safe")
    void toStringIsSafe() {
        final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        buf.skip(5);
        buf.limit(10);

        assertThat(buf.toString()).endsWith(buf.getClass().getSimpleName()+"[1,2,3,4,5,6,7,8,9,10]");

        assertEquals(5, buf.position());
        assertEquals(10, buf.limit());
    }

    @Test
    void toStringWithOffsetAndLen() {
        final var buf = wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, 2, 4);
        // toString() doesn't depend on position, but respects limit
        assertThat(buf.toString()).endsWith(buf.getClass().getSimpleName()+"[0,1,2,3,4,5]");
        buf.limit(10);
        assertThat(buf.toString()).endsWith(buf.getClass().getSimpleName()+"[0,1,2,3,4,5,6,7,8,9]");
    }

    @Test
    void toStringWithSlice() {
        final var buf = wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}).slice(2, 4);
        assertThat(buf.toString()).endsWith(buf.getClass().getSimpleName()+"[2,3,4,5]");
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                0,
                1,
                2,
                4,
                7,
                8,
                15,
                16,
                31,
                32,
                33,
                127,
                128,
                512,
                1000,
                1024,
                4000,
                16384,
                65535,
                65536,
                65537,
                0xFFFFFF,
                0x1000000,
                0x1000001,
                0x7FFFFFFF,
                -1,
                -7,
                -8,
                -9,
                -127,
                -128,
                -129,
                -65535,
                -65536,
                -0xFFFFFF,
                -0x1000000,
                -0x1000001,
                -0x80000000
            })
    @DisplayName("readVarInt() works with views")
    void sliceThenReadVarInt(final int num) {
        final var buf = allocate(100);
        buf.writeVarInt(num, false);
        final long afterFirstIntPos = buf.position();
        buf.writeVarInt(num + 1, false);
        final long afterSecondIntPos = buf.position();

        final var slicedBuf = buf.slice(afterFirstIntPos, afterSecondIntPos - afterFirstIntPos);
        final int readback = slicedBuf.readVarInt(false);
        assertEquals(num + 1, readback);
        assertEquals(afterSecondIntPos - afterFirstIntPos, slicedBuf.position());
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                0,
                1,
                2,
                4,
                7,
                8,
                15,
                16,
                31,
                32,
                33,
                127,
                128,
                512,
                1000,
                1024,
                4000,
                16384,
                65535,
                65536,
                65537,
                0xFFFFFF,
                0x1000000,
                0x1000001,
                0x7FFFFFFF,
                -1,
                -7,
                -8,
                -9,
                -127,
                -128,
                -129,
                -65535,
                -65536,
                -0xFFFFFF,
                -0x1000000,
                -0x1000001,
                -0x80000000
            })
    @DisplayName("readVar() won't read beyond 10 bytes")
    void readVarFromLargeBuffer(final int num) {
        final var buf = allocate(100);
        buf.writeVarInt(num, false);
        final long afterPos = buf.position();

        buf.reset();
        final int readback = buf.readVarInt(false);
        assertEquals(num, readback);
        assertEquals(afterPos, buf.position());
    }

    @ParameterizedTest
    @ValueSource(
            longs = {
                0,
                1,
                7,
                8,
                9,
                127,
                128,
                129,
                1023,
                1024,
                1025,
                65534,
                65535,
                65536,
                0xFFFFFFFFL,
                0x100000000L,
                0x100000001L,
                0xFFFFFFFFFFFFL,
                0x1000000000000L,
                0x1000000000001L,
                -1,
                -7,
                -8,
                -9,
                -127,
                -128,
                -129,
                -65534,
                -65535,
                -65536,
                -0xFFFFFFFFL,
                -0x100000000L,
                -0x100000001L
            })
    @DisplayName("readVarLong() works with views")
    void sliceThenReadVarLong(final long num) {
        final var buf = allocate(256);
        buf.writeVarLong(num, false);
        final long afterFirstIntPos = buf.position();
        buf.writeVarLong(num + 1, false);
        final long afterSecondIntPos = buf.position();

        final var slicedBuf = buf.slice(afterFirstIntPos, afterSecondIntPos - afterFirstIntPos);
        final long readback = slicedBuf.readVarLong(false);
        assertEquals(num + 1, readback);
        assertEquals(afterSecondIntPos - afterFirstIntPos, slicedBuf.position());
    }

    @Test
    @DisplayName("readBytes() works with views")
    void sliceThenReadBytes() {
        final int SIZE = 100;
        final var buf = allocate(SIZE);
        for (int i = 0; i < SIZE; i++) {
            buf.writeByte((byte) i);
        }

        final int START = 10;
        final int LEN = 10;
        buf.reset();
        buf.skip(START);
        final var bytes = buf.readBytes(LEN);
        assertEquals(START + LEN, buf.position());
        for (int i = START; i < START + LEN; i++) {
            assertEquals((byte) i, bytes.getByte(i - START));
        }

        final var slicedBuf = buf.slice(START, LEN);
        final var slicedBytes = slicedBuf.readBytes(LEN);
        assertEquals(LEN, slicedBuf.position());
        for (int i = 0; i < LEN; i++) {
            assertEquals((byte) (i + START), slicedBytes.getByte(i));
        }
    }

    @Test
    @DisplayName("readBytes() does always copy")
    void readBytesCopies() {
        byte[] bytes = new byte[] {1, 2, 3, 4, 5};
        byte[] bytesOrig = bytes.clone();
        final var buf = wrap(bytes);
        final Bytes readBytes = buf.readBytes(5);
        assertArrayEquals(bytesOrig, readBytes.toByteArray());
        bytes[0] = 127;
        bytes[1] = 127;
        bytes[2] = 127;
        bytes[3] = 127;
        bytes[4] = 127;
        assertArrayEquals(bytesOrig, readBytes.toByteArray());
    }

    @Test
    void getBytesLeavesPositionBufferedData() {
        final var buf = wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        final var dst = BufferedData.allocate(4);
        buf.getBytes(1, dst);
        assertThat(buf.position()).isEqualTo(0);
        assertThat(dst.position()).isEqualTo(0);
    }

    @Test
    void getBytesLeavesPositionByteBuffer() {
        final var buf = wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        final var dst = ByteBuffer.allocate(4);
        buf.getBytes(1, dst);
        assertThat(buf.position()).isEqualTo(0);
        assertThat(dst.position()).isEqualTo(0);
    }

    @Nested
    final class ReadableSequentialDataTest extends ReadableSequentialTestBase {

        @NonNull
        @Override
        protected ReadableSequentialData emptySequence() {
            return BufferedData.EMPTY_BUFFER;
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.skip(10);
            return buf;
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            return wrap(arr);
        }
    }

    @Nested
    final class RandomAccessDataTest extends RandomAccessTestBase {

        @NonNull
        @Override
        protected ReadableSequentialData emptySequence() {
            return new RandomAccessSequenceAdapter(BufferedData.EMPTY_BUFFER);
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.skip(10);
            return new RandomAccessSequenceAdapter(buf, 10);
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            return new RandomAccessSequenceAdapter(wrap(arr));
        }

        @NonNull
        @Override
        protected RandomAccessData randomAccessData(@NonNull byte[] arr) {
            return wrap(arr);
        }
    }

    @Nested
    final class WritableSequentialDataTest extends WritableTestBase {
        @NonNull
        @Override
        protected WritableSequentialData sequence() {
            return allocate(1024 * 1024 * 2); // the largest expected test value is 1024 * 1024
        }

        @NonNull
        @Override
        protected WritableSequentialData eofSequence() {
            return wrap(new byte[0]);
        }

        @NonNull
        @Override
        protected byte[] extractWrittenBytes(@NonNull WritableSequentialData seq) {
            final var buf = (BufferedData) seq;
            final var bytes = new byte[Math.toIntExact(buf.position())];
            buf.getBytes(0, bytes);
            return bytes;
        }
    }

    @Nested
    final class ReadableSequentialDataViewTest extends ReadableTestBase {

        @NonNull
        @Override
        protected ReadableSequentialData emptySequence() {
            final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.position(7);
            return buf.view(0);
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.position(2);
            final var view = buf.view(5);
            view.skip(5);
            return view;
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            BufferedData buf = allocate(arr.length + 20);
            for (int i = 0; i < 10; i++) {
                buf.writeByte((byte) i);
            }
            buf.position(buf.length() - 10);
            for (int i = 0; i < 10; i++) {
                buf.writeByte((byte) i);
            }
            buf.position(10);
            buf.writeBytes(arr);
            buf.position(10);
            return buf.view(arr.length);
        }
    }

    @Nested
    final class RandomAccessDataViewTest extends ReadableTestBase {

        @NonNull
        @Override
        protected ReadableSequentialData emptySequence() {
            final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.position(7);
            return new RandomAccessSequenceAdapter(buf.view(0));
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.position(2);
            final var view = buf.view(5);
            view.skip(5);
            return new RandomAccessSequenceAdapter(view, 5);
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            BufferedData buf = allocate(arr.length + 20);
            for (int i = 0; i < 10; i++) {
                buf.writeByte((byte) i);
            }
            buf.position(buf.length() - 10);
            for (int i = 0; i < 10; i++) {
                buf.writeByte((byte) i);
            }
            buf.position(10);
            buf.writeBytes(arr);
            buf.position(10);
            return new RandomAccessSequenceAdapter(buf.view(arr.length));
        }
    }

    @Nested
    final class WritableSequentialDataViewTest extends WritableTestBase {
        @NonNull
        @Override
        protected WritableSequentialData sequence() {
            final var mb = 1024 * 1024;
            final var buf = allocate(mb * 4); // the largest expected test value is 1 mb
            buf.position(mb);
            return buf.view(mb * 2);
        }

        @NonNull
        @Override
        protected WritableSequentialData eofSequence() {
            final var buf = wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.position(7);
            return buf.view(0);
        }

        @NonNull
        @Override
        protected byte[] extractWrittenBytes(@NonNull WritableSequentialData seq) {
            final var buf = (BufferedData) seq;
            final var bytes = new byte[Math.toIntExact(buf.position())];
            buf.getBytes(0, bytes);
            return bytes;
        }
    }
}
