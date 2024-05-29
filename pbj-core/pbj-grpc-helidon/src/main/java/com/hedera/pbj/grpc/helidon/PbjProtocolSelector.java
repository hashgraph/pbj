package com.hedera.pbj.grpc.helidon;

import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ACCEPT_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ENCODING;

import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;
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
public class PbjProtocolSelector implements Http2SubProtocolSelector {
    private final DeadlineDetector deadlineDetector;

    /**
     * Create a new PBJ based grpc protocol selector (default). Access restricted to be package-private so as
     * to limit instantiation to the {@link PbjProtocolProvider}.
     */
    PbjProtocolSelector() {
        deadlineDetector = new DeadlineDetector() {
            private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

            @Override
            public ScheduledFuture<?> scheduleDeadline(long deadline, Runnable onDeadlineExceeded) {
                return executorService.schedule(onDeadlineExceeded, deadline, TimeUnit.NANOSECONDS);
            }
        };
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
                                         Http2Settings serverSettings,
                                         Http2Settings clientSettings,
                                         StreamFlowControl flowControl,
                                         Http2StreamState currentStreamState,
                                         Router router) {
        // As per the specification, only POST requests are supported. I would have thought that the code here should
        // return a response code of 405 (Method Not Allowed) if the method is not POST, but the code here just returns
        // NOT_SUPPORTED. I'm not sure if this is technically correct.
        if (prologue.method() != Method.POST) {
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
        PbjRouting routing = router.routing(PbjRouting.class, PbjRouting.EMPTY);
        PbjMethodRoute route = routing.findRoute(prologue);
        if (route == null) {
            return new SubProtocolResult(true,
                    new PbjErrorProtocolHandler(streamWriter, streamId, currentStreamState, h -> {
                            h.set(Http2Headers.STATUS_NAME, Status.OK_200.code());
                            h.set(GrpcStatus.NOT_FOUND);
                    }));
        }

        // This implementation currently only supports "identity" and "gzip" compression. As per the documentation:
        // If a client message is compressed by an algorithm that is not supported by a server, the message will result
        // in an UNIMPLEMENTED error status on the server. The server will include a grpc-accept-encoding header [in]
        // the response which specifies the algorithms that the server accepts.
        final var encoding = httpHeaders.value(GRPC_ENCODING).orElse("identity");
        if (!"identity".equals(encoding) && !"gzip".equals(encoding)) {
            return new SubProtocolResult(true,
                    new PbjErrorProtocolHandler(streamWriter, streamId, currentStreamState, h -> {
                            h.set(GrpcStatus.UNIMPLEMENTED);
                            h.set(GRPC_ACCEPT_ENCODING, "identity,gzip");
                    }));
        }

        // This looks like a valid call! We will return a new PbjProtocolHandler to handle the request.
        return new SubProtocolResult(true,
                new PbjProtocolHandler(prologue,
                        headers,
                        streamWriter,
                        streamId,
                        serverSettings,
                        clientSettings,
                        flowControl,
                        currentStreamState,
                        route,
                        deadlineDetector));
    }
}
