package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Base test class for testing {@link WritableSequentialData}.
 *
 * <p> I will implement this test in terms of a {@link WritableSequentialData}, which will apply to
 * {@link WritableStreamingData} and {@link BufferedData}.
 */
public abstract class WritableTestBase extends SequentialTestBase {

    @Override
    @NonNull
    protected abstract WritableSequentialData sequence();

    @Override
    @NonNull
    protected abstract WritableSequentialData eofSequence();

    @NonNull
    protected abstract byte[] extractWrittenBytes(@NonNull WritableSequentialData seq);

    @Nested
    @DisplayName("writeByte()")
    final class WriteByteTest {
        @Test
        @DisplayName("Writing a byte to a full writable sequence throws BufferOverflowException")
        void writeToEofDataThrows() {
            // Given an eof sequence
            final var seq = eofSequence();
            // When we try to write a byte, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeByte((byte) 1)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a byte past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            seq.skip(5);
            // When we try to write a byte, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeByte((byte) 1)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Bytes written end up in the sequence correctly")
        void write() {
            // Given a sequence
            final var seq = sequence();
            final var bytes = "Hello, world!".getBytes(StandardCharsets.UTF_8);
            // When we write each byte, then the position is updated
            for (byte testByte : bytes) {
                final var pos = seq.position();
                assertThat(seq.hasRemaining()).isTrue();
                seq.writeByte(testByte);
                assertThat(seq.position()).isEqualTo(pos + 1);
            }
            // And when we get to the end, all the bytes were received by the underlying stream
            assertThat(extractWrittenBytes(seq)).isEqualTo(bytes);
        }
    }

    @Nested
    @DisplayName("writeUnsignedByte()")
    final class WriteUnsignedByteTest {
        @Test
        @DisplayName("Writing an unsigned byte from an eof sequence throws BufferOverflowException")
        void writeToEofDataThrows() {
            // Given an eof sequence
            final var seq = eofSequence();
            // When we try to write an unsigned byte, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeUnsignedByte(0b1101_0011)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing an unsigned byte past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            seq.skip(5);
            // When we try to read an unsigned byte, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeUnsignedByte(0b1101_0011)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing an unsigned byte")
        void write() {
            // Given a sequence
            final var seq = sequence();
            // When we write the byte (with a single byte that could be interpreted as negative if signed),
            final var pos = seq.position();
            seq.writeUnsignedByte(0b1110_0011);
            // then the position forward by a single byte
            assertThat(seq.position()).isEqualTo(pos + 1);
            // and the byte was written unmodified
            final var expected = new byte[] { (byte) 0b1110_0011 };
            assertThat(extractWrittenBytes(seq)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("writeBytes()")
    final class WriteBytesTest {
        @Test
        @DisplayName("Writing bytes with a null src throws NullPointerException")
        void readNullSrcThrows() {
            // Given a sequence
            final var seq = sequence();

            // When we try to write bytes using a null byte array, then we get a NullPointerException
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> seq.writeBytes((byte[]) null)).isInstanceOf(NullPointerException.class);
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> seq.writeBytes(null, 0, 10)).isInstanceOf(NullPointerException.class);

            // When we try to write bytes using a null ByteBuffer, then we get a NullPointerException
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> seq.writeBytes((ByteBuffer) null)).isInstanceOf(NullPointerException.class);

            // When we try to write bytes using a null BufferedData, then we get a NullPointerException
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> seq.writeBytes((BufferedData) null)).isInstanceOf(NullPointerException.class);

            // When we try to write bytes using a null RandomAccessData, then we get a NullPointerException
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> seq.writeBytes((RandomAccessData) null)).isInstanceOf(NullPointerException.class);

            // When we try to write bytes using a null InputStream, then we get a NullPointerException
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> seq.writeBytes(null, 10)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Writing bytes with a negative offset throws IndexOutOfBoundsException")
        void negativeOffsetThrows() {
            // Given a sequence
            final var seq = sequence();
            // When we try to write bytes using a byte array with a negative offset, then we get an IndexOutOfBoundsException
            assertThatThrownBy(() -> seq.writeBytes(new byte[10], -1, 10)).isInstanceOf(IndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("Writing bytes with an offset that is too large throws IndexOutOfBoundsException")
        void tooLargeOffsetThrows() {
            // Given a sequence
            final var seq = sequence();
            // When we try to write bytes using a byte array with an offset that is too large,
            // then we get an IndexOutOfBoundsException
            assertThatThrownBy(() -> seq.writeBytes(new byte[10], 11, 10))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            // When we try to write bytes using a byte array with an offset + length that is too large,
            // then we get an IndexOutOfBoundsException
            assertThatThrownBy(() -> seq.writeBytes(new byte[10], 9, 2))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("Writing bytes with a negative length throws IllegalArgumentException")
        void negativeLengthThrows() {
            // Given a sequence
            final var seq = sequence();
            // When we try to write bytes using a byte array with a negative length, then we get an IllegalArgumentException
            assertThatThrownBy(() -> seq.writeBytes(new byte[10], 0, -1)).isInstanceOf(IllegalArgumentException.class);
            // When we try to write bytes using an input stream with a negative length, then we get an IllegalArgumentException
            final var stream = new ByteArrayInputStream(new byte[10]);
            assertThatThrownBy(() -> seq.writeBytes(stream,  -1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Writing bytes to an eof sequence throws BufferOverflowException")
        void writeToEofThrows() {
            // Given an eof sequence
            final var seq = eofSequence();

            // When we try to write a byte array, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeBytes(new byte[10])).isInstanceOf(BufferOverflowException.class);
            assertThatThrownBy(() -> seq.writeBytes(new byte[10], 0, 10)).isInstanceOf(BufferOverflowException.class);

            // When we try to write a ByteBuffer, then we get a BufferOverflowException
            final var byteBuffer = ByteBuffer.allocate(10);
            assertThatThrownBy(() -> seq.writeBytes(byteBuffer)).isInstanceOf(BufferOverflowException.class);

            // When we try to write a BufferedData, then we get a BufferOverflowException
            final var bufferedData = BufferedData.allocate(10);
            assertThatThrownBy(() -> seq.writeBytes(bufferedData)).isInstanceOf(BufferOverflowException.class);

            // When we try to write Bytes, then we get a BufferOverflowException
            final var bytes = Bytes.wrap("abc");
            assertThatThrownBy(() -> seq.writeBytes(bytes)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing bytes where the sequence position is at the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            seq.skip(5);

            // When we try to write a byte array, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeBytes(new byte[10])).isInstanceOf(BufferOverflowException.class);
            assertThatThrownBy(() -> seq.writeBytes(new byte[10], 0, 10)).isInstanceOf(BufferOverflowException.class);

            // When we try to write a ByteBuffer, then we get a BufferOverflowException
            final var byteBuffer = ByteBuffer.allocate(10);
            assertThatThrownBy(() -> seq.writeBytes(byteBuffer)).isInstanceOf(BufferOverflowException.class);

            // When we try to write a BufferedData, then we get a BufferOverflowException
            final var bufferedData = BufferedData.allocate(10);
            assertThatThrownBy(() -> seq.writeBytes(bufferedData)).isInstanceOf(BufferOverflowException.class);

            // When we try to write Bytes, then we get a BufferOverflowException
            final var bytes = Bytes.wrap("abc");
            assertThatThrownBy(() -> seq.writeBytes(bytes)).isInstanceOf(BufferOverflowException.class);
        }


        @Test
        @DisplayName("Writing bytes from an InputStream with less data than the maxLength returns number of bytes written")
        void writingFromInputStreamWithInsufficientData() {
            // Given a sequence and an input stream with some data
            final var seq = sequence();
            final var bytes = new byte[] { 1, 2, 3, 4, 5 };
            final var stream = new ByteArrayInputStream(bytes);
            // When we write the stream data to the sequence, and the max length is larger than the number
            // of bytes we have to write,
            final var numBytesWritten = seq.writeBytes(stream, 10);
            // Then only the bytes available in the stream are written and the number of bytes written are returned.
            assertThat(numBytesWritten).isEqualTo(5);
            assertThat(extractWrittenBytes(seq)).isEqualTo(bytes);
        }

        @Test
        @DisplayName("Writing bytes from an InputStream with no data return 0")
        void writingFromInputStreamWithNoData() {
            // Given a sequence and an input stream with no data
            final var seq = sequence();
            final var bytes = new byte[] { };
            final var stream = new ByteArrayInputStream(bytes);
            // When we write the stream data to the sequence
            final var numBytesWritten = seq.writeBytes(stream, 10);
            // Then we get back 0
            assertThat(numBytesWritten).isZero();
        }

        @Test
        @DisplayName("Writing bytes from an InputStream with lots of data")
        void writingFromInputStreamWithLotsOfData() {
            // Given a sequence and an input stream with lots of data
            final var seq = sequence();
            final var bytes = new byte[1024*1024];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) i;
            }
            final var stream = new ByteArrayInputStream(bytes);
            // When we write the stream data to the sequence
            final var numBytesWritten = seq.writeBytes(stream, 1024 * 1024 + 10);
            // Then we get back the amount of data we wrote
            assertThat(numBytesWritten).isEqualTo(1024 * 1024);
            // And the bytes were actually written
            assertThat(extractWrittenBytes(seq)).isEqualTo(bytes);
        }

        @Test
        @DisplayName("Writing bytes from a src byte array where the src has length of 0")
        void writeZeroSrcByteArray() {
            // Given a sequence and an empty src byte array
            final var seq = sequence();
            final var src = new byte[0];
            final var pos = seq.position();
            // When we try to write bytes from the src, then the position does not change,
            // and the sequence is not modified
            seq.writeBytes(src);
            assertThat(seq.position()).isEqualTo(pos);
            assertThat(extractWrittenBytes(seq)).isEmpty();
        }

        @Test
        @DisplayName("Writing bytes from a src byte array with offset and length where the length is 0")
        void writeZeroSrcByteArrayWithOffset() {
            // Given a sequence and a src byte array
            final var seq = sequence();
            final var src = new byte[10];
            final var pos = seq.position();
            // When we try to write bytes from the src but with a 0 length, then the position does not change,
            // and the sequence is unchanged.
            seq.writeBytes(src, 5, 0);
            assertThat(seq.position()).isEqualTo(pos);
            assertThat(extractWrittenBytes(seq)).isEmpty();
        }

        @Test
        @DisplayName("Writing bytes from a src ByteBuffer where the src has length of 0")
        void writeZeroSrcByteBuffer() {
            // Given a sequence and an empty src ByteBuffer
            final var seq = sequence();
            final var src = ByteBuffer.allocate(0);
            final var pos = seq.position();
            // When we try to write bytes from the src, then the position does not change,
            // and the sequence is unchanged.
            seq.writeBytes(src);
            assertThat(seq.position()).isEqualTo(pos);
            assertThat(extractWrittenBytes(seq)).isEmpty();
        }

        @Test 
        @DisplayName("Writing bytes from a src BufferedData where the src has length of 0")
        void writeZeroSrcBufferedData() {
            // Given a sequence and an empty src BufferedData
            final var seq = sequence();
            final var src = BufferedData.allocate(0);
            final var pos = seq.position();
            // When we try to write bytes from the src, then the position does not change,
            // and the sequence is unchanged.
            seq.writeBytes(src);
            assertThat(seq.position()).isEqualTo(pos);
            assertThat(extractWrittenBytes(seq)).isEmpty();
        }

        @Test
        @DisplayName("Writing bytes from a src byte array where the src is smaller than the sequence limit")
        void writeSmallerSrcByteArray() {
            // Given a sequence with a src byte array who's size is less than the limit
            final var seq = sequence();
            seq.limit(10);
            // When we try writing bytes from the src
            final var src = new byte[] { 1, 2, 3, 4, 5 };
            final var pos = seq.position();
            seq.writeBytes(src);
            // Then the sequence received those bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(src);
            assertThat(seq.position()).isEqualTo(pos + src.length);
        }

        @Test
        @DisplayName("Writing bytes from a src byte array with offset where the src is smaller than the sequence limit")
        void writeSmallerSrcByteArrayWithOffset() {
            // Given a sequence with a src byte array who's size is less than the limit
            final var seq = sequence();
            seq.limit(10);
            final var src = new byte[] { 1, 2, 3, 4, 5 };
            // When we try writing bytes from the src
            final var pos = seq.position();
            seq.writeBytes(src, 2, 2);
            // Then the sequence received those bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 3, 4 });
            assertThat(seq.position()).isEqualTo(pos + 2);
        }

        @Test
        @DisplayName("Writing bytes from a src ByteBuffer where the src is smaller than the sequence limit")
        void writeSmallerSrcByteBuffer() {
            // Given a sequence with a src ByteBuffer who's size is less than the limit
            final var seq = sequence();
            seq.limit(10);
            // When we try writing bytes from the src
            final var src = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5 });
            final var pos = seq.position();
            seq.writeBytes(src);
            // Then the sequence received those bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(src.array());
            assertThat(seq.position()).isEqualTo(pos + 5);
        }

        @Test
        @DisplayName("Writing bytes from a src ByteBuffer with offset where the src is smaller than the sequence limit")
        void writeSmallerSrcByteBufferWithOffset() {
            // Given a sequence with a src ByteBuffer who's size is less than the limit
            final var seq = sequence();
            seq.limit(10);
            final var src = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5 });
            src.position(2);
            // When we try writing bytes from the src
            final var pos = seq.position();
            seq.writeBytes(src);
            // Then the sequence received those bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 3, 4, 5 });
            assertThat(seq.position()).isEqualTo(pos + 3);
        }

        @Test
        @DisplayName("Writing bytes from a src BufferedData where the src is smaller than the sequence limit")
        void writeSmallerSrcBufferedData() {
            // Given a sequence with a src BufferedData who's size is less than the limit
            final var seq = sequence();
            seq.limit(10);
            // When we try writing bytes from the src
            final var src = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5 });
            final var pos = seq.position();
            seq.writeBytes(src);
            // Then the sequence received those bytes and the position is updated
            final var writtenBytes = new byte[1024]; // make large enough to hold extra bytes should they have been written
            assertThat(src.getBytes(0, writtenBytes)).isEqualTo(5);
            assertThat(extractWrittenBytes(seq)).isEqualTo(Arrays.copyOfRange(writtenBytes, 0, 5));
            assertThat(seq.position()).isEqualTo(pos + 5);
        }

        @Test
        @DisplayName("Writing bytes from a src BufferedData with offset where the src is smaller than the sequence limit")
        void writeSmallerSrcBufferedDataWithOffset() {
            // Given a sequence with a src ByteBuffer who's size is less than the limit
            final var seq = sequence();
            seq.limit(10);
            final var src = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5 });
            src.position(2);
            // When we try writing bytes from the src
            final var pos = seq.position();
            seq.writeBytes(src);
            // Then the sequence received those bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 3, 4, 5 });
            assertThat(seq.position()).isEqualTo(pos + 3);
        }

        @Test
        @DisplayName("Writing bytes from a src RandomAccessData where the src is smaller than the sequence limit")
        void writeSmallerSrcRandomAccessData() {
            // Given a sequence with a src RandomAccessData who's size is less than the limit
            final var seq = sequence();
            seq.limit(10);
            // When we try writing bytes from the src
            final var src = Bytes.wrap(new byte[] { 1, 2, 3, 4, 5 });
            final var pos = seq.position();
            seq.writeBytes(src);
            // Then the sequence received those bytes and the position is updated
            final var writtenBytes = new byte[1024]; // make large enough to hold extra bytes should they have been written
            assertThat(src.getBytes(0, writtenBytes)).isEqualTo(5);
            assertThat(extractWrittenBytes(seq)).isEqualTo(Arrays.copyOfRange(writtenBytes, 0, 5));
            assertThat(seq.position()).isEqualTo(pos + 5);
        }

        @Test
        @DisplayName("Writing bytes from a src InputStream where the maxLength is smaller than the sequence limit")
        void writeSmallerSrcInputStream() {
            // Given a sequence with a src InputStream with lots of items
            final var seq = sequence();
            seq.limit(10);
            final var srcBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
            final var stream = new ByteArrayInputStream(srcBytes);
            // When we try writing bytes from the src with a maxLength less than the limit
            final var pos = seq.position();
            seq.writeBytes(stream, 5);
            // Then the sequence received those fewer bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 1, 2, 3, 4, 5 });
            assertThat(seq.position()).isEqualTo(pos + 5);
        }

        @Test
        @DisplayName("Writing bytes from a src byte array where the src is the same length as the sequence limit")
        void writeSrcByteArray() {
            final var seq = sequence();
            seq.limit(10);
            final var src = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            final var pos = seq.position();
            seq.writeBytes(src);
            assertThat(extractWrittenBytes(seq)).isEqualTo(src);
            assertThat(seq.position()).isEqualTo(pos + src.length);
        }

        @Test
        @DisplayName("Writing bytes from a src byte array with offset where the src is the same length as the sequence limit")
        void writeSrcByteArrayWithOffset() {
            final var seq = sequence();
            seq.limit(5);
            final var src = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            final var pos = seq.position();
            seq.writeBytes(src, 5, 5);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 6, 7, 8, 9, 10 });
            assertThat(seq.position()).isEqualTo(pos + 5);
        }

        @Test
        @DisplayName("Writing bytes from a src ByteBuffer where the src is the same length as the sequence limit")
        void writeSrcByteBuffer() {
            final var seq = sequence();
            seq.limit(10);
            final var src = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            final var pos = seq.position();
            seq.writeBytes(src);
            assertThat(extractWrittenBytes(seq)).isEqualTo(src.array());
            assertThat(seq.position()).isEqualTo(pos + 10);
        }

        @Test
        @DisplayName("Writing bytes from a src ByteBuffer with offset where the src is the same length as the sequence limit")
        void writeSrcByteBufferWithOffset() {
            final var seq = sequence();
            seq.limit(5);
            final var src = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            src.position(2);
            src.limit(7);
            final var pos = seq.position();
            seq.writeBytes(src);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 3, 4, 5, 6, 7 });
            assertThat(seq.position()).isEqualTo(pos + 5);
        }

        @Test
        @DisplayName("Writing bytes from a direct ByteBuffer")
        void writeSrcDirectByteBufferWithOffset() {
            final var seq = sequence();
            final int LEN = 10;
            seq.limit(LEN);
            final var src = ByteBuffer.allocateDirect(LEN);
            src.put(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            src.flip();
            final var pos = seq.position();
            seq.writeBytes(src);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            assertThat(seq.position()).isEqualTo(pos + 10);
        }

        @Test
        @DisplayName("Writing bytes from a src BufferedData where the src is the same length as the sequence limit")
        void writeSrcBufferedData() {
            final var seq = sequence();
            seq.limit(10);
            final var src = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            final var pos = seq.position();
            seq.writeBytes(src);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            assertThat(seq.position()).isEqualTo(pos + 10);
        }

        @Test
        @DisplayName("Writing bytes from a src BufferedData with offset where the src is the same length as the sequence limit")
        void writeSrcBufferedDataWithOffset() {
            final var seq = sequence();
            seq.limit(5);
            final var src = BufferedData.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            src.position(2);
            src.limit(7);
            final var pos = seq.position();
            seq.writeBytes(src);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 3, 4, 5, 6, 7 });
            assertThat(seq.position()).isEqualTo(pos + 5);
        }

        @Test
        @DisplayName("Writing bytes from a src RandomAccessData where the src is the same length as the sequence limit")
        void writeSrcRandomAccessData() {
            final var seq = sequence();
            seq.limit(10);
            final var src = Bytes.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            final var pos = seq.position();
            seq.writeBytes(src);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            assertThat(seq.position()).isEqualTo(pos + 10);
        }

        @Test
        @DisplayName("Writing bytes from a src InputStream where the maxLength is the same length as the sequence limit")
        void writeSrcInputStream() {
            // Given a sequence with a src InputStream with the same number of items as the limit
            final var seq = sequence();
            seq.limit(10);
            final var srcBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            final var stream = new ByteArrayInputStream(srcBytes);
            // When we try writing bytes from the src with a maxLength equal to limit
            final var pos = seq.position();
            seq.writeBytes(stream, 10);
            // Then the sequence received those bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            assertThat(seq.position()).isEqualTo(pos + 10);
        }

        @Test
        @DisplayName("Writing bytes from a src InputStream where the maxLength is the larger than the sequence limit")
        void writeSrcInputStreamLargerThanLimit() {
            // Given a sequence with a src InputStream with more items than the limit
            final var seq = sequence();
            seq.limit(10);
            final var srcBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
            final var stream = new ByteArrayInputStream(srcBytes);
            // When we try writing bytes from the src with a maxLength greater than the limit
            final var pos = seq.position();
            seq.writeBytes(stream, 15);
            // Then the sequence received only up to `limit` bytes and the position is updated
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
            assertThat(seq.position()).isEqualTo(pos + 10);
        }

        @Test
        @DisplayName("Writing bytes from a src InputStream with offset where the maxLength is 0 does nothing")
        void writeSrcInputStreamWithTooSmallMaxLength() {
            // Given a sequence with a src input stream
            final var seq = sequence();
            final var arr = new byte[] { 1, 2, 3, 4, 5 };
            final var src = new ByteArrayInputStream(arr);
            // When we try writing bytes from the src with a maxLength that is == 0
            final var pos = seq.position();
            assertThat(seq.writeBytes(src, 0)).isZero();
            // Then nothing was received and the position is unchanged
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[0]);
            assertThat(seq.position()).isEqualTo(pos);
        }

        @Test
        @DisplayName("Writing bytes from a src InputStream where nothing is remaining in the seq does nothing")
        void writeSrcInputStreamWithNothingRemaining() {
            // Given a sequence with a src input stream and a seq with nothing remaining
            final var seq = sequence();
            seq.limit(0);
            final var arr = new byte[] { 1, 2, 3, 4, 5 };
            final var src = new ByteArrayInputStream(arr);
            // When we try writing bytes from the src with a maxLength that is > 0
            final var pos = seq.position();
            assertThat(seq.writeBytes(src, 5)).isZero();
            // Then nothing was received and the position is unchanged
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[0]);
            assertThat(seq.position()).isEqualTo(pos);
        }

        @Test
        @DisplayName("Writing from a closed stream throws DataAccessException")
        void closed() throws IOException {
            // Given a sequence
            final var seq = sequence();
            final var src = mock(InputStream.class);
            doThrow(IOException.class).when(src).read(any(), anyInt(), anyInt());
            // When we try to write some bytes, then we get an exception because the stream throws IOException
            assertThatThrownBy(() -> seq.writeBytes(src, 5)).isInstanceOf(DataAccessException.class);
        }

        @ParameterizedTest(name = "offset={0}, length={1}")
        @CsvSource({
                "-1, 1", // Negative offset
                "100, 10", // Offset larger than the src array size
                "5, 10", // Offset+Length larger than the src array size
        })
        @DisplayName("Writing bytes where the src offset and length are bad")
        void badOffsetLength(int offset, int length) {
            final var seq = sequence();
            assertThatThrownBy(() -> seq.writeBytes(new byte[10], offset, length))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("writeInt()")
    final class WriteIntTest {
        @Test
        @DisplayName("Writing an int to an eof sequence throws BufferOverflowException")
        void writeToEofSequenceThrows() {
            final var seq = eofSequence();
            assertThatThrownBy(() -> seq.writeInt(1)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing an int past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            // When we try to write an int, then we get a BufferOverflowException
            seq.skip(4); // Only 1 byte left, not enough
            assertThatThrownBy(() -> seq.writeInt(1234)).isInstanceOf(BufferOverflowException.class);
            assertThatThrownBy(() -> seq.writeInt(1234, LITTLE_ENDIAN)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing an int when less than 4 bytes are remaining throws BufferOverflowException")
        void writeInsufficientDataThrows() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(10);
            // When we try to write an int, then we get a BufferOverflowException
            final var pos = 10 - Integer.BYTES + 1; // A position that doesn't reserve enough bytes
            seq.skip(pos);
            for (int i = pos; i < 10; i++, seq.skip(1)) {
                assertThatThrownBy(() -> seq.writeInt(1)).isInstanceOf(BufferOverflowException.class);
            }
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(ints = {Integer.MIN_VALUE, -8, -1, 0, 1, 8, Integer.MAX_VALUE})
        @DisplayName("Writing an int")
        void write(int value) {
            // Given a sequence
            final var seq = sequence();
            final var pos = seq.position();
            // When we write an int, then it is the same as the one we wrote, and the position has moved forward
            // by 4 bytes
            seq.writeInt(value);
            assertThat(seq.position()).isEqualTo(pos + Integer.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putInt(value)));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(ints = {Integer.MIN_VALUE, -8, -1, 0, 1, 8, Integer.MAX_VALUE})
        @DisplayName("Writing an int in Little Endian")
        void writeLittleEndian(int value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeInt(value, LITTLE_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Integer.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putInt(value), LITTLE_ENDIAN));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(ints = {Integer.MIN_VALUE, -8, -1, 0, 1, 8, Integer.MAX_VALUE})
        @DisplayName("Writing an int in Big Endian")
        void writeBigEndian(int value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeInt(value, BIG_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Integer.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putInt(value), BIG_ENDIAN));
        }

        @Test
        @DisplayName("Write a mixture of big and little endian data")
        void writeMixedEndian() {
            final var seq = sequence();
            seq.writeInt(0x01020304);
            seq.writeInt(0x05060708, LITTLE_ENDIAN);
            seq.writeInt(0x090A0B0C);
            seq.writeInt(0x0D0E0F10, LITTLE_ENDIAN);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> {
                c.putInt(0x01020304);
                c.order(LITTLE_ENDIAN);
                c.putInt(0x05060708);
                c.order(BIG_ENDIAN);
                c.putInt(0x090A0B0C);
                c.order(LITTLE_ENDIAN);
                c.putInt(0x0D0E0F10);
            }));
        }
    }

    @Nested
    @DisplayName("writeUnsignedInt()")
    final class WriteUnsignedIntTest {
        @Test
        @DisplayName("Writing an unsigned int to an eof sequence throws BufferOverflowException")
        void writeToEofSequenceThrows() {
            final var seq = eofSequence();
            assertThatThrownBy(() -> seq.writeUnsignedInt(1)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing an unsigned int past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            // When we try to write an unsigned int, then we get a BufferOverflowException
            seq.skip(4); // Only 1 byte left, not enough
            assertThatThrownBy(() -> seq.writeUnsignedInt(1)).isInstanceOf(BufferOverflowException.class);
            assertThatThrownBy(() -> seq.writeUnsignedInt(1234, LITTLE_ENDIAN)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing an unsigned int when less than 4 bytes are remaining throws BufferOverflowException")
        void writeInsufficientDataThrows() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(10);
            // When we try to write an int, then we get a BufferOverflowException
            final var pos = 10 - Integer.BYTES + 1; // A position that doesn't reserve enough bytes
            seq.skip(pos);
            for (int i = pos; i < 10; i++, seq.skip(1)) {
                assertThatThrownBy(() -> seq.writeUnsignedInt(1)).isInstanceOf(BufferOverflowException.class);
            }
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(longs = {0x00FFFFFFFFL, 0, 1, 8, 0x007FFFFFFFL})
        @DisplayName("Writing an unsigned int")
        void write(long value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeUnsignedInt(value);
            assertThat(seq.position()).isEqualTo(pos + Integer.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putInt((int) value)));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(longs = {0x00FFFFFFFFL, 0, 1, 8, 0x007FFFFFFFL})
        @DisplayName("Writing an unsigned int in Little Endian")
        void writeLittleEndian(long value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeUnsignedInt(value, LITTLE_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Integer.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putInt((int) value), LITTLE_ENDIAN));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(longs = {0x00FFFFFFFFL, 0, 1, 8, 0x007FFFFFFFL})
        @DisplayName("Writing an unsigned int in Big Endian")
        void writeBigEndian(long value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeUnsignedInt(value, BIG_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Integer.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putInt((int) value), BIG_ENDIAN));
        }

        @Test
        @DisplayName("Write a mixture of big and little endian data")
        void writeMixedEndian() {
            final var seq = sequence();
            seq.writeUnsignedInt(0x91020304L);
            seq.writeUnsignedInt(0x95060708L, LITTLE_ENDIAN);
            seq.writeUnsignedInt(0x990A0B0CL);
            seq.writeUnsignedInt(0x9D0E0F10L, LITTLE_ENDIAN);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> {
                c.putInt(0x91020304);
                c.order(LITTLE_ENDIAN);
                c.putInt(0x95060708);
                c.order(BIG_ENDIAN);
                c.putInt(0x990A0B0C);
                c.order(LITTLE_ENDIAN);
                c.putInt(0x9D0E0F10);
            }));
        }
    }

    @Nested
    @DisplayName("writeLong()")
    final class WriteLongTest {
        @Test
        @DisplayName("Writing a long to an eof sequence throws BufferOverflowException")
        void writeToEofSequenceThrows() {
            final var seq = eofSequence();
            assertThatThrownBy(() -> seq.writeLong(1L)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a long past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            // When we try to write a long, then we get a BufferOverflowException
            seq.skip(4); // Only 1 byte left, not enough
            assertThatThrownBy(() -> seq.writeLong(1L)).isInstanceOf(BufferOverflowException.class);
            assertThatThrownBy(() -> seq.writeLong(1234, LITTLE_ENDIAN)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a long when less than 8 bytes are remaining throws BufferOverflowException")
        void writeInsufficientDataThrows() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(10);
            // When we try to write an int, then we get a BufferOverflowException
            final var pos = 10 - Long.BYTES + 1; // A position that doesn't reserve enough bytes
            seq.skip(pos);
            for (int i = pos; i < 10; i++, seq.skip(1)) {
                assertThatThrownBy(() -> seq.writeLong(1L)).isInstanceOf(BufferOverflowException.class);
            }
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(longs = {Long.MIN_VALUE, -8, -1, 0, 1, 8, Long.MAX_VALUE})
        @DisplayName("Writing a long")
        void write(long value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeLong(value);
            assertThat(seq.position()).isEqualTo(pos + Long.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putLong(value)));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(longs = {Long.MIN_VALUE, -8, -1, 0, 1, 8, Long.MAX_VALUE})
        @DisplayName("Writing a long in Little Endian")
        void writeLittleEndian(long value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeLong(value, LITTLE_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Long.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putLong(value), LITTLE_ENDIAN));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(longs = {Long.MIN_VALUE, -8, -1, 0, 1, 8, Long.MAX_VALUE})
        @DisplayName("Writing a long in Big Endian")
        void writeBigEndian(long value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeLong(value, BIG_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Long.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putLong(value), BIG_ENDIAN));
        }

        @Test
        @DisplayName("Write a mixture of big and little endian data")
        void writeMixedEndian() {
            final var seq = sequence();
            seq.writeLong(0x0102030405060708L);
            seq.writeLong(0x05060708090A0B0CL, LITTLE_ENDIAN);
            seq.writeLong(0x990A0B0C0D0E0F10L);
            seq.writeLong(0x9D0E0F1011121314L, LITTLE_ENDIAN);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> {
                c.putLong(0x0102030405060708L);
                c.order(LITTLE_ENDIAN);
                c.putLong(0x05060708090A0B0CL);
                c.order(BIG_ENDIAN);
                c.putLong(0x990A0B0C0D0E0F10L);
                c.order(LITTLE_ENDIAN);
                c.putLong(0x9D0E0F1011121314L);
            }));
        }
    }

    @Nested
    @DisplayName("writeFloat()")
    final class WriteFloatTest {
        @Test
        @DisplayName("Writing a float to an eof sequence throws BufferOverflowException")
        void writeToEofSequenceThrows() {
            final var seq = eofSequence();
            assertThatThrownBy(() -> seq.writeFloat(1.2f)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a float past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            // When we try to write a float, then we get a BufferOverflowException
            seq.skip(4); // Only 1 byte left, not enough
            assertThatThrownBy(() -> seq.writeFloat(1.2f)).isInstanceOf(BufferOverflowException.class);
            seq.skip(1); // No bytes left, not enough
            assertThatThrownBy(() -> seq.writeFloat(1.2f)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a float when less than 4 bytes are remaining throws BufferOverflowException")
        void writeInsufficientDataThrows() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(10);
            // When we try to write an int, then we get a BufferOverflowException
            final var pos = 10 - Float.BYTES + 1; // A position that doesn't reserve enough bytes
            seq.skip(pos);
            for (int i = pos; i < 10; i++, seq.skip(1)) {
                assertThatThrownBy(() -> seq.writeFloat(1.2f)).isInstanceOf(BufferOverflowException.class);
            }
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -8.2f, -1.3f, 0, 1.4f, 8.5f, Float.MAX_VALUE, Float.POSITIVE_INFINITY})
        @DisplayName("Writing a float")
        void write(float value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeFloat(value);
            assertThat(seq.position()).isEqualTo(pos + Float.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putFloat(value)));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -8.2f, -1.3f, 0, 1.4f, 8.5f, Float.MAX_VALUE, Float.POSITIVE_INFINITY})
        @DisplayName("Writing a float in Little Endian")
        void writeLittleEndian(float value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeFloat(value, LITTLE_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Float.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putFloat(value), LITTLE_ENDIAN));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -8.2f, -1.3f, 0, 1.4f, 8.5f, Float.MAX_VALUE, Float.POSITIVE_INFINITY})
        @DisplayName("Writing a float in Big Endian")
        void writeBigEndian(float value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeFloat(value, BIG_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Float.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putFloat(value), BIG_ENDIAN));
        }

        @Test
        @DisplayName("Write a mixture of big and little endian data")
        void writeMixedEndian() {
            final var seq = sequence();
            seq.writeFloat(0x01020304);
            seq.writeFloat(0x05060708, LITTLE_ENDIAN);
            seq.writeFloat(0x990A0B0C);
            seq.writeFloat(0x9D0E0F10, LITTLE_ENDIAN);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> {
                c.putFloat(0x01020304);
                c.order(LITTLE_ENDIAN);
                c.putFloat(0x05060708);
                c.order(BIG_ENDIAN);
                c.putFloat(0x990A0B0C);
                c.order(LITTLE_ENDIAN);
                c.putFloat(0x9D0E0F10);
            }));
        }
    }

    @Nested
    @DisplayName("writeDouble()")
    final class WriteDoubleTest {
        @Test
        @DisplayName("Writing a double to an eof sequence throws BufferOverflowException")
        void writeToEofSequenceThrows() {
            final var seq = eofSequence();
            assertThatThrownBy(() -> seq.writeDouble(1.3)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a double past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            // When we try to write a double, then we get a BufferOverflowException
            seq.skip(4); // Only 1 byte left, not enough
            assertThatThrownBy(() -> seq.writeDouble(1.3)).isInstanceOf(BufferOverflowException.class);
            seq.skip(1); // No bytes left, not enough
            assertThatThrownBy(() -> seq.writeDouble(1.3)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a double when less than 8 bytes are remaining throws BufferOverflowException")
        void writeInsufficientDataThrows() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(10);
            // When we try to write an int, then we get a BufferOverflowException
            final var pos = 10 - Double.BYTES + 1; // A position that doesn't reserve enough bytes
            seq.skip(pos);
            for (int i = pos; i < 10; i++, seq.skip(1)) {
                assertThatThrownBy(() -> seq.writeDouble(1.3)).isInstanceOf(BufferOverflowException.class);
            }
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -8.2f, -1.3f, 0, 1.4f, 8.5f, Double.MAX_VALUE, Double.POSITIVE_INFINITY})
        @DisplayName("Writing a double")
        void write(double value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeDouble(value);
            assertThat(seq.position()).isEqualTo(pos + Double.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putDouble(value)));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -8.2f, -1.3f, 0, 1.4f, 8.5f, Double.MAX_VALUE, Double.POSITIVE_INFINITY})
        @DisplayName("Writing a double in Little Endian")
        void writeLittleEndian(double value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeDouble(value, LITTLE_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Double.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putDouble(value), LITTLE_ENDIAN));
        }

        @ParameterizedTest(name = "value={0}")
        @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -8.2f, -1.3f, 0, 1.4f, 8.5f, Double.MAX_VALUE, Double.POSITIVE_INFINITY})
        @DisplayName("Writing a double in Big Endian")
        void writeBigEndian(double value) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeDouble(value, BIG_ENDIAN);
            assertThat(seq.position()).isEqualTo(pos + Double.BYTES);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> c.putDouble(value), BIG_ENDIAN));
        }

        @Test
        @DisplayName("Write a mixture of big and little endian data")
        void writeMixedEndian() {
            final var seq = sequence();
            seq.writeDouble(0x9102030405060708L);
            seq.writeDouble(0x990A0B0C0D0E0F10L, LITTLE_ENDIAN);
            seq.writeDouble(0x1112131415161718L);
            seq.writeDouble(0x191A1B1C1D1E1F20L, LITTLE_ENDIAN);
            assertThat(extractWrittenBytes(seq)).isEqualTo(asBytes(c -> {
                c.putDouble(0x9102030405060708L);
                c.order(LITTLE_ENDIAN);
                c.putDouble(0x990A0B0C0D0E0F10L); // Same bytes but in little endian
                c.order(BIG_ENDIAN);
                c.putDouble(0x1112131415161718L);
                c.order(LITTLE_ENDIAN);
                c.putDouble(0x191A1B1C1D1E1F20L); // Same bytes but in little endian
            }));
        }
    }
    
    @Nested
    @DisplayName("writeVarInt()")
    final class WriteVarIntTest {
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        @DisplayName("Writing a varint to an eof sequence throws BufferOverflowException")
        void writeToEofSequenceThrows(final boolean zigZag) {
            final var seq = eofSequence();
            assertThatThrownBy(() -> seq.writeVarInt(1234, zigZag)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a varint past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            seq.skip(5);
            // When we try to write a varint, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeVarInt(1234, false)).isInstanceOf(BufferOverflowException.class);
            assertThatThrownBy(() -> seq.writeVarInt(1234, true)).isInstanceOf(BufferOverflowException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        @DisplayName("Writing a varint when less than 4 bytes are remaining throws BufferOverflowException")
        void writeInsufficientDataThrows(final boolean zigZag) {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(10);
            // When we try to write an int, then we get a BufferOverflowException
            final var pos = 10 - 1; // A position that doesn't reserve enough bytes
            seq.skip(pos);
            assertThatThrownBy(() -> seq.writeVarInt(1234, zigZag)).isInstanceOf(BufferOverflowException.class);
            // A subsequent skip() will also throw an exception now that we hit the end of buffer
            assertThatThrownBy(() -> seq.skip(1)).isInstanceOf(BufferUnderflowException.class);
        }

        @Test
        @DisplayName("Write a varint")
        void write() {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeVarInt(300, false);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { (byte) 0b10101100, 0b00000010 });
            assertThat(seq.position()).isEqualTo(pos + 2);
        }

        @Test
        @DisplayName("Write a varint with zigzag encoding")
        void writeZigZag() {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeVarInt(-151, true);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { (byte) 0b10101101, 0b00000010 });
            assertThat(seq.position()).isEqualTo(pos + 2);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3, 7, 8, 9, 1023, 1024, 1025, 65535, 65536, 0x7FFFFFFF,
                -1, -2, -7, -1023, -1024, -65535, -65536, -0x7FFFFFFF, -0x80000000})
        @DisplayName("Varints must be encoded with less than 5 bytes")
        void checkVarIntLen(final int num) {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeVarInt(num, false);
            final var afterPos = seq.position();
            if (num >= 0) {
                assertThat(afterPos - pos).isLessThanOrEqualTo(5);
            } else {
                // negative ints are always encoded with 10 bytes if zigzag=false
                assertThat(afterPos - pos).isEqualTo(10);
            }
            seq.writeVarInt(num, true);
            final var afterAfterPos = seq.position();
            assertThat(afterAfterPos - afterPos).isLessThanOrEqualTo(5);
        }
    }

    @Nested
    @DisplayName("writeVarLong()")
    final class WriteVarLongTest {
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        @DisplayName("Writing a varlong to an eof sequence throws BufferOverflowException")
        void writeToEofSequenceThrows(final boolean zigZag) {
            final var seq = eofSequence();
            assertThatThrownBy(() -> seq.writeVarLong(3882918382L, zigZag)).isInstanceOf(BufferOverflowException.class);
        }

        @Test
        @DisplayName("Writing a varlong past the limit throws BufferOverflowException")
        void writePastLimit() {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(5);
            seq.skip(5);
            // When we try to write a varlong, then we get a BufferOverflowException
            assertThatThrownBy(() -> seq.writeVarLong(3882918382L, false)).isInstanceOf(BufferOverflowException.class);
            assertThatThrownBy(() -> seq.writeVarLong(3882918382L, true)).isInstanceOf(BufferOverflowException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        @DisplayName("Writing a varlong when less than 4 bytes are remaining throws BufferOverflowException")
        void writeInsufficientDataThrows(final boolean zigZag) {
            // Given a sequence with a limit where position == limit
            final var seq = sequence();
            seq.limit(10);
            // When we try to write an int, then we get a BufferOverflowException
            final var pos = 10 - 1; // A position that doesn't reserve enough bytes
            seq.skip(pos);
            assertThatThrownBy(() -> seq.writeVarLong(3882918382L, zigZag)).isInstanceOf(BufferOverflowException.class);
            // A subsequent skip() will also throw an exception now that we hit the end of buffer
            assertThatThrownBy(() -> seq.skip(1)).isInstanceOf(BufferUnderflowException.class);
        }

        @Test
        @DisplayName("Write a varlong")
        void write() {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeVarLong(300, false);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { (byte) 0b10101100, 0b00000010 });
            assertThat(seq.position()).isEqualTo(pos + 2);
        }

        @Test
        @DisplayName("Write a varlong with zigzag encoding")
        void writeZigZag() {
            final var seq = sequence();
            final var pos = seq.position();
            seq.writeVarLong(-151, true);
            assertThat(extractWrittenBytes(seq)).isEqualTo(new byte[] { (byte) 0b10101101, 0b00000010 });
            assertThat(seq.position()).isEqualTo(pos + 2);
        }
    }
}
