package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;

public class RandomAccessDataTest extends ReadableTestBase {

    @NonNull
    @Override
    protected ReadableSequentialData emptySequence() {
        return new RandomAccessSequenceAdapter(new StubbedRandomAccessData(new byte[0]));
    }

    @NonNull
    @Override
    protected ReadableSequentialData fullyReadSequence() {
        final var buf = new RandomAccessSequenceAdapter(new StubbedRandomAccessData(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }));
        buf.skip(10);
        return buf;
    }

    @NonNull
    @Override
    protected ReadableSequentialData sequence(@NonNull final byte[] arr) {
        return new RandomAccessSequenceAdapter(new StubbedRandomAccessData(arr));
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
        }
}
