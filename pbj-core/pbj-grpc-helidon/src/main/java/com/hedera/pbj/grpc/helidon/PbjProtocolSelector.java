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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sub-protocol selector for HTTP/2. This is the main entry point into the PBJ implementation of gRPC. The web
 * server will use this class to determine if a request is a gRPC request and if so, how to handle it.
 */
class PbjProtocolSelector implements Http2SubProtocolSelector {
    /** This routing has absolutely no routes. */
    private static final PbjRouting EMPTY = PbjRouting.builder().build();

    private final PbjConfig config;
    private final DeadlineDetector deadlineDetector;
    private final ScheduledExecutorService deadlineExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final Counter requestCounter;
    private final Counter failedRequestCounter;

    /**
     * Create a new PBJ based grpc protocol selector (default). Access restricted to be package-private so as
     * to limit instantiation to the {@link PbjProtocolProvider}.
     */
    PbjProtocolSelector(@NonNull final PbjConfig config) {
        this.config = requireNonNull(config);
        this.deadlineDetector = (deadline, onDeadlineExceeded) ->
                deadlineExecutorService.schedule(onDeadlineExceeded, deadline, TimeUnit.NANOSECONDS);

        final var metricRegistry = Metrics.globalRegistry();
        this.requestCounter = metricRegistry.getOrCreate(Counter.builder("pbj.grpc.requests")
                .scope("vendor")
                .description("The number of gRPC requests"));
        this.failedRequestCounter = metricRegistry.getOrCreate(Counter.builder("pbj.grpc.request.failures")
                .scope("vendor")
                .description("The number of failed gRPC requests"));
    }

    /**
     * Called by Helidon to create the sub-protocol for PBJ gRPC requests. The {@link SubProtocolResult} returned
     * will be responsible for handling the request.
     */
    @Override
    public SubProtocolResult subProtocol(ConnectionContext ctx,
                                         HttpPrologue prologue,
                                         Http2Headers headers,
                                         Http2StreamWriter streamWriter,
                                         int streamId,
                                         Http2Settings serverSettings, // unused
                                         Http2Settings clientSettings, // unused
                                         StreamFlowControl flowControl,
                                         Http2StreamState currentStreamState,
                                         Router router) {
        this.requestCounter.increment();

        // As per the specification, only POST requests are supported. I would have thought that the code here should
        // return a response code of 405 (Method Not Allowed) if the method is not POST, but the code here just returns
        // NOT_SUPPORTED. I'm not sure if this is technically correct.
        if (prologue.method() != Method.POST) {
            this.failedRequestCounter.increment();
            return NOT_SUPPORTED;
        }

        // If Content-Type does not begin with "application/grpc", gRPC servers SHOULD respond with HTTP status of
        // 415 (Unsupported Media Type). This will prevent other HTTP/2 clients from interpreting a gRPC error
        // response, which uses status 200 (OK), as successful.
        // See https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
        final var httpHeaders = headers.httpHeaders();
        final var contentType = httpHeaders.value(HeaderNames.CONTENT_TYPE).orElse("");
        if (!contentType.startsWith("application/grpc")) {
            return new SubProtocolResult(true,
                    new PbjErrorProtocolHandler(streamWriter, streamId, currentStreamState, h ->
                            h.set(Http2Headers.STATUS_NAME, Status.UNSUPPORTED_MEDIA_TYPE_415.code())));
        }

        // Look up the route based on the path. If that route does not exist, we return a 200 OK response with
        // a gRPC status of NOT_FOUND.
        final var routing = router.routing(PbjRouting.class, EMPTY);
        final var route = routing.findRoute(prologue);
        if (route == null) {
            this.failedRequestCounter.increment();
            return new SubProtocolResult(true,
                    new RouteNotFoundHandler(streamWriter, streamId, currentStreamState));
        }

        // This is a valid call!
        return new SubProtocolResult(true,
                new PbjProtocolHandler(headers,
                        streamWriter,
                        streamId,
                        serverSettings,
                        clientSettings,
                        flowControl,
                        currentStreamState,
                        config,
                        route,
                        deadlineDetector));
    }

    /**
     * A handler for the case where the path is not found.
     */
    private static final class RouteNotFoundHandler implements Http2SubProtocolSelector.SubProtocolHandler {
        private final Http2StreamWriter streamWriter;
        private final int streamId;
        private Http2StreamState currentStreamState;

        RouteNotFoundHandler(final Http2StreamWriter streamWriter,
                             final int streamId,
                             final Http2StreamState currentStreamState) {
            this.streamWriter = streamWriter;
            this.streamId = streamId;
            this.currentStreamState = currentStreamState;
        }

        @Override
        public void init() {
            WritableHeaders<?> writable = WritableHeaders.create();
            writable.set(Http2Headers.STATUS_NAME, Status.OK_200.code());
            writable.set(GrpcHeaders.NOT_FOUND);
            Http2Headers http2Headers = Http2Headers.create(writable);
            streamWriter.writeHeaders(http2Headers,
                    streamId,
                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                    FlowControl.Outbound.NOOP);
            currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
        }

        @Override
        public Http2StreamState streamState() {
            return currentStreamState;
        }

        @Override
        public void rstStream(Http2RstStream rstStream) {
            // No-op
        }

        @Override
        public void windowUpdate(Http2WindowUpdate update) {
            // No-op
        }

        @Override
        public void data(Http2FrameHeader header, BufferData data) {
            // No-op
        }
    }
}
