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

import static com.hedera.pbj.grpc.helidon.Constants.GRPC_ENCODING_IDENTITY;
import static com.hedera.pbj.grpc.helidon.Constants.IDENTITY;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ACCEPT_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_TIMEOUT;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_JSON;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_PROTO;
import static java.lang.System.Logger.Level.ERROR;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpException;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class HeadersProcessorImpl implements HeadersProcessor {
    private final System.Logger LOGGER = System.getLogger(this.getClass().getName());

    /** The regular expression used to parse the grpc-timeout header. */
    private static final String GRPC_TIMEOUT_REGEX = "(\\d{1,8})([HMSmun])";

    private static final Pattern GRPC_TIMEOUT_PATTERN = Pattern.compile(GRPC_TIMEOUT_REGEX);

    /**
     * A future representing the background task detecting deadlines. If there is a deadline, then
     * this future will represent the task that will be executed when the deadline is reached. If
     * there is no deadline, then we default to a non-null no-op future that exists in the infinite
     * future.
     *
     * <p>Method calls on this object are thread-safe.
     */
    private ScheduledFuture<?> deadlineFuture;

    /**
     * If there is a timeout defined for the request, then this detector is used to determine when
     * the timeout deadline has been met. The detector runs on a background thread/timer.
     */
    private final DeadlineDetector deadlineDetector;

    /**
     * The service method that this connection was created for. The route has information about the
     * {@link ServiceInterface} and method to invoke, as well as metrics, and other information.
     */
    private final PbjMethodRoute route;

    private final Http2StreamWriter streamWriter;
    private final StreamFlowControl flowControl;
    private final int streamId;
    private ServiceInterface.RequestOptions options;
    private final GrpcDataProcessor grpcDataProcessor;
    private Pipeline<? super Bytes> pipeline;

    HeadersProcessorImpl(
            @NonNull final Http2Headers headers,
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final StreamFlowControl flowControl,
            @NonNull final PbjMethodRoute route,
            @NonNull final DeadlineDetector deadlineDetector,
            @NonNull final GrpcDataProcessor grpcDataProcessor) {

        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.flowControl = requireNonNull(flowControl);
        this.route = requireNonNull(route);
        this.deadlineDetector = requireNonNull(deadlineDetector);
        this.grpcDataProcessor = requireNonNull(grpcDataProcessor);

        try {
            // If Content-Type does not begin with "application/grpc", gRPC servers SHOULD respond
            // with HTTP status of 415 (Unsupported Media Type). This will prevent other HTTP/2
            // clients from interpreting a gRPC error response, which uses status 200 (OK), as
            // successful.
            // See https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
            // In addition, "application/grpc" is interpreted as "application/grpc+proto".
            final var requestHeaders = headers.httpHeaders();
            final var requestContentType =
                    requestHeaders.contentType().orElse(HttpMediaTypes.PLAINTEXT_UTF_8);
            final var ct = requestContentType.text();
            final var contentType =
                    switch (ct) {
                        case APPLICATION_GRPC, APPLICATION_GRPC_PROTO -> APPLICATION_GRPC_PROTO;
                        case APPLICATION_GRPC_JSON -> APPLICATION_GRPC_JSON;
                        default -> {
                            if (ct.startsWith(APPLICATION_GRPC)) {
                                yield ct;
                            }
                            throw new HttpException(
                                    "Unsupported", Status.UNSUPPORTED_MEDIA_TYPE_415);
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
            final var encodingHeader = requestHeaders.value(GRPC_ENCODING).orElse(IDENTITY);
            if (!IDENTITY.equals(encodingHeader)) {
                throw new GrpcException(GrpcStatus.UNIMPLEMENTED);
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
            options =
                    new Options(
                            Optional.ofNullable(headers.authority()), // the client (see http2 spec)
                            contentType.equals(APPLICATION_GRPC_PROTO),
                            contentType.equals(APPLICATION_GRPC_JSON),
                            contentType);

        } catch (final GrpcException grpcException) {
            route.failedGrpcRequestCounter().increment();
            new TrailerOnlyBuilder(streamWriter, streamId, flowControl)
                    .grpcStatus(grpcException.status())
                    .statusMessage(grpcException.getMessage())
                    .send();
            error();
        } catch (final HttpException httpException) {
            route.failedHttpRequestCounter().increment();
            new TrailerOnlyBuilder(streamWriter, streamId, flowControl)
                    .httpStatus(httpException.status())
                    .grpcStatus(GrpcStatus.INVALID_ARGUMENT)
                    .send();
            error();
        } catch (final Exception unknown) {
            route.failedUnknownRequestCounter().increment();
            LOGGER.log(ERROR, "Failed to initialize grpc protocol handler", unknown);
            new TrailerOnlyBuilder(streamWriter, streamId, flowControl)
                    .grpcStatus(GrpcStatus.UNKNOWN)
                    .send();
            error();
        }
    }

    public void setPipeline(@NonNull final Pipeline<? super Bytes> pipeline) {
        this.pipeline = requireNonNull(pipeline);
    }

    public void cancelDeadlineFuture(boolean isCancelled) {
        deadlineFuture.cancel(isCancelled);
    }

    public ServiceInterface.RequestOptions options() {
        return options;
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
        // FUTURE: I think to support custom headers in the response, we would have to list them
        // here.
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
                flowControl.outbound());
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

    /**
     * An error has occurred. Cancel the deadline future if it's still active, and set the stream
     * state accordingly.
     *
     * <p>May be called by different threads concurrently.
     */
    private void error() {
        // Canceling a future that has already completed has no effect. So by canceling here, we are
        // saying:
        // "If you have not yet executed, never execute. If you have already executed, then just
        // ignore me".
        // The "isCancelled" flag is set if the future was canceled before it was executed.

        // cancel is threadsafe
        cancelDeadlineFuture(false);
        grpcDataProcessor.setCurrentStreamState(current -> Http2StreamState.CLOSED);
    }

    /** Simple implementation of the {@link ServiceInterface.RequestOptions} interface. */
    private record Options(
            Optional<String> authority, boolean isProtobuf, boolean isJson, String contentType)
            implements ServiceInterface.RequestOptions {}
}
