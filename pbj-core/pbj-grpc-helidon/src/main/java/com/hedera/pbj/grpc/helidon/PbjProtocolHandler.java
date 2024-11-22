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
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ACCEPT_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_TIMEOUT;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_JSON;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_PROTO;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
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
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Implementation of gRPC based on PBJ. This class specifically contains the glue logic for bridging
 * between Helidon and the generated PBJ service handler endpoints. An instance of this class is
 * created for each new connection, and each connection is made to a specific method endpoint.
 */
final class PbjProtocolHandler implements Http2SubProtocolSelector.SubProtocolHandler {
    private static final System.Logger LOGGER =
            System.getLogger(PbjProtocolHandler.class.getName());

    /** The only grpc-encoding supported by this implementation. */
    private static final String IDENTITY = "identity";

    /** A pre-created and cached *response* header for "grpc-encoding: identity". */
    private static final Header GRPC_ENCODING_IDENTITY =
            HeaderValues.createCached("grpc-encoding", IDENTITY);

    /** The regular expression used to parse the grpc-timeout header. */
    private static final String GRPC_TIMEOUT_REGEX = "(\\d{1,8})([HMSmun])";

    private static final Pattern GRPC_TIMEOUT_PATTERN = Pattern.compile(GRPC_TIMEOUT_REGEX);

    // Helidon-specific fields related to the connection itself
    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final FlowControl.Outbound flowControl;
    private final AtomicReference<Http2StreamState> currentStreamState;

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
    private Future<?> deadlineFuture = CompletableFuture.completedFuture(null);

    /**
     * The bytes of the next incoming message. This is created dynamically as a message is received,
     * and is never larger than the system configured {@link PbjConfig#maxMessageSizeBytes()}.
     *
     * <p>This member is only accessed by the {@link #data} method, which is called sequentially.
     */
    private byte[] entityBytes = null;

    /**
     * The current index into {@link #entityBytes} into which data is to be read.
     *
     * <p>This member is only accessed by the {@link #data} method, which is called sequentially.
     */
    private int entityBytesIndex = 0;

    /** States for currentReadState state ,machine */
    enum ReadState {
        /**
         * Start state, when we are looking for first byte that says if data is compressed or not
         */
        START,
        /**
         * State were we are reading length, can be partial length of final point when we have all
         * length bytes
         */
        READ_LENGTH,
        /** State where we are reading the protobuf entity bytes */
        READ_ENTITY_BYTES
    }

    /** State machine as we read bytes from incoming data */
    private ReadState currentReadState = ReadState.START;

    /** Number of read bytes between 0 and {@code Integer.BYTES} = 4 */
    private int numOfPartReadBytes = 0;

    /** Byte array to store bytes as we build up to a full 4 byte integer */
    private final byte[] partReadLengthBytes = new byte[Integer.BYTES];

    /**
     * The communication pipeline between server and client
     *
     * <p>This member isn't final because it is set in the {@link #init()} method. It should not be
     * set at any other time.
     *
     * <p>Method calls on this object are thread-safe.
     */
    private Pipeline<? super Bytes> pipeline;

    /** Create a new instance */
    PbjProtocolHandler(
            @NonNull final Http2Headers headers,
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final FlowControl.Outbound flowControl,
            @NonNull final Http2StreamState currentStreamState,
            @NonNull final PbjConfig config,
            @NonNull final PbjMethodRoute route,
            @NonNull final DeadlineDetector deadlineDetector) {
        this.headers = requireNonNull(headers);
        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.flowControl = requireNonNull(flowControl);
        this.currentStreamState = new AtomicReference<>(requireNonNull(currentStreamState));
        this.config = requireNonNull(config);
        this.route = requireNonNull(route);
        this.deadlineDetector = requireNonNull(deadlineDetector);
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
            final var requestContentType =
                    requestHeaders.contentType().orElse(null);
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

            // This implementation currently only supports "identity" compression.
            //
            // As per the documentation:
            // If a client message is compressed by an algorithm that is not supported by a server,
            // the message will result in an UNIMPLEMENTED error status on the server. The server
            // will include a grpc-accept-encoding header [in] the response which specifies the
            // algorithms that the server accepts.
            //
            // Note that in the HeadersBuilder we ALWAYS set the grpc-accept-encoding header.
            // FUTURE: Add support for the other compression schemes and let the response be in the
            // same scheme that was sent to us, or another scheme in "grpc-accept-encoding" that
            // we support, or identity.
            final var encodings = requestHeaders.contains(GRPC_ENCODING)
                    ? requestHeaders.get(GRPC_ENCODING).allValues(true)
                    : List.of(IDENTITY);
            boolean identitySpecified = false;
            for (final var encoding : encodings) {
                if (encoding.startsWith(IDENTITY)) {
                    identitySpecified = true;
                    break;
                }
            }
            if (!identitySpecified) {
                throw new GrpcException(
                        GrpcStatus.UNIMPLEMENTED,
                        "Decompressor is not installed for grpc-encoding \"" + String.join(", ", encodings) + "\"");
            }

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

            // FUTURE: If the client sends a "grpc-accept-encoding", and if we support one of them,
            // then we should pick one and use it in the response. Otherwise, we should not have
            // any compression.

            // If the grpc-timeout header is present, determine when that timeout would occur, or
            // default to a future that is so far in the future it will never happen.
            final var timeout = requestHeaders.value(GRPC_TIMEOUT);
            deadlineFuture =
                    timeout.map(this::scheduleDeadline).orElse(new NoopScheduledFuture<>());

            // At this point, the request itself is valid. Maybe it will still fail to be handled by
            // the service interface, but as far as the protocol is concerned, this was a valid
            // request. Send the headers back to the client (the messages and trailers will be sent
            // later).
            sendResponseHeaders(GRPC_ENCODING_IDENTITY, requestContentType, emptyList());

            // NOTE: The specification mentions the "Message-Type" header. Like everyone else, we're
            // just going to ignore that header. See https://github.com/grpc/grpc/issues/12468.

            // FUTURE: Should we support custom metadata, we would extract it here and pass it along
            // via "options".
            // We should have a wrapper around them, such that we don't process the custom headers
            // ourselves, but allow the service interface to look up special headers based on key.

            // Create the "options" to make available to the ServiceInterface. These options are
            // used to decide on the best way to parse or handle the request.
            final var options =
                    new Options(
                            Optional.ofNullable(headers.authority()), // the client (see http2 spec)
                            contentType.equals(APPLICATION_GRPC_PROTO),
                            contentType.equals(APPLICATION_GRPC_JSON),
                            contentType);

            // Setup the subscribers. The "outgoing" subscriber will send messages to the client.
            // This is given to the "open" method on the service to allow it to send messages to
            // the client.
            final Pipeline<? super Bytes> outgoing = new SendToClientSubscriber();
            pipeline = route.service().open(route.method(), options, outgoing);
        } catch (final GrpcException grpcException) {
            route.failedGrpcRequestCounter().increment();
            new TrailerOnlyBuilder()
                    .grpcStatus(grpcException.status())
                    .statusMessage(grpcException.getMessage())
                    .send();
            error();
        } catch (final HttpException httpException) {
            route.failedHttpRequestCounter().increment();
            new TrailerOnlyBuilder()
                    .httpStatus(httpException.status())
                    .grpcStatus(GrpcStatus.INVALID_ARGUMENT)
                    .statusMessage(httpException.getMessage())
                    .send();
            error();
        } catch (final Exception unknown) {
            route.failedUnknownRequestCounter().increment();
            LOGGER.log(ERROR, "Failed to initialize grpc protocol handler", unknown);
            new TrailerOnlyBuilder().grpcStatus(GrpcStatus.UNKNOWN).send();
            error();
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
            while (data.available() > 0) {
                switch (currentReadState) {
                    case START:
                        {
                            // Read whether this message is compressed. We do not currently support
                            // compression.
                            final var isCompressed = (data.read() == 1);
                            if (isCompressed) {
                                // The error will eventually result in the stream being closed
                                throw new GrpcException(
                                        GrpcStatus.UNIMPLEMENTED, "Compression is not supported");
                            }
                            currentReadState = ReadState.READ_LENGTH;
                            numOfPartReadBytes = 0;
                            break;
                        }
                    case READ_LENGTH:
                        {
                            // if I have not read a full int yet then read more from available bytes
                            if (numOfPartReadBytes < Integer.BYTES) {
                                // we do not have enough bytes yet to read a 4 byte int
                                // read the bytes we do have and store them for next time
                                final int bytesToRead =
                                        Math.min(
                                                data.available(),
                                                Integer.BYTES - numOfPartReadBytes);
                                data.read(partReadLengthBytes, numOfPartReadBytes, bytesToRead);
                                numOfPartReadBytes += bytesToRead;
                            }
                            // check if we have read all the 4 bytes of the length int32
                            if (numOfPartReadBytes == Integer.BYTES) {
                                final long length =
                                        ((long) partReadLengthBytes[0] & 0xFF) << 24
                                                | ((long) partReadLengthBytes[1] & 0xFF) << 16
                                                | ((long) partReadLengthBytes[2] & 0xFF) << 8
                                                | ((long) partReadLengthBytes[3] & 0xFF);
                                if (length > config.maxMessageSizeBytes()) {
                                    throw new GrpcException(
                                            GrpcStatus.INVALID_ARGUMENT,
                                            "Message size exceeds maximum allowed size");
                                }
                                // Create a buffer to hold the message. We sadly cannot reuse this buffer
                                // because once we have filled it and wrapped it in Bytes and sent it to the
                                // handler, some user code may grab and hold that Bytes object for an arbitrary
                                // amount of time, and if we were to scribble into the same byte array, we
                                // would break the application. So we need a new buffer each time :-(
                                entityBytes = new byte[(int) length];
                                entityBytesIndex = 0;
                                // done with length now, so move on to next state
                                currentReadState = ReadState.READ_ENTITY_BYTES;
                            }
                            break;
                        }
                    case READ_ENTITY_BYTES:
                        {
                            // By the time we get here, entityBytes is no longer null. It may be empty, or it
                            // may already have been partially populated from a previous iteration. It may be
                            // that the number of bytes available to be read is larger than just this one
                            // message. So we need to be careful to read, from what is available, only up to
                            // the message length, and to leave the rest for the next iteration.
                            final int available = data.available();
                            final int numBytesToRead =
                                    Math.min(entityBytes.length - entityBytesIndex, available);
                            data.read(entityBytes, entityBytesIndex, numBytesToRead);
                            entityBytesIndex += numBytesToRead;

                            // If we have completed reading the message, then we can proceed.
                            if (entityBytesIndex == entityBytes.length) {
                                currentReadState = ReadState.START;
                                // Grab and wrap the bytes and reset to being reading the next
                                // message
                                final var bytes = Bytes.wrap(entityBytes);
                                pipeline.onNext(bytes);
                                entityBytesIndex = 0;
                                entityBytes = null;
                            }
                            break;
                        }
                }
            }

            // The end of the stream has been reached! It is possible that a bad client will send
            // end of stream before all the message data we sent. In that case, it is as if the
            // message were never sent.
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                entityBytesIndex = 0;
                entityBytes = null;
                currentStreamState.set(Http2StreamState.HALF_CLOSED_REMOTE);
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
     * Helper function. Given a string of digits followed by a unit, schedule a callback to be
     * invoked when the deadline is exceeded, and return the associated future. The proper format of
     * the string is defined in the specification as:
     *
     * <pre>
     *      Timeout → "grpc-timeout" TimeoutValue TimeoutUnit
     *      TimeoutValue → {positive integer as ASCII string of at most 8 digits}
     *      TimeoutUnit → Hour / Minute / Second / Millisecond / Microsecond / Nanosecond
     *             Hour → "H"
     *             Minute → "M"
     *             Second → "S"
     *             Millisecond → "m"
     *             Microsecond → "u"
     *             Nanosecond → "n"
     * </pre>
     *
     * <p>Illegal values result in the deadline being ignored.
     *
     * @param timeout The timeout value. Cannot be null.
     * @return The future representing the task that will be executed if/when the deadline is
     *     reached.
     */
    @NonNull
    private ScheduledFuture<?> scheduleDeadline(@NonNull final String timeout) {
        final var matcher = GRPC_TIMEOUT_PATTERN.matcher(timeout);
        if (matcher.matches()) {
            final var num = Integer.parseInt(matcher.group(1));
            final var unit = matcher.group(2);
            final var deadline =
                    System.nanoTime()
                            * TimeUnit.NANOSECONDS.convert(
                                    num,
                                    switch (unit) {
                                        case "H" -> TimeUnit.HOURS;
                                        case "M" -> TimeUnit.MINUTES;
                                        case "S" -> TimeUnit.SECONDS;
                                        case "m" -> TimeUnit.MILLISECONDS;
                                        case "u" -> TimeUnit.MICROSECONDS;
                                        case "n" -> TimeUnit.NANOSECONDS;
                                            // This should NEVER be reachable, because the matcher
                                            // would not have matched.
                                        default -> throw new GrpcException(
                                                GrpcStatus.INTERNAL, "Invalid unit: " + unit);
                                    });
            return deadlineDetector.scheduleDeadline(
                    deadline,
                    () -> {
                        route.deadlineExceededCounter().increment();
                        pipeline.onError(new GrpcException(GrpcStatus.DEADLINE_EXCEEDED));
                    });
        }

        return new NoopScheduledFuture<>();
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
        grpcHeaders.set(GRPC_ACCEPT_ENCODING, IDENTITY);
        customMetadata.forEach(grpcHeaders::set);
        if (messageEncoding != null) {
            grpcHeaders.set(messageEncoding);
        }

        final var http2Headers = Http2Headers.create(grpcHeaders);

        streamWriter.writeHeaders(
                http2Headers,
                streamId,
                Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                flowControl);
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
        @NonNull private GrpcStatus grpcStatus = GrpcStatus.OK;
        @Nullable private String statusMessage;
        @NonNull private final List<Header> customMetadata = emptyList(); // Never set

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
                    Http2Flag.HeaderFlags.create(
                            Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                    flowControl);
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
        protected void send(
                @NonNull final WritableHeaders<?> httpHeaders,
                @NonNull final Http2Headers http2Headers) {
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
        @Override
        public void onSubscribe(@NonNull final Flow.Subscription subscription) {
            // FUTURE: Add support for flow control
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(@NonNull final Bytes response) {
            try {
                final int length = (int) response.length();
                final var bufferData = BufferData.create(5 + length);
                bufferData.write(0); // 0 means no compression
                bufferData.writeUnsignedInt32(length);
                bufferData.write(response.toByteArray());
                final var header =
                        Http2FrameHeader.create(
                                bufferData.available(),
                                Http2FrameTypes.DATA,
                                Http2Flag.DataFlags.create(0),
                                streamId);

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
        }

        @Override
        public void onComplete() {
            new TrailerBuilder().send();

            deadlineFuture.cancel(false);

            currentStreamState.getAndUpdate(
                    currentValue -> {
                        if (requireNonNull(currentValue) == Http2StreamState.OPEN) {
                            return Http2StreamState.HALF_CLOSED_LOCAL;
                        }
                        return Http2StreamState.CLOSED;
                    });
        }
    }

    /** Simple implementation of the {@link ServiceInterface.RequestOptions} interface. */
    private record Options(
            Optional<String> authority, boolean isProtobuf, boolean isJson, String contentType)
            implements ServiceInterface.RequestOptions {}

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
