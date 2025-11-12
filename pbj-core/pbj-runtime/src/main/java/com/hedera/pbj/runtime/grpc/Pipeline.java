// SPDX-License-Identifier: Apache-2.0
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
    default void clientEndStreamReceived() {}

    /**
     * Closes the network connection over which this Pipeline transmits data.
     * Certain transport protocols, such as HTTP2, allow serving multiple requests over the same network connection
     * via "streams". Calling this method will close any and all open streams running over the associated connection,
     * terminating any other affected requests/Pipelines. Under normal circumstances, applications should prefer
     * to use `onComplete()` instead, which would only affect the stream associated with this Pipeline instance.
     */
    default void closeConnection() {}

    /**
     * {@inheritDoc}
     * @throws RuntimeException if an error occurs while trying to write data to the pipeline
     */
    @Override
    default void onNext(T item) throws RuntimeException {}
}
