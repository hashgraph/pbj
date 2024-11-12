package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;

import java.util.concurrent.Flow;

import static java.lang.System.Logger.Level.ERROR;
import static java.util.Objects.requireNonNull;

/**
 * The implementation of {@link Pipeline} used to send messages to the client. It
 * receives bytes from the handlers to send to the client.
 */
final class SendToClientSubscriber implements Pipeline<Bytes> {

    private final System.Logger LOGGER = System.getLogger(this.getClass().getName());

    private Runnable onErrorHandler;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final StreamFlowControl flowControl;
    private final PbjMethodRoute route;
    private final GrpcDataProcessor grpcDataProcessor;
    private final HeadersProcessor headersProcessor;
    private Pipeline<? super Bytes> pipeline;

    SendToClientSubscriber(
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final StreamFlowControl flowControl,
            @NonNull final PbjMethodRoute route,
            @NonNull final GrpcDataProcessor grpcDataProcessor,
            @NonNull final HeadersProcessor headersProcessor) {
        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.flowControl = requireNonNull(flowControl);
        this.route = requireNonNull(route);
        this.grpcDataProcessor = requireNonNull(grpcDataProcessor);
        this.headersProcessor = requireNonNull(headersProcessor);
    }

    public void setPipeline(@NonNull final Pipeline<? super Bytes> pipeline) {
        this.pipeline = requireNonNull(pipeline);
    }

    public Pipeline<Bytes> subscriber() {
        return this;
    }

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

            streamWriter.writeData(
                    new Http2FrameData(header, bufferData), flowControl.outbound());
        } catch (final Exception e) {
            LOGGER.log(ERROR, "Failed to respond to grpc request: " + route.method(), e);
            pipeline.onError(e);
        }
    }

    @Override
    public void onError(@NonNull final Throwable throwable) {
        if (onErrorHandler != null) {
            // Invoke the handlers registered by
            // the application code integrated
            // with the PBJ Helidon Plugin.
            onErrorHandler.run();
        }

        if (throwable instanceof final GrpcException grpcException) {
            new TrailerBuilder(streamWriter, streamId, flowControl)
                    .grpcStatus(grpcException.status())
                    .statusMessage(grpcException.getMessage())
                    .send();
        } else {
            LOGGER.log(ERROR, "Failed to send response", throwable);
            new TrailerBuilder(streamWriter, streamId, flowControl)
                    .grpcStatus(GrpcStatus.INTERNAL).send();
        }

        headersProcessor.cancelDeadlineFuture(false);
        grpcDataProcessor.setCurrentStreamState(current -> Http2StreamState.CLOSED);
    }

    @Override
    public void onComplete() {
        new TrailerBuilder(streamWriter, streamId, flowControl)
                .send();

        headersProcessor.cancelDeadlineFuture(false);

        grpcDataProcessor.setCurrentStreamState(currentValue -> {
            if (requireNonNull(currentValue) == Http2StreamState.OPEN) {
                return Http2StreamState.HALF_CLOSED_LOCAL;
            }
            return Http2StreamState.CLOSED;
        });
    }

    @Override
    public void registerOnErrorHandler(@NonNull final Runnable handler) {
        this.onErrorHandler = requireNonNull(handler);
    }
}