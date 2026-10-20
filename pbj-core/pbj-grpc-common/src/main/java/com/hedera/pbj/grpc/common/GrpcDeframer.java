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
 * message, exactly one message, or the tail of one message and the head of another. This class is transport-neutral:
 * it is used by the native HTTP/2 gRPC handler and by the gRPC-Web handler alike.
 *
 * <p>Each complete message is decompressed if needed and handed to the {@code onMessage} consumer.
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

    private final int maxMessageSizeBytes;
    private final GrpcCompression.Decompressor decompressor;
    private final Consumer<Bytes> onMessage;
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

    /** The index into {@link #entityBytes} into which data is to be read next. */
    private int entityBytesIndex = 0;

    /**
     * Create a new deframer.
     *
     * @param maxMessageSizeBytes the maximum size of a single message, before decompression
     * @param decompressor the decompressor to use for messages that have the compression flag set
     * @param onMessage called once for each complete message
     */
    public GrpcDeframer(
            final int maxMessageSizeBytes,
            @NonNull final GrpcCompression.Decompressor decompressor,
            @NonNull final Consumer<Bytes> onMessage) {
        this.maxMessageSizeBytes = maxMessageSizeBytes;
        this.decompressor = requireNonNull(decompressor);
        this.onMessage = requireNonNull(onMessage);
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
                            onMessage.accept(Bytes.EMPTY);
                        } else {
                            // Create a buffer to hold the message. We sadly cannot reuse this buffer
                            // because once we have filled it and wrapped it in Bytes and sent it to the
                            // handler, some user code may grab and hold that Bytes object for an arbitrary
                            // amount of time, and if we were to scribble into the same byte array, we
                            // would break the application. So we need a new buffer each time :-(
                            entityBytes = new byte[(int) length];
                            entityBytesIndex = 0;
                            // done with length now, so move on to next state
                            currentReadState = ReadState.READ_ENTITY_BYTES;
                        }
                    }
                    break;
                }
                case READ_ENTITY_BYTES: {
                    // By the time we get here, entityBytes is no longer null. It may already have been partially
                    // populated from a previous iteration. It may be that the number of bytes available to be read
                    // is larger than just this one message. So we need to be careful to read, from what is
                    // available, only up to the message length, and to leave the rest for the next iteration.
                    final int numBytesToRead = Math.min(entityBytes.length - entityBytesIndex, data.available());
                    data.read(entityBytes, entityBytesIndex, numBytesToRead);
                    entityBytesIndex += numBytesToRead;

                    // If we have completed reading the message, then we can proceed.
                    if (entityBytesIndex == entityBytes.length) {
                        currentReadState = ReadState.START;
                        // Grab and wrap the bytes and reset to begin reading the next message
                        var bytes = Bytes.wrap(entityBytes);
                        entityBytesIndex = 0;
                        entityBytes = null;
                        if (entityIsCompressed == 1) {
                            bytes = decompressor.decompress(bytes);
                        }
                        onMessage.accept(bytes);
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
