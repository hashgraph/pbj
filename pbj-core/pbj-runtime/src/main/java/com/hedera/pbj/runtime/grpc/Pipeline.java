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
     * {@inheritDoc}
     * @throws RuntimeException if an error occurs while trying to write data to the pipeline
     */
    @Override
    default void onNext(T item) throws RuntimeException {}
}
