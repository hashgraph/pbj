// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes signals delivered to an application request pipeline. Cancellation is recorded without waiting for an
 * application callback: its terminal signal is delivered after the active callback returns. Applications must
 * cooperate with interruption to make cancellation prompt while executing a request.
 */
final class SerializedRequestPipeline implements Pipeline<Bytes> {
    private final Pipeline<? super Bytes> delegate;
    private final ReentrantLock deliveryLock = new ReentrantLock();
    private final AtomicReference<Runnable> terminal = new AtomicReference<>();
    private final AtomicBoolean terminalDelivered = new AtomicBoolean();

    /** Guarded by {@link #deliveryLock}. */
    private boolean requestEnded;

    SerializedRequestPipeline(final Pipeline<? super Bytes> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
        requireNonNull(subscription);
        if (!deliver(() -> delegate.onSubscribe(subscription))) {
            subscription.cancel();
        }
    }

    @Override
    public void onNext(final Bytes item) {
        requireNonNull(item);
        deliver(() -> {
            if (!requestEnded) {
                delegate.onNext(item);
            }
        });
    }

    @Override
    public void clientEndStreamReceived() {
        deliver(() -> {
            if (!requestEnded) {
                requestEnded = true;
                delegate.clientEndStreamReceived();
            }
        });
    }

    @Override
    public void onError(final Throwable throwable) {
        requireNonNull(throwable);
        terminate(() -> delegate.onError(throwable));
    }

    @Override
    public void onComplete() {
        terminate(delegate::onComplete);
    }

    @Override
    public void closeConnection() {
        deliver(delegate::closeConnection);
    }

    private void terminate(final Runnable callback) {
        if (terminal.compareAndSet(null, callback)) {
            drainTerminal();
        }
    }

    private boolean deliver(final Runnable callback) {
        if (deliveryLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Request callbacks must not reenter the pipeline");
        }
        Throwable failure = null;
        boolean delivered = false;
        deliveryLock.lock();
        try {
            if (terminal.get() == null) {
                callback.run();
                delivered = true;
            }
        } catch (final RuntimeException | Error e) {
            failure = e;
        } finally {
            deliveryLock.unlock();
        }
        if (failure instanceof final VirtualMachineError fatal) {
            throw fatal;
        }
        try {
            // This must happen after unlocking. If cancellation raced with the unlock, either this thread or the
            // cancelling thread acquires the lock and delivers the terminal signal.
            drainTerminal();
        } catch (final VirtualMachineError fatal) {
            throw fatal;
        } catch (final RuntimeException | Error terminalFailure) {
            if (failure == null) {
                throw terminalFailure;
            }
            failure.addSuppressed(terminalFailure);
        }
        if (failure instanceof final RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof final Error error) {
            throw error;
        }
        return delivered;
    }

    private void drainTerminal() {
        // A reentrant cancellation from inside onNext must be deferred until onNext has returned, too.
        if (deliveryLock.isHeldByCurrentThread() || !deliveryLock.tryLock()) {
            return;
        }
        try {
            final Runnable callback = terminal.get();
            if (callback != null && terminalDelivered.compareAndSet(false, true)) {
                callback.run();
            }
        } finally {
            deliveryLock.unlock();
        }
    }
}
