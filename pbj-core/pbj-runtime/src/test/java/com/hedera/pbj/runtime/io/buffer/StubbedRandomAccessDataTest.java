package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

public class StubbedRandomAccessDataTest extends RandomAccessTestBase {

    @NonNull
    @Override
    protected ReadableSequentialData emptySequence() {
        return new RandomAccessSequenceAdapter(new StubbedRandomAccessData(new byte[0]));
    }

    @NonNull
    @Override
    protected ReadableSequentialData fullyUsedSequence() {
        final var buf = new RandomAccessSequenceAdapter(new StubbedRandomAccessData(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }));
        buf.skip(10);
        return buf;
    }

    @NonNull
    @Override
    protected ReadableSequentialData sequence(@NonNull final byte[] arr) {
        return new RandomAccessSequenceAdapter(new StubbedRandomAccessData(arr));
    }

    @NonNull
    @Override
    protected RandomAccessData randomAccessData(@NonNull byte[] bytes) {
        return new StubbedRandomAccessData(bytes);
    }

    private record StubbedRandomAccessData(@NonNull byte[] bytes) implements RandomAccessData {
        @Override
        public long length() {
            return bytes.length;
        }

        @Override
        public byte getByte(long offset) {
            return bytes[Math.toIntExact(offset)];
        }

        @Override
        public void writeTo(@NonNull OutputStream outStream) {
            try {
                outStream.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeTo(@NonNull OutputStream outStream, int offset, int length) {
            try {
                outStream.write(bytes, offset, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
