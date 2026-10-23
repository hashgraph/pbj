// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Builds length-prefixed gRPC frames. This is the inverse of {@link GrpcDeframer}.
 *
 * <p>A frame is a one byte flags field, followed by the length of the payload as a 4-byte big-endian unsigned
 * integer, followed by the payload. For regular messages the flags are 0 (uncompressed) or 1 (compressed). gRPC-Web
 * additionally uses {@link #TRAILERS_FLAG} for the final frame that carries the trailers.
 */
public final class GrpcFramer {
    /** Flag for an uncompressed message. */
    public static final int UNCOMPRESSED_FLAG = 0x00;

    /** Flag for a compressed message. */
    public static final int COMPRESSED_FLAG = 0x01;

    /** The gRPC-Web flag marking a frame that carries trailers rather than a message. */
    public static final int TRAILERS_FLAG = 0x80;

    private GrpcFramer() {}

    /**
     * Compresses the message with the given encoding, if it isn't "identity", and frames it.
     *
     * @param message the serialized message
     * @param encoding the name of a compressor registered with {@link GrpcCompression}
     * @return a new array holding the complete frame
     */
    @NonNull
    public static byte[] frameMessage(@NonNull final Bytes message, @NonNull final String encoding) {
        final Bytes compressed = GrpcCompression.getCompressor(encoding).compress(message);
        final int flags = GrpcCompression.IDENTITY.equals(encoding) ? UNCOMPRESSED_FLAG : COMPRESSED_FLAG;
        return frame(flags, compressed);
    }

    /**
     * Frames the given payload with the given flags.
     *
     * @param flags the value of the first byte of the frame
     * @param payload the payload of the frame
     * @return a new array holding the complete frame
     */
    @NonNull
    public static byte[] frame(final int flags, @NonNull final Bytes payload) {
        requireNonNull(payload);
        final int length = (int) payload.length();
        final byte[] frame = new byte[GrpcDeframer.PREFIX_LENGTH + length];
        frame[0] = (byte) flags;
        frame[1] = (byte) (length >>> 24);
        frame[2] = (byte) (length >>> 16);
        frame[3] = (byte) (length >>> 8);
        frame[4] = (byte) length;
        payload.getBytes(0, frame, GrpcDeframer.PREFIX_LENGTH, length);
        return frame;
    }
}
