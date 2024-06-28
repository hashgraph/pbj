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

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Thrown by an application when handling a gRPC request if the request fails. The status will be one of the canonical
 * gRPC statuses, and must be specified. This is returned back to the gRPC client. The message is optional and will be
 * returned to the client if specified. The cause is not returned to the client, but is used for debugging purposes.
 */
public class GrpcException extends RuntimeException {
    /** The GRPC Status to return to the client */
    private final @NonNull GrpcStatus status;

    /**
     * Create a new exception with the given status.
     * @param status the status of the exception
     */
    public GrpcException(@NonNull final GrpcStatus status) {
        this(status, null, null);
    }

    /**
     * Create a new exception with the given status and message.
     * @param status the status of the exception
     * @param message the message of the exception
     */
    public GrpcException(@NonNull final GrpcStatus status, @Nullable final String message) {
        this(status, message, null);
    }

    /**
     * Create a new exception with the given status and cause.
     * @param status the status of the exception
     * @param cause the cause of the exception
     */
    public GrpcException(@NonNull final GrpcStatus status, @Nullable final Throwable cause) {
        this(status, null, cause);
    }

    /**
     * Create a new gRPC Exception.
     * @param status the status of the exception
     * @param message the message of the exception
     * @param cause the cause of the exception
     */
    public GrpcException(
            @NonNull final GrpcStatus status, @Nullable final String message, @Nullable final Throwable cause) {
        super(message, cause);
        this.status = requireNonNull(status);
        if (status == GrpcStatus.OK) {
            throw new IllegalArgumentException("status cannot be OK");
        }
    }

    /**
     * Get the status of the exception.
     * @return the status of the exception
     */
    @NonNull
    public final GrpcStatus status() {
        return status;
    }

    /**
     * Get the message of the exception.
     * @return the message of the exception
     */
    @Nullable
    @Override
    public String getMessage() {
        return super.getMessage();
    }
}
