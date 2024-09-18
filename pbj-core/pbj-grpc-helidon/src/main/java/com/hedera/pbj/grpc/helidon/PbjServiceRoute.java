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

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import java.util.List;

/** An implementation of {@link PbjRoute} that represents an entire service. */
class PbjServiceRoute extends PbjRoute {
    /** The name of the service. */
    private final String serviceName;

    /** One {@link PbjMethodRoute} for each method in the service. */
    private final List<PbjMethodRoute> routes;

    /**
     * Create a new instance.
     *
     * @param service the service to represent
     */
    PbjServiceRoute(@NonNull final ServiceInterface service) {
        this.serviceName = requireNonNull(service).serviceName();
        this.routes =
                service.methods().stream()
                        .map(method -> new PbjMethodRoute(service, method))
                        .toList();
    }

    @Override
    @NonNull
    PbjMethodRoute toPbjMethodRoute(@NonNull final HttpPrologue prologue) {
        for (final PbjMethodRoute route : routes) {
            final var accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route;
            }
        }
        throw new IllegalStateException(
                "PbjServiceRoute("
                        + serviceName
                        + ") accepted prologue, "
                        + "but cannot provide route: "
                        + prologue);
    }

    @Override
    @NonNull
    PathMatchers.MatchResult accepts(@NonNull final HttpPrologue prologue) {
        for (final PbjMethodRoute route : routes) {
            final var accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return accepts;
            }
        }
        return PathMatchers.MatchResult.notAccepted();
    }
}
