package com.hedera.pbj.runtime.grpc;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Thrown by an application when handling a gRPC requeset if the request fails.
 */
public class GrpcException extends RuntimeException {
    private final GrpcStatus status;

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

    public GrpcException(@NonNull final GrpcStatus status, @Nullable final Throwable cause) {
        this(status, null, cause);
    }

    public GrpcException(
            @NonNull final GrpcStatus status,
            @Nullable final String message,
            @Nullable final Throwable cause) {
        super(message, cause);
        this.status = requireNonNull(status);
        if (status == GrpcStatus.OK) {
            throw new IllegalArgumentException("status cannot be OK");
        }
    }

    @NonNull
    public final GrpcStatus status() {
        return status;
    }

    @NonNull
    @Override
    public String getMessage() {
        return super.getMessage();
    }
}
