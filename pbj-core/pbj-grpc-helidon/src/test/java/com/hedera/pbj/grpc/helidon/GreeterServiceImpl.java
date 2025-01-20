// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import greeter.HelloReply;
import greeter.HelloRequest;
import java.util.ArrayList;
import java.util.concurrent.Flow;

class GreeterServiceImpl implements GreeterService {
    GrpcStatus errorToThrow = null;

    @Override
    public HelloReply sayHello(HelloRequest request) {
        if (errorToThrow != null) {
            throw new GrpcException(errorToThrow);
        }

        return HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
    }

    // Streams of stuff coming from the client, with a single response.
    @Override
    public Pipeline<? super HelloRequest> sayHelloStreamRequest(Pipeline<? super HelloReply> replies) {
        final var names = new ArrayList<String>();
        return new Pipeline<>() {
            @Override
            public void clientEndStreamReceived() {
                onComplete();
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE); // turn off flow control
            }

            @Override
            public void onNext(HelloRequest item) {
                names.add(item.getName());
            }

            @Override
            public void onError(Throwable throwable) {
                replies.onError(throwable);
            }

            @Override
            public void onComplete() {
                final var reply = HelloReply.newBuilder()
                        .setMessage("Hello " + String.join(", ", names))
                        .build();
                replies.onNext(reply);
                replies.onComplete();
            }
        };
    }

    @Override
    public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
        for (int i = 0; i < 10; i++) {
            replies.onNext(HelloReply.newBuilder().setMessage("Hello!").build());
        }

        replies.onComplete();
    }

    @Override
    public Pipeline<? super HelloRequest> sayHelloStreamBidi(Pipeline<? super HelloReply> replies) {
        // Here we receive info from the client. In this case, it is a stream of requests with
        // names. We will respond with a stream of replies.
        return new Pipeline<>() {
            @Override
            public void clientEndStreamReceived() {
                onComplete();
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE); // turn off flow control
            }

            @Override
            public void onNext(HelloRequest item) {
                replies.onNext(HelloReply.newBuilder()
                        .setMessage("Hello " + item.getName())
                        .build());
            }

            @Override
            public void onError(Throwable throwable) {
                replies.onError(throwable);
            }

            @Override
            public void onComplete() {
                replies.onComplete();
            }
        };
    }
}
