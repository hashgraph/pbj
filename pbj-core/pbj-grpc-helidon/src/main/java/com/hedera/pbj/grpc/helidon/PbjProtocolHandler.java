// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static com.hedera.pbj.grpc.helidon.GrpcHeaders.APPLICATION_GRPC_PROTO_TYPE;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ACCEPT_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_TIMEOUT;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_JSON;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_PROTO;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.INFO;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.grpc.common.GrpcDeframer;
import com.hedera.pbj.grpc.common.GrpcFramer;
import com.hedera.pbj.grpc.common.GrpcRequestOptions;
import com.hedera.pbj.grpc.common.GrpcTimeout;
import com.hedera.pbj.grpc.common.compression.ZstdGrpcTransformer;
import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriEncoding;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Implementation of gRPC based on PBJ. This class specifically contains the glue logic for bridging
 * between Helidon and the generated PBJ service handler endpoints. An instance of this class is
 * created for each new connection, and each connection is made to a specific method endpoint.
 */
final class PbjProtocolHandler implements Http2SubProtocolSelector.SubProtocolHandler {
    static {
        new ZstdGrpcTransformer().register("zstd");
    }

    private static final System.Logger LOGGER = System.getLogger(PbjProtocolHandler.class.getName());

    private static final HeaderName GRPC_ENCODING = HeaderNames.createFromLowercase("grpc-encoding");
    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.createFromLowercase("grpc-accept-encoding");

    // Helidon-specific fields related to the connection itself
    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final FlowControl.Outbound flowControl;
    private final AtomicReference<Http2StreamState> currentStreamState;
    private final ConnectionContext connectionContext;

    /**
     * The service method that this connection was created for. The route has information about the
     * {@link ServiceInterface} and method to invoke, as well as metrics, and other information.
     */
    private final PbjMethodRoute route;

    private final PbjConfig config;

    /**
     * If there is a timeout defined for the request, then this detector is used to determine when
     * the timeout deadline has been met. The detector runs on a background thread/timer.
     */
    private final DeadlineDetector deadlineDetector;

    /**
     * A future representing the background task detecting deadlines. If there is a deadline, then
     * this future will represent the task that will be executed when the deadline is reached. If
     * there is no deadline, then we default to a non-null no-op future that exists in the infinite
     * future.
     *
     * <p>This member isn't final because it is set in the {@link #init()} method. It should not be
     * set at any other time, although it is initialized to avoid any possible NPE.
     *
     * <p>Method calls on this object are thread-safe.
     */
    private volatile Future<?> deadlineFuture = CompletableFuture.completedFuture(null);

    /** Publishes the request pipeline to the deadline callback and defers cancellation until open returns. */
    private final Object pipelineLock = new Object();

    /** A deadline that expired before the request pipeline was available. Guarded by {@link #pipelineLock}. */
    private GrpcException pendingDeadline;

    /** Serializes response writes and allows only one terminal response, including initialization failures. */
    private final SendToClientSubscriber outgoing = new SendToClientSubscriber();

    /**
     * Reassembles incoming messages from the data frames. Created in {@link #init()} once the decompressor is known.
     *
     * <p>This member is only accessed by the {@link #data} method, which is called sequentially.
     */
    private GrpcDeframer deframer;

    /** Adapts the Helidon {@link BufferData} of each data frame for the {@link #deframer}. */
    private final BufferDataInput bufferDataInput = new BufferDataInput();

    /**
     * The communication pipeline between server and client
     *
     * <p>This member isn't final because it is set in the {@link #init()} method. It should not be
     * set at any other time.
     *
     * <p>Method calls on this object are thread-safe.
     */
    private Pipeline<? super Bytes> pipeline;

    /**
     * A Decompressor for incoming requests.
     *
     * <p>This member isn't final because it is set in the {@link #init()} method. It should not be
     * set at any other time.
     */
    private GrpcCompression.Decompressor decompressor;

    /**
     * An encoding for outgoing replies, e.g. "identity", or "gzip".
     *
     * <p>This member isn't final because it is set in the {@link #init()} method. It should not be
     * set at any other time.
     */
    private String outgoingEncoding = GrpcCompression.IDENTITY;

    /** Create a new instance */
    PbjProtocolHandler(
            @NonNull final Http2Headers headers,
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final FlowControl.Outbound flowControl,
            @NonNull final Http2StreamState currentStreamState,
            @NonNull final PbjConfig config,
            @NonNull final PbjMethodRoute route,
            @NonNull final DeadlineDetector deadlineDetector,
            @NonNull final ConnectionContext ctx) {
        this.headers = requireNonNull(headers);
        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.flowControl = requireNonNull(flowControl);
        this.currentStreamState = new AtomicReference<>(requireNonNull(currentStreamState));
        this.config = requireNonNull(config);
        this.route = requireNonNull(route);
        this.deadlineDetector = requireNonNull(deadlineDetector);
        this.connectionContext = ctx;
    }

    /**
     * Called at the very beginning of the request, before any data has arrived. At this point we
     * can look at the request headers and determine whether we have a valid request, and do any
     * other initialization we need to.
     */
    @Override
    public void init() {
        route.requestCounter().increment();

        try {
            // If Content-Type does not begin with "application/grpc", gRPC servers SHOULD respond
            // with HTTP status of 415 (Unsupported Media Type). This will prevent other HTTP/2
            // clients from interpreting a gRPC error response, which uses status 200 (OK), as
            // successful.
            // See https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
            // In addition, "application/grpc" is interpreted as "application/grpc+proto".
            final var requestHeaders = headers.httpHeaders();
            final var requestContentType = requestHeaders.contentType().orElse(null);
            final var ct = requestContentType == null ? "" : requestContentType.text();
            final var contentType =
                    switch (ct) {
                        case APPLICATION_GRPC, APPLICATION_GRPC_PROTO -> APPLICATION_GRPC_PROTO;
                        case APPLICATION_GRPC_JSON -> APPLICATION_GRPC_JSON;
                        default -> {
                            if (ct.startsWith(APPLICATION_GRPC)) {
                                yield ct;
                            }
                            throw new HttpException(
                                    "invalid gRPC request content-type \"" + ct + "\"",
                                    Status.UNSUPPORTED_MEDIA_TYPE_415);
                        }
                    };

            final var encodings = requestHeaders.contains(GRPC_ENCODING)
                    ? requestHeaders.get(GRPC_ENCODING).allValues(true)
                    : List.of(GrpcCompression.IDENTITY);
            try {
                this.decompressor = GrpcCompression.determineDecompressor(encodings);
            } catch (IllegalStateException ex) {
                throw new GrpcException(
                        GrpcStatus.UNIMPLEMENTED,
                        "Decompressor is not installed for grpc-encoding \"" + String.join(", ", encodings) + "\"",
                        ex);
            }

            // Metadata, aka additional GRPC headers, provided by the client with the request.
            final Map<String, String> metadata = requestHeaders.stream()
                    .filter(header -> !header.name().toLowerCase().startsWith("grpc-"))
                    .collect(Collectors.toUnmodifiableMap(Header::name, Header::values));

            // The client may have sent a "grpc-accept-encoding" header. Note that
            // "grpc-accept-encoding" is not well specified. I am following what I see work with
            // the grpc.io client library, and the definition of "accept-encoding" for HTTP, such
            // that, identity is *always* safe, but only compression algorithms supported by
            // "grpc-accept-encoding" should be used if any compression algorithm will be used.
            //
            // To support this claim, the spec says:
            // "A Compressed-Flag value of 1 indicates that the binary octet sequence of Message is
            // compressed using the mechanism declared by the Message-Encoding header. A value of
            // 0 indicates that no encoding of Message bytes has occurred.
            //
            // This seems to support the notion that compression can be enabled or disabled
            // irrespective of the grpc-accept-encoding header.
            this.outgoingEncoding = GrpcCompression.determineCompressorName(
                    requestHeaders.contains(GRPC_ACCEPT_ENCODING)
                            ? requestHeaders.get(GRPC_ACCEPT_ENCODING).allValues(true)
                            : List.of(),
                    route.serviceConfig().encoding());
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "outgoingEncoding: " + outgoingEncoding);
            }

            this.deframer =
                    new GrpcDeframer(config.maxMessageSizeBytes(), decompressor, bytes -> pipeline.onNext(bytes));

            // Count initialization time against the client's timeout, including sending the response headers.
            final var timeout = requestHeaders.value(GRPC_TIMEOUT);
            final long timeoutStart = System.nanoTime();

            // At this point, the request itself is valid. Maybe it will still fail to be handled by
            // the service interface, but as far as the protocol is concerned, this was a valid
            // request. Send the headers back to the client (the messages and trailers will be sent
            // later).
            sendResponseHeaders(
                    HeaderValues.create(
                            GRPC_ACCEPT_ENCODING,
                            route.serviceConfig().acceptEncodings().isEmpty()
                                    ? GrpcCompression.IDENTITY
                                    : String.join(",", route.serviceConfig().acceptEncodings())),
                    requestContentType,
                    emptyList());

            // The timer can expire while service.open() is running. It closes the response immediately and
            // notifies the request pipeline once open has returned it.
            deadlineFuture =
                    timeout.map(value -> scheduleDeadline(value, timeoutStart)).orElse(new NoopScheduledFuture<>());
            if (outgoing.isCompleted()) {
                deadlineFuture.cancel(false);
                return;
            }

            // NOTE: The specification mentions the "Message-Type" header. Like everyone else, we're
            // just going to ignore that header. See https://github.com/grpc/grpc/issues/12468.

            // FUTURE: Should we support custom metadata, we would extract it here and pass it along
            // via "options".
            // We should have a wrapper around them, such that we don't process the custom headers
            // ourselves, but allow the service interface to look up special headers based on key.

            // Create the "options" to make available to the ServiceInterface. These options are
            // used to decide on the best way to parse or handle the request.
            final var options = new GrpcRequestOptions(
                    Optional.ofNullable(headers.authority()), // the client (see http2 spec)
                    contentType,
                    config.maxMessageSizeBytes(),
                    metadata,
                    connectionContext.remotePeer().address(),
                    connectionContext.remotePeer().tlsCertificates());

            // Setup the subscribers. The "outgoing" subscriber will send messages to the client.
            // This is given to the "open" method on the service to allow it to send messages to
            // the client.
            final var openedPipeline = route.service().open(route.method(), options, outgoing);
            final GrpcException expiredDeadline;
            synchronized (pipelineLock) {
                pipeline = openedPipeline;
                expiredDeadline = pendingDeadline;
                pendingDeadline = null;
            }
            if (expiredDeadline != null) {
                notifyDeadline(openedPipeline, expiredDeadline);
            }
        } catch (final GrpcException grpcException) {
            route.failedGrpcRequestCounter().increment();
            outgoing.initializationError(Status.OK_200, grpcException.status(), grpcException.getMessage());
            error();
        } catch (final HttpException httpException) {
            route.failedHttpRequestCounter().increment();
            outgoing.initializationError(
                    httpException.status(), GrpcStatus.INVALID_ARGUMENT, httpException.getMessage());
            error();
        } catch (final Exception unknown) {
            route.failedUnknownRequestCounter().increment();
            LOGGER.log(INFO, "Failed to initialize grpc protocol handler", unknown);
            // If the socket is closed, then sending the trailers would fail too.
            // But we still want to call error() so that we clean up resources:
            try {
                outgoing.initializationError(Status.OK_200, GrpcStatus.UNKNOWN, null);
            } finally {
                error();
            }
        }
    }

    @Override
    @NonNull
    public Http2StreamState streamState() {
        return currentStreamState.get();
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

        try {
            // NOTE: if the deadline is exceeded, then the stream will be closed and data will no
            // longer flow.
            // There is some asynchronous behavior here, but in the worst case, we handle a few more
            // bytes before the stream is closed.
            bufferDataInput.data = data;
            deframer.add(bufferDataInput);

            // The end of the stream has been reached! It is possible that a bad client will send
            // end of stream before all the message data we sent. In that case, it is as if the
            // message were never sent.
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                deframer.reset();
                // HALF_CLOSED_REMOTE prevents the Helidon server from closing the stream.
                // So we mark the stream as CLOSED as this is the only state under which
                // the server HTTP2 stream will be properly released by Helidon.
                currentStreamState.set(Http2StreamState.CLOSED);
                pipeline.clientEndStreamReceived();
            }
        } catch (final Exception e) {
            // I have to propagate this error through the service interface, so it can respond to
            // errors in the connection, tear down resources, etc. It will also forward this on
            // to the client, causing the connection to be torn down.
            pipeline.onError(e);
        }
    }

    /**
     * An error has occurred. Cancel the deadline future if it's still active, and set the stream
     * state accordingly.
     *
     * <p>May be called by different threads concurrently.
     */
    private void error() {
        // Canceling a future that has already completed has no effect. So by canceling here, we are saying:
        // "If you have not yet executed, never execute. If you have already executed, then just ignore me".
        // The "isCancelled" flag is set if the future was canceled before it was executed.

        // cancel is threadsafe
        deadlineFuture.cancel(false);
        currentStreamState.set(Http2StreamState.CLOSED);
    }

    /**
     * Helper function. Given the value of the {@code grpc-timeout} header, schedule a callback to be invoked when the
     * deadline is exceeded, and return the associated future. See {@link GrpcTimeout} for the format.
     *
     * <p>Illegal values result in the deadline being ignored.
     *
     * @param timeout The timeout value. Cannot be null.
     * @param timeoutStart The {@link System#nanoTime()} value when the timeout was first read.
     * @return The future representing the task that will be executed if/when the deadline is
     *     reached.
     */
    @NonNull
    private ScheduledFuture<?> scheduleDeadline(@NonNull final String timeout, final long timeoutStart) {
        final var timeoutNanos = GrpcTimeout.parseNanos(timeout);
        if (timeoutNanos.isPresent()) {
            final long remainingNanos = Math.max(0, timeoutNanos.getAsLong() - (System.nanoTime() - timeoutStart));
            return deadlineDetector.scheduleDeadline(remainingNanos, this::deadlineExceeded);
        }

        return new NoopScheduledFuture<>();
    }

    private void deadlineExceeded() {
        final var expiredDeadline = new GrpcException(GrpcStatus.DEADLINE_EXCEEDED);
        if (!outgoing.fail(expiredDeadline)) {
            return;
        }
        route.deadlineExceededCounter().increment();
        final Pipeline<? super Bytes> requests;
        synchronized (pipelineLock) {
            requests = pipeline;
            if (requests == null) {
                pendingDeadline = expiredDeadline;
            }
        }
        if (requests != null) {
            notifyDeadline(requests, expiredDeadline);
        }
    }

    private void notifyDeadline(
            @NonNull final Pipeline<? super Bytes> requests, @NonNull final GrpcException expiredDeadline) {
        try {
            requests.onError(expiredDeadline);
        } catch (final RuntimeException e) {
            LOGGER.log(DEBUG, "Service failed to handle an expired deadline for " + route.method(), e);
        }
    }

    /**
     * According to the specification, the "Response-Headers" are:
     *
     * <pre>HTTP-Status [Message-Encoding] [Message-Accept-Encoding] Content-Type *Custom-Metadata
     * </pre>
     *
     * <p>Where "Status" is <strong>always</strong> 200 OK.
     *
     * <p>The Response-Headers are normally sent, followed by the data, followed by trailers. But if
     * the request fails right away, before any handling, then it is possible to send Trailers-Only
     * instead.
     */
    private void sendResponseHeaders(
            @Nullable final Header messageEncoding,
            @NonNull final HttpMediaType contentType,
            @NonNull final List<Header> customMetadata) {

        // Some headers are http2 specific, the rest are used for the grpc protocol
        final var grpcHeaders = WritableHeaders.create();
        // FUTURE: I think to support custom headers in the response, we would have to list them here.
        // Since this has to be sent before we have any data to send, we must know ahead of time
        // which custom headers are to be returned.
        grpcHeaders.set(HeaderNames.TRAILER, "grpc-status, grpc-message");
        grpcHeaders.set(Http2Headers.STATUS_NAME, Status.OK_200.code());
        grpcHeaders.contentType(contentType);
        grpcHeaders.set(GRPC_ENCODING, outgoingEncoding);
        grpcHeaders.set(GRPC_ACCEPT_ENCODING, GrpcCompression.IDENTITY);
        customMetadata.forEach(grpcHeaders::set);
        if (messageEncoding != null) {
            grpcHeaders.set(messageEncoding);
        }

        final var http2Headers = Http2Headers.create(grpcHeaders);

        streamWriter.writeHeaders(
                http2Headers, streamId, Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS), flowControl);
    }

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
    private class TrailerBuilder {
        @NonNull
        private GrpcStatus grpcStatus = GrpcStatus.OK;

        @Nullable
        private String statusMessage;

        @NonNull
        private final List<Header> customMetadata = emptyList(); // Never set

        /**
         * Sets the gRPC status to return. Normally, the HTTP status will always be 200, while the
         * gRPC status can be anything.
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
         * Actually sends the headers. This method exists so that "trailers-only" can call it to
         * send the normal headers.
         */
        protected void send(@NonNull final WritableHeaders<?> httpHeaders, @NonNull final Http2Headers http2Headers) {
            httpHeaders.set(requireNonNull(GrpcHeaders.header(requireNonNull(grpcStatus))));
            httpHeaders.set(GRPC_ACCEPT_ENCODING, GrpcCompression.IDENTITY);
            customMetadata.forEach(httpHeaders::set);
            if (statusMessage != null) {
                final var percentEncodedMessage = UriEncoding.encodeUri(statusMessage);
                httpHeaders.set(GrpcHeaders.GRPC_MESSAGE, percentEncodedMessage);
            }

            streamWriter.writeHeaders(
                    http2Headers,
                    streamId,
                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                    flowControl);

            // We've just sent out trailers, indicating whether a success or a failure.
            // Nothing is going to happen on this stream anymore.
            // Mark the stream as closed, so that the Helidon Http2ServerStream updates its status to CLOSED,
            // and therefore the Http2Connection actually releases the stream.
            currentStreamState.set(Http2StreamState.CLOSED);
        }
    }

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
    private class TrailerOnlyBuilder extends TrailerBuilder {
        private Status httpStatus = Status.OK_200;
        private final HttpMediaType contentType = APPLICATION_GRPC_PROTO_TYPE;

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
        protected void send(@NonNull final WritableHeaders<?> httpHeaders, @NonNull final Http2Headers http2Headers) {
            http2Headers.status(httpStatus);
            httpHeaders.contentType(requireNonNull(contentType));
            super.send(httpHeaders, http2Headers);
        }
    }

    /**
     * The implementation of {@link Pipeline} used to send messages to the client. It
     * receives bytes from the handlers to send to the client.
     */
    private final class SendToClientSubscriber implements Pipeline<Bytes> {
        /** Guarded by this subscriber's monitor, along with response writes. */
        private boolean completed;

        private synchronized boolean isCompleted() {
            return completed;
        }

        private synchronized void initializationError(
                @NonNull final Status status, @NonNull final GrpcStatus grpcStatus, @Nullable final String message) {
            if (completed) {
                return;
            }
            completed = true;
            new TrailerOnlyBuilder()
                    .httpStatus(status)
                    .grpcStatus(grpcStatus)
                    .statusMessage(message)
                    .send();
        }

        @Override
        public void onSubscribe(@NonNull final Flow.Subscription subscription) {
            // FUTURE: Add support for flow control
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public synchronized void onNext(@NonNull final Bytes response) {
            if (completed) {
                return;
            }
            try {
                final var bufferData = BufferData.create(GrpcFramer.frameMessage(response, outgoingEncoding));
                final var header = Http2FrameHeader.create(
                        bufferData.available(), Http2FrameTypes.DATA, Http2Flag.DataFlags.create(0), streamId);

                // This method may throw an UncheckedIOException. If this happens, the connection with the client
                // has been violently terminated, and we should raise the error, and we should throw an exception
                // so the user knows the connection is toast.
                streamWriter.writeData(new Http2FrameData(header, bufferData), flowControl);
            } catch (final Exception e) {
                LOGGER.log(DEBUG, "Failed to respond to grpc request: " + route.method(), e);
                route.failedResponseCounter().increment();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onError(@NonNull final Throwable throwable) {
            fail(throwable);
        }

        private synchronized boolean fail(@NonNull final Throwable throwable) {
            if (completed) {
                return false;
            }
            completed = true;
            try {
                if (throwable instanceof final GrpcException grpcException) {
                    new TrailerBuilder()
                            .grpcStatus(grpcException.status())
                            .statusMessage(grpcException.getMessage())
                            .send();
                } else {
                    LOGGER.log(DEBUG, "Failed to send response", throwable);
                    new TrailerBuilder().grpcStatus(GrpcStatus.INTERNAL).send();
                }
            } catch (Exception ignored) {
                // If an exception is thrown trying to return headers, we're already in the error state, so
                // just continue.
            }
            error();
            return true;
        }

        @Override
        public synchronized void onComplete() {
            if (completed) {
                return;
            }
            completed = true;
            try {
                new TrailerBuilder().send();
            } catch (final RuntimeException e) {
                error();
                throw e;
            }

            deadlineFuture.cancel(false);

            currentStreamState.getAndUpdate(currentValue -> {
                if (requireNonNull(currentValue) == Http2StreamState.OPEN) {
                    return Http2StreamState.HALF_CLOSED_LOCAL;
                }
                return Http2StreamState.CLOSED;
            });
        }

        @Override
        public void closeConnection() {
            connectionContext.serverSocket().close();
        }
    }

    /** A reusable {@link GrpcDeframer.Input} over the {@link BufferData} of the current data frame. */
    private static final class BufferDataInput implements GrpcDeframer.Input {
        private BufferData data;

        @Override
        public int available() {
            return data.available();
        }

        @Override
        public int read() {
            return data.read();
        }

        @Override
        public void read(@NonNull final byte[] dst, final int offset, final int length) {
            data.read(dst, offset, length);
        }
    }

    /**
     * A {@link ScheduledFuture} that does nothing. This is used when there is no deadline set for
     * the request. A new instance of this must be created (or we need a "reset" method) for each
     * {@link PbjProtocolHandler} instance, because it can become "corrupted" if canceled from any
     * particular call.
     */
    private static final class NoopScheduledFuture<Void> extends CompletableFuture<Void>
            implements ScheduledFuture<Void> {
        @Override
        public long getDelay(@NonNull final TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(@NonNull final Delayed o) {
            // Since all NoopScheduledFuture instances have "0" as the delay, any other Delayed
            // instance with a non-0
            // delay will come after this one.
            return (int) (o.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
