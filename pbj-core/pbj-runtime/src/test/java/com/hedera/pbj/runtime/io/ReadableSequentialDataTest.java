// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.pbj.runtime.io.stream.EOFException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ReadableSequentialDataTest extends ReadableSequentialTestBase {

    @NonNull
    @Override
    protected ReadableSequentialData emptySequence() {
        return new StubbedSequence(new byte[0]);
    }

    @NonNull
    private ReadableSequentialData throwingSequence() {
        return new StubbedSequence(new byte[] {1}, () -> new EOFException());
    }

    @Test
    @DisplayName("Stream with readByte() throwing an exception")
    void throwingSequenceTest() {
        final var stream = throwingSequence();
        assertThat(stream.hasRemaining()).isTrue();
        final var bytes = new byte[4];
        final long bytesRead = stream.readBytes(bytes);
        assertThat(bytesRead).isEqualTo(0);
        assertThat(stream.position()).isEqualTo(0);
        assertThat(bytes).containsExactly(0, 0, 0, 0);
        assertThat(stream.hasRemaining()).isTrue();
    }

    @Test
    @DisplayName("Verify asInputStream()")
    void testAsInputStream() throws IOException {
        ReadableSequentialData sequence = sequence(new byte[] {1, 2, 3, (byte) 254, (byte) 255});
        InputStream inputStream = sequence.asInputStream();

        assertThat(inputStream.read()).isEqualTo(1);
        assertThat(inputStream.read()).isEqualTo(2);
        assertThat(inputStream.read()).isEqualTo(3);
        // NOTE: do NOT convert to byte. The returned integer value must be in 0..255.
        // Converting to byte would interpret both literal 255 and -1 as (byte) 255,
        // which is wrong because -1 means something else in InputStream.read().
        assertThat(inputStream.read()).isEqualTo(254);
        assertThat(inputStream.read()).isEqualTo(255);
        // Now we're at the end:
        assertThat(inputStream.read()).isEqualTo(-1);
        assertThat(inputStream.read()).isEqualTo(-1);
    }

    @NonNull
    @Override
    protected ReadableSequentialData fullyUsedSequence() {
        final var seq = new StubbedSequence(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
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

        private final Supplier<? extends RuntimeException> unconditionalExceptionSupplier;

        private StubbedSequence(
                @NonNull final byte[] bytes,
                @NonNull final Supplier<? extends RuntimeException> unconditionalExceptionSupplier) {
            this.bytes = bytes;
            this.limit = this.bytes.length;
            this.unconditionalExceptionSupplier = unconditionalExceptionSupplier;
        }

        private StubbedSequence(@NonNull final byte[] bytes) {
            this(bytes, null);
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
        public void skip(long count) {
            if (count > limit - position) {
                throw new BufferUnderflowException();
            }
            if (count <= 0) {
                return;
            }
            position += count;
        }

        @Override
        public byte readByte() {
            if (unconditionalExceptionSupplier != null) {
                throw unconditionalExceptionSupplier.get();
            }
            if (position >= limit) {
                throw new BufferUnderflowException();
            }

            return bytes[(int) position++];
        }
    }
}
