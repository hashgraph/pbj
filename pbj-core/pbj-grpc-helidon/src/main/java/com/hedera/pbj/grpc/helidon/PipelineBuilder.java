package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

public class PipelineBuilder {

    private final PbjMethodRoute route;
    private final ServiceInterface.RequestOptions options;
    private final Pipeline<Bytes> outgoing;

    PipelineBuilder(
            @NonNull final PbjMethodRoute route,
            @NonNull final ServiceInterface.RequestOptions options,
            @NonNull final Pipeline<Bytes> outgoing) {
        this.route = requireNonNull(route);
        this.options = requireNonNull(options);
        this.outgoing = requireNonNull(outgoing);
    }

    public Pipeline<? super Bytes> createPipeline() {
        // Setup the subscribers. The "outgoing" subscriber will send messages to the client.
        // This is given to the "open" method on the service to allow it to send messages to
        // the client.
        return route.service().open(route.method(), options, outgoing);
    }
}
