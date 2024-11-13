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

import static com.hedera.pbj.grpc.helidon.Constants.IDENTITY;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ACCEPT_ENCODING;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.GrpcStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.common.uri.UriEncoding;
import io.helidon.http.Header;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;
import java.util.List;

/**
 * A convenience class for building the trailers. In the specification, it says:
 *
 * <pre>
 *     Trailers → Status [Status-Message] *Custom-Metadata
 *     Status → "grpc-status" 1*DIGIT ; 0-9
 *     Status-Message → "grpc-message" Percent-Encoded
 *     Percent-Encoded → 1*(Percent-Byte-Unencoded / Percent-Byte-Encoded)
 *     Percent-Byte-Unencoded → 1*( %x20-%x24 / %x26-%x7E ) ; space and VCHAR, except %
 *     Percent-Byte-Encoded → "%" 2HEXDIGIT ; 0-9 A-F
 * </pre>
 */
class TrailerBuilder {
    @NonNull private GrpcStatus grpcStatus = GrpcStatus.OK;
    @Nullable private String statusMessage;
    @NonNull private final List<Header> customMetadata = emptyList(); // Never set

    private final Http2StreamWriter streamWriter;
    private final StreamFlowControl flowControl;
    final int streamId;

    TrailerBuilder(
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final StreamFlowControl flowControl) {
        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.flowControl = flowControl;
    }

    /**
     * Sets the gRPC status to return. Normally, the HTTP status will always be 200, while the gRPC
     * status can be anything.
     */
    @NonNull
    public TrailerBuilder grpcStatus(@NonNull final GrpcStatus grpcStatus) {
        this.grpcStatus = grpcStatus;
        return this;
    }

    /** Optionally, set the status message. May be null. */
    @NonNull
    public TrailerBuilder statusMessage(@Nullable final String statusMessage) {
        this.statusMessage = statusMessage;
        return this;
    }

    /** Send the headers to the client */
    public final void send() {
        final var httpHeaders = WritableHeaders.create();
        final var http2Headers = Http2Headers.create(httpHeaders);
        send(httpHeaders, http2Headers);
    }

    /**
     * Actually sends the headers. This method exists so that "trailers-only" can call it to send
     * the normal headers.
     */
    protected void send(
            @NonNull final WritableHeaders<?> httpHeaders,
            @NonNull final Http2Headers http2Headers) {
        httpHeaders.set(requireNonNull(GrpcHeaders.header(requireNonNull(grpcStatus))));
        httpHeaders.set(GRPC_ACCEPT_ENCODING, IDENTITY);
        customMetadata.forEach(httpHeaders::set);
        if (statusMessage != null) {
            final var percentEncodedMessage = UriEncoding.encodeUri(statusMessage);
            httpHeaders.set(GrpcHeaders.GRPC_MESSAGE, percentEncodedMessage);
        }

        streamWriter.writeHeaders(
                http2Headers,
                streamId,
                Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                flowControl.outbound());
    }
}
