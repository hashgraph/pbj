package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.HttpException;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;

import static java.lang.System.Logger.Level.ERROR;
import static java.util.Objects.requireNonNull;

public class PipelineBuilder {

    private final System.Logger LOGGER = System.getLogger(this.getClass().getName());

    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final StreamFlowControl flowControl;
    private final PbjMethodRoute route;
    private final ServiceInterface.RequestOptions options;
    private final Pipeline<Bytes> outgoing;
    private final GrpcDataProcessor grpcDataProcessor;
    private final HeadersProcessor headersProcessor;

    PipelineBuilder(
            @NonNull final Http2StreamWriter streamWriter,
            final int streamId,
            @NonNull final StreamFlowControl flowControl,
            @NonNull final PbjMethodRoute route,
            @NonNull final ServiceInterface.RequestOptions options,
            @NonNull final Pipeline<Bytes> outgoing,
            @NonNull final GrpcDataProcessor grpcDataProcessor,
            @NonNull final HeadersProcessor headersProcessor) {
        this.streamWriter = requireNonNull(streamWriter);
        this.streamId = streamId;
        this.flowControl = requireNonNull(flowControl);
        this.route = requireNonNull(route);
        this.options = requireNonNull(options);
        this.outgoing = requireNonNull(outgoing);
        this.grpcDataProcessor = requireNonNull(grpcDataProcessor);
        this.headersProcessor = requireNonNull(headersProcessor);
    }

    public Pipeline<? super Bytes> createPipeline() {
        // Setup the subscribers. The "outgoing" subscriber will send messages to the client.
        // This is given to the "open" method on the service to allow it to send messages to
        // the client.
        try {
            return route.service().open(route.method(), options, outgoing);
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
                    .grpcStatus(GrpcStatus.UNKNOWN).send();
            error();
        }

        return null;
    }

    private void error() {
        headersProcessor.cancelDeadlineFuture(false);
        grpcDataProcessor.setCurrentStreamState(current -> Http2StreamState.CLOSED);
    }
}
