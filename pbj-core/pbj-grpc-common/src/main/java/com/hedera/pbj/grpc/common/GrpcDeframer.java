// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Reassembles length-prefixed gRPC messages from a stream of bytes that may arrive in arbitrary chunks.
 *
 * <p>Each gRPC message on the wire is prefixed with 5 bytes: one byte compression flag (0 or 1), followed by the
 * length of the message as a 4-byte big-endian unsigned integer. A single chunk of input may contain a part of a
 * message, exactly one message, or the tail of one message and the head of another. Framing is independent of the
 * HTTP transport and of whether messages are requests or responses.
 *
 * <p>Each complete message is handed to the configured consumer, optionally after decompression.
 *
 * <p>A non-empty payload uses one array of its declared size, allocated when its first bytes arrive. A length
 * prefix alone does not allocate a payload array. Once payload bytes arrive, the full declared size is reserved,
 * bounded by the configured maximum message size.
 *
 * <p>This class is not thread-safe. Callers are expected to feed it data sequentially.
 */
public final class GrpcDeframer {
    /** The number of bytes in the prefix of every gRPC message. */
    public static final int PREFIX_LENGTH = 5;

    /** States for the read state machine. */
    private enum ReadState {
        /** Start state, when we are looking for the first byte that says if data is compressed or not. */
        START,
        /** State where we are reading the length. We may only have part of the 4 length bytes. */
        READ_LENGTH,
        /** State where we are reading the message bytes. */
        READ_ENTITY_BYTES
    }

    /**
     * A source of bytes for the deframer. This keeps the deframer independent of any particular buffer type, such as
     * a Helidon {@code BufferData}.
     */
    public interface Input {
        /** The number of bytes that may be read without blocking. */
        int available();

        /** Read a single unsigned byte. */
        int read();

        /** Read {@code length} bytes into {@code dst} starting at {@code offset}. */
        void read(byte[] dst, int offset, int length);
    }

    /** Receives a complete wire payload before decompression, including its compression flag. */
    @FunctionalInterface
    public interface MessageConsumer {
        /** Receives one immutable wire payload. Flags are either zero or one. */
        void accept(int compressionFlag, Bytes payload);
    }

    private final int maxMessageSizeBytes;
    private final MessageConsumer onMessage;
    private final ArrayInput arrayInput = new ArrayInput();

    private ReadState currentReadState = ReadState.START;

    /** The compression flag of the current message. */
    private int entityIsCompressed = 0;

    /** Number of length bytes read so far, between 0 and {@code Integer.BYTES}. */
    private int numOfPartReadBytes = 0;

    /** Bytes of the length integer read so far. */
    private final byte[] partReadLengthBytes = new byte[Integer.BYTES];

    /**
     * The bytes of the message currently being read. This is never larger than {@link #maxMessageSizeBytes}.
     */
    private byte[] entityBytes = null;

    /** The declared wire payload length, validated against {@link #maxMessageSizeBytes} before allocation. */
    private int entityLength;

    /** The index into {@link #entityBytes} into which data is to be read next. */
    private int entityBytesIndex = 0;

    /**
     * Create a new deframer.
     *
     * @param maxMessageSizeBytes the maximum wire and decompressed size of a single message
     * @param decompressor the decompressor to use for messages that have the compression flag set
     * @param onMessage called once for each complete message
     */
    public GrpcDeframer(
            final int maxMessageSizeBytes,
            @NonNull final GrpcCompression.Decompressor decompressor,
            @NonNull final Consumer<Bytes> onMessage) {
        this(maxMessageSizeBytes, decompressingConsumer(maxMessageSizeBytes, decompressor, onMessage));
    }

    /**
     * Creates a deframer that exposes wire payloads before decompression. This permits callers to inspect network
     * bytes or queue compressed messages before decoding them.
     *
     * @param maxMessageSizeBytes maximum wire payload size
     * @param onMessage receives the compression flag and immutable payload of each complete message
     */
    public GrpcDeframer(final int maxMessageSizeBytes, @NonNull final MessageConsumer onMessage) {
        if (maxMessageSizeBytes < 0) {
            throw new IllegalArgumentException("maxMessageSizeBytes must not be negative");
        }
        this.maxMessageSizeBytes = maxMessageSizeBytes;
        this.onMessage = requireNonNull(onMessage);
    }

    private static MessageConsumer decompressingConsumer(
            final int maxMessageSizeBytes,
            final GrpcCompression.Decompressor decompressor,
            final Consumer<Bytes> onMessage) {
        requireNonNull(decompressor);
        requireNonNull(onMessage);
        return (flag, bytes) -> onMessage.accept(
                flag == 1 ? GrpcCompression.decompress(decompressor, bytes, maxMessageSizeBytes) : bytes);
    }

    /**
     * Feed bytes from a byte array into the deframer.
     *
     * @param data the array to read from
     * @param offset the offset of the first byte to read
     * @param length the number of bytes to read
     * @throws GrpcException if the data is not a valid gRPC message stream
     */
    public void add(@NonNull final byte[] data, final int offset, final int length) {
        arrayInput.reset(data, offset, length);
        add(arrayInput);
    }

    /**
     * Feed all available bytes from the given input into the deframer.
     *
     * @param data the input to read from
     * @throws GrpcException if the data is not a valid gRPC message stream
     */
    public void add(@NonNull final Input data) {
        while (data.available() > 0) {
            switch (currentReadState) {
                case START: {
                    // Read whether this message is compressed.
                    entityIsCompressed = data.read();
                    if (entityIsCompressed != 0 && entityIsCompressed != 1) {
                        throw new GrpcException(
                                GrpcStatus.UNIMPLEMENTED,
                                "Compression flag " + entityIsCompressed
                                        + " is not supported. Only 0 and 1 are supported.");
                    }
                    currentReadState = ReadState.READ_LENGTH;
                    numOfPartReadBytes = 0;
                    break;
                }
                case READ_LENGTH: {
                    // if I have not read a full int yet then read more from available bytes
                    if (numOfPartReadBytes < Integer.BYTES) {
                        // we do not have enough bytes yet to read a 4 byte int
                        // read the bytes we do have and store them for next time
                        final int bytesToRead = Math.min(data.available(), Integer.BYTES - numOfPartReadBytes);
                        data.read(partReadLengthBytes, numOfPartReadBytes, bytesToRead);
                        numOfPartReadBytes += bytesToRead;
                    }
                    // check if we have read all the 4 bytes of the length int32
                    if (numOfPartReadBytes == Integer.BYTES) {
                        final long length = ((long) partReadLengthBytes[0] & 0xFF) << 24
                                | ((long) partReadLengthBytes[1] & 0xFF) << 16
                                | ((long) partReadLengthBytes[2] & 0xFF) << 8
                                | ((long) partReadLengthBytes[3] & 0xFF);
                        if (length > maxMessageSizeBytes) {
                            throw new GrpcException(
                                    GrpcStatus.INVALID_ARGUMENT, "Message size exceeds maximum allowed size");
                        }

                        // In this case we have a request with no message, like an empty unary request
                        if (length == 0) {
                            currentReadState = ReadState.START;
                            onMessage.accept(entityIsCompressed, Bytes.EMPTY);
                        } else {
                            // Allocate only when payload bytes arrive. A length prefix alone must not reserve
                            // the full advertised size for a stalled sender.
                            entityLength = (int) length;
                            entityBytes = null;
                            entityBytesIndex = 0;
                            currentReadState = ReadState.READ_ENTITY_BYTES;
                        }
                    }
                    break;
                }
                case READ_ENTITY_BYTES: {
                    if (entityBytes == null) {
                        // Allocate once on the first payload bytes, then fill the same array across input chunks.
                        entityBytes = new byte[entityLength];
                    }
                    final int numBytesToRead = Math.min(entityLength - entityBytesIndex, data.available());
                    data.read(entityBytes, entityBytesIndex, numBytesToRead);
                    entityBytesIndex += numBytesToRead;

                    if (entityBytesIndex == entityLength) {
                        currentReadState = ReadState.START;
                        // Completed messages own their array; it is never reused for another message.
                        final Bytes bytes = Bytes.wrap(entityBytes);
                        entityBytesIndex = 0;
                        entityLength = 0;
                        entityBytes = null;
                        onMessage.accept(entityIsCompressed, bytes);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Discards any partially read message. A client may end the stream before sending all the bytes of a message, in
     * which case it is as if the message were never sent.
     */
    public void reset() {
        currentReadState = ReadState.START;
        numOfPartReadBytes = 0;
        entityBytesIndex = 0;
        entityLength = 0;
        entityBytes = null;
    }

    /**
     * Whether the deframer is in the middle of a message, that is, it has received some but not all bytes of it.
     *
     * @return true if a message is partially read
     */
    public boolean isMidMessage() {
        return currentReadState != ReadState.START;
    }

    /** A reusable {@link Input} over a region of a byte array. */
    private static final class ArrayInput implements Input {
        private byte[] data;
        private int position;
        private int limit;

        void reset(@NonNull final byte[] data, final int offset, final int length) {
            this.data = requireNonNull(data);
            this.position = offset;
            this.limit = offset + length;
        }

        @Override
        public int available() {
            return limit - position;
        }

        @Override
        public int read() {
            return data[position++] & 0xFF;
        }

        @Override
        public void read(@NonNull final byte[] dst, final int offset, final int length) {
            System.arraycopy(data, position, dst, offset, length);
            position += length;
        }
    }
}
