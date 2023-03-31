package com.hedera.pbj.runtime.io;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferUnderflowException;

final class ReadableSequentialDataTest extends ReadableTestBase {
    @NonNull
    @Override
    protected ReadableSequentialData emptySequence() {
        return new StubbedSequence(new byte[0]);
    }

    @NonNull
    @Override
    protected ReadableSequentialData fullyUsedSequence() {
        final var seq = new StubbedSequence(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        seq.skip(10);
        return seq;
    }

    @NonNull
    @Override
    protected ReadableSequentialData sequence(@NonNull byte[] arr) {
        return new StubbedSequence(arr);
    }

    private static final class StubbedSequence implements ReadableSequentialData {
        private final byte[] bytes;
        private long position = 0;
        private long limit;

        private StubbedSequence(@NonNull final byte[] bytes) {
            this.bytes = bytes;
            this.limit = this.bytes.length;
        }


        @Override
        public long capacity() {
            return bytes.length;
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
        public void limit(long limit) {
            this.limit = Math.min(Math.max(position, limit), capacity());
        }

        @Override
        public long skip(long count) {
            final long numToSkip = Math.max(Math.min(count, limit - position), 0);
            position += numToSkip;
            return numToSkip;
        }

        @Override
        public byte readByte() {
            if (position >= limit) {
                throw new BufferUnderflowException();
            }

            return bytes[(int) position++];
        }

    }
}
