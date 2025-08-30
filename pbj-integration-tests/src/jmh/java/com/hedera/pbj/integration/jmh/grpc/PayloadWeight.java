// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc;

import java.util.function.Function;
import java.util.function.Supplier;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/**
 * Specifies how "heavy" a benchmark should be in terms of the message payload size.
 */
public enum PayloadWeight {
    LIGHT(() -> GreeterService.EMPTY_REQUEST, request -> GreeterService.EMPTY_REPLY),
    NORMAL(() -> new HelloRequest("a".repeat(256)), request -> new HelloReply(request.name())),
    HEAVY(() -> new HelloRequest("a".repeat(8192)), request -> new HelloReply(request.name()));

    public final Supplier<HelloRequest> requestSupplier;
    public final Function<HelloRequest, HelloReply> replyProvider;

    PayloadWeight(
            final Supplier<HelloRequest> requestSupplier, final Function<HelloRequest, HelloReply> replyProvider) {
        this.requestSupplier = requestSupplier;
        this.replyProvider = replyProvider;
    }
}
