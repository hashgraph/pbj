package com.hedera.pbj.grpc.helidon;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import java.util.List;

/**
 * An implementation of {@link PbjRoute} that represents an entire service.
 */
class PbjServiceRoute extends PbjRoute {
    /** The name of the service. */
    private final String serviceName;
    /** One {@link PbjMethodRoute} for each method in the service. */
    private final List<PbjMethodRoute> routes;

    PbjServiceRoute(final @NonNull ServiceInterface service) {
        this.serviceName = requireNonNull(service).serviceName();
        this.routes = service.methods().stream().map(method -> new PbjMethodRoute(service, method)).toList();
    }

    @Override
    @NonNull
    PbjMethodRoute toPbjMethodRoute(final @NonNull HttpPrologue prologue) {
        for (final PbjMethodRoute route : routes) {
            final var accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route;
            }
        }
        throw new IllegalStateException("PbjServiceRoute(" + serviceName + ") accepted prologue, " +
                "but cannot provide route: " + prologue);
    }

    @Override
    @NonNull
    PathMatchers.MatchResult accepts(final @NonNull HttpPrologue prologue) {
        for (final PbjMethodRoute route : routes) {
            final var accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return accepts;
            }
        }
        return PathMatchers.MatchResult.notAccepted();
    }
}
