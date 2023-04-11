package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

public class RandomAccessDataTest extends ReadableTestBase {

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

    @ParameterizedTest
    @ValueSource(strings = { "", "a", "ab", "abc", "✅" })
    void utf8Strings(final String s) {
        final var buf = new StubbedRandomAccessData(s.getBytes(StandardCharsets.UTF_8));
        assertThat(buf.asUtf8String()).isEqualTo(s);
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
