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
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;

/**
 * Represents a route in a {@link PbjRouting} that corresponds to a specific gRPC service method. An
 * instance of this class is created for each gRPC method on a {@link ServiceInterface} registered
 * with a {@link PbjRouting}.
 */
final class PbjMethodRoute extends PbjRoute {
    private static final String SEP = "/";
    @NonNull private final ServiceInterface service;
    @NonNull private final ServiceInterface.Method method;
    @NonNull private final String fullPath;
    @NonNull private final PathMatcher pathMatcher;

    // Metrics related fields. These can safely be reused across threads and invocations
    private static final String SCOPE = "vendor";
    private static final String SERVICE_TAG = "service";
    private static final String METHOD_TAG = "method";
    private static final String FAILURE_TAG = "failure";
    @NonNull private final Counter requestCounter;
    @NonNull private final Counter failedGrpcRequestCounter;
    @NonNull private final Counter failedHttpRequestCounter;
    @NonNull private final Counter failedUnknownRequestCounter;
    @NonNull private final Counter deadlineExceededCounter;

    /**
     * Constructor
     *
     * @param service The service that the method belongs to
     * @param method The method that this route represents
     */
    PbjMethodRoute(
            @NonNull final ServiceInterface service,
            @NonNull final ServiceInterface.Method method) {
        this.service = requireNonNull(service);
        this.method = requireNonNull(method);

        final var serviceName = service.fullName();
        final var methodName = method.name();
        this.fullPath = SEP + serviceName + SEP + methodName;
        this.pathMatcher = PathMatchers.exact(fullPath);

        final var metricRegistry = Metrics.globalRegistry();
        this.requestCounter =
                metricRegistry.getOrCreate(
                        Counter.builder("pbj.grpc.requests")
                                .scope(SCOPE)
                                .addTag(Tag.create(SERVICE_TAG, serviceName))
                                .addTag(Tag.create(METHOD_TAG, methodName))
                                .description("The number of gRPC requests"));
        this.failedGrpcRequestCounter =
                metricRegistry.getOrCreate(
                        Counter.builder("pbj.grpc.failed.requests")
                                .scope(SCOPE)
                                .addTag(Tag.create(SERVICE_TAG, serviceName))
                                .addTag(Tag.create(METHOD_TAG, methodName))
                                .addTag(Tag.create(FAILURE_TAG, "grpc-exception"))
                                .description("The number of failed gRPC requests"));
        this.failedHttpRequestCounter =
                metricRegistry.getOrCreate(
                        Counter.builder("pbj.grpc.failed.requests")
                                .scope(SCOPE)
                                .addTag(Tag.create(SERVICE_TAG, serviceName))
                                .addTag(Tag.create(METHOD_TAG, methodName))
                                .addTag(Tag.create(FAILURE_TAG, "http-exception"))
                                .description("The number of failed HTTP requests"));
        this.failedUnknownRequestCounter =
                metricRegistry.getOrCreate(
                        Counter.builder("pbj.grpc.failed.requests")
                                .scope(SCOPE)
                                .addTag(Tag.create(SERVICE_TAG, serviceName))
                                .addTag(Tag.create(METHOD_TAG, methodName))
                                .addTag(Tag.create(FAILURE_TAG, "unknown-exception"))
                                .description("The number of failed unknown requests"));
        this.deadlineExceededCounter =
                metricRegistry.getOrCreate(
                        Counter.builder("pbj.grpc.deadline.exceeded")
                                .scope(SCOPE)
                                .addTag(Tag.create(SERVICE_TAG, serviceName))
                                .addTag(Tag.create(METHOD_TAG, methodName))
                                .description(
                                        "The number of gRPC requests that exceeded their"
                                                + " deadline"));
    }

    @Override
    @NonNull
    PbjMethodRoute toPbjMethodRoute(@NonNull final HttpPrologue grpcPrologue) {
        return this;
    }

    @Override
    @NonNull
    PathMatchers.MatchResult accepts(@NonNull final HttpPrologue prologue) {
        return pathMatcher.match(prologue.uriPath());
    }

    /** The {@link ServiceInterface.Method} that this route represents. */
    @NonNull
    ServiceInterface.Method method() {
        return method;
    }

    /** The {@link ServiceInterface} that this route represents. */
    @NonNull
    ServiceInterface service() {
        return service;
    }

    /** The full path, such as `/example.HelloService/SayHello`. */
    @NonNull
    String fullPath() {
        return fullPath;
    }

    @NonNull
    public Counter requestCounter() {
        return requestCounter;
    }

    @NonNull
    public Counter failedGrpcRequestCounter() {
        return failedGrpcRequestCounter;
    }

    @NonNull
    public Counter failedHttpRequestCounter() {
        return failedHttpRequestCounter;
    }

    @NonNull
    public Counter failedUnknownRequestCounter() {
        return failedUnknownRequestCounter;
    }

    @NonNull
    public Counter deadlineExceededCounter() {
        return deadlineExceededCounter;
    }
}
