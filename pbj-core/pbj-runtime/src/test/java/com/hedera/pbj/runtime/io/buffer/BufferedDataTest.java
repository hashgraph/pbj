package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.WritableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BufferedDataTest {
    // FUTURE Test that "view" shows the updated data when it is changed on the fly
    // FUTURE Verify capacity is never negative (it can't be, maybe nothing to test here)

    @Test
    @DisplayName("toString() is safe")
    void toStringIsSafe() {
        final var buf = BufferedData.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        buf.skip(5);
        buf.limit(10);

        final var ignored = buf.toString();

        assertEquals(5, buf.position());
        assertEquals(10, buf.limit());
    }

    @Nested
    final class ReadableSequentialDataTest extends ReadableTestBase {

        @NonNull
        @Override
        protected ReadableSequentialData emptySequence() {
            return BufferedData.EMPTY_BUFFER;
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = BufferedData.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
            buf.skip(10);
            return buf;
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            return BufferedData.wrap(arr);
        }
    }

    @Nested
    final class RandomAccessDataTest extends ReadableTestBase {

        @NonNull
        @Override
        protected ReadableSequentialData emptySequence() {
            return new RandomAccessSequenceAdapter(BufferedData.EMPTY_BUFFER);
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            buf.skip(10);
            return new RandomAccessSequenceAdapter(buf, 10);
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            return new RandomAccessSequenceAdapter(BufferedData.wrap(arr));
        }
    }

    @Nested
    final class WritableSequentialDataTest extends WritableTestBase {
        @NonNull
        @Override
        protected WritableSequentialData sequence() {
            return BufferedData.allocate(1024 * 1024 * 2); // the largest expected test value is 1024 * 1024
        }

        @NonNull
        @Override
        protected WritableSequentialData eofSequence() {
            return BufferedData.wrap(new byte[0]);
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
            final var buf = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            buf.position(7);
            return buf.view(0);
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            buf.position(2);
            final var view = buf.view(5);
            view.skip(5);
            return view;
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            BufferedData buf = BufferedData.allocate(arr.length + 20);
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
            final var buf = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            buf.position(7);
            return new RandomAccessSequenceAdapter(buf.view(0));
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            final var buf = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            buf.position(2);
            final var view = buf.view(5);
            view.skip(5);
            return new RandomAccessSequenceAdapter(view, 5);
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            BufferedData buf = BufferedData.allocate(arr.length + 20);
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
            final var buf = BufferedData.allocate(mb * 4); // the largest expected test value is 1 mb
            buf.position(mb);
            return buf.view(mb * 2);
        }

        @NonNull
        @Override
        protected WritableSequentialData eofSequence() {
            final var buf = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
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
