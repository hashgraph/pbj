// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc;

import com.hedera.pbj.runtime.grpc.Pipeline;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/**
 * A trivial Greeter service implementation for a JMH benchmark.
 */
public class GreeterService implements GreeterInterface {
    static final HelloRequest EMPTY_REQUEST = new HelloRequest("");
    static final HelloReply EMPTY_REPLY = new HelloReply("");

    private final PayloadWeight weight;
    private final int streamCount;

    /**
     * Constructor. If `realFast` is true, then replies are static empty strings,
     * otherwise they're new HelloReply objects with the names from the requests.
     * The `streamCount` specifies the number of streamed messages per a request
     * and similar counters for streaming APIs.
     */
    public GreeterService(final PayloadWeight weight, final int streamCount) {
        this.weight = weight;
        this.streamCount = streamCount;
    }

    @NonNull
    @Override
    public HelloReply sayHello(@NonNull HelloRequest request) {
        return weight.replyProvider.apply(request);
    }

    @Override
    public void sayHelloStreamReply(@NonNull HelloRequest request, @NonNull Pipeline<? super HelloReply> replies) {
        for (int i = 0; i < streamCount; i++) {
            replies.onNext(sayHello(request));
        }
        replies.onComplete();
    }

    @NonNull
    @Override
    public Pipeline<? super HelloRequest> sayHelloStreamRequest(@NonNull Pipeline<? super HelloReply> replies) {
        final boolean realFast = weight == PayloadWeight.LIGHT;
        final AtomicInteger counter = realFast ? new AtomicInteger() : null;
        final List<HelloRequest> requests = realFast ? null : new ArrayList<>();
        return new Pipeline<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(streamCount);
            }

            @Override
            public void onError(Throwable throwable) {
                new RuntimeException(throwable).printStackTrace();
            }

            @Override
            public void onComplete() {}

            @Override
            public void onNext(HelloRequest item) throws RuntimeException {
                if (realFast) {
                    if (counter.incrementAndGet() == streamCount) {
                        replies.onNext(sayHello(item));
                        replies.onComplete();
                    }
                } else {
                    requests.add(item);
                    if (requests.size() == streamCount) {
                        replies.onNext(new HelloReply(
                                requests.stream().map(HelloRequest::name).collect(Collectors.joining(","))));
                        replies.onComplete();
                    }
                }
            }
        };
    }

    @NonNull
    @Override
    public Pipeline<? super HelloRequest> sayHelloStreamBidi(@NonNull Pipeline<? super HelloReply> replies) {
        final AtomicInteger counter = new AtomicInteger();
        return new Pipeline<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}

            @Override
            public void onNext(HelloRequest item) throws RuntimeException {
                for (int i = 0; i < streamCount; i++) {
                    replies.onNext(sayHello(item));
                }
                if (counter.incrementAndGet() == streamCount) {
                    replies.onComplete();
                }
            }
        };
    }
}
