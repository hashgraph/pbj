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

import static com.hedera.pbj.grpc.helidon.GrpcHeaders.APPLICATION_GRPC_PROTO_TYPE;
import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;

/**
 * A convenience class for building the trailers in the event of a catastrophic error before any
 * headers could be sent to the client in response. In the specification, it says:
 *
 * <pre>
 *     Response-Headers & Trailers-Only are each delivered in a single HTTP2 HEADERS frame block.
 *     Most responses are expected to have both headers and trailers but Trailers-Only is permitted
 *     for calls that produce an immediate error. Status must be sent in Trailers even if the status
 *     code is OK.
 * </pre>
 *
 * It extends {@link TrailerBuilder} and delegates to its parent to send common headers.
 */
class TrailerOnlyBuilder extends TrailerBuilder {
    private Status httpStatus = Status.OK_200;
    private final HttpMediaType contentType = APPLICATION_GRPC_PROTO_TYPE;

    TrailerOnlyBuilder(
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final StreamFlowControl flowControl) {
        super(streamWriter, streamId, flowControl);
    }

    /** The HTTP Status to return in these trailers. The status will default to 200 OK. */
    @NonNull
    public TrailerOnlyBuilder httpStatus(@Nullable final Status httpStatus) {
        this.httpStatus = httpStatus;
        return this;
    }

    /**
     * Send the headers back to the client
     *
     * @param httpHeaders The normal HTTP headers (also grpc headers)
     * @param http2Headers The HTTP2 pseudo-headers
     */
    @Override
    protected void send(
            @NonNull final WritableHeaders<?> httpHeaders,
            @NonNull final Http2Headers http2Headers) {
        http2Headers.status(httpStatus);
        httpHeaders.contentType(requireNonNull(contentType));
        super.send(httpHeaders, http2Headers);
    }
}
