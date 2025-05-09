// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.WritableTestBase;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void defaultCapacity() throws IOException {
        // Given a sequence
        try (final var seq = sequence()) {
            // When we ask for the capacity, then we get Long.MAX_VALUE
            assertThat(seq.capacity()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Test
    @DisplayName("capacity() returns the capacity specified in the constructor")
    void specifiedCapacity() throws IOException {
        // Given a sequence
        try (final var seq = new WritableStreamingData(new ByteArrayOutputStream(), 10)) {
            // When we ask for the capacity, then we get 10
            assertThat(seq.capacity()).isEqualTo(10);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, 1024, 1025, 2048, 3000})
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
        assertThatThrownBy(() -> seq.skip(1)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("Writing to a closed stream throws DataAccessException")
    void closed() throws IOException {
        // Given a sequence
        final var stream = mock(OutputStream.class);
        final var seq = new WritableStreamingData(stream);
        doThrow(IOException.class).when(stream).write(any(), anyInt(), anyInt());
        final var src = new ByteArrayInputStream("Gonna Throw".getBytes(StandardCharsets.UTF_8));
        // When we try to write some bytes, then we get an exception because the stream throws IOException
        assertThatThrownBy(() -> seq.writeBytes(src, 5)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("Calling flush() is delegated to the OutputStream")
    void testFlushable() throws IOException {
        final OutputStream out = mock(OutputStream.class);
        doNothing().when(out).flush();

        final WritableStreamingData seq = new WritableStreamingData(out);

        seq.flush();

        verify(out, times(1)).flush();
        verifyNoMoreInteractions(out);
    }

    @Test
    @DisplayName("writeBytes(RandomAccessData) should delegate to RandomAccessData.writeTo(OutputStream)")
    void testWriteBytesFastPath() {
        final OutputStream out = mock(OutputStream.class);
        final RandomAccessData data = mock(RandomAccessData.class);
        doReturn(10L).when(data).length();
        doNothing().when(data).writeTo(out);

        final WritableStreamingData seq = new WritableStreamingData(out);

        seq.writeBytes(data);

        verify(data, times(1)).length();
        verify(data, times(1)).writeTo(out);
        verifyNoMoreInteractions(data, out);

        assertEquals(10L, seq.position());
    }
}
