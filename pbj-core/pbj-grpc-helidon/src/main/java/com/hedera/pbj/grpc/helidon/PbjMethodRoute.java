package com.hedera.pbj.grpc.helidon;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;

/**
 * Represents a route in a {@link PbjRouting} that corresponds to a specific gRPC service method.
 */
final class PbjMethodRoute extends PbjRoute {
    private static final String SEP = "/";
    private final ServiceInterface service;
    private final ServiceInterface.Method method;
    private final PathMatcher pathMatcher;

    PbjMethodRoute(
            final @NonNull ServiceInterface service,
            final @NonNull ServiceInterface.Method method) {
        this.service = requireNonNull(service);
        this.method = requireNonNull(method);
        this.pathMatcher = PathMatchers.exact(service.fullName() + SEP + method.name());
    }

    @Override
    @NonNull
    PbjMethodRoute toPbjMethodRoute(final @NonNull HttpPrologue grpcPrologue) {
        return this;
    }

    @Override
    @NonNull
    PathMatchers.MatchResult accepts(final @NonNull HttpPrologue prologue) {
        return pathMatcher.match(prologue.uriPath());
    }

    /** The {@link ServiceInterface.Method} that this route represents. */
    ServiceInterface.Method method() {
        return method;
    }

    /** The {@link ServiceInterface} that this route represents. */
    ServiceInterface service() {
        return service;
    }
}
