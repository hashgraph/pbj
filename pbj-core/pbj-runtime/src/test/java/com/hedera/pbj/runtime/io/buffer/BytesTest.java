package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

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

    @ParameterizedTest
    @ValueSource(strings = { "", "a", "ab", "abc", "✅" })
    void utf8Strings(final String s) {
        final var buf = Bytes.wrap(s.getBytes(StandardCharsets.UTF_8));
        assertThat(buf.asUtf8String()).isEqualTo(s);
    }
}
