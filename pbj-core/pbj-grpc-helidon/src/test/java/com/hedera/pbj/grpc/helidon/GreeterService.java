/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.grpc.helidon;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.Pipelines;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.HelloReply;
import greeter.HelloRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * This service doesn't rely on any PBJ objects, because the build right now doesn't have a good way
 * to use the compiler. This will be fixed in a future release. So for now, we use Google's
 * generated protobuf objects.
 */
public interface GreeterService extends ServiceInterface {
    enum GreeterMethod implements Method {
        sayHello,
        sayHelloStreamRequest,
        sayHelloStreamReply,
        sayHelloStreamBidi
    }

    // Unary, a single request/response call.
    HelloReply sayHello(HelloRequest request);

    // A stream of messages coming from the client, with a single response from the server.
    Pipeline<? super HelloRequest> sayHelloStreamRequest(
            Flow.Subscriber<? super HelloReply> replies);

    // A single request from the client, with a stream of responses from the server.
    void sayHelloStreamReply(HelloRequest request, Flow.Subscriber<? super HelloReply> replies);

    // A bidirectional stream of requests and responses between the client and the server.
    Pipeline<? super HelloRequest> sayHelloStreamBidi(
            Flow.Subscriber<? super HelloReply> replies);

    @NonNull
    default String serviceName() {
        return "Greeter";
    }

    @NonNull
    default String fullName() {
        return "greeter.Greeter";
    }

    @NonNull
    default List<Method> methods() {
        return Arrays.asList(GreeterMethod.values());
    }

    @Override
    @NonNull
    default Pipeline<? super Bytes> open(
            final @NonNull Method method,
            final @NonNull RequestOptions options,
            final @NonNull Flow.Subscriber<? super Bytes> replies) {

        final var m = (GreeterMethod) method;
        try {
            return switch (m) {
                    // Simple request -> response
                case sayHello -> Pipelines.<HelloRequest, HelloReply>unary()
                        .mapRequest(bytes -> parseRequest(bytes, options))
                        .method(this::sayHello)
                        .mapResponse(reply -> createReply(reply, options))
                        .respondTo(replies)
                        .build();
                    // Client sends many requests with a single response from the server at the end
                case sayHelloStreamRequest -> Pipelines.<HelloRequest, HelloReply>clientStreaming()
                        .mapRequest(bytes -> parseRequest(bytes, options))
                        .method(this::sayHelloStreamRequest)
                        .mapResponse(reply -> createReply(reply, options))
                        .respondTo(replies)
                        .build();
                    // Client sends a single request and the server sends many responses
                case sayHelloStreamReply -> Pipelines.<HelloRequest, HelloReply>serverStreaming()
                        .mapRequest(bytes -> parseRequest(bytes, options))
                        .method(this::sayHelloStreamReply)
                        .mapResponse(reply -> createReply(reply, options))
                        .respondTo(replies)
                        .build();
                    // Client and server are sending messages back and forth.
                case sayHelloStreamBidi -> Pipelines.<HelloRequest, HelloReply>bidiStreaming()
                        .mapRequest(bytes -> parseRequest(bytes, options))
                        .method(this::sayHelloStreamBidi)
                        .mapResponse(reply -> createReply(reply, options))
                        .respondTo(replies)
                        .build();
            };
        } catch (Exception e) {
            replies.onError(e);
            return Pipelines.noop();
        }
    }

    @NonNull
    private HelloRequest parseRequest(
            @NonNull final Bytes message, @NonNull final RequestOptions options)
            throws InvalidProtocolBufferException {
        Objects.requireNonNull(message);

        final HelloRequest request;
        if (options.isProtobuf()) {
            request = HelloRequest.parseFrom(message.toByteArray());
        } else if (options.isJson()) {
            final var builder = HelloRequest.newBuilder();
            JsonFormat.parser().merge(message.asUtf8String(), builder);
            request = builder.build();
        } else {
            request = HelloRequest.newBuilder().setName(message.asUtf8String()).build();
        }
        return request;
    }

    @NonNull
    private Bytes createReply(
            @NonNull final HelloReply reply, @NonNull final RequestOptions options)
            throws InvalidProtocolBufferException {
        Objects.requireNonNull(reply);

        if (options.isProtobuf()) {
            return Bytes.wrap(reply.toByteArray());
        } else if (options.isJson()) {
            return Bytes.wrap(JsonFormat.printer().print(reply));
        } else {
            return Bytes.wrap(reply.getMessage().getBytes());
        }
    }
}
