// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static com.hedera.pbj.runtime.grpc.ServiceInterface.RequestOptions.APPLICATION_GRPC;
import static io.helidon.http.HeaderValues.createCached;

import com.hedera.pbj.runtime.grpc.GrpcStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;

/** Constants for gRPC related HTTP2 Headers. */
final class GrpcHeaders {
    static final HeaderName GRPC_TIMEOUT = HeaderNames.create("grpc-timeout");
    static final HeaderName GRPC_ENCODING = HeaderNames.create("grpc-encoding");
    static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");
    static final HeaderName GRPC_MESSAGE = HeaderNames.createFromLowercase("grpc-message");
    static final HeaderName GRPC_STATUS = HeaderNames.createFromLowercase("grpc-status");
    static final HttpMediaType APPLICATION_GRPC_PROTO_TYPE = HttpMediaType.create(APPLICATION_GRPC);

    // A set of Headers with pre-cached values for the different "grpc-status" header.
    static final Header OK = createCached(GRPC_STATUS, GrpcStatus.OK.ordinal());
    static final Header CANCELLED = createCached(GRPC_STATUS, GrpcStatus.CANCELLED.ordinal());
    static final Header UNKNOWN = createCached(GRPC_STATUS, GrpcStatus.UNKNOWN.ordinal());
    static final Header INVALID_ARGUMENT = createCached(GRPC_STATUS, GrpcStatus.INVALID_ARGUMENT.ordinal());
    static final Header DEADLINE_EXCEEDED = createCached(GRPC_STATUS, GrpcStatus.DEADLINE_EXCEEDED.ordinal());
    static final Header NOT_FOUND = createCached(GRPC_STATUS, GrpcStatus.NOT_FOUND.ordinal());
    static final Header ALREADY_EXISTS = createCached(GRPC_STATUS, GrpcStatus.ALREADY_EXISTS.ordinal());
    static final Header PERMISSION_DENIED = createCached(GRPC_STATUS, GrpcStatus.PERMISSION_DENIED.ordinal());
    static final Header RESOURCE_EXHAUSTED = createCached(GRPC_STATUS, GrpcStatus.RESOURCE_EXHAUSTED.ordinal());
    static final Header FAILED_PRECONDITION = createCached(GRPC_STATUS, GrpcStatus.FAILED_PRECONDITION.ordinal());
    static final Header ABORTED = createCached(GRPC_STATUS, GrpcStatus.ABORTED.ordinal());
    static final Header OUT_OF_RANGE = createCached(GRPC_STATUS, GrpcStatus.OUT_OF_RANGE.ordinal());
    static final Header UNIMPLEMENTED = createCached(GRPC_STATUS, GrpcStatus.UNIMPLEMENTED.ordinal());
    static final Header INTERNAL = createCached(GRPC_STATUS, GrpcStatus.INTERNAL.ordinal());
    static final Header UNAVAILABLE = createCached(GRPC_STATUS, GrpcStatus.UNAVAILABLE.ordinal());
    static final Header DATA_LOSS = createCached(GRPC_STATUS, GrpcStatus.DATA_LOSS.ordinal());
    static final Header UNAUTHENTICATED = createCached(GRPC_STATUS, GrpcStatus.UNAUTHENTICATED.ordinal());

    private GrpcHeaders() {
        // prevent instantiation
    }

    /**
     * Maps the given {@link #GRPC_STATUS} to a corresponding {@link Header}.
     *
     * @param status The status.
     * @return The corresponding {@link Header}.
     */
    @NonNull
    static Header header(@NonNull final GrpcStatus status) {
        return switch (status) {
            case OK -> OK;
            case CANCELLED -> CANCELLED;
            case UNKNOWN -> UNKNOWN;
            case INVALID_ARGUMENT -> INVALID_ARGUMENT;
            case DEADLINE_EXCEEDED -> DEADLINE_EXCEEDED;
            case NOT_FOUND -> NOT_FOUND;
            case ALREADY_EXISTS -> ALREADY_EXISTS;
            case PERMISSION_DENIED -> PERMISSION_DENIED;
            case RESOURCE_EXHAUSTED -> RESOURCE_EXHAUSTED;
            case FAILED_PRECONDITION -> FAILED_PRECONDITION;
            case ABORTED -> ABORTED;
            case OUT_OF_RANGE -> OUT_OF_RANGE;
            case UNIMPLEMENTED -> UNIMPLEMENTED;
            case INTERNAL -> INTERNAL;
            case UNAVAILABLE -> UNAVAILABLE;
            case DATA_LOSS -> DATA_LOSS;
            case UNAUTHENTICATED -> UNAUTHENTICATED;
        };
    }
}
