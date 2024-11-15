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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.uri.UriEncoding;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PbjProtocolHandlerTest {
    private Http2Headers headers;
    private StreamWriterStub streamWriter;
    private int streamId;
    private OutboundFlowControlStub flowControl;
    private Http2StreamState currentStreamState;
    private PbjConfigStub config;
    private PbjMethodRoute route;
    private DeadlineDetectorStub deadlineDetector;

    @BeforeAll
    static void beforeAll() {
    }

    @BeforeEach
    void setUp() {
        headers = Http2Headers.create(WritableHeaders.create());
        streamWriter = new StreamWriterStub();
        streamId = 1;
        flowControl = new OutboundFlowControlStub();
        currentStreamState = Http2StreamState.OPEN;
        config = new PbjConfigStub();
        route = new PbjMethodRoute(new GreeterServiceImpl(), GreeterService.GreeterMethod.sayHello);
        deadlineDetector = new DeadlineDetectorStub();

        assumeThat(route.requestCounter().count()).isEqualTo(0);
        assumeThat(route.failedGrpcRequestCounter().count()).isEqualTo(0);
        assumeThat(route.failedHttpRequestCounter().count()).isEqualTo(0);
        assumeThat(route.failedUnknownRequestCounter().count()).isEqualTo(0);
    }

    /**
     * If the content-type is missing, or does not start with "application/grpc", the server should respond with a 415
     * Unsupported Media Type and the stream state should end up CLOSED. See
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md"/>
     */
    @ValueSource(strings = {"", "text/plain", "application/json"})
    @ParameterizedTest
    void unsupportedContentType(String contentType) {
        final var h = WritableHeaders.create();
        if (!contentType.isBlank()) h.add(HeaderNames.CONTENT_TYPE, contentType);
        headers = Http2Headers.create(h);
        final var handler = new PbjProtocolHandler(headers, streamWriter, streamId, flowControl, currentStreamState, config, route, deadlineDetector);
        handler.init();
        // Even though the request failed, it was made, and should be counted
        assertThat(route.requestCounter().count()).isEqualTo(1);
        // And since it failed the failed counter should be incremented
        assertThat(route.failedGrpcRequestCounter().count()).isEqualTo(0);
        assertThat(route.failedHttpRequestCounter().count()).isEqualTo(1);
        assertThat(route.failedUnknownRequestCounter().count()).isEqualTo(0);

        // Check the HTTP2 response header frame was error 415
        assertThat(streamWriter.writtenHeaders).hasSize(1);
        assertThat(streamWriter.writtenDataFrames).isEmpty();
        final var responseHeaderFrame = streamWriter.writtenHeaders.getFirst();
        assertThat(responseHeaderFrame.status()).isEqualTo(Status.UNSUPPORTED_MEDIA_TYPE_415);

        // I verified with the go GRPC server its behavior in this scenario. The following headers should be
        // available in the response
        // Content-Type: application/grpc
        // Grpc-Message: invalid gRPC request content-type ""
        // Grpc-Status: 3
        final var responseHeaders = responseHeaderFrame.httpHeaders().stream()
                .collect(Collectors.toMap(Header::name, Header::values));
        assertThat(responseHeaders).contains(
                entry("grpc-status", "" + GrpcStatus.INVALID_ARGUMENT.ordinal()),
                entry("grpc-message", UriEncoding.encodeUri("invalid gRPC request content-type \"" + contentType + "\"")),
                entry("Content-Type", "application/grpc"),
                entry("grpc-accept-encoding", "identity"));

        // The stream should be closed
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
    }

    private static final class OutboundFlowControlStub implements FlowControl.Outbound {

        @Override
        public long incrementStreamWindowSize(int increment) {
            return 0;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            return new Http2FrameData[0];
        }

        @Override
        public void blockTillUpdate() {

        }

        @Override
        public int maxFrameSize() {
            return 0;
        }

        @Override
        public void decrementWindowSize(int decrement) {

        }

        @Override
        public void resetStreamWindowSize(int size) {

        }

        @Override
        public int getRemainingWindowSize() {
            return 0;
        }
    }

    private static final class StreamWriterStub implements Http2StreamWriter {
        private final List<Http2FrameData> writtenDataFrames = new ArrayList<>();
        private final List<Http2Headers> writtenHeaders = new ArrayList<>();


        @Override
        public void write(Http2FrameData frame) {
            writtenDataFrames.add(frame);
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            writtenDataFrames.add(frame);
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl) {
            writtenHeaders.add(headers);
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, Http2FrameData dataFrame, FlowControl.Outbound flowControl) {
            writtenHeaders.add(headers);
            writtenDataFrames.add(dataFrame);
            return 0;
        }
    }

    private static final class PbjConfigStub implements PbjConfig {

        @Override
        public int maxMessageSizeBytes() {
            return 0;
        }

        @Override
        public String name() {
            return "";
        }
    }

    private static final class DeadlineDetectorStub implements DeadlineDetector {
        @NonNull
        @Override
        public ScheduledFuture<?> scheduleDeadline(long deadlineNanos, @NonNull Runnable onDeadlineExceeded) {
            return new ScheduledFuture<>() {
                @Override
                public long getDelay(@NonNull TimeUnit unit) {
                    return 0;
                }

                @Override
                public int compareTo(@NonNull Delayed o) {
                    return 0;
                }

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return false;
                }

                @Override
                public Object get() {
                    return null;
                }

                @Override
                public Object get(long timeout, @NonNull TimeUnit unit) {
                    return null;
                }
            };
        }
    }
}
