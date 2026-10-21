// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Decodes a base64 stream that arrives in arbitrary chunks, as used by the gRPC-Web text mode
 * ({@code application/grpc-web-text}).
 *
 * <p>Base64 decodes in groups of 4 characters, but the network may split the body anywhere, so up to 3 characters are
 * carried over from one chunk to the next. The sender may also encode each frame separately, so padding ({@code =})
 * may appear in the middle of the stream, at the end of any 4-character group. Whitespace is ignored.
 *
 * <p>This class is not thread-safe.
 */
public final class Base64ChunkDecoder implements AutoCloseable {
    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final byte[] EMPTY = new byte[0];

    /** Characters left over from the previous chunk that did not make a complete 4-character group. */
    private final byte[] pending = new byte[4];

    private int pendingCount = 0;
    private boolean closed;

    /**
     * Decodes as much of the given chunk as possible.
     *
     * @param src the array holding the base64 characters
     * @param offset the offset of the first character
     * @param length the number of characters
     * @return the decoded bytes, possibly empty if the chunk didn't complete a 4-character group
     * @throws GrpcException with {@link GrpcStatus#INVALID_ARGUMENT} if the input is not valid base64
     */
    @NonNull
    public byte[] decode(@NonNull final byte[] src, final int offset, final int length) {
        requireNonNull(src);
        if (closed) {
            throw new IllegalStateException("Decoder has already been closed");
        }
        final byte[] chars = new byte[pendingCount + length];
        System.arraycopy(pending, 0, chars, 0, pendingCount);
        int total = pendingCount;
        for (int i = offset; i < offset + length; i++) {
            final byte c = src[i];
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                chars[total++] = c;
            }
        }

        final int full = total - total % 4;
        pendingCount = total - full;
        System.arraycopy(chars, full, pending, 0, pendingCount);
        if (full == 0) {
            return EMPTY;
        }

        // Decode runs of groups. A run ends after any group that contains padding, because the JDK decoder does
        // not accept padding in the middle of its input.
        final ByteBuffer out = ByteBuffer.allocate(full / 4 * 3);
        int start = 0;
        for (int group = 0; group < full; group += 4) {
            if (chars[group + 3] == '=') {
                decodeInto(chars, start, group + 4 - start, out);
                start = group + 4;
            }
        }
        if (start < full) {
            decodeInto(chars, start, full - start, out);
        }

        final byte[] result = new byte[out.position()];
        out.flip().get(result);
        return result;
    }

    /**
     * Called at the end of the stream.
     *
     * @throws GrpcException with {@link GrpcStatus#INVALID_ARGUMENT} if the stream ended in the middle of a
     *     4-character group
     */
    public void finish() {
        close();
    }

    /** Discards unfinished input after cancellation or an I/O failure; closing then performs no validation. */
    public void abort() {
        closed = true;
        pendingCount = 0;
    }

    /** Validates a normal end of input. Idempotent; callers must abort streams that did not reach normal EOF. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            final int remaining = pendingCount;
            pendingCount = 0;
            if (remaining != 0) {
                throw new GrpcException(GrpcStatus.INVALID_ARGUMENT, "Truncated base64 in grpc-web-text request");
            }
        }
    }

    private static void decodeInto(
            @NonNull final byte[] chars, final int offset, final int length, @NonNull final ByteBuffer out) {
        try {
            out.put(DECODER.decode(ByteBuffer.wrap(chars, offset, length)));
        } catch (final IllegalArgumentException e) {
            throw new GrpcException(GrpcStatus.INVALID_ARGUMENT, "Invalid base64 in grpc-web-text request", e);
        }
    }
}
