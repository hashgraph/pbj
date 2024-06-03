package com.hedera.pbj.grpc.helidon;

import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ACCEPT_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_TIMEOUT;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_JSON;
import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC_PROTO;
import static java.lang.System.Logger.Level.ERROR;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaTypes;
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
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Implementation of gRPC relying on PBJ. This class specifically contains the glue logic for bridging between
 * Helidon and the generated PBJ service handler endpoints. An instance of this class is created for each new
 * connection, and each connection is made to a specific method endpoint.
 */
final class PbjProtocolHandler implements Http2SubProtocolSelector.SubProtocolHandler {
    private static final System.Logger LOGGER = System.getLogger(PbjProtocolHandler.class.getName());
    private static final String IDENTITY = "identity";

    private static final Header GRPC_ENCODING_IDENTITY = HeaderValues.createCached("grpc-encoding", IDENTITY);

    private static final String GRPC_TIMEOUT_REGEX = "(\\d{1,8})([HMSmun])";
    private static final Pattern GRPC_TIMEOUT_PATTERN = Pattern.compile(GRPC_TIMEOUT_REGEX);

    // Helidon-specific fields related to the connection itself
    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final StreamFlowControl flowControl;
    private Http2StreamState currentStreamState;

    /**
     * The service method that this connection was created for. The route has information about the
     * {@link ServiceInterface} and method to invoke.
     */
    private final PbjMethodRoute route;
    /**
     * If there is a timeout defined for the request, then this detector is used to determine when the timeout
     * deadline has been met. The detector runs on a background thread/timer.
     */
    private final DeadlineDetector deadlineDetector;
    /**
     * A future representing the background task detecting deadlines. If there is a deadline, then this future will
     * represent the task that will be executed when the deadline is reached. If there is no deadline, then we default
     * to a non-null no-op future that exists in the infinite future.
     */
    private ScheduledFuture<?> deadlineFuture;
    /** The current index into {@link #entityBytes} into which data is to be read. */
    private int entityBytesIndex = 0;
    /**
     * The bytes of the next incoming message. This is created dynamically as a message is received, and is never
     * larger than the system configured {@link PbjConfigBlueprint#maxMessageSize()}.
     */
    private byte[] entityBytes = null;
    private Flow.Subscriber<? super Bytes> incoming;
    private Flow.Subscriber<? super Bytes> outgoing;

    /** Create a new instance */
    PbjProtocolHandler(final @NonNull Http2Headers headers,
                       final @NonNull Http2StreamWriter streamWriter,
                       final int streamId,
                       final @NonNull StreamFlowControl flowControl,
                       final @NonNull Http2StreamState currentStreamState,
                       final @NonNull PbjMethodRoute route,
                       final @NonNull DeadlineDetector deadlineDetector) {
        this.headers = requireNonNull(headers);
        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.flowControl = requireNonNull(flowControl);
        this.currentStreamState = requireNonNull(currentStreamState);
        this.route = requireNonNull(route);
        this.deadlineDetector = requireNonNull(deadlineDetector);
    }

    /**
     * Called at the very beginning of the request, before any data has arrived. At this point we can look at the
     * request headers and determine whether we have a valid request, and do any other initialization we need ot.
     */
    @Override
    public void init() {
        try {
            // If Content-Type does not begin with "application/grpc", gRPC servers SHOULD respond with HTTP status of
            // 415 (Unsupported Media Type). This will prevent other HTTP/2 clients from interpreting a gRPC error
            // response, which uses status 200 (OK), as successful.
            // See https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
            // In addition, "application/grpc" is interpreted as "application/grpc+proto".
            final var httpHeaders = headers.httpHeaders();
            final var contentTypeMediaType = httpHeaders.contentType().orElse(HttpMediaTypes.PLAINTEXT_UTF_8);
            final var ct = contentTypeMediaType.text();
            final var contentType = switch(ct) {
                case APPLICATION_GRPC, APPLICATION_GRPC_PROTO -> APPLICATION_GRPC_PROTO;
                case APPLICATION_GRPC_JSON -> APPLICATION_GRPC_JSON;
                default -> {
                    if (ct.startsWith(APPLICATION_GRPC)) {
                        yield ct;
                    }
                    throw new FatalGrpcException(Status.UNSUPPORTED_MEDIA_TYPE_415);
                }
            };

            // This implementation currently only supports "identity" and "gzip" compression. This implementation
            // only supports receiving compressed / encoded messages, it always responds with "identity" messages.
            // This could be extended in the future. Ideally we'd respond with the same compression that was sent to us.
            //
            // As per the documentation:
            // If a client message is compressed by an algorithm that is not supported by a server, the message will
            // result in an UNIMPLEMENTED error status on the server. The server will include a grpc-accept-encoding
            // header [in] the response which specifies the algorithms that the server accepts.
            final var encodingHeader = httpHeaders.value(GRPC_ENCODING).orElse(IDENTITY);
            if (!IDENTITY.equals(encodingHeader)) {
                throw new FatalGrpcException(h -> {
                    h.set(Http2Headers.STATUS_NAME, Status.OK_200.code());
                    h.set(GrpcStatus.UNIMPLEMENTED);
                    h.set(GRPC_ACCEPT_ENCODING, IDENTITY);
                });
            }

            // If the grpc-timeout header is present, determine when that timeout would occur, or default to a future
            // that is so far in the future it will never happen.
            final var timeout = httpHeaders.value(GRPC_TIMEOUT);
            deadlineFuture = timeout.isPresent()
                    ? scheduleDeadline(timeout.get())
                    : new NoopScheduledFuture();

            // Future: Should we support custom metadata, we would extract it here and pass it along via "options".

            // Create the "options" to make available to the ServiceInterface. These options are used to decide on the
            // best way to parse or handle the request.
            final var options = new Options(
                    contentType.equals(APPLICATION_GRPC_PROTO),
                    contentType.equals(APPLICATION_GRPC_JSON),
                    contentType);

            // Call the ServiceInterface to let it know of the new connection.
            outgoing = new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // No flow control here!
                }

                @Override
                public void onNext(Bytes item) {
                    onSendMessage(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    LOGGER.log(ERROR, "Failed to send response", throwable);
                    close();
                }

                @Override
                public void onComplete() {
                    close();
                }
            };

            incoming = route.service().open(options, route.method(), outgoing);

            // Send the headers to the client. This is the first thing we do, and it is done before any data is sent.
            final var responseHeaders = WritableHeaders.create();
            responseHeaders.contentType(contentTypeMediaType);
            responseHeaders.set(GRPC_ENCODING_IDENTITY);
            responseHeaders.set(GrpcStatus.OK);
            final var http2Headers = Http2Headers.create(responseHeaders);
            http2Headers.status(io.helidon.http.Status.OK_200);
            streamWriter.writeHeaders(http2Headers,
                    streamId,
                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                    flowControl.outbound());
        } catch (final FatalGrpcException grpcException) {
            // The request was bad. We need to respond with the appropriate status code and close the stream.
            // This doesn't involve any logging (but could involve some metrics, so we track the number of failed
            // requests).
            // FUTURE Increment a metric counter for failed requests and of different types of failures
            final WritableHeaders<?> writable = WritableHeaders.create();
            grpcException.headerCallback().accept(writable);
            final var http2Headers = Http2Headers.create(writable);
            streamWriter.writeHeaders(http2Headers,
                    streamId,
                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                    FlowControl.Outbound.NOOP);
            currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
        } catch (final Exception unknown) {
            LOGGER.log(ERROR, "Failed to initialize grpc protocol handler", unknown);
            throw new RuntimeException(unknown);
        }
    }

    @Override
    public Http2StreamState streamState() {
        return currentStreamState;
    }

    @Override
    public void rstStream(Http2RstStream rstStream) {
        // Nothing to do
    }

    @Override
    public void windowUpdate(Http2WindowUpdate update) {
        // Nothing to do
    }

    /**
     * Called by the webserver whenever some additional data is available on the stream. The data comes in chunks,
     * it may be that an entire message is available in the chunk, or it may be that the data is broken out over
     * multiple chunks.
     */
    @Override
    public void data(Http2FrameHeader header, BufferData data) {
        try {
            while (data.available() > 0) {
                // First chunk of data contains the compression flag and the length of the message
                if (entityBytes == null) {
                    // Read whether this message is compressed. We do not currently support compression.
                    final var isCompressed = (data.read() == 1);
                    if (isCompressed) {
                        // TODO Proper logging and error handling
                        throw new IllegalArgumentException("Compression is not supported");
                    }
                    // Read the length of the message. As per the grpc protocol specification, each message on the
                    // wire is prefixed with the number of bytes for the message. However, to prevent a DOS attack
                    // where the attacker sends us a very large length and exhausts our memory, we have a maximum
                    // message size configuration setting. Using that, we can detect attempts to exhaust our memory.
                    final long length = data.readUnsignedInt32();
                    if (length > PbjConfigBlueprint.DEFAULT_MAX_MESSAGE_SIZE) {
                        // TODO Proper logging and error handling
                        throw new IllegalArgumentException("Message size exceeds maximum allowed size: " +
                                length + " > " + PbjConfigBlueprint.DEFAULT_MAX_MESSAGE_SIZE);
                    }
                    // Create a buffer to hold the message. We sadly cannot reuse this buffer because once we have
                    // filled it and wrapped it in Bytes and sent it to the handler, some user code may grab and hold
                    // that Bytes object for an arbitrary amount of time, and if we were to scribble into the same
                    // byte array, we would break the application. So we need a new buffer each time :-(
                    entityBytes = new byte[(int) length];
                    entityBytesIndex = 0;
                }

                // By the time we get here, entityBytes is no longer null. It may be empty, or it may already have
                // been partially populated from a previous iteration. It may be that the number of bytes available
                // to be read is larger than just this one message. So we need to be careful to read, from what is
                // available, only up to the message length, and to leave the rest for the next iteration.
                final int available = data.available();
                final int numBytesToRead = Math.min(entityBytes.length - entityBytesIndex, available);
                data.read(entityBytes, entityBytesIndex, numBytesToRead);
                entityBytesIndex += numBytesToRead;

                // If we have completed reading the message, then we can proceed.
                if (entityBytesIndex == entityBytes.length) {
                    // Grab and wrap the bytes and reset to being reading the next message
                    final var bytes = Bytes.wrap(entityBytes);
                    incoming.onNext(bytes);
                    entityBytesIndex = 0;
                    entityBytes = null;
                }
            }

            // The end of the stream has been reached! It is possible that a bad client will send end of stream before
            // all the message data we sent. In that case, it is as if the message were never sent.
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                entityBytesIndex = 0;
                entityBytes = null;
                currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
                incoming.onComplete();
            }
        } catch (final Exception e) {
            LOGGER.log(ERROR, "Failed to process grpc request: " + data.debugDataHex(true), e);
            incoming.onError(e);
            outgoing.onError(e); // TODO Not sure which.... both?
        }
    }

    /**
     * Sends the given message to the client
     * @param response The bytes to send.
     */
    public void onSendMessage(final @NonNull Bytes response) {
        try {
            final int length = (int) response.length();
            final var bufferData = BufferData.create(5 + length);
            bufferData.write(0); // 0 means no compression
            bufferData.writeUnsignedInt32(length);
            bufferData.write(response.toByteArray());

            final var header = Http2FrameHeader.create(bufferData.available(),
                    Http2FrameTypes.DATA,
                    Http2Flag.DataFlags.create(0),
                    streamId);

            streamWriter.writeData(new Http2FrameData(header, bufferData), flowControl.outbound());
        } catch (final Exception e) {
            LOGGER.log(ERROR, "Failed to respond to grpc request: " + route.method(), e);
        }
    }

    private synchronized void close() {
        final var responseHeaders = WritableHeaders.create();
        // Canceling a future that has already completed has no effect. So by canceling here, we are saying:
        // "If you have not yet executed, never execute. If you have already executed, then just ignore me".
        // The "isCancelled" flag is set if the future was canceled before it was executed.
        deadlineFuture.cancel(false);
        // If the deadline was canceled, then we have not yet responded to the client. So the response is OK. On the
        // other hand, if th deadline was NOT canceled, then the deadline was exceeded.
//        if (!deadlineFuture.isCancelled()) {
            responseHeaders.set(GrpcStatus.OK);
//        } else {
//            responseHeaders.set(GrpcStatus.DEADLINE_EXCEEDED);
//        }
        final var http2Headers = Http2Headers.create(responseHeaders);
        streamWriter.writeHeaders(http2Headers,
                streamId,
                Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                flowControl.outbound());
        currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
    }

    private ScheduledFuture<?> scheduleDeadline(final @NonNull String timeout) {
        final var matcher = GRPC_TIMEOUT_PATTERN.matcher(timeout);
        if (matcher.matches()) {
            final var num = Integer.parseInt(matcher.group(0));
            final var unit = matcher.group(1);
            final var deadline = System.nanoTime() + num * switch (unit) {
                case "H" -> 3600_000_000_000L;
                case "M" -> 60_000_000_000L;
                case "S" -> 1_000_000_000L;
                case "m" -> 1_000_000L;
                case "u" -> 1_000L;
                case "n" -> 1L;
                default -> throw new IllegalArgumentException("Invalid unit: " + unit);
            };
            return deadlineDetector.scheduleDeadline(deadline, this::close);
        }

        return new NoopScheduledFuture();
    }

    /**
     * Simple implementation of the {@link ServiceInterface.RequestOptions} interface.
     */
    private record Options(boolean isProtobuf, boolean isJson, String contentType)
            implements ServiceInterface.RequestOptions {
    }

    /**
     * A {@link ScheduledFuture} that does nothing. This is used when there is no deadline set for the request.
     * A new instance of this must be created (or we need a "reset" method) for each {@link PbjProtocolHandler}
     * instance, because it can become "corrupted" if canceled from any particular call.
     */
    private static final class NoopScheduledFuture extends CompletableFuture<Void> implements ScheduledFuture<Void> {
        @Override
        public long getDelay(final @NonNull TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(final @NonNull Delayed o) {
            // Since all NoopScheduledFuture instances have "0" as the delay, any other Delayed instance with a non-0
            // delay will come after this one.
            return (int) (o.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
