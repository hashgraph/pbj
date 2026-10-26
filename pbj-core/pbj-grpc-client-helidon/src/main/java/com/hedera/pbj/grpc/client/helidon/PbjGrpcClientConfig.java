// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import io.helidon.common.tls.Tls;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration for PBJ GRPC client.
 *
 * @param readTimeout A read timeout. Duration.ofSeconds(10) is a good default.
 * @param tls TLS configuration.
 * @param authority An optional authority string.
 * @param contentType A content type, such as "application/grpc+proto" or "application/grpc+json".
 * @param encoding Default GRPC encoding for sending data to remote peers, e.g. "identity", "gzip", etc.
 *      Note that the encoding must be registered as a `Compressor` with `GrpcCompression` to actually be supported,
 *      otherwise "identity" is used by default.
 *      If the remote peer doesn't support this encoding, then the sender is free to choose another one supported
 *      by both the remote peer and the `GrpcCompression`.
 * @param acceptEncodings Accepted GRPC encodings for receiving data from remote peers which typically refer
 *      to compression algorithms, e.g. "identity", "gzip", etc.
 *      Note that the encoding must be registered as a `Decompressor` with `GrpcCompression` to actually be supported.
 * @param maxSize the maximum field size passed to the reply codec, defaults to Codec.DEFAULT_MAX_SIZE.
 * @param maxIncomingBufferSize the maximum buffered wire bytes, including message prefixes and queued messages.
 *                              A decoded message is also limited to this value minus the five-byte gRPC prefix.
 *                              Must be larger than `maxSize` to account for protobuf metadata and to support
 *                              multiple queued messages, especially for server or bidi streaming.
 *                              Defaults to Codec.DEFAULT_MAX_SIZE * 5.
 */
public record PbjGrpcClientConfig(
        Duration readTimeout,
        Tls tls,
        Optional<String> authority,
        String contentType,
        String encoding,
        Set<String> acceptEncodings,
        int maxSize,
        int maxIncomingBufferSize) {

    /** For backward compatibility before encodings were introduced. */
    public PbjGrpcClientConfig(Duration readTimeout, Tls tls, Optional<String> authority, String contentType) {
        this(
                readTimeout,
                tls,
                authority,
                contentType,
                GrpcCompression.IDENTITY,
                GrpcCompression.getDecompressorNames(),
                Codec.DEFAULT_MAX_SIZE,
                Codec.DEFAULT_MAX_SIZE * 5);
    }

    /** For backward compatibility before maxSize was introduced. */
    public PbjGrpcClientConfig(
            Duration readTimeout,
            Tls tls,
            Optional<String> authority,
            String contentType,
            String encoding,
            Set<String> acceptEncodings) {
        this(
                readTimeout,
                tls,
                authority,
                contentType,
                encoding,
                acceptEncodings,
                Codec.DEFAULT_MAX_SIZE,
                Codec.DEFAULT_MAX_SIZE * 5);
    }
}
