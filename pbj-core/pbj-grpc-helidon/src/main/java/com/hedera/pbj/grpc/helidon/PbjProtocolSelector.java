// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Metrics;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sub-protocol selector for HTTP/2. This is the main entry point into the PBJ implementation of
 * gRPC. The web server will use this class to determine if a request is a gRPC request and if so,
 * how to handle it.
 */
class PbjProtocolSelector implements Http2SubProtocolSelector {
    /** This routing has absolutely no routes. */
    private static final PbjRouting EMPTY = PbjRouting.builder().build();

    private final PbjConfig config;
    private final DeadlineDetector deadlineDetector;
    private final ScheduledExecutorService deadlineExecutorService =
            Executors.newSingleThreadScheduledExecutor();
    private final Counter requestCounter;
    private final Counter failedRequestCounter;

    /**
     * Create a new PBJ based grpc protocol selector (default). Access restricted to be
     * package-private so as to limit instantiation to the {@link PbjProtocolProvider}.
     */
    PbjProtocolSelector(@NonNull final PbjConfig config) {
        this.config = requireNonNull(config);
        this.deadlineDetector =
                (deadline, onDeadlineExceeded) ->
                        deadlineExecutorService.schedule(
                                onDeadlineExceeded, deadline, TimeUnit.NANOSECONDS);

        final var metricRegistry = Metrics.globalRegistry();
        this.requestCounter =
                metricRegistry.getOrCreate(
                        Counter.builder("pbj.grpc.requests")
                                .scope("vendor")
                                .description("The number of gRPC requests"));
        this.failedRequestCounter =
                metricRegistry.getOrCreate(
                        Counter.builder("pbj.grpc.request.failures")
                                .scope("vendor")
                                .description("The number of failed gRPC requests"));
    }

    /**
     * Called by Helidon to create the sub-protocol for PBJ gRPC requests. The {@link
     * SubProtocolResult} returned will be responsible for handling the request.
     */
    @Override
    public SubProtocolResult subProtocol(
            @NonNull final ConnectionContext ctx, // unused
            @NonNull final HttpPrologue prologue,
            @NonNull final Http2Headers headers,
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final Http2Settings serverSettings, // unused
            @NonNull final Http2Settings clientSettings, // unused
            @NonNull final StreamFlowControl flowControl,
            @NonNull final Http2StreamState currentStreamState,
            @NonNull final Router router) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(prologue);
        Objects.requireNonNull(headers);
        Objects.requireNonNull(streamWriter);
        Objects.requireNonNull(serverSettings);
        Objects.requireNonNull(clientSettings);
        Objects.requireNonNull(flowControl);
        Objects.requireNonNull(currentStreamState);
        Objects.requireNonNull(router);

        this.requestCounter.increment();

        // As per the specification, only POST requests are supported. I would have thought that the
        // code here should return a response code of 405 (Method Not Allowed) if the method is not
        // POST, but the code here just returns NOT_SUPPORTED. I'm not sure if this is technically
        // correct.
        if (prologue.method() != Method.POST) {
            this.failedRequestCounter.increment();
            return NOT_SUPPORTED;
        }

        // Look up the route based on the path. If that route does not exist, we return a 200 OK
        // response with a gRPC status of NOT_FOUND.
        final var routing = router.routing(PbjRouting.class, EMPTY);
        final var route = routing.findRoute(prologue);
        if (route == null) {
            this.failedRequestCounter.increment();
            return new SubProtocolResult(
                    true, new RouteNotFoundHandler(streamWriter, streamId, currentStreamState));
        }

        // This is a valid call!
        return new SubProtocolResult(
                true,
                new PbjProtocolHandler(
                        headers,
                        streamWriter,
                        streamId,
                        flowControl.outbound(),
                        currentStreamState,
                        config,
                        route,
                        deadlineDetector));
    }
}
