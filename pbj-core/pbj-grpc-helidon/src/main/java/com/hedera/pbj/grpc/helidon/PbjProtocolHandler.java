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

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

import java.util.Objects;
import java.util.concurrent.Flow;

import static java.lang.System.Logger.Level.ERROR;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of gRPC based on PBJ. This class specifically contains the glue logic for bridging
 * between Helidon and the generated PBJ service handler endpoints. An instance of this class is
 * created for each new connection, and each connection is made to a specific method endpoint.
 */
final class PbjProtocolHandler implements Http2SubProtocolSelector.SubProtocolHandler {

    /**
     * The service method that this connection was created for. The route has information about the
     * {@link ServiceInterface} and method to invoke, as well as metrics, and other information.
     */
    private final PbjMethodRoute route;
    private final Pipeline<? super Bytes> pipeline;
    private final GrpcDataProcessor grpcDataProcessor;

    /** Create a new instance */
    PbjProtocolHandler(
            @NonNull final PbjMethodRoute route,
            @NonNull final GrpcDataProcessor grpcDataProcessor,
            @NonNull final Pipeline<? super Bytes> pipeline) {
        this.route = requireNonNull(route);
        this.grpcDataProcessor = requireNonNull(grpcDataProcessor);
        this.pipeline = requireNonNull(pipeline);
    }

    /**
     * Called at the very beginning of the request, before any data has arrived. At this point we
     * can look at the request headers and determine whether we have a valid request, and do any
     * other initialization we need to.
     */
    @Override
    public void init() {
        route.requestCounter().increment();
    }

    @Override
    @NonNull
    public Http2StreamState streamState() {
        return grpcDataProcessor.getCurrentStreamState();
    }

    @Override
    public void rstStream(@NonNull final Http2RstStream rstStream) {
        pipeline.onComplete();
    }

    @Override
    public void windowUpdate(@NonNull final Http2WindowUpdate update) {
        // Nothing to do
    }

    /**
     * Called by the webserver whenever some additional data is available on the stream. The data
     * comes in chunks, it may be that an entire message is available in the chunk, or it may be
     * that the data is broken out over multiple chunks.
     */
    @Override
    public void data(@NonNull final Http2FrameHeader header, @NonNull final BufferData data) {
        Objects.requireNonNull(header);
        Objects.requireNonNull(data);

        grpcDataProcessor.data(header, data);
    }
}
