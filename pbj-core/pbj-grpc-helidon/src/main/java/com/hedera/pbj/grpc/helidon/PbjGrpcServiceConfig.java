// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.GrpcCompression;
import java.util.Set;

/**
 * PBJ GRPC service configuration that allows one to override the defaults, for example, to force a service instance
 * to reply using the "gzip" encoding, or exclude "gzip" from accepted encodings, etc.
 * <p>
 * Note that encodings must be registered as a `Compressor` and/or a `Decompressor` with `GrpcCompression` to actually
 * be supported. If unregistered, or the remote peer doesn't support a particular encoding, then "identity" may be used
 * by the service instance.
 *
 * @param encoding default encoding for outgoing messages, e.g. "identity", "gzip", etc.
 * @param acceptEncodings accepted encodings for incoming messages
 */
public record PbjGrpcServiceConfig(String encoding, Set<String> acceptEncodings) {
    public static final PbjGrpcServiceConfig DEFAULT =
            new PbjGrpcServiceConfig(GrpcCompression.IDENTITY, GrpcCompression.getDecompressorNames());
}
