package com.hedera.pbj.runtime.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

class PipelinesTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class NoopTest {
        @Mock Flow.Subscription subscription;

        @Test
        void noopWithNullSubscriptionThrowsNPE() {
            final var noop = Pipelines.noop();
            assertThat(noop).isNotNull();
            assertThatThrownBy(() -> noop.onSubscribe(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void noopOnSubscribeCallsTheSubscriptionWithMaxRequests() {
            final var noop = Pipelines.noop();
            noop.onSubscribe(subscription);
            verify(subscription).request(Long.MAX_VALUE);
        }

        @Test
        void noopOnNextDoesNothing() {
            final var noop = Pipelines.noop();
            noop.onNext(Bytes.wrap("SHOULD NOT DO ANYTHING"));
            assertThat(noop).isNotNull(); // if we get here, all is well.
        }

        @Test
        void errorsCauseSubscriptionToBeCanceled() {
            final var noop = Pipelines.noop();
            noop.onSubscribe(subscription);
            noop.onError(new RuntimeException("Causes the subscription to be cancelled"));
            verify(subscription).cancel();
        }

        @Test
        void noopOnCompleteDoesNothing() {
            final var noop = Pipelines.noop();
            noop.onComplete();
            assertThat(noop).isNotNull(); // if we get here, all is well.
        }

    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class UnaryTest {
        @Mock Pipeline<Bytes> replies;
        @Mock Flow.Subscription subscription;

        @Test
        void requestMapperIsRequired() {
            final var builder = Pipelines.<String, String>unary()
                    .method(String::toUpperCase)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The request mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void methodIsRequired() {
            final var builder = Pipelines.<String, String>unary()
                    .mapRequest(Bytes::asUtf8String)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The method must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void responseMapperIsRequired() {
            final var builder = Pipelines.<String, String>unary()
                    .mapRequest(Bytes::asUtf8String)
                    .method(String::toUpperCase)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The response mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void respondToIsRequired() {
            final var builder = Pipelines.<String, String>unary()
                    .mapRequest(Bytes::asUtf8String)
                    .method(String::toUpperCase)
                    .mapResponse(Bytes::wrap);

            assertThatThrownBy(builder::build)
                    .hasMessage("The replies subscriber must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nullSubscriptionThrowsNPE() {
            final var pipeline = Pipelines.<String, String>unary()
                    .mapRequest(Bytes::asUtf8String)
                    .method(String::toUpperCase)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            assertThatThrownBy(() -> pipeline.onSubscribe(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void onNextTwiceThrowsISE() {
            final var pipeline = Pipelines.<String, String>unary()
                    .mapRequest(Bytes::asUtf8String)
                    .method(String::toUpperCase)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            pipeline.onNext(Bytes.wrap("world"));
            verify(replies).onError(any(IllegalStateException.class));
        }

        @Test
        void exceptionDuring_onNext_IsHandled() {
            final var ex = new RuntimeException("Some exception");
            doThrow(ex).when(replies).onNext(any());
            final var pipeline = Pipelines.<String, String>unary()
                    .mapRequest(Bytes::asUtf8String)
                    .method(String::toUpperCase)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            verify(replies).onError(any(RuntimeException.class));
        }

        @Test
        void positive() {
            final var pipeline = Pipelines.<String, String>unary()
                    .mapRequest(Bytes::asUtf8String)
                    .method(String::toUpperCase)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(subscription);
            pipeline.onNext(Bytes.wrap("hello"));
            verify(replies).onNext(Bytes.wrap("HELLO"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class BidiTest {
        @Mock Pipeline<String> client;
        @Mock Pipeline<Bytes> replies;
        @Mock Flow.Subscription subscription;

        @Test
        void requestMapperIsRequired() {
            final var builder = Pipelines.<String, String>bidiStreaming()
                    .method(sink -> client)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The request mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void methodIsRequired() {
            final var builder = Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The method must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void responseMapperIsRequired() {
            final var builder = Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> client)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The response mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void respondToIsRequired() {
            final var builder = Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> client)
                    .mapResponse(Bytes::wrap);

            assertThatThrownBy(builder::build)
                    .hasMessage("The replies subscriber must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nullSubscriptionThrowsNPE() {
            final var pipeline = Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> client)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            assertThatThrownBy(() -> pipeline.onSubscribe(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void onCompleteNextThrowsISE() {
            final var pipeline = Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> {
                        lenient().doAnswer(invocation -> {
                            final var msg = invocation.getArgument(0, String.class);
                            sink.onNext(msg.toUpperCase());
                            return null;
                        }).when(client).onNext(any());
                        return client;
                    })
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            pipeline.onNext(Bytes.wrap("world"));
            verify(replies, times(2)).onNext(any());
            verify(replies, never()).onComplete();
            verify(replies, never()).onError(any(RuntimeException.class));

            pipeline.onComplete();
            verify(replies, times(2)).onNext(any());
            verify(replies).onComplete();
            verify(replies, never()).onError(any(RuntimeException.class));

            pipeline.onNext(Bytes.wrap("!!!"));
            verify(replies, times(2)).onNext(any());
            verify(replies).onError(any(IllegalStateException.class));
        }

        @Test
        void exceptionDuring_onNext_IsHandled() {
            final var ex = new RuntimeException("Some exception");
            doThrow(ex).when(client).onNext(any());
            final var pipeline = Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> client)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            verify(replies).onError(any(RuntimeException.class));
        }

        @Test
        void exceptionDuring_responseConverter_IsHandled() {
            final var ex = new RuntimeException("Some exception");
            Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> { throw ex; })
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            verify(replies).onError(any(RuntimeException.class));
        }

        @Test
        void positive() {
            final var pipeline = Pipelines.<String, String>bidiStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> {
                        doAnswer(invocation -> {
                            final var msg = invocation.getArgument(0, String.class);
                            sink.onNext(msg.toUpperCase());
                            return null;
                        }).when(client).onNext(any());
                        return client;
                    })
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            final var argCaptor = ArgumentCaptor.forClass(Bytes.class);
            pipeline.onSubscribe(subscription);
            pipeline.onNext(Bytes.wrap("hello"));
            pipeline.onNext(Bytes.wrap("world"));
            verify(replies, times(2)).onNext(argCaptor.capture());
            assertThat(argCaptor.getAllValues()).containsExactly(
                    Bytes.wrap("HELLO"),
                    Bytes.wrap("WORLD"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ServerStreamingTest {
        @Mock Pipeline<Bytes> replies;
        @Mock Flow.Subscription subscription;

        @Test
        void requestMapperIsRequired() {
            final var builder = Pipelines.<String, String>serverStreaming()
                    .method((msg, sink) -> sink.onNext(msg.toUpperCase()))
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The request mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void methodIsRequired() {
            final var builder = Pipelines.<String, String>serverStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The method must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void responseMapperIsRequired() {
            final var builder = Pipelines.<String, String>serverStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method((msg, sink) -> sink.onNext(msg.toUpperCase()))
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The response mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void respondToIsRequired() {
            final var builder = Pipelines.<String, String>serverStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method((msg, sink) -> sink.onNext(msg.toUpperCase()))
                    .mapResponse(Bytes::wrap);

            assertThatThrownBy(builder::build)
                    .hasMessage("The replies subscriber must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nullSubscriptionThrowsNPE() {
            final var pipeline = Pipelines.<String, String>serverStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method((msg, sink) -> sink.onNext(msg.toUpperCase()))
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            assertThatThrownBy(() -> pipeline.onSubscribe(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void onCompleteNextThrowsISE() {
            final var pipeline = Pipelines.<String, String>serverStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method((msg, sink) -> sink.onNext(msg.toUpperCase()))
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            pipeline.onNext(Bytes.wrap("world"));
            pipeline.onComplete();
            pipeline.onNext(Bytes.wrap("!!!"));
            verify(replies).onError(any(IllegalStateException.class));
        }

        @Test
        void badRequestMapperCallsOnError() {
            final var ex = new RuntimeException("Bad bad bad");
            final var pipeline = Pipelines.<String, String>serverStreaming()
                    .mapRequest(bytes -> { throw ex; })
                    .method((msg, sink) -> sink.onNext(msg.toUpperCase()))
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            verify(replies).onError(ex);
        }

        @Test
        void badMethodCallsOnError() {
            final var ex = new RuntimeException("Bad bad bad");
            final var pipeline = Pipelines.<String, String>serverStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method((msg, sink) -> { throw ex; })
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            verify(replies).onError(ex);
        }

        @Test
        void positive() {
            final var pipeline = Pipelines.<String, String>serverStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method((msg, sink) -> sink.onNext(msg.toUpperCase()))
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(subscription);
            pipeline.onNext(Bytes.wrap("hello"));
            verify(replies).onNext(Bytes.wrap("HELLO"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ClientStreamingTest {
        @Mock Pipeline<Bytes> replies;
        @Mock Flow.Subscription subscription;

        @Test
        void requestMapperIsRequired() {
            final var builder = Pipelines.<String, String>clientStreaming()
                    .method(ConcatenatingHandler::new)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The request mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void methodIsRequired() {
            final var builder = Pipelines.<String, String>clientStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The method must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void responseMapperIsRequired() {
            final var builder = Pipelines.<String, String>clientStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(ConcatenatingHandler::new)
                    .respondTo(replies);

            assertThatThrownBy(builder::build)
                    .hasMessage("The response mapper must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void respondToIsRequired() {
            final var builder = Pipelines.<String, String>clientStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(ConcatenatingHandler::new)
                    .mapResponse(Bytes::wrap);

            assertThatThrownBy(builder::build)
                    .hasMessage("The replies subscriber must be specified.")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nullSubscriptionThrowsNPE() {
            final var pipeline = Pipelines.<String, String>clientStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(ConcatenatingHandler::new)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            assertThatThrownBy(() -> pipeline.onSubscribe(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void onCompleteNextThrowsISE() {
            final var pipeline = Pipelines.<String, String>clientStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(ConcatenatingHandler::new)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            pipeline.onNext(Bytes.wrap("world"));
            pipeline.onComplete();
            pipeline.onNext(Bytes.wrap("!!!"));
            verify(replies).onError(any(IllegalStateException.class));
        }

        @Test
        void badRequestMapperCallsOnError() {
            final var ex = new RuntimeException("Bad bad bad");
            final var pipeline = Pipelines.<String, String>clientStreaming()
                    .mapRequest(bytes -> { throw ex; })
                    .method(ConcatenatingHandler::new)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(mock(Flow.Subscription.class));
            pipeline.onNext(Bytes.wrap("hello"));
            verify(replies).onError(ex);
        }

        @Test
        void badMethodCallsOnError() {
            final var ex = new RuntimeException("Bad bad bad");
            Pipelines.<String, String>clientStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(sink -> { throw ex; })
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            verify(replies).onError(ex);
        }

        @Test
        void positive() {
            final var pipeline = Pipelines.<String, String>clientStreaming()
                    .mapRequest(Bytes::asUtf8String)
                    .method(ConcatenatingHandler::new)
                    .mapResponse(Bytes::wrap)
                    .respondTo(replies)
                    .build();

            pipeline.onSubscribe(subscription);
            pipeline.onNext(Bytes.wrap("hello"));
            pipeline.onNext(Bytes.wrap("world"));
            pipeline.onComplete();
            verify(replies).onNext(Bytes.wrap("hello:world"));
        }

        private static final class ConcatenatingHandler implements Pipeline<String> {
            private final List<String> strings = new ArrayList<>();
            private final Pipeline<? super String> sink;

            private ConcatenatingHandler(Pipeline<? super String> sink) {
                this.sink = sink;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                // Nothing
            }

            @Override
            public void onNext(String item) {
                strings.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                // Nothing
            }

            @Override
            public void onComplete() {
                sink.onNext(String.join(":", strings));
            }
        }
    }
}
