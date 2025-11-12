// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Flow;

/**
 * Utility class for generating a "pipeline" of processing steps for gRPC services. This is not intended to be used
 * directly by application code, but rather by the PBJ compiler when generating service interfaces.
 */
public final class Pipelines {

    private Pipelines() {
        // No instantiation
    }

    /**
     * Returns a {@link Flow.Subscriber} that does nothing. This can be used in cases where a subscriber is required
     * but no proper implementation is available.
     *
     * @return A No-op subscriber.
     */
    public static Pipeline<? super Bytes> noop() {
        return new Pipeline<>() {
            @Override
            public void clientEndStreamReceived() {
                // Nothing to do
            }

            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(@NonNull final Flow.Subscription subscription) {
                this.subscription = requireNonNull(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(@NonNull final Bytes item) {
                // Nothing to do
            }

            @Override
            public void onError(@NonNull final Throwable throwable) {
                // Just cancel the subscription but nothing else to do
                subscription.cancel();
            }

            @Override
            public void onComplete() {
                // Nothing to do
            }
        };
    }

    /**
     * Create a new pipeline for a unary gRPC service method. A unary method is a simple request/response method.
     *
     * @return A new builder for constructing the pipeline.
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public static <T, R> UnaryBuilder<T, R> unary() {
        return new UnaryBuilderImpl<>();
    }

    /**
     * Create a new pipeline for a bidirectional streaming gRPC service method. A bidirectional streaming method
     * allows for a stream of requests and a stream of responses to operate concurrently.
     *
     * @return A new builder for constructing the pipeline.
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public static <T, R> BidiStreamingBuilder<T, R> bidiStreaming() {
        return new BidiStreamingBuilderImpl<>();
    }

    /**
     * Create a new pipeline for a client streaming gRPC service method. A client streaming method allows for a
     * stream of requests to be sent to the server, with a single response returned at the very end.
     *
     * @return A new builder for constructing the pipeline.
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public static <T, R> ClientStreamingBuilder<T, R> clientStreaming() {
        return new ClientStreamingBuilderImpl<>();
    }

    /**
     * Create a new pipeline for a server streaming gRPC service method. A server streaming method allows for a
     * single request to be sent to the server, with a stream of responses returned.
     *
     * @return A new builder for constructing the pipeline.
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public static <T, R> ServerStreamingBuilder<T, R> serverStreaming() {
        return new ServerStreamingBuilderImpl<>();
    }

    /**
     * A builder for constructing the pipeline for a unary gRPC service method.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public interface UnaryBuilder<T, R> {
        /**
         * Configures a lambda for mapping from {@link Bytes} to the request message type. This must be specified.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        UnaryBuilder<T, R> mapRequest(@NonNull ExceptionalFunction<Bytes, T> mapper);

        /**
         * Configures the unary method to be called when a request is received. This method handles the request and
         * returns a response. This must be specified.
         *
         * @param method The method to call.
         * @return This builder.
         */
        @NonNull
        UnaryBuilder<T, R> method(@NonNull ExceptionalFunction<T, R> method);

        /**
         * Configures a lambda for mapping from the response message type to {@link Bytes}. This must be specified.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        UnaryBuilder<T, R> mapResponse(@NonNull ExceptionalFunction<R, Bytes> mapper);

        /**
         * Configures a subscriber to receive the response messages. This must be specified. This subscriber is
         * provided by the web server and is responsible for sending the responses back to the client.
         *
         * @param replies The subscriber to receive the responses.
         * @return This builder.
         */
        @NonNull
        UnaryBuilder<T, R> respondTo(@NonNull Pipeline<? super Bytes> replies);

        /**
         * Builds the pipeline and returns it. The returned pipeline receives the incoming messages, and contains
         * the replies that are sent back to the client.
         *
         * @return the communication pipeline
         */
        @NonNull
        Pipeline<? super Bytes> build();
    }

    /**
     * A builder for constructing the pipeline for a bidirectional streaming gRPC service method.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public interface BidiStreamingBuilder<T, R> {
        /**
         * Configures a lambda for mapping from {@link Bytes} to the request message type. This must be specified.
         * This function will be called once for each message arriving from the client.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        BidiStreamingBuilder<T, R> mapRequest(@NonNull ExceptionalFunction<Bytes, T> mapper);

        /**
         * Configures the bidirectional streaming method to be called when a request is received. This method is given
         * a subscriber that it can push responses to, and it returns a subscriber that the system can push requests to.
         * This must be specified.
         *
         * @param method The method to call.
         * @return This builder.
         */
        @NonNull
        BidiStreamingBuilder<T, R> method(@NonNull BidiStreamingMethod<T, R> method);

        /**
         * Configures a lambda for mapping from the response message type to {@link Bytes}. This must be specified.
         * This function will be called once for each message that the method sends back to the client.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        BidiStreamingBuilder<T, R> mapResponse(@NonNull ExceptionalFunction<R, Bytes> mapper);

        /**
         * Configures a subscriber to receive the response messages. This must be specified. This subscriber is
         * provided by the web server and is responsible for sending the responses back to the client.
         *
         * @param replies The subscriber to receive the responses.
         * @return This builder.
         */
        @NonNull
        BidiStreamingBuilder<T, R> respondTo(@NonNull Pipeline<? super Bytes> replies);

        /**
         * Builds the pipeline and returns it. The returned pipeline receives the incoming messages, and contains
         * the replies that are sent back to the client.
         *
         * @return the communication pipeline
         */
        @NonNull
        Pipeline<? super Bytes> build();
    }

    /**
     * A builder for constructing the pipeline for a client streaming gRPC service method.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public interface ClientStreamingBuilder<T, R> {
        /**
         * Configures a lambda for mapping from {@link Bytes} to the request message type. This must be specified.
         * This function will be called once for each message arriving from the client.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        ClientStreamingBuilder<T, R> mapRequest(@NonNull ExceptionalFunction<Bytes, T> mapper);

        /**
         * Configures the client streaming method to be called when a request is received. This method is given
         * a subscriber that it can push responses to, and it returns a subscriber that the system can push requests to.
         * Only a single message is returned through the subscriber.
         * This must be specified.
         *
         * @param method The method to call.
         * @return This builder.
         */
        @NonNull
        ClientStreamingBuilder<T, R> method(@NonNull ClientStreamingMethod<T, R> method);

        /**
         * Configures a lambda for mapping from the response message type to {@link Bytes}. This must be specified.
         * This function will be called once for each message that the method sends back to the client.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        ClientStreamingBuilder<T, R> mapResponse(@NonNull ExceptionalFunction<R, Bytes> mapper);

        /**
         * Configures a subscriber to receive the response messages. This must be specified. This subscriber is
         * provided by the web server and is responsible for sending the responses back to the client.
         *
         * @param replies The subscriber to receive the responses.
         * @return This builder.
         */
        @NonNull
        ClientStreamingBuilder<T, R> respondTo(@NonNull Pipeline<? super Bytes> replies);

        /**
         * Builds the pipeline and returns it. The returned pipeline receives the incoming messages, and contains
         * the replies that are sent back to the client.
         *
         * @return the communication pipeline
         */
        @NonNull
        Pipeline<? super Bytes> build();
    }

    /**
     * A builder for constructing the pipeline for a server streaming gRPC service method.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public interface ServerStreamingBuilder<T, R> {
        /**
         * Configures a lambda for mapping from {@link Bytes} to the request message type. This must be specified.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        ServerStreamingBuilder<T, R> mapRequest(@NonNull ExceptionalFunction<Bytes, T> mapper);

        /**
         * Configures the server streaming method to be called when a request is received. This method is given
         * a subscriber that it can push responses to. This must be specified.
         *
         * @param method The method to call.
         * @return This builder.
         */
        @NonNull
        ServerStreamingBuilder<T, R> method(@NonNull ServerStreamingMethod<T, R> method);

        /**
         * Configures a lambda for mapping from the response message type to {@link Bytes}. This must be specified.
         * This function will be called once for each message that the method sends back to the client.
         *
         * @param mapper The mapping function.
         * @return This builder.
         */
        @NonNull
        ServerStreamingBuilder<T, R> mapResponse(@NonNull ExceptionalFunction<R, Bytes> mapper);

        /**
         * Configures a subscriber to receive the response messages. This must be specified. This subscriber is
         * provided by the web server and is responsible for sending the responses back to the client.
         *
         * @param replies The subscriber to receive the responses.
         * @return This builder.
         */
        @NonNull
        ServerStreamingBuilder<T, R> respondTo(@NonNull Pipeline<? super Bytes> replies);

        /**
         * Builds the pipeline and returns it. The returned pipeline receives the incoming messages, and contains
         * the replies that are sent back to the client.
         *
         * @return the communication pipeline
         */
        @NonNull
        Pipeline<? super Bytes> build();
    }

    /**
     * A function that can throw an exception.
     *
     * @param <T> The type of the input.
     * @param <R> The type of the output.
     */
    @FunctionalInterface
    public interface ExceptionalFunction<T, R> {
        /**
         * Applies this function to the given argument.
         *
         * @param t The input argument.
         * @return The function result.
         * @throws Exception If an error occurs during processing.
         */
        @NonNull
        R apply(@NonNull T t) throws Exception;
    }

    /**
     * A function that handles a client streaming gRPC service method. Many messages are received from the client,
     * but only a single response is sent back to the client when completed.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    @FunctionalInterface
    public interface ClientStreamingMethod<T, R>
            extends ExceptionalFunction<Pipeline<? super R>, Pipeline<? super T>> {}

    /**
     * A function that handles a server streaming gRPC service method. A single request is received from the client,
     * and many responses are sent back to the client.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public interface ServerStreamingMethod<T, R> {
        /**
         * Applies the server streaming method.
         *
         * @param request The request message.
         * @param replies The subscriber to send responses to.
         * @throws Exception If an error occurs during processing.
         */
        void apply(@NonNull T request, @NonNull Pipeline<? super R> replies) throws Exception;
    }

    /**
     * A function that handles a bidirectional streaming gRPC service method. Many messages are received from the
     * client, and many responses are sent back to the client.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    public interface BidiStreamingMethod<T, R> extends ExceptionalFunction<Pipeline<? super R>, Pipeline<? super T>> {}

    /**
     * A convenient base class for the different builders. All builders have to hold state for request and
     * response mapping functions, as well as the subscriber to send responses to, so we have a base class.
     * This class also implements the {@link Pipeline} and {@link Flow.Subscription} interfaces, to
     * reduce the overall number of instances created.
     *
     * <p>A {@link Flow.Subscription} is provided to each subscriber at the time they subscribe. Technically
     * this can be a many-to-one relationship, but in our case, there is only going to be one subscriber for
     * this {@link Flow.Subscription}, so we can simplify things a bit.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    private abstract static class PipelineBuilderImpl<T, R> implements Pipeline<Bytes>, Flow.Subscription {
        protected ExceptionalFunction<Bytes, T> requestMapper;
        protected ExceptionalFunction<R, Bytes> responseMapper;
        protected Pipeline<? super Bytes> replies;
        private Flow.Subscription sourceSubscription;
        protected boolean completed = false;

        @Override
        public void request(long n) {
            // If we supported flow control, we'd pay attention to the number being presented. And we should, ideally,
            // implement flow control. For now, we don't, so for now this is ignored.
        }

        @Override
        public void cancel() {
            sourceSubscription.cancel();
        }

        @Override
        public void onSubscribe(@NonNull final Flow.Subscription subscription) {
            // This method is called ...
            this.sourceSubscription = requireNonNull(subscription);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onError(@NonNull final Throwable throwable) {
            if (replies != null) {
                replies.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            completed = true;
            if (replies != null) {
                replies.onComplete();
            }
        }

        protected void validateParams() {
            if (replies == null) {
                throw new IllegalStateException("The replies subscriber must be specified.");
            }

            if (requestMapper == null) {
                throw new IllegalStateException("The request mapper must be specified.");
            }

            if (responseMapper == null) {
                throw new IllegalStateException("The response mapper must be specified.");
            }
        }
    }

    /**
     * The implementation of the {@link UnaryBuilder} interface.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    private static final class UnaryBuilderImpl<T, R> extends PipelineBuilderImpl<T, R> implements UnaryBuilder<T, R> {
        private ExceptionalFunction<T, R> method;

        @Override
        @NonNull
        public UnaryBuilder<T, R> mapRequest(@NonNull final ExceptionalFunction<Bytes, T> mapper) {
            this.requestMapper = requireNonNull(mapper);
            return this;
        }

        @Override
        @NonNull
        public UnaryBuilder<T, R> method(@NonNull final ExceptionalFunction<T, R> method) {
            this.method = requireNonNull(method);
            return this;
        }

        @Override
        @NonNull
        public UnaryBuilder<T, R> mapResponse(@NonNull final ExceptionalFunction<R, Bytes> mapper) {
            this.responseMapper = requireNonNull(mapper);
            return this;
        }

        @Override
        @NonNull
        public UnaryBuilder<T, R> respondTo(@NonNull final Pipeline<? super Bytes> replies) {
            this.replies = requireNonNull(replies);
            return this;
        }

        @Override
        @NonNull
        public Pipeline<? super Bytes> build() {
            validateParams();
            if (method == null) {
                throw new IllegalStateException("The method must be specified.");
            }

            replies.onSubscribe(this);
            return this;
        }

        @Override
        public void onNext(@NonNull final Bytes message) {
            // A unary method call is pretty simple. We take the incoming bytes, convert them into the request
            // message type, call the method, and then convert the response message back into bytes. If there
            // are any exceptions, we forward that along. Otherwise, we just do the work and complete.

            if (completed) {
                replies.onError(new IllegalStateException("Unary method already called."));
                return;
            }

            try {
                final var request = requestMapper.apply(message);
                final var reply = method.apply(request);
                final var replyBytes = responseMapper.apply(reply);
                replies.onNext(replyBytes);
                onComplete();
            } catch (RuntimeException e) {
                replies.onError(e);
                throw e;
            } catch (Exception e) {
                replies.onError(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void clientEndStreamReceived() {
            // nothing to do, as onComplete is always called inside onNext
        }

        @Override
        public void closeConnection() {
            replies.closeConnection();
        }
    }

    /**
     * The implementation of the {@link BidiStreamingBuilder} interface.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    private static final class BidiStreamingBuilderImpl<T, R> extends PipelineBuilderImpl<T, R>
            implements BidiStreamingBuilder<T, R> {
        private BidiStreamingMethod<T, R> method;
        private Pipeline<? super T> incoming;

        @Override
        @NonNull
        public BidiStreamingBuilderImpl<T, R> mapRequest(@NonNull final ExceptionalFunction<Bytes, T> mapper) {
            this.requestMapper = mapper;
            return this;
        }

        @Override
        @NonNull
        public BidiStreamingBuilderImpl<T, R> method(@NonNull final BidiStreamingMethod<T, R> method) {
            this.method = method;
            return this;
        }

        @Override
        @NonNull
        public BidiStreamingBuilderImpl<T, R> mapResponse(@NonNull final ExceptionalFunction<R, Bytes> mapper) {
            this.responseMapper = mapper;
            return this;
        }

        @Override
        @NonNull
        public BidiStreamingBuilderImpl<T, R> respondTo(@NonNull final Pipeline<? super Bytes> replies) {
            this.replies = replies;
            return this;
        }

        @Override
        @NonNull
        public Pipeline<? super Bytes> build() {
            validateParams();
            if (method == null) {
                throw new IllegalStateException("The method must be specified.");
            }

            replies.onSubscribe(this);

            // This subscriber maps from the response type to bytes and sends them back to the client. Whenever
            // the "onNext" method produces a new response, it will pass through this subscriber before being
            // forwarded to the "replies" subscriber, where the webserver will return it to the client.
            final var responseConverter = new MapSubscriber<R, Bytes>(replies, item -> responseMapper.apply(item));

            try {
                incoming = method.apply(responseConverter);
            } catch (Exception e) {
                replies.onError(e);
            }
            return this;
        }

        @Override
        public void onNext(@NonNull final Bytes message) {
            if (completed) {
                replies.onError(new IllegalStateException("BidiStreaming method already called."));
                return;
            }

            try {
                final var request = requestMapper.apply(message);
                incoming.onNext(request);
            } catch (RuntimeException e) {
                replies.onError(e);
                throw e;
            } catch (Exception e) {
                replies.onError(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onComplete() {
            incoming.onComplete();
            super.onComplete();
        }

        @Override
        public void clientEndStreamReceived() {
            // if the client stream is ended, the entire pipeline is ended
            onComplete();
        }

        @Override
        public void closeConnection() {
            replies.closeConnection();
        }
    }

    /**
     * The implementation of the {@link ClientStreamingBuilder} interface.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    private static final class ClientStreamingBuilderImpl<T, R> extends PipelineBuilderImpl<T, R>
            implements ClientStreamingBuilder<T, R> {
        private ClientStreamingMethod<T, R> method;
        private Pipeline<? super T> incoming;

        @Override
        @NonNull
        public ClientStreamingBuilderImpl<T, R> mapRequest(@NonNull final ExceptionalFunction<Bytes, T> mapper) {
            this.requestMapper = mapper;
            return this;
        }

        @Override
        @NonNull
        public ClientStreamingBuilderImpl<T, R> method(@NonNull final ClientStreamingMethod<T, R> method) {
            this.method = method;
            return this;
        }

        @Override
        @NonNull
        public ClientStreamingBuilderImpl<T, R> mapResponse(@NonNull final ExceptionalFunction<R, Bytes> mapper) {
            this.responseMapper = mapper;
            return this;
        }

        @Override
        @NonNull
        public ClientStreamingBuilderImpl<T, R> respondTo(@NonNull final Pipeline<? super Bytes> replies) {
            this.replies = replies;
            return this;
        }

        @Override
        @NonNull
        public Pipeline<? super Bytes> build() {
            validateParams();
            if (method == null) {
                throw new IllegalStateException("The method must be specified.");
            }
            replies.onSubscribe(this);
            final var responseConverter = new MapSubscriber<R, Bytes>(replies, item -> responseMapper.apply(item));

            try {
                incoming = method.apply(responseConverter);
            } catch (Exception e) {
                replies.onError(e);
            }
            return this;
        }

        @Override
        public void onNext(@NonNull final Bytes message) {
            if (completed) {
                replies.onError(new IllegalStateException("ClientStreaming method already called."));
                return;
            }

            try {
                final var request = requestMapper.apply(message);
                incoming.onNext(request);
            } catch (RuntimeException e) {
                replies.onError(e);
                throw e;
            } catch (Exception e) {
                replies.onError(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onComplete() {
            incoming.onComplete();
            super.onComplete();
        }

        @Override
        public void clientEndStreamReceived() {
            onComplete();
        }

        @Override
        public void closeConnection() {
            replies.closeConnection();
        }
    }

    /**
     * The implementation of the {@link ServerStreamingBuilder} interface.
     *
     * @param <T> The type of the request message.
     * @param <R> The type of the response message.
     */
    private static final class ServerStreamingBuilderImpl<T, R> extends PipelineBuilderImpl<T, R>
            implements ServerStreamingBuilder<T, R> {
        private ServerStreamingMethod<T, R> method;
        private Pipeline<? super R> responseConverter;

        @Override
        @NonNull
        public ServerStreamingBuilderImpl<T, R> mapRequest(@NonNull final ExceptionalFunction<Bytes, T> mapper) {
            this.requestMapper = mapper;
            return this;
        }

        @Override
        @NonNull
        public ServerStreamingBuilderImpl<T, R> method(@NonNull final ServerStreamingMethod<T, R> method) {
            this.method = method;
            return this;
        }

        @Override
        @NonNull
        public ServerStreamingBuilderImpl<T, R> mapResponse(@NonNull final ExceptionalFunction<R, Bytes> mapper) {
            this.responseMapper = mapper;
            return this;
        }

        @Override
        @NonNull
        public ServerStreamingBuilderImpl<T, R> respondTo(@NonNull final Pipeline<? super Bytes> replies) {
            this.replies = replies;
            return this;
        }

        @Override
        @NonNull
        public Pipeline<? super Bytes> build() {
            validateParams();
            if (method == null) {
                throw new IllegalStateException("The method must be specified.");
            }

            responseConverter = new MapSubscriber<>(replies, item -> responseMapper.apply(item));
            responseConverter.onSubscribe(
                    this); // Theoretically this should be done. But now I'm subscribing to this AND replies!
            return this;
        }

        @Override
        public void onNext(@NonNull final Bytes message) {
            if (completed) {
                replies.onError(new IllegalStateException("ServerStreaming method already called."));
                return;
            }

            try {
                final var request = requestMapper.apply(message);
                method.apply(request, responseConverter);
            } catch (RuntimeException e) {
                replies.onError(e);
                throw e;
            } catch (Exception e) {
                replies.onError(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void clientEndStreamReceived() {
            // nothing to do
            // the server will continue streaming, since the message coming from the client is a subscription request
        }

        @Override
        public void closeConnection() {
            replies.closeConnection();
        }
    }

    /**
     * A subscriber that maps from one type to another. It is like a Java "map" operation on a stream, but as a
     * subscriber.
     *
     * @param next The subscriber to send the mapped values to.
     * @param mapper The function to map from one type to another.
     * @param <T> The type of the input.
     * @param <R> The type of the output.
     */
    private record MapSubscriber<T, R>(Pipeline<? super R> next, ExceptionalFunction<T, R> mapper)
            implements Pipeline<T>, Flow.Subscription {

        private MapSubscriber {
            next.onSubscribe(this);
        }

        @Override
        public void request(long n) {
            // We don't care about flow control right now. We should, but we don't.
        }

        @Override
        public void cancel() {
            // FUTURE: Look into implementing this
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            try {
                final var r = mapper.apply(item);
                next.onNext(r);
            } catch (RuntimeException e) {
                next.onError(e);
                throw e;
            } catch (Throwable t) {
                next.onError(t);
                throw new RuntimeException(t);
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

        @Override
        public void closeConnection() {
            next.closeConnection();
        }
    }
}
