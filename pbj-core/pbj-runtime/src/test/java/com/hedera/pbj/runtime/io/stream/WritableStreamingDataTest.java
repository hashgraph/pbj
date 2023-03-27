package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.WritableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WritableStreamingDataTest extends WritableTestBase {
    private ByteArrayOutputStream out;

    @NonNull
    @Override
    protected WritableStreamingData sequence() {
        return new WritableStreamingData(out = new ByteArrayOutputStream());
    }

    @NonNull
    @Override
    protected WritableStreamingData eofSequence() {
        final var sequence = new WritableStreamingData(out = new ByteArrayOutputStream(), 10);
        sequence.writeBytes("0123456789".getBytes(StandardCharsets.UTF_8));
        return sequence;
    }

    @NonNull
    @Override
    protected byte[] extractWrittenBytes(@NonNull WritableSequentialData seq) {
        return out.toByteArray();
    }

    @Test
    @DisplayName("capacity() returns Long.MAX_VALUE by default")
    void defaultCapacity() throws Exception {
        // Given a sequence
        try (final var seq = sequence()) {
            // When we ask for the capacity, then we get Long.MAX_VALUE
            assertThat(seq.capacity()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Test
    @DisplayName("capacity() returns the capacity specified in the constructor")
    void specifiedCapacity() throws Exception {
        // Given a sequence
        try (final var seq = new WritableStreamingData(new ByteArrayOutputStream(), 10)) {
            // When we ask for the capacity, then we get 10
            assertThat(seq.capacity()).isEqualTo(10);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0, 2, 1024, 1025, 2048, 3000 })
    @DisplayName("Skip inserts empty bytes into the output stream")
    void skip(final int numBytesToSkip) {
        // Given a sequence
        final var stream = new ByteArrayOutputStream();
        final var seq = new WritableStreamingData(stream);
        // When we skip 10 bytes
        seq.skip(numBytesToSkip);
        // Then the output stream has 10 empty bytes
        final var expected = new byte[Math.max(numBytesToSkip, 0)];
        assertThat(stream.toByteArray()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Skipping with a closed stream throws DataAccessException")
    void skipClosed() throws IOException {
        // Given a sequence
        final var stream = mock(OutputStream.class);
        final var seq = new WritableStreamingData(stream);
        doThrow(IOException.class).when(stream).write(any(), anyInt(), anyInt());
        // When we try to skip some bytes, then we get an exception because the stream throws IOException
        assertThatThrownBy(() -> seq.skip(1)).isInstanceOf(DataAccessException.class);
    }
}
