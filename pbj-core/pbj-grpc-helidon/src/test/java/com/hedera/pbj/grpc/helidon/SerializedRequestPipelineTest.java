// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SerializedRequestPipelineTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationWaitsForTheActiveCallbackWithoutBlockingTheCanceller(final boolean halfClose) throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch interruptionIgnored = new CountDownLatch(1);
        final CountDownLatch errorDelivered = new CountDownLatch(1);
        final List<String> signals = new ArrayList<>();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline() {
            @Override
            public void onNext(final Bytes item) {
                block();
            }

            @Override
            public void clientEndStreamReceived() {
                block();
            }

            private void block() {
                signals.add("start");
                entered.countDown();
                boolean interrupted = false;
                try {
                    while (true) {
                        try {
                            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                            break;
                        } catch (final InterruptedException e) {
                            interrupted = true;
                            interruptionIgnored.countDown();
                        }
                    }
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                signals.add("end");
            }

            @Override
            public void onError(final Throwable throwable) {
                signals.add("error");
                errorDelivered.countDown();
            }
        });
        final FutureTask<Void> request = new FutureTask<>(() -> {
            if (halfClose) {
                pipeline.clientEndStreamReceived();
            } else {
                pipeline.onNext(Bytes.EMPTY);
            }
            return null;
        });
        final Thread requestThread = Thread.startVirtualThread(request);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            final FutureTask<Void> cancel = new FutureTask<>(() -> {
                pipeline.onError(new IllegalStateException("cancelled"));
                return null;
            });
            Thread.startVirtualThread(cancel);
            cancel.get(1, TimeUnit.SECONDS);
            requestThread.interrupt();
            assertThat(interruptionIgnored.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(errorDelivered.getCount()).isEqualTo(1);
        } finally {
            release.countDown();
        }
        request.get(2, TimeUnit.SECONDS);
        assertThat(errorDelivered.getCount()).isZero();
        pipeline.onNext(Bytes.EMPTY);
        pipeline.onError(new IllegalStateException("late"));
        pipeline.onComplete();
        assertThat(signals).containsExactly("start", "end", "error");
    }

    @Test
    void reentrantCancellationIsDeliveredAfterOnNextReturns() {
        final List<String> signals = new ArrayList<>();
        final AtomicReference<SerializedRequestPipeline> wrapper = new AtomicReference<>();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline() {
            @Override
            public void onNext(final Bytes item) {
                signals.add("start");
                wrapper.get().onError(new IllegalStateException("cancelled"));
                signals.add("end");
            }

            @Override
            public void onError(final Throwable throwable) {
                signals.add("error");
            }
        });
        wrapper.set(pipeline);
        pipeline.onNext(Bytes.EMPTY);
        assertThat(signals).containsExactly("start", "end", "error");
    }

    @Test
    void fatalVmErrorsEscapeWithoutDeliveringMoreCallbacks() {
        final OutOfMemoryError fatal = new OutOfMemoryError("synthetic test failure");
        final AtomicReference<SerializedRequestPipeline> wrapper = new AtomicReference<>();
        final AtomicBoolean errorDelivered = new AtomicBoolean();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline() {
            @Override
            public void onNext(final Bytes item) {
                wrapper.get().onError(new IllegalStateException("cancelled"));
                throw fatal;
            }

            @Override
            public void onError(final Throwable throwable) {
                errorDelivered.set(true);
            }
        });
        wrapper.set(pipeline);
        assertThatThrownBy(() -> pipeline.onNext(Bytes.EMPTY)).isSameAs(fatal);
        assertThat(errorDelivered).isFalse();
    }

    static Stream<Throwable> callbackFailures() {
        return Stream.of(
                new IllegalStateException("request failure"),
                new AssertionError("request failure"),
                new IOException("unexpected checked request failure"));
    }

    @ParameterizedTest
    @MethodSource("callbackFailures")
    void callbackFailureIsPreservedWhenDeferredCancellationAlsoFails(final Throwable failure) {
        final IllegalArgumentException terminalFailure = new IllegalArgumentException("terminal failure");
        final AtomicReference<SerializedRequestPipeline> wrapper = new AtomicReference<>();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline() {
            @Override
            public void onNext(final Bytes item) {
                wrapper.get().onError(new IllegalStateException("cancelled"));
                SerializedRequestPipelineTest.<RuntimeException>throwUnchecked(failure);
            }

            @Override
            public void onError(final Throwable throwable) {
                throw terminalFailure;
            }
        });
        wrapper.set(pipeline);

        assertThatThrownBy(() -> pipeline.onNext(Bytes.EMPTY)).satisfies(thrown -> assertPropagated(thrown, failure));
        assertThat(failure.getSuppressed()).containsExactly(terminalFailure);
    }

    @ParameterizedTest
    @MethodSource("callbackFailures")
    void identicalCallbackAndTerminalFailuresDoNotMaskTheOriginal(final Throwable failure) {
        final AtomicReference<SerializedRequestPipeline> wrapper = new AtomicReference<>();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline() {
            @Override
            public void onNext(final Bytes item) {
                wrapper.get().onError(new IllegalStateException("cancelled"));
                SerializedRequestPipelineTest.<RuntimeException>throwUnchecked(failure);
            }

            @Override
            public void onError(final Throwable throwable) {
                SerializedRequestPipelineTest.<RuntimeException>throwUnchecked(failure);
            }
        });
        wrapper.set(pipeline);

        assertThatThrownBy(() -> pipeline.onNext(Bytes.EMPTY)).satisfies(thrown -> assertPropagated(thrown, failure));
        assertThat(failure.getSuppressed()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("callbackFailures")
    void deferredTerminalFailureIsPropagatedAfterASuccessfulCallback(final Throwable failure) {
        final AtomicReference<SerializedRequestPipeline> wrapper = new AtomicReference<>();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline() {
            @Override
            public void onNext(final Bytes item) {
                wrapper.get().onError(new IllegalStateException("cancelled"));
            }

            @Override
            public void onError(final Throwable throwable) {
                SerializedRequestPipelineTest.<RuntimeException>throwUnchecked(failure);
            }
        });
        wrapper.set(pipeline);

        assertThatThrownBy(() -> pipeline.onNext(Bytes.EMPTY)).satisfies(thrown -> assertPropagated(thrown, failure));
    }

    private static void assertPropagated(final Throwable thrown, final Throwable failure) {
        if (failure instanceof RuntimeException || failure instanceof Error) {
            assertThat(thrown).isSameAs(failure);
        } else {
            assertThat(thrown).isInstanceOf(RuntimeException.class).hasCause(failure);
        }
    }

    /** Exercises unexpected checked failures from callbacks whose Java signatures do not declare them. */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(final Throwable failure) throws T {
        throw (T) failure;
    }

    @Test
    void halfCloseIsDeliveredOnceAndRejectsLaterRequests() {
        final List<String> signals = new ArrayList<>();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline() {
            @Override
            public void onNext(final Bytes item) {
                signals.add("message");
            }

            @Override
            public void clientEndStreamReceived() {
                signals.add("half-close");
            }

            @Override
            public void onComplete() {
                signals.add("complete");
            }
        });
        pipeline.onNext(Bytes.EMPTY);
        pipeline.clientEndStreamReceived();
        pipeline.clientEndStreamReceived();
        pipeline.onNext(Bytes.EMPTY);
        pipeline.onComplete();
        pipeline.onNext(Bytes.EMPTY);
        assertThat(signals).containsExactly("message", "half-close", "complete");
    }

    @Test
    void subscriptionAfterTerminationIsCancelled() {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final SerializedRequestPipeline pipeline = new SerializedRequestPipeline(new TestPipeline());
        pipeline.onComplete();
        pipeline.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(final long n) {}

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
        assertThat(cancelled).isTrue();
    }

    private static class TestPipeline implements Pipeline<Bytes> {
        @Override
        public void onSubscribe(final Flow.Subscription subscription) {}

        @Override
        public void onError(final Throwable throwable) {}

        @Override
        public void onComplete() {}
    }
}
