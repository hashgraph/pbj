package com.hedera.pbj.runtime.grpc;

import java.util.concurrent.Flow;

public abstract class MapSubscriber<T,R> implements Flow.Subscriber<T> {
    private final Flow.Subscriber<? super R> next;

    public MapSubscriber(Flow.Subscriber<? super R> next) {
        this.next = next;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // TODO
    }

    @Override
    public void onNext(T item) {
        try {
            final var r = map(item);
            next.onNext(r);
        } catch (Throwable t) {
            next.onError(t);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        next.onError(throwable);
    }

    @Override
    public void onComplete() {
        next.onComplete();
    }

    public abstract R map(T item) throws Exception;
}
