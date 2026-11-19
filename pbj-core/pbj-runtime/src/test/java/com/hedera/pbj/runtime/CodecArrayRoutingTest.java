// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.ArrayCursor;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every {@code parse} entry point sends array-backed input to the array parser of a codec that has one: an
 * array-backed {@link BufferedData}, a {@link Bytes}, and the sequential view {@link Bytes#toReadableSequentialData()}
 * returns, which is how Hiero usually parses {@link Bytes}.
 */
final class CodecArrayRoutingTest {

    /** Reads one-byte varints to the end of its input, and records which parser read them and with what arguments. */
    private static final class RecordingCodec extends Codec<List<Long>> {
        int arrayParses;
        int streamParses;
        boolean lastStrictMode;
        boolean lastParseUnknownFields;
        int lastMaxDepth;
        int lastMaxSize;

        @Override
        protected boolean hasArrayParser() {
            return true;
        }

        @Override
        protected List<Long> parseArrayImpl(
                @NonNull final ArrayCursor input,
                final boolean strictMode,
                final boolean parseUnknownFields,
                final int maxDepth,
                final int maxSize) {
            arrayParses++;
            record(strictMode, parseUnknownFields, maxDepth, maxSize);
            final List<Long> values = new ArrayList<>();
            while (input.hasRemaining()) {
                values.add(input.varint());
            }
            return values;
        }

        @NonNull
        @Override
        protected List<Long> parseImpl(
                @NonNull final ReadableSequentialData input,
                final boolean strictMode,
                final boolean parseUnknownFields,
                final int maxDepth,
                final int maxSize) {
            streamParses++;
            record(strictMode, parseUnknownFields, maxDepth, maxSize);
            final List<Long> values = new ArrayList<>();
            while (input.hasRemaining()) {
                values.add(input.readVarLong(false));
            }
            return values;
        }

        private void record(
                final boolean strictMode, final boolean parseUnknownFields, final int maxDepth, final int maxSize) {
            lastStrictMode = strictMode;
            lastParseUnknownFields = parseUnknownFields;
            lastMaxDepth = maxDepth;
            lastMaxSize = maxSize;
        }

        @Override
        protected void writeImpl(@NonNull final List<Long> item, @NonNull final WritableSequentialData output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int measureRecord(final List<Long> item) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> getDefaultInstance() {
            return List.of();
        }
    }

    private static final Bytes VALUES = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});

    @Test
    void bytesEntryPointsUseTheArrayParser() throws Exception {
        final RecordingCodec codec = new RecordingCodec();
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), codec.parse(VALUES));
        codec.parseStrict(VALUES);
        codec.parse(VALUES, false, 7);
        codec.parse(VALUES, true, true, 9, 1000);
        assertEquals(4, codec.arrayParses);
        assertEquals(0, codec.streamParses);
        // the full overload passes every argument through
        assertTrue(codec.lastStrictMode);
        assertTrue(codec.lastParseUnknownFields);
        assertEquals(9, codec.lastMaxDepth);
        assertEquals(1000, codec.lastMaxSize);
    }

    @Test
    void theSequentialViewOfBytesUsesTheArrayParser() throws Exception {
        final RecordingCodec codec = new RecordingCodec();
        final ReadableSequentialData input = VALUES.toReadableSequentialData();
        assertEquals(
                List.of(1L, 2L, 3L, 4L, 5L),
                codec.parse(input, false, true, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE));
        assertEquals(1, codec.arrayParses);
        assertEquals(0, codec.streamParses);
        assertTrue(codec.lastParseUnknownFields);
        // the input is consumed, as the stream parser would leave it
        assertEquals(5, input.position());
        assertFalse(input.hasRemaining());
    }

    @Test
    void theSequentialViewIsReadFromItsPositionToItsLimit() throws Exception {
        final RecordingCodec codec = new RecordingCodec();
        // a slice of a larger array, read from position 1 to limit 4 of the view
        final Bytes slice = Bytes.wrap(new byte[] {9, 9, 1, 2, 3, 4, 5, 9}).slice(2, 5);
        final ReadableSequentialData input = slice.toReadableSequentialData();
        input.skip(1);
        input.limit(4);
        assertEquals(List.of(2L, 3L, 4L), codec.parse(input));
        assertEquals(1, codec.arrayParses);
        assertEquals(4, input.position());
        assertEquals(4, input.limit());
    }

    @Test
    void arrayBackedBufferedDataUsesTheArrayParser() throws Exception {
        final RecordingCodec codec = new RecordingCodec();
        final BufferedData input = BufferedData.wrap(VALUES.toByteArray());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), codec.parse(input));
        assertEquals(1, codec.arrayParses);
        assertEquals(5, input.position());
    }

    @Test
    void otherInputsUseTheStreamParser() throws Exception {
        final RecordingCodec codec = new RecordingCodec();
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), codec.parse(new ReadableStreamingData(VALUES.toByteArray())));
        assertEquals(0, codec.arrayParses);
        assertEquals(1, codec.streamParses);
    }
}
