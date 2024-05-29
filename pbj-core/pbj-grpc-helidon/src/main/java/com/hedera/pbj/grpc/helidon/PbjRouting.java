package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.ServiceInterface;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.Routing;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A Helidon {@link Routing} used for constructing the routes for PBJ-based gRPC services.
 *
 * <pre>
 * {@code
 *     WebServer.builder()
 *                 .port(8080)
 *                 .addRouting(PbjRouting.builder().service(new HelloServiceImpl()))
 *                 .build()
 *                 .start();
 * }
 * </pre>
 */
public class PbjRouting implements Routing {
    /** This routing has absolutely no routes. */
    static final PbjRouting EMPTY = PbjRouting.builder().build();

    /** The list of routes. */
    private final List<PbjRoute> routes;

    /** Create a new instance. This is private, so it can only be created using the builder method. */
    private PbjRouting(final @NonNull Builder builder) {
        this.routes = new ArrayList<>(builder.routes);
    }

    @Override
    public Class<? extends Routing> routingType() {
        return PbjRouting.class;
    }

    @Override
    public void beforeStart() {
        for (final PbjRoute route : routes) {
            route.beforeStart();
        }
    }

    @Override
    public void afterStop() {
        for (final PbjRoute route : routes) {
            route.afterStop();
        }
    }

    /**
     * Find a route that matches the given prologue. A prologue would be the first part of the path, for instance.
     * When registered, a route may have wildcard matches to paths, etc.
     *
     * @param prologue the prologue to match
     * @return the route that matches the prologue, or {@code null} if no route matches
     */
    PbjMethodRoute findRoute(final HttpPrologue prologue) {
        for (final PbjRoute route : routes) {
            final var accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route.toPbjMethodRoute(prologue);
            }
        }

        return null;
    }

    /**
     * Create a new builder instance to be used to construct a {@link PbjRouting} instance.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link PbjRouting}. A single {@link PbjRouting} may contain multiple services.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, PbjRouting> {
        private final List<PbjRoute> routes = new LinkedList<>();

        private Builder() {
        }

        @Override
        public PbjRouting build() {
            return new PbjRouting(this);
        }

        /**
         * Configure grpc service.
         *
         * @param service service to add
         * @return updated builder
         */
        public Builder service(final ServiceInterface service) {
            return route(new PbjServiceRoute(service));
        }

        private Builder route(PbjRoute route) {
            routes.add(route);
            return this;
        }
    }
}
