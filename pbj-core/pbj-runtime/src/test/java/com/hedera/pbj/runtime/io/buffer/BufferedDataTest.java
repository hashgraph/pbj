package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class BufferedDataTest {
    // TODO Test that "view" shows the updated data when it is changed on the fly
    // TODO Verify capacity is never negative (it can't be, maybe nothing to test here)

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
}
