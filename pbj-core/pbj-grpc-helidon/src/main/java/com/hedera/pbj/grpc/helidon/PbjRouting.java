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

package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.Routing;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A Helidon {@link Routing} used for constructing the routes for PBJ-based gRPC services.
 *
 * <pre>{@code
 * WebServer.builder()
 *             .port(8080)
 *             .addRouting(PbjRouting.builder().service(new HelloServiceImpl()))
 *             .build()
 *             .start();
 * }</pre>
 */
public class PbjRouting implements Routing {
    /** The list of routes. */
    @NonNull private final List<PbjRoute> routes;

    /**
     * Create a new instance. This is private, so it can only be created using the builder method.
     */
    private PbjRouting(@NonNull final Builder builder) {
        this.routes = new ArrayList<>(builder.routes);
    }

    @Override
    @NonNull
    public Class<? extends Routing> routingType() {
        return PbjRouting.class;
    }

    @Override
    public void beforeStart() {
        routes.forEach(PbjRoute::beforeStart);
    }

    @Override
    public void afterStop() {
        routes.forEach(PbjRoute::afterStop);
    }

    /**
     * Find a route that matches the given prologue. A prologue would be the first part of the path,
     * for instance. When registered, a route may have wildcard matches to paths, etc.
     *
     * @param prologue the prologue to match
     * @return the route that matches the prologue, or {@code null} if no route matches
     */
    @Nullable
    PbjMethodRoute findRoute(@NonNull final HttpPrologue prologue) {
        for (final var route : routes) {
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
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link PbjRouting}. A single {@link PbjRouting} may contain multiple
     * services.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, PbjRouting> {
        private final List<PbjRoute> routes = new LinkedList<>();

        private Builder() {}

        @Override
        @NonNull
        public PbjRouting build() {
            return new PbjRouting(this);
        }

        /**
         * Configure grpc service.
         *
         * @param service service to add
         * @return updated builder
         */
        @NonNull
        public Builder service(@NonNull final ServiceInterface service) {
            return route(new PbjServiceRoute(service));
        }

        @NonNull
        private Builder route(@NonNull final PbjRoute route) {
            routes.add(route);
            return this;
        }
    }
}
