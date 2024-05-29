package com.hedera.pbj.grpc.helidon;

import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_ENCODING;
import static com.hedera.pbj.grpc.helidon.GrpcHeaders.GRPC_TIMEOUT;
import static java.lang.System.Logger.Level.ERROR;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.grpc.helidon.encoding.Encoding;
import com.hedera.pbj.runtime.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
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
    private static final Header GRPC_ENCODING_IDENTITY = HeaderValues.createCached("grpc-encoding", "identity");

    private static final HttpMediaType APPLICATION_GRPC = HttpMediaType.create("application/grpc");
    private static final HttpMediaType APPLICATION_GRPC_PROTO = HttpMediaType.create("application/grpc+proto");
    private static final HttpMediaType APPLICATION_GRPC_JSON = HttpMediaType.create("application/grpc+json");

    private static final String GRPC_TIMEOUT_REGEX = "(\\d{1,8})([HMSmun])";
    private static final Pattern GRPC_TIMEOUT_PATTERN = Pattern.compile(GRPC_TIMEOUT_REGEX);

    // Helidon-specific fields related to the connection itself
    private final HttpPrologue prologue;
    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final Http2Settings serverSettings;
    private final Http2Settings clientSettings;
    private final StreamFlowControl flowControl;
    private Http2StreamState currentStreamState;

    /** The service method that this connection was created for */
    private final PbjMethodRoute route;
    /**
     * If there is a timeout defined for the request, then this detected is used to determine when the timeout
     * deadline has been met.
     */
    private final DeadlineDetector deadlineDetector;
    /** A future representing the background task detecting deadlines. */
    private ScheduledFuture<?> deadlineFuture = new NoopScheduledFuture();
    /** Whether the next incoming message is compressed. */
    private boolean isCompressed;
    /** The encoding as determined by the grpc-encoding header. Will not be null. */
    private Encoding encoding;
    /** The current index into {@link #entityBytes} into which data is to be read. */
    private int entityBytesIndex = 0;
    /**
     * The bytes of the next incoming message. This is created dynamically as a message is received, and is never
     * larger than the system configured {@link PbjConfigBlueprint#maxMessageSize()}.
     */
    private byte[] entityBytes = null;
    private BlockingQueue<Bytes> incomingMessages;

    /** Create a new instance */
    PbjProtocolHandler(final @NonNull HttpPrologue prologue,
                       final @NonNull Http2Headers headers,
                       final @NonNull Http2StreamWriter streamWriter,
                       final int streamId,
                       final @NonNull Http2Settings serverSettings,
                       final @NonNull Http2Settings clientSettings,
                       final @NonNull StreamFlowControl flowControl,
                       final @NonNull Http2StreamState currentStreamState,
                       final @NonNull PbjMethodRoute route,
                       final @NonNull DeadlineDetector deadlineDetector) {

        this.prologue = requireNonNull(prologue);
        this.headers = requireNonNull(headers);
        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.serverSettings = requireNonNull(serverSettings);
        this.clientSettings = requireNonNull(clientSettings);
        this.flowControl = requireNonNull(flowControl);
        this.currentStreamState = requireNonNull(currentStreamState);
        this.route = requireNonNull(route);
        this.deadlineDetector = requireNonNull(deadlineDetector);
    }

    @Override
    public void init() {
        try {
            // If the grpc-timeout header is present, determine when that timeout would occur.
            final var timeout = headers.httpHeaders().value(GRPC_TIMEOUT);
            if (timeout.isPresent()) {
                final var matcher = GRPC_TIMEOUT_PATTERN.matcher(timeout.get());
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
                    deadlineFuture = deadlineDetector.scheduleDeadline(deadline, () -> {
                        close(GrpcStatus.DEADLINE_EXCEEDED);
                    });
                }
            }

            // Get the encoding to use. We always use one, even if it is just "identity". This implementation currently
            // only supports receiving compressed / encoded messages, it always responds with "identity" messages.
            // This could be modified in the future, there is no reason not to support compression.
            final var encodingHeader = headers.httpHeaders().value(GRPC_ENCODING).orElse("identity");
            encoding = switch (encodingHeader) {
                case "identity" -> Encoding.IDENTITY;
                case "gzip" -> Encoding.GZIP;
                default -> throw new IllegalArgumentException("Unsupported encoding: " + encodingHeader);
            };

            // We know the content type has been set and starts with "application/grpc", otherwise this handler would
            // not have been called. But we don't know whether it is "application/grpc" or "application/grpc+proto" or
            // "application/grpc+json". Normalize "application/grpc" to "application/grpc+proto", and otherwise just
            // pass whatever the content type is along to the service handler. Maybe it will support something
            final var contentType = headers.httpHeaders().contentType().orElseThrow();
            final var normalizedContentType = contentType.equals(APPLICATION_GRPC) ? APPLICATION_GRPC_PROTO : contentType;
            final var contentSubType = normalizedContentType.subtype();
            final var contentTypeExt = contentSubType.substring(contentSubType.indexOf('+') + 1);

            // todo Extract any custom metadata to pass along as well

            // Create the "options" to make available to the service handler. These options are used by the service
            // handler to decide on the best way to parse or handle the request.
            final var options = new ServiceInterface.RequestOptions() {
                @Override
                public boolean isProtobuf() {
                    return contentTypeExt.equals(ServiceInterface.RequestOptions.APPLICATION_GRPC_PROTO);
                }

                @Override
                public boolean isJson() {
                    return contentTypeExt.equals(ServiceInterface.RequestOptions.APPLICATION_GRPC_JSON);
                }

                @Override
                public String contentType() {
                    return contentTypeExt;
                }
            };

            incomingMessages = new ArrayBlockingQueue<>(10); // TODO Take from config
            route.service().open(options, route.method(), incomingMessages, new ServiceInterface.ResponseCallback() {
                @Override
                public void start() {
                    // todo ignoring headers, just sending required response headers
                    WritableHeaders<?> writable = WritableHeaders.create();
                    writable.set(HeaderNames.CONTENT_TYPE, normalizedContentType.text()); // Respond with the same content type we received
                    writable.set(GRPC_ENCODING_IDENTITY);

                    Http2Headers http2Headers = Http2Headers.create(writable);
                    http2Headers.status(io.helidon.http.Status.OK_200);
                    streamWriter.writeHeaders(http2Headers,
                            streamId,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                            flowControl.outbound());
                }

                @Override
                public void send(Bytes response) {
                    try {
                        final int length = (int) response.length();
                        BufferData bufferData = BufferData.create(5 + length);
                        bufferData.write(0);
                        bufferData.writeUnsignedInt32(length);
                        bufferData.write(response.toByteArray());

                        // todo flags based on method type
                        // end flag should be sent when last message is sent (or just rst stream if we cannot determine this)

                        Http2FrameHeader header = Http2FrameHeader.create(bufferData.available(),
                                Http2FrameTypes.DATA,
                                Http2Flag.DataFlags.create(0),
                                streamId);

                        streamWriter.writeData(new Http2FrameData(header, bufferData), flowControl.outbound());
                    } catch (Exception e) {
                        LOGGER.log(ERROR, "Failed to respond to grpc request: " + route.method(), e);
                    }

                }

                @Override
                public void close() {
                    // If the deadline has not already been reached, then go ahead and cancel it.
                    deadlineFuture.cancel(false);
                    // If the deadline was not canceled, then it means it was already done before we got here,
                    // which means a separate close has already happened, so we cannot close again.
                    if (!deadlineFuture.isCancelled()) {
                        PbjProtocolHandler.this.close(GrpcStatus.OK);
                    }
                }
            });
        } catch (Throwable e) {
            LOGGER.log(ERROR, "Failed to initialize grpc protocol handler", e);
            throw e;
        }

    }

    @Override
    public Http2StreamState streamState() {
        return currentStreamState;
    }

    @Override
    public void rstStream(Http2RstStream rstStream) {
//        listener.onComplete();
    }

    @Override
    public void windowUpdate(Http2WindowUpdate update) {

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
                    isCompressed = (data.read() == 1);
                    if (isCompressed) {
                        // TODO Proper logging and error handling
                        throw new IllegalArgumentException("Compression is not supported");
                    }
                    // Read the length of the message. As per the grpc protocol specification, each message on the
                    // wire is prefixed with the number of bytes for the message. However, to prevent a DOS attack
                    // where the attacker sends us a very large length and exhausts our memory, we have a maximum
                    // message size configuration setting. Using that, we can detect attempts to exhaust our memory.
                    final long length = data.readUnsignedInt32();
                    if (length > PbjConfigBlueprint.DEFAULT_MAX_MESSAGE_SIZE) { // TODO Needs proper config
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
                    incomingMessages.put(bytes);
                    entityBytesIndex = 0;
                    entityBytes = null;
                }
            }

            // The end of the stream has been reached! It is possible that a bad client will send end of stream before
            // all the message data we sent. In that case, it is as if the message were never sent.
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                entityBytesIndex = 0;
                entityBytes = null;
//                listener.onHalfClose();
                currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
            }
        } catch (Exception e) {
            LOGGER.log(ERROR, "Failed to process grpc request: " + data.debugDataHex(true), e);
        }
    }

    private void close(Header status) {
        WritableHeaders<?> writable = WritableHeaders.create();
        writable.set(status);

        Http2Headers http2Headers = Http2Headers.create(writable);
        streamWriter.writeHeaders(http2Headers,
                streamId,
                Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                flowControl.outbound());
        currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
    }

    private static final class NoopScheduledFuture extends CompletableFuture<Void> implements ScheduledFuture<Void> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return 0;
        }
    }
}
