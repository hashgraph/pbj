package com.hedera.pbj.grpc.helidon;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

import java.util.Objects;

/**
 * A handler for the case where the path is not found.
 */
final class RouteNotFoundHandler
        implements Http2SubProtocolSelector.SubProtocolHandler {
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private Http2StreamState currentStreamState;

    /**
     * Constructor
     * @param streamWriter the stream writer
     * @param streamId the stream id
     * @param currentStreamState the current stream state
     */
    RouteNotFoundHandler(
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final Http2StreamState currentStreamState) {
        this.streamWriter = Objects.requireNonNull(streamWriter);
        this.streamId = streamId;
        this.currentStreamState = Objects.requireNonNull(currentStreamState);
    }

    @Override
    public void init() {
        final WritableHeaders<?> writable = WritableHeaders.create();
        writable.set(Http2Headers.STATUS_NAME, Status.OK_200.code());
        writable.set(GrpcHeaders.NOT_FOUND);
        final Http2Headers http2Headers = Http2Headers.create(writable);
        streamWriter.writeHeaders(
                http2Headers,
                streamId,
                Http2Flag.HeaderFlags.create(
                        Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                FlowControl.Outbound.NOOP);
        currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
    }

    @Override
    @NonNull
    public Http2StreamState streamState() {
        return currentStreamState;
    }

    @Override
    public void rstStream(@NonNull final Http2RstStream rstStream) {
        // No-op
    }

    @Override
    public void windowUpdate(@NonNull final Http2WindowUpdate update) {
        // No-op
    }

    @Override
    public void data(@NonNull final Http2FrameHeader header, @NonNull final BufferData data) {
        // No-op
    }
}
