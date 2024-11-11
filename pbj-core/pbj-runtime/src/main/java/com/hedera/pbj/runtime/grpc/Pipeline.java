/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.runtime.grpc;

import java.util.concurrent.Flow;

/**
 * Represents a pipeline of data that is being processed by a gRPC service.
 *
 * @param <T> The subscribed item type
 */
public interface Pipeline<T> extends Flow.Subscriber<T> {
    /** Called when an END_STREAM frame is received from the client. */
    default void clientEndStreamReceived() {}

    default void registerOnErrorHandler(Runnable runnable) {}
}
