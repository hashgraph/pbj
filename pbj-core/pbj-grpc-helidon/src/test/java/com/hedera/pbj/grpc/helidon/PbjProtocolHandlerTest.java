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
import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.hedera.pbj.grpc.helidon.PbjProtocolHandlerTest.TestGreeterService.TestGreeterMethod.sayHelloStreamReply;
import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PbjProtocolHandlerTest {

    @Mock private Http2StreamWriter http2StreamWriter;

    @Mock private StreamFlowControl streamFlowControl;

    @Mock private PbjMethodRoute pbjMethodRoute;

    @Mock private BufferData bufferData;

    @Mock private HeadersProcessor headersProcessor;

    @Mock private ServiceInterface.RequestOptions options;

    @Mock private static Consumer<String> testConsumer;


    @Test
    public void testOnErrorHandlerCalledOnException() {

        final HelloRequest helloRequest = HelloRequest.newBuilder().setName("Alice").build();
        final var grpcDataProcessor = new TestGrpcDataProcessor(helloRequest);
        when(headersProcessor.options()).thenReturn(options);
        final SendToClientSubscriber sendToClientSubscriber = new SendToClientSubscriber(
                http2StreamWriter, 1, streamFlowControl, pbjMethodRoute, grpcDataProcessor, headersProcessor);

        final Pipeline<? super HelloReply> subscriber = mock(Pipeline.class);
        when(pbjMethodRoute.service()).thenReturn(new TestGreeterService(subscriber));
        when(pbjMethodRoute.method()).thenReturn(sayHelloStreamReply);

        final PipelineBuilder pipelineBuilder = new PipelineBuilder(
                http2StreamWriter, 1, streamFlowControl, pbjMethodRoute, headersProcessor.options(), sendToClientSubscriber.subscriber(), grpcDataProcessor, headersProcessor);
        final Pipeline<? super Bytes> pipeline = pipelineBuilder.createPipeline();

        grpcDataProcessor.setPipeline(pipeline);
        sendToClientSubscriber.setPipeline(pipeline);

        when(bufferData.available()).thenReturn(1, 0);
        grpcDataProcessor.data(null, bufferData);

        doThrow(IllegalArgumentException.class).when(bufferData).available();
        grpcDataProcessor.data(null, bufferData);

        verify(testConsumer, timeout(50).times(1)).accept("TEST");
    }

    private static class TestGrpcDataProcessor implements GrpcDataProcessor {

        private Pipeline<? super Bytes> pipeline;
        private final byte[] helloRequestBytes;

        private TestGrpcDataProcessor(final HelloRequest helloRequest) {
            this.helloRequestBytes = helloRequest.toByteArray();
        }

        public void data(@NonNull final Http2FrameHeader header, @NonNull final BufferData data) {
            try {
                while (data.available() > 0) {
                    pipeline.onNext(Bytes.wrap(helloRequestBytes));
                }
            } catch (Exception e) {
                pipeline.onError(e);
            }
        }

        public void setPipeline(@NonNull final Pipeline<? super Bytes> pipeline) {
            this.pipeline = requireNonNull(pipeline);
        }

        public void setCurrentStreamState(UnaryOperator<Http2StreamState> operator) {}

        public Http2StreamState getCurrentStreamState() {
            return null;
        }
    }

    static class TestGreeterService implements ServiceInterface {

        public TestGreeterService(final Pipeline<? super HelloReply> subscriber) {
//            this.subscriber = subscriber;
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
                    testConsumer.accept("TEST");
                });
        }

        @NonNull
        Pipeline<? super HelloRequest> sayHelloStreamBidi(
                Pipeline<? super HelloReply> replies) {
            return null;
        }
    }
}
