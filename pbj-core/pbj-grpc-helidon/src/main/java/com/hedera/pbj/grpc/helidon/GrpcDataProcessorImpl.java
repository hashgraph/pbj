/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.grpc.helidon;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2StreamState;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

public class GrpcDataProcessorImpl implements GrpcDataProcessor {

    /**
     * The bytes of the next incoming message. This is created dynamically as a message is received,
     * and is never larger than the system configured {@link PbjConfig#maxMessageSizeBytes()}.
     *
     * <p>This member is only accessed by the {@link #data} method, which is called sequentially.
     */
    private byte[] entityBytes;

    /**
     * The current index into {@link #entityBytes} into which data is to be read.
     *
     * <p>This member is only accessed by the {@link #data} method, which is called sequentially.
     */
    private int entityBytesIndex;

    /** States for currentReadState state ,machine */
    enum ReadState {
        /**
         * Start state, when we are looking for first byte that says if data is compressed or not
         */
        START,
        /**
         * State were we are reading length, can be partial length of final point when we have all
         * length bytes
         */
        READ_LENGTH,
        /** State where we are reading the protobuf entity bytes */
        READ_ENTITY_BYTES
    }

    /** State machine as we read bytes from incoming data */
    private ReadState currentReadState = ReadState.START;

    /** Number of read bytes between 0 and {@code Integer.BYTES} = 4 */
    private int numOfPartReadBytes;

    /** Byte array to store bytes as we build up to a full 4 byte integer */
    private final byte[] partReadLengthBytes = new byte[Integer.BYTES];

    private final PbjConfig config;
    private final AtomicReference<Http2StreamState> currentStreamState;

    /**
     * The communication pipeline between server and client
     *
     * <p>Method calls on this object are thread-safe.
     */
    private Pipeline<? super Bytes> pipeline;

    public GrpcDataProcessorImpl(
            @NonNull final PbjConfig config, @NonNull final Http2StreamState currentStreamState) {

        this.config = requireNonNull(config);
        this.currentStreamState = new AtomicReference<>(requireNonNull(currentStreamState));
    }

    public void setPipeline(@NonNull final Pipeline<? super Bytes> pipeline) {
        this.pipeline = requireNonNull(pipeline);
    }

    @Override
    public void data(@NonNull final Http2FrameHeader header, @NonNull final BufferData data) {

        try {
            // NOTE: if the deadline is exceeded, then the stream will be closed and data will no
            // longer flow.
            // There is some asynchronous behavior here, but in the worst case, we handle a few more
            // bytes before the stream is closed.
            while (data.available() > 0) {
                // First chunk of data contains the compression flag and the length of the message
                if (entityBytes == null) {
                    // Read whether this message is compressed. We do not currently support
                    // compression.
                    final var isCompressed = (data.read() == 1);
                    if (isCompressed) {
                        // The error will eventually result in the stream being closed
                        throw new GrpcException(
                                GrpcStatus.UNIMPLEMENTED, "Compression is not supported");
                    }
                    // Read the length of the message. As per the grpc protocol specification, each
                    // message on the wire is prefixed with the number of bytes for the message.
                    // However, to prevent a DOS attack where the attacker sends us a very large
                    // length and exhausts our memory, we have a maximum message size configuration
                    // setting. Using that, we can detect attempts to exhaust our memory.
                    final long length = data.readUnsignedInt32();
                    if (length > config.maxMessageSizeBytes()) {
                        throw new GrpcException(
                                GrpcStatus.INVALID_ARGUMENT,
                                "Message size exceeds maximum allowed size");
                    }
                    // Create a buffer to hold the message. We sadly cannot reuse this buffer
                    // because once we have filled it and wrapped it in Bytes and sent it to the
                    // handler, some user code may grab and hold that Bytes object for an arbitrary
                    // amount of time, and if we were to scribble into the same byte array, we
                    // would break the application. So we need a new buffer each time :-(
                    entityBytes = new byte[(int) length];
                    entityBytesIndex = 0;
                }

                // By the time we get here, entityBytes is no longer null. It may be empty, or it
                // may already have been partially populated from a previous iteration. It may be
                // that the number of bytes available to be read is larger than just this one
                // message. So we need to be careful to read, from what is available, only up to
                // the message length, and to leave the rest for the next iteration.
                final int available = data.available();
                final int numBytesToRead =
                        Math.min(entityBytes.length - entityBytesIndex, available);
                data.read(entityBytes, entityBytesIndex, numBytesToRead);
                entityBytesIndex += numBytesToRead;

                // If we have completed reading the message, then we can proceed.
                if (entityBytesIndex == entityBytes.length) {
                    // Grab and wrap the bytes and reset to being reading the next message
                    final var bytes = Bytes.wrap(entityBytes);
                    pipeline.onNext(bytes);
                    entityBytesIndex = 0;
                    entityBytes = null;
                }
            }

            // The end of the stream has been reached! It is possible that a bad client will send
            // end of stream before all the message data we sent. In that case, it is as if the
            // message were never sent.
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                entityBytesIndex = 0;
                entityBytes = null;
                currentStreamState.set(Http2StreamState.HALF_CLOSED_REMOTE);

                pipeline.clientEndStreamReceived();
            }
        } catch (final Exception e) {
            // I have to propagate this error through the service interface, so it can respond to
            // errors in the connection, tear down resources, etc. It will also forward this on
            // to the client, causing the connection to be torn down.
            pipeline.onError(e);
        }
    }

    @Override
    public void setCurrentStreamState(UnaryOperator<Http2StreamState> operator) {
        this.currentStreamState.getAndUpdate(operator);
    }

    @Override
    public Http2StreamState getCurrentStreamState() {
        return currentStreamState.get();
    }
}
