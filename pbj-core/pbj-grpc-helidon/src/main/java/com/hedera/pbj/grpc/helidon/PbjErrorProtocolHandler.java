package com.hedera.pbj.grpc.helidon;

import io.helidon.common.buffers.BufferData;
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
import java.util.function.Consumer;

class PbjErrorProtocolHandler implements Http2SubProtocolSelector.SubProtocolHandler {
        private final Http2StreamWriter streamWriter;
        private final int streamId;
        private final Consumer<WritableHeaders<?>> headerCallback;
        private Http2StreamState currentStreamState;

    PbjErrorProtocolHandler(final Http2StreamWriter streamWriter,
                            final int streamId,
                            final Http2StreamState currentStreamState,
                            final Consumer<WritableHeaders<?>> headerCallback) {
            this.streamWriter = streamWriter;
            this.streamId = streamId;
            this.currentStreamState = currentStreamState;
            this.headerCallback = headerCallback;
        }

    @Override
    public void init() {
        WritableHeaders<?> writable = WritableHeaders.create();
        headerCallback.accept(writable);
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
