package com.hedera.pbj.runtime.grpc;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.Flow;

public final class Pipelines {

    private Pipelines() {
        // No instantiation
    }

    public static <T, R> UnaryBuilder<T, R> unary() {
        return new UnaryBuilderImpl();
    }

    public static <T, R> BidiStreamingBuilder<T, R> bidiStreaming() {
        return new BidiStreamingBuilderImpl();
    }

    public static <T, R> ClientStreamingBuilder<T, R> clientStreaming() {
        return new ClientStreamingBuilderImpl();
    }

    public static <T, R> ServerStreamingBuilder<T, R> serverStreaming() {
        return new ServerStreamingBuilderImpl();
    }

    // Interfaces that represent each step along the path of constructing the pipeline

    public interface PipelineBuilder<T, R> {
        PipelineBuilder<T, R> mapRequest(MappingMethod<Bytes, T> mapper);
        PipelineBuilder<T, R> mapResponse(MappingMethod<R, Bytes> mapper);
        PipelineBuilder<T, R> respondTo(Flow.Subscriber<? super Bytes> replies);
        Flow.Subscriber<? super Bytes> build();
    }

    public interface UnaryBuilder<T,R> extends PipelineBuilder<T,R> {
        UnaryBuilder<T, R> mapRequest(MappingMethod<Bytes, T> mapper);
        UnaryBuilder<T, R> method(UnaryMethod<T, R> method);
        UnaryBuilder<T, R> mapResponse(MappingMethod<R, Bytes> mapper);
        UnaryBuilder<T, R> respondTo(Flow.Subscriber<? super Bytes> replies);
    }

    public interface BidiStreamingBuilder<T,R> extends PipelineBuilder<T,R> {
        BidiStreamingBuilder<T, R> mapRequest(MappingMethod<Bytes, T> mapper);
        BidiStreamingBuilder<T, R> method(BidiStreamingMethod<T, R> method);
        BidiStreamingBuilder<T, R> mapResponse(MappingMethod<R, Bytes> mapper);
        BidiStreamingBuilder<T, R> respondTo(Flow.Subscriber<? super Bytes> replies);
    }

    public interface ClientStreamingBuilder<T,R> extends PipelineBuilder<T,R> {
        ClientStreamingBuilder<T, R> mapRequest(MappingMethod<Bytes, T> mapper);
        ClientStreamingBuilder<T, R> method(ClientStreamingMethod<T, R> method);
        ClientStreamingBuilder<T, R> mapResponse(MappingMethod<R, Bytes> mapper);
        ClientStreamingBuilder<T, R> respondTo(Flow.Subscriber<? super Bytes> replies);
    }

    public interface ServerStreamingBuilder<T,R> extends PipelineBuilder<T,R> {
        ServerStreamingBuilder<T, R> mapRequest(MappingMethod<Bytes, T> mapper);
        ServerStreamingBuilder<T, R> method(ServerStreamingMethod<T, R> method);
        ServerStreamingBuilder<T, R> mapResponse(MappingMethod<R, Bytes> mapper);
        ServerStreamingBuilder<T, R> respondTo(Flow.Subscriber<? super Bytes> replies);
    }

    // Implementations
    private abstract static class PipelineBuilderImpl<T, R> implements PipelineBuilder<T, R>, Flow.Subscriber<Bytes>, Flow.Subscription {
        protected MappingMethod<Bytes, T> requestMapper;
        protected MappingMethod<R, Bytes> responseMapper;
        protected Flow.Subscriber<? super Bytes> replies;

        @Override
        public PipelineBuilder<T, R> mapRequest(MappingMethod<Bytes, T> mapper) {
            this.requestMapper = mapper;
            return this;
        }

        @Override
        public PipelineBuilder<T, R> mapResponse(MappingMethod<R, Bytes> mapper) {
            this.responseMapper = mapper;
            return this;
        }

        @Override
        public PipelineBuilder<T, R> respondTo(Flow.Subscriber<? super Bytes> replies) {
            this.replies = replies;
            return this;
        }

        @Override
        public Flow.Subscriber<? super Bytes> build() {
            replies.onSubscribe(this);
            return this;
        }

        @Override
        public void request(long n) {
            // TODO
        }

        @Override
        public void cancel() {
            // TODO
        }


        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE); // turn off flow control for now
        }

        @Override
        public void onError(Throwable throwable) {
            replies.onError(throwable);
        }

        @Override
        public void onComplete() {
            replies.onComplete();
        }
    }

    private static final class UnaryBuilderImpl<T, R> extends PipelineBuilderImpl<T, R> implements UnaryBuilder<T, R> {
        private UnaryMethod<T, R> method;

        @Override
        public UnaryBuilder<T, R> mapRequest(MappingMethod<Bytes, T> mapper) {
            super.mapRequest(mapper);
            return this;
        }

        @Override
        public UnaryBuilder<T, R> method(UnaryMethod<T, R> method) {
            this.method = method;
            return this;
        }

        @Override
        public UnaryBuilder<T, R> mapResponse(MappingMethod<R, Bytes> mapper) {
            super.mapResponse(mapper);
            return this;
        }

        @Override
        public UnaryBuilder<T, R> respondTo(Flow.Subscriber<? super Bytes> replies) {
            super.respondTo(replies);
            return this;
        }

        @Override
        public Flow.Subscriber<? super Bytes> build() {
            super.build();
            return this;
        }

        @Override
        public void onNext(final Bytes message) {
            try {
                final var request = requestMapper.apply(message);
                final var reply = method.apply(request);
                final var replyBytes = responseMapper.apply(reply);
                replies.onNext(replyBytes);
                replies.onComplete();
            } catch (Exception e) {
                replies.onError(e);
            }
        }
    }

    private static final class BidiStreamingBuilderImpl<T, R> extends PipelineBuilderImpl<T, R> implements BidiStreamingBuilder<T, R> {
        private BidiStreamingMethod<T, R> method;
        private Flow.Subscriber<? super T> incoming;

        @Override
        public BidiStreamingBuilderImpl<T, R> mapRequest(MappingMethod<Bytes, T> mapper) {
            super.mapRequest(mapper);
            return this;
        }

        @Override
        public BidiStreamingBuilderImpl<T, R> method(BidiStreamingMethod<T, R> method) {
            this.method = method;
            return this;
        }

        @Override
        public BidiStreamingBuilderImpl<T, R> mapResponse(MappingMethod<R, Bytes> mapper) {
            super.mapResponse(mapper);
            return this;
        }

        @Override
        public BidiStreamingBuilderImpl<T, R> respondTo(Flow.Subscriber<? super Bytes> replies) {
            super.respondTo(replies);
            return this;
        }

        @Override
        public Flow.Subscriber<? super Bytes> build() {
            super.build();
            final var responseConverter = new MapSubscriber<R, Bytes>(replies) {
                @Override
                public Bytes map(R item) throws Exception {
                    return responseMapper.apply(item);
                }
            };

            try {
                incoming = method.apply(responseConverter);
            } catch (Exception e) {
                replies.onError(e);
            }
            return this;
        }

        @Override
        public void onNext(final Bytes message) {
            try {
                final var request = requestMapper.apply(message);
                incoming.onNext(request);
            } catch (Exception e) {
                replies.onError(e);
            }
        }

        @Override
        public void onComplete() {
            incoming.onComplete();
            super.onComplete();
        }
    }

    private static final class ClientStreamingBuilderImpl<T, R> extends PipelineBuilderImpl<T, R> implements ClientStreamingBuilder<T, R> {
        private ClientStreamingMethod<T, R> method;
        private Flow.Subscriber<? super T> incoming;

        @Override
        public ClientStreamingBuilderImpl<T, R> mapRequest(MappingMethod<Bytes, T> mapper) {
            super.mapRequest(mapper);
            return this;
        }

        @Override
        public ClientStreamingBuilderImpl<T, R> method(ClientStreamingMethod<T, R> method) {
            this.method = method;
            return this;
        }

        @Override
        public ClientStreamingBuilderImpl<T, R> mapResponse(MappingMethod<R, Bytes> mapper) {
            super.mapResponse(mapper);
            return this;
        }

        @Override
        public ClientStreamingBuilderImpl<T, R> respondTo(Flow.Subscriber<? super Bytes> replies) {
            super.respondTo(replies);
            return this;
        }

        @Override
        public Flow.Subscriber<? super Bytes> build() {
            super.build();
            final var responseConverter = new MapSubscriber<R, Bytes>(replies) {
                @Override
                public Bytes map(R item) throws Exception {
                    return responseMapper.apply(item);
                }
            };

            try {
                incoming = method.apply(responseConverter);
            } catch (Exception e) {
                replies.onError(e);
            }
            return this;
        }

        @Override
        public void onNext(final Bytes message) {
            try {
                final var request = requestMapper.apply(message);
                incoming.onNext(request);
            } catch (Exception e) {
                replies.onError(e);
            }
        }

        @Override
        public void onComplete() {
            incoming.onComplete();
            super.onComplete();
        }
    }

    private static final class ServerStreamingBuilderImpl<T, R> extends PipelineBuilderImpl<T, R> implements ServerStreamingBuilder<T, R> {
        private ServerStreamingMethod<T, R> method;

        @Override
        public ServerStreamingBuilderImpl<T, R> mapRequest(MappingMethod<Bytes, T> mapper) {
            super.mapRequest(mapper);
            return this;
        }

        @Override
        public ServerStreamingBuilderImpl<T, R> method(ServerStreamingMethod<T, R> method) {
            this.method = method;
            return this;
        }

        @Override
        public ServerStreamingBuilderImpl<T, R> mapResponse(MappingMethod<R, Bytes> mapper) {
            super.mapResponse(mapper);
            return this;
        }

        @Override
        public ServerStreamingBuilderImpl<T, R> respondTo(Flow.Subscriber<? super Bytes> replies) {
            super.respondTo(replies);
            return this;
        }

        @Override
        public Flow.Subscriber<? super Bytes> build() {
            super.build();
            return this;
        }

        @Override
        public void onNext(final Bytes message) {
            final var converter = new MapSubscriber<R, Bytes>(replies) {
                @Override
                public Bytes map(R item) throws Exception {
                    return responseMapper.apply(item);
                }
            };

            try {
                final var request = requestMapper.apply(message);
                method.apply(request, converter);
            } catch (Exception e) {
                replies.onError(e);
            }
        }
    }

//
//    public static final class BidiBuilder<T, R> {
//        private final BidiStreamingMethod<T, R> handler;
//        private final MappingMethod<Bytes, T> mapper;
//
//        public BidiBuilder(BidiStreamingMethod<T, R> handler, MappingMethod<Bytes, T> mapper) {
//            this.handler = handler;
//            this.mapper = mapper;
//        }
//
//        /*
//
//                    final var r = new MapSubscriber<HelloReply, Bytes>(replies) {
//                        @Override
//                        public Bytes map(HelloReply item) throws Exception {
//                            return createReply(item, options);
//                        }
//                    };
//
//                    final var incoming = new MapSubscriber<Bytes, HelloRequest>(sayHelloStreamBidi(r)) {
//                        @Override
//                        public HelloRequest map(Bytes item) throws Exception {
//                            return parseRequest(item, options);
//                        }
//                    };
//                    return new UnarySubscriber<>() {
//                        @Override
//                        public void onNext(Bytes item) {
//                            try {
//                                incoming.onNext(item);
//                            } catch (Exception e) {
//                                replies.onError(e);
//                            }
//                        }
//
//                        @Override
//                        public void onComplete() {
//                            incoming.onComplete();
//                        }
//                    };
//
//         */
//
//        public ResponseMappingBuilder<R> map(MappingMethod<R, Bytes> mapper) {
//            return new ResponseMappingBuilder<>(mapper);
//        }
//
//    }
//
//    public static final class ClientStreamingBuilder<T, R> {
//        private final ClientStreamingMethod<T, R> handler;
//        private final MappingMethod<Bytes, T> mapper;
//
//        public ClientStreamingBuilder(ClientStreamingMethod<T, R> handler, MappingMethod<Bytes, T> mapper) {
//            this.handler = handler;
//            this.mapper = mapper;
//        }
//
//        /*
//
//                    // This guy will convert from HelloReply to Bytes and send it back to the client.
//                    final var r = new MapSubscriber<HelloReply, Bytes>(replies) {
//                        @Override
//                        public Bytes map(HelloReply item) throws Exception {
//                            return createReply(item, options);
//                        }
//                    };
//
//                    final var incoming = new MapSubscriber<Bytes, HelloRequest>(sayHelloStreamRequest(r)) {
//                        @Override
//                        public HelloRequest map(Bytes item) throws Exception {
//                            return parseRequest(item, options);
//                        }
//                    };
//
//                    return new UnarySubscriber<>() {
//                        @Override
//                        public void onNext(Bytes item) {
//                            try {
//                                incoming.onNext(item);
//                            } catch (Exception e) {
//                                replies.onError(e);
//                            }
//                        }
//
//                        @Override
//                        public void onComplete() {
//                            incoming.onComplete();
//                        }
//                    };
//         */
//
//        public ResponseMappingBuilder<R> map(MappingMethod<R, Bytes> mapper) {
//            return new ResponseMappingBuilder<>(mapper);
//        }
//    }

    public interface MappingMethod<T, R> {
        R apply(T request) throws Exception;
    }

    public interface UnaryMethod<T, R> {
        R apply(T request) throws Exception;
    }

    public interface ClientStreamingMethod<T, R> {
        Flow.Subscriber<? super T> apply(Flow.Subscriber<? super R> replies) throws Exception;
    }

    public interface ServerStreamingMethod<T, R> {
        void apply(T request, Flow.Subscriber<? super R> replies) throws Exception;
    }

    public interface BidiStreamingMethod<T, R> {
        Flow.Subscriber<? super T> apply(Flow.Subscriber<? super R> replies) throws Exception;
    }

    private record CallParams<T, R>(MappingMethod<Bytes, T> mapper, Runnable r, MappingMethod<R, Bytes> mapper2) {

    }
}
