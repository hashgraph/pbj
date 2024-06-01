package com.hedera.pbj.runtime.grpc;

import java.util.concurrent.Flow;

// Used by the compiler when generating service interfaces. Do not use directly.
public abstract class UnarySubscriber<T> implements Flow.Subscriber<T> {
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // Unary requests only have one item, so we request one item only.
        subscription.request(1);
    }

    @Override
    public final void onError(Throwable throwable) {
        // TODO: Handle error
    }

    @Override
    public void onComplete() {
        // Nothing to do here.
    }
}
