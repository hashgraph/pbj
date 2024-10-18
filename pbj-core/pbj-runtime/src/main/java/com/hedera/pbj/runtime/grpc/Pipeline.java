package com.hedera.pbj.runtime.grpc;

import java.util.concurrent.Flow;

/**
 * Represents a pipeline of data that is being processed by a gRPC service.
 *
 * @param <T> The subscribed item type
 */
public interface Pipeline<T> extends Flow.Subscriber<T> {
    /**
     * Called when an END_STREAM frame is received from the client.
     */
    void clientEndStreamReceived();
}
