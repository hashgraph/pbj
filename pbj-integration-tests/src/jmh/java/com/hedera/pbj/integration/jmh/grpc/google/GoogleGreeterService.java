// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc.google;

import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import pbj.integration.tests.GreeterGrpc;
import pbj.integration.tests.HelloReply;
import pbj.integration.tests.HelloRequest;

/** Greeter service implementation for Google GRPC server. */
class GoogleGreeterService extends GreeterGrpc.GreeterImplBase {
    static final HelloRequest EMPTY_REQUEST =
            HelloRequest.newBuilder().setName("").build();
    static final HelloReply EMPTY_REPLY = HelloReply.newBuilder().setMessage("").build();

    private final GooglePayloadWeight weight;
    private final int streamCount;

    /**
     * Constructor.
     */
    public GoogleGreeterService(final GooglePayloadWeight weight, final int streamCount) {
        this.weight = weight;
        this.streamCount = streamCount;
    }

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(weight.replyProvider.apply(request));
        responseObserver.onCompleted();
    }

    @Override
    public void sayHelloStreamReply(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        for (int i = 0; i < streamCount; i++) {
            responseObserver.onNext(weight.replyProvider.apply(request));
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<HelloRequest> sayHelloStreamRequest(StreamObserver<HelloReply> responseObserver) {
        final boolean realFast = weight == GooglePayloadWeight.LIGHT;
        final AtomicInteger counter = realFast ? new AtomicInteger() : null;
        final List<HelloRequest> requests = realFast ? null : new ArrayList<>();
        return new StreamObserver<HelloRequest>() {
            public void onNext(HelloRequest request) {
                if (realFast) {
                    if (counter.incrementAndGet() == streamCount) {
                        responseObserver.onNext(weight.replyProvider.apply(request));
                        responseObserver.onCompleted();
                    }
                } else {
                    requests.add(request);
                    if (requests.size() == streamCount) {
                        responseObserver.onNext(HelloReply.newBuilder()
                                .setMessage(requests.stream()
                                        .map(HelloRequest::getName)
                                        .collect(Collectors.joining(",")))
                                .build());
                        responseObserver.onCompleted();
                    }
                }
            }

            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<HelloRequest> sayHelloStreamBidi(StreamObserver<HelloReply> responseObserver) {
        final AtomicInteger counter = new AtomicInteger();
        return new StreamObserver<HelloRequest>() {
            public void onNext(HelloRequest request) {
                for (int i = 0; i < streamCount; i++) {
                    responseObserver.onNext(weight.replyProvider.apply(request));
                }
                if (counter.incrementAndGet() == streamCount) {
                    responseObserver.onCompleted();
                }
            }

            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
