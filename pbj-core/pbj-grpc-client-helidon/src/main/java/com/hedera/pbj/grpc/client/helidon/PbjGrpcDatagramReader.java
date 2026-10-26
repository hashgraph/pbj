// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.grpc.common.GrpcDeframer;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.common.buffers.BufferData;
import java.nio.BufferOverflowException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Bounded queue of complete wire messages, backed by the shared gRPC deframer. The client drains this queue before
 * reading more HTTP/2 data. Payloads remain compressed until the client inspects and decodes them.
 * This adapter is not thread-safe.
 */
class PbjGrpcDatagramReader {
    /** A wire message whose immutable payload is owned by the shared deframer. */
    record Datagram(int compressedFlag, Bytes data) {}

    static final int PREFIX_LENGTH = GrpcDeframer.PREFIX_LENGTH;

    private final int maxBufferSize;
    private final Queue<Datagram> messages = new ArrayDeque<>();
    private final BufferInput input = new BufferInput();
    private final GrpcDeframer deframer;
    private long bufferedBytes;

    PbjGrpcDatagramReader(final int maxBufferSize) {
        if (maxBufferSize < PREFIX_LENGTH) {
            throw new IllegalArgumentException("maxBufferSize must hold a gRPC prefix");
        }
        this.maxBufferSize = maxBufferSize;
        deframer = new GrpcDeframer(
                maxBufferSize - PREFIX_LENGTH, (flag, payload) -> messages.add(new Datagram(flag, payload)));
    }

    /** Adds a frame while accounting for queued messages and the partially received message together. */
    void add(final BufferData data) {
        if (data.available() > maxBufferSize - bufferedBytes) {
            throw new BufferOverflowException();
        }
        bufferedBytes += data.available();
        input.data = data;
        try {
            deframer.add(input);
        } catch (final GrpcException e) {
            if (e.status() == GrpcStatus.INVALID_ARGUMENT) {
                throw new BufferOverflowException();
            }
            // Preserve the client API's failure type for invalid compression flags.
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            input.data = null;
        }
    }

    /** Returns the next complete message, or null if more input is required. */
    Datagram extractNextDatagram() {
        final Datagram message = messages.poll();
        if (message != null) {
            bufferedBytes -= PREFIX_LENGTH + message.data().length();
        }
        return message;
    }

    private static final class BufferInput implements GrpcDeframer.Input {
        private BufferData data;

        @Override
        public int available() {
            return data.available();
        }

        @Override
        public int read() {
            return data.read();
        }

        @Override
        public void read(final byte[] destination, final int offset, final int length) {
            data.read(destination, offset, length);
        }
    }
}
