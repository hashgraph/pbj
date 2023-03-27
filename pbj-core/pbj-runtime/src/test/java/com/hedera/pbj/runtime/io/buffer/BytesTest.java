package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;

public class BytesTest extends ReadableTestBase {

    @NonNull
    @Override
    protected ReadableSequentialData emptySequence() {
        return new RandomAccessSequenceAdapter(Bytes.EMPTY);
    }

    @NonNull
    @Override
    protected ReadableSequentialData fullyUsedSequence() {
        final var buf = Bytes.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).toReadableSequentialData();
        buf.skip(10);
        return buf;
    }

    @NonNull
    @Override
    protected ReadableSequentialData sequence(@NonNull final byte[] arr) {
        return Bytes.wrap(arr).toReadableSequentialData();
    }
}
