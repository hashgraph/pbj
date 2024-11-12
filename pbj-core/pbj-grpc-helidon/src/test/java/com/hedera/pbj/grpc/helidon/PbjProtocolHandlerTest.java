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
import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.Pipelines;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.hedera.pbj.grpc.helidon.PbjProtocolHandlerTest.TestGreeterService.TestGreeterMethod.sayHelloStreamReply;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PbjProtocolHandlerTest {

    @Mock private Http2Headers http2Headers;

    @Mock private Http2StreamWriter http2StreamWriter;

    @Mock private StreamFlowControl streamFlowControl;

    @Mock private Http2StreamState http2StreamState;

    @Mock private PbjConfig pbjConfig;

    @Mock private PbjMethodRoute pbjMethodRoute;

    @Mock private DeadlineDetector deadlineDetector;

    @Mock private BufferData bufferData;


    @Mock private Pipeline<? super Bytes> pipeline;

    @Test
    public void testOnErrorHandlerCalledOnException() {

        // We're testing the onError() routing from PbjProtocolHandler into
        // an Application defined method. To confirm the registered handler
        // gets called when there's an exception.
        final Pipeline<? super HelloReply> subscriber = mock(Pipeline.class);
        final HeadersProcessor headersProcessor = new HeadersProcessor(
                http2Headers, http2StreamWriter, 1, streamFlowControl, pbjMethodRoute, deadlineDetector);
        final var grpcDataProcessor = new GrpcDataProcessorImpl(pbjConfig, http2StreamState);

        final SendToClientSubscriber sendToClientSubscriber = new SendToClientSubscriber(
                http2StreamWriter, 1, streamFlowControl, pbjMethodRoute, grpcDataProcessor, headersProcessor);
        final PipelineBuilder pipelineBuilder = new PipelineBuilder(pbjMethodRoute, headersProcessor.options(), sendToClientSubscriber.subscriber());

        grpcDataProcessor.setPipeline(pipeline);
        sendToClientSubscriber.setPipeline(pipeline);
        headersProcessor.setPipeline(pipeline);

        final PbjProtocolHandler testPbjProtocolHandler = new PbjProtocolHandler(
                http2StreamWriter,
                1,
                streamFlowControl,
                pbjMethodRoute,
                grpcDataProcessor,
                headersProcessor,
                pipeline);

        doThrow(IllegalArgumentException.class).when(bufferData).available();
        testPbjProtocolHandler.data(null, bufferData);
    }

    static class TestGreeterService implements ServiceInterface {

        private final Pipeline<? super HelloReply> subscriber;

        public TestGreeterService(final Pipeline<? super HelloReply> subscriber) {
            this.subscriber = subscriber;
        }

        enum TestGreeterMethod implements Method {
            sayHelloStreamReply,
            sayHelloStreamBidi
        }

        @NonNull
        public String serviceName() {
            return "Greeter";
        }

        @NonNull
        public String fullName() {
            return "greeter.Greeter";
        }

        @NonNull
        public List<Method> methods() {
            return Arrays.asList(GreeterService.GreeterMethod.values());
        }

        @Override
        @NonNull
        public Pipeline<? super Bytes> open(
                final @NonNull Method method,
                final @NonNull RequestOptions options,
                final @NonNull Pipeline<? super Bytes> replies) {

            final var m = (TestGreeterMethod) method;
            try {
                return switch (m) {
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

        void sayHelloStreamReply(
                HelloRequest request, Pipeline<? super HelloReply> replies) {
                replies.registerOnErrorHandler(() -> {
                    System.out.println("Error handler called");
                    subscriber.onError(new NoSuchAlgorithmException());
                });
        }

        @NonNull
        Pipeline<? super HelloRequest> sayHelloStreamBidi(
                Pipeline<? super HelloReply> replies) {
            return null;
        }
    }
}
