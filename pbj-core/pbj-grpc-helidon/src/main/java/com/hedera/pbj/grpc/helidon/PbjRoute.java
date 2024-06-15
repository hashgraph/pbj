package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.Route;

/**
 * This base class represents a route in a {@link PbjRouting}. The route could represent an entire
 * {@link ServiceInterface}, or a specific method within a service.
 */
abstract class PbjRoute implements Route {
    /**
     * Given a {@link HttpPrologue}, locate and return the appropriate {@link PbjMethodRoute} object that represents
     * the gRPC service and method that should be invoked.
     * @param grpcPrologue The prologue of the HTTP request.
     * @return The {@link PbjMethodRoute} object that represents the gRPC service and method that should be invoked,
     * or null.
     */
    abstract @Nullable PbjMethodRoute toPbjMethodRoute(@NonNull final HttpPrologue grpcPrologue);

    /**
     * Given a {@link HttpPrologue}, determine if this route should accept the request.
     * @param prologue The prologue of the HTTP request.
     * @return A {@link PathMatchers.MatchResult} that indicates if this route should accept the request.
     */
    abstract @NonNull PathMatchers.MatchResult accepts(@NonNull final HttpPrologue prologue);
}
