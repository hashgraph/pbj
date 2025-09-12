// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc.google;

import java.util.function.Function;
import java.util.function.Supplier;
import pbj.integration.tests.HelloReply;
import pbj.integration.tests.HelloRequest;

/**
 * Specifies how "heavy" a benchmark should be in terms of the message payload size.
 */
public enum GooglePayloadWeight {
    LIGHT(() -> GoogleGreeterService.EMPTY_REQUEST, request -> GoogleGreeterService.EMPTY_REPLY),
    NORMAL(
            () -> HelloRequest.newBuilder().setName("a".repeat(256)).build(),
            request -> HelloReply.newBuilder().setMessage(request.getName()).build()),
    HEAVY(
            () -> HelloRequest.newBuilder().setName("a".repeat(8192)).build(),
            request -> HelloReply.newBuilder().setMessage(request.getName()).build());

    public final Supplier<HelloRequest> requestSupplier;
    public final Function<HelloRequest, HelloReply> replyProvider;

    GooglePayloadWeight(
            final Supplier<HelloRequest> requestSupplier, final Function<HelloRequest, HelloReply> replyProvider) {
        this.requestSupplier = requestSupplier;
        this.replyProvider = replyProvider;
    }
}
