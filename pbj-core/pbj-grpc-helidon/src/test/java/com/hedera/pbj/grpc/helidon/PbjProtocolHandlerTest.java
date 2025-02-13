// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriEncoding;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.metrics.api.Metrics;
import io.netty.handler.codec.http2.Http2Flags;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private ServiceInterfaceStub service;

    @BeforeEach
    void setUp() {
        headers = Http2Headers.create(WritableHeaders.create().add(HeaderNames.CONTENT_TYPE, "application/grpc+proto"));
        streamWriter = new StreamWriterStub();
        streamId = 1;
        flowControl = new OutboundFlowControlStub();
        currentStreamState = Http2StreamState.OPEN;
        config = new PbjConfigStub();
        service = new ServiceInterfaceStub();
        route = new PbjMethodRoute(service, ServiceInterfaceStub.METHOD);
        deadlineDetector = new DeadlineDetectorStub();

        Metrics.globalRegistry().close(); // reset the counters
        assertThat(route.requestCounter().count()).isZero();
        assertThat(route.failedGrpcRequestCounter().count()).isZero();
        assertThat(route.failedHttpRequestCounter().count()).isZero();
        assertThat(route.failedUnknownRequestCounter().count()).isZero();
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

        // Initializing the handler will throw an error because the content types are unsupported
        final var handler = new PbjProtocolHandler(
                headers, streamWriter, streamId, flowControl, currentStreamState, config, route, deadlineDetector);
        handler.init();

        // Even though the request failed, it was made, and should be counted
        assertThat(route.requestCounter().count()).isEqualTo(1);
        // And since it failed the failed counter should be incremented
        assertThat(route.failedGrpcRequestCounter().count()).isZero();
        assertThat(route.failedHttpRequestCounter().count()).isEqualTo(1);
        assertThat(route.failedUnknownRequestCounter().count()).isZero();

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
        final var responseHeaders =
                responseHeaderFrame.httpHeaders().stream().collect(Collectors.toMap(Header::name, Header::values));
        assertThat(responseHeaders)
                .contains(
                        entry("grpc-status", "" + GrpcStatus.INVALID_ARGUMENT.ordinal()),
                        entry(
                                "grpc-message",
                                UriEncoding.encodeUri("invalid gRPC request content-type \"" + contentType + "\"")),
                        entry("Content-Type", "application/grpc"),
                        entry("grpc-accept-encoding", "identity"));

        // The stream should be closed
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
    }

    /**
     * If the content type is "application/grpc", then it is treated as "application/grpc+proto".
     */
    @Test
    void contentTypeIsNormalized() {
        final var h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc");
        headers = Http2Headers.create(h);

        // Initialize will succeed!
        final var handler = new PbjProtocolHandler(
                headers, streamWriter, streamId, flowControl, currentStreamState, config, route, deadlineDetector);
        handler.init();
        assertThat(route.requestCounter().count()).isEqualTo(1);

        // This will call the service
        final var data = createRequestData("Alice");
        sendAllData(handler, data);

        assertThat(service.calledMethod).isSameAs(ServiceInterfaceStub.METHOD);
        assertThat(service.receivedBytes).contains(data);
        assertThat(service.opts.contentType()).isEqualTo("application/grpc+proto");

        assertThat(route.failedGrpcRequestCounter().count()).isZero();
        assertThat(route.failedHttpRequestCounter().count()).isZero();
        assertThat(route.failedUnknownRequestCounter().count()).isZero();
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.HALF_CLOSED_REMOTE);
    }

    /**
     * These are perfectly valid encodings, but none of them are supported at this time.
     *
     * @param encoding the encoding to test with.
     */
    @ValueSource(strings = {"gzip", "compress", "deflate", "br", "zstd", "gzip, deflate;q=0.5"})
    @ParameterizedTest
    void unsupportedGrpcEncodings(String encoding) {
        final var h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc+proto");
        h.add(HeaderNames.create("grpc-encoding"), encoding);
        headers = Http2Headers.create(h);

        // Initializing the handler will throw an error because the content types are unsupported
        final var handler = new PbjProtocolHandler(
                headers, streamWriter, streamId, flowControl, currentStreamState, config, route, deadlineDetector);
        handler.init();

        // Even though the request failed, it was made, and should be counted
        assertThat(route.requestCounter().count()).isEqualTo(1);
        // And since it failed the failed counter should be incremented
        assertThat(route.failedGrpcRequestCounter().count()).isEqualTo(1);
        assertThat(route.failedHttpRequestCounter().count()).isZero();
        assertThat(route.failedUnknownRequestCounter().count()).isZero();

        // The HTTP2 response itself was successful, but the GRPC response was not
        assertThat(streamWriter.writtenHeaders).hasSize(1);
        assertThat(streamWriter.writtenDataFrames).isEmpty();
        final var responseHeaderFrame = streamWriter.writtenHeaders.getFirst();
        assertThat(responseHeaderFrame.status()).isEqualTo(Status.OK_200);

        // I verified with the go GRPC server its behavior in this scenario. The following headers should be
        // available in the response
        // Content-Type: application/grpc
        // Grpc-Message: grpc: Decompressor is not installed for grpc-encoding "[bad encoding here]"
        // Grpc-Status: 12
        final var responseHeaders =
                responseHeaderFrame.httpHeaders().stream().collect(Collectors.toMap(Header::name, Header::values));
        assertThat(responseHeaders)
                .contains(
                        entry("grpc-status", "" + GrpcStatus.UNIMPLEMENTED.ordinal()),
                        entry(
                                "grpc-message",
                                UriEncoding.encodeUri(
                                        "Decompressor is not installed for grpc-encoding \"" + encoding + "\"")),
                        entry("Content-Type", "application/grpc"),
                        entry("grpc-accept-encoding", "identity"));

        // The stream should be closed
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
    }

    /**
     * These are encodings we support. They all contain "identity".
     *
     * <p>See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding"/> for information
     * on the encoding header syntax.
     *
     * @param encoding
     */
    @ValueSource(
            strings = {
                // Simple identity strings with qualifiers
                "identity",
                "identity;q=0.5",
                "identity;",
                "identity;nonsense",
                // an identity with and without a qualifier in a list of encodings
                "gzip, deflate;q=0.5, identity;q=0.1",
                "gzip, deflate;q=0.5, identity",
                "gzip, identity;q=0.1, deflate;q=0.5",
                "gzip, identity, deflate;q=0.5",
                "identity;q=.9, deflate;q=0.5, gzip;q=0.1, br;q=0.1",
                "identity, deflate;q=0.5, gzip;q=0.1, br;q=0.1"
            })
    @ParameterizedTest
    void supportedComplexEncodingsWithIdentity(String encoding) {
        final var h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc+proto");
        h.add(HeaderNames.create("grpc-encoding"), encoding);
        headers = Http2Headers.create(h);

        // Initializing the handler will throw an error because the content types are unsupported
        final var handler = new PbjProtocolHandler(
                headers, streamWriter, streamId, flowControl, currentStreamState, config, route, deadlineDetector);
        handler.init();

        // Even though the request failed, it was made, and should be counted
        assertThat(route.requestCounter().count()).isEqualTo(1);
        // And since it failed the failed counter should be incremented
        assertThat(route.failedGrpcRequestCounter().count()).isZero();
        assertThat(route.failedHttpRequestCounter().count()).isZero();
        assertThat(route.failedUnknownRequestCounter().count()).isZero();

        final var data = createRequestData("Alice");
        sendAllData(handler, data);

        // The HTTP2 response itself was successful, and so was the gRPC response
        assertThat(streamWriter.writtenHeaders).hasSize(1);
        assertThat(streamWriter.writtenDataFrames).isEmpty();
        final var responseHeaderFrame = streamWriter.writtenHeaders.getFirst();
        assertThat(responseHeaderFrame.status()).isEqualTo(Status.OK_200);

        final var responseHeaders =
                responseHeaderFrame.httpHeaders().stream().collect(Collectors.toMap(Header::name, Header::values));
        assertThat(responseHeaders)
                .contains(entry("Content-Type", "application/grpc+proto"), entry("grpc-accept-encoding", "identity"));

        // The stream should be closed
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.HALF_CLOSED_REMOTE);
    }

    @Test
    void errorThrownForOnNextWhenStreamIsClosed() {
        // Use a custom streamWriter that will throw an exception when "streamClosed" is set to true, and it is
        // asked to write something. This can be used to simulate what happens when the network connection fails.
        final var streamClosed = new AtomicBoolean(false);
        streamWriter = new StreamWriterStub() {
            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                if (streamClosed.get()) {
                    throw new IllegalStateException("Stream is closed");
                }
            }
        };

        // Within this test, the replyRef will be set once when the setup is complete, and then
        // will be available for the test code to use to call onNext, onError, etc. as required.
        final var replyRef = new AtomicReference<Pipeline<? super HelloReply>>();
        route = new PbjMethodRoute(
                new GreeterServiceImpl() {
                    @Override
                    public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
                        replyRef.set(replies);
                    }
                },
                GreeterService.GreeterMethod.sayHelloStreamReply);

        final var handler = new PbjProtocolHandler(
                headers, streamWriter, streamId, flowControl, currentStreamState, config, route, deadlineDetector);
        handler.init();
        sendAllData(handler, createRequestData("Alice"));

        final var replies = replyRef.get();
        assertThat(replies).isNotNull();

        replies.onNext(HelloReply.newBuilder().setMessage("Good").build());
        streamClosed.set(true);

        final var failingReply = HelloReply.newBuilder().setMessage("Bad").build();
        assertThatThrownBy(() -> replies.onNext(failingReply)).isInstanceOf(Exception.class);

        assertThat(route.requestCounter().count()).isEqualTo(1);
        assertThat(route.failedGrpcRequestCounter().count()).isEqualTo(0);
        assertThat(route.failedHttpRequestCounter().count()).isEqualTo(0);
        assertThat(route.failedUnknownRequestCounter().count()).isEqualTo(0);
        assertThat(route.failedResponseCounter().count()).isEqualTo(1);
    }

    private Bytes createRequestData(String name) {
        return createData(HelloRequest.newBuilder().setName(name).build());
    }

    private Bytes createData(HelloRequest request) {
        return Bytes.wrap(request.toByteArray());
    }

    private void sendAllData(PbjProtocolHandler handler, Bytes bytes) {
        final var frameHeader = createDataFrameHeader((int) bytes.length());
        final var buf = createDataFrameBytes(bytes);
        handler.data(frameHeader, buf);
    }

    private BufferData createDataFrameBytes(Bytes data) {
        try {
            final var buf = new ByteArrayOutputStream((int) data.length() + 5);
            final var s = new DataOutputStream(buf);
            s.writeByte(0);
            s.writeInt((int) data.length());
            data.writeTo(s);
            return BufferData.create(buf.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Http2FrameHeader createDataFrameHeader(int length) {
        return Http2FrameHeader.create(
                length + 5, Http2FrameTypes.DATA, Http2Flag.DataFlags.create(Http2Flags.END_STREAM), streamId);
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
        public void blockTillUpdate() {}

        @Override
        public int maxFrameSize() {
            return 0;
        }

        @Override
        public void decrementWindowSize(int decrement) {}

        @Override
        public void resetStreamWindowSize(int size) {}

        @Override
        public int getRemainingWindowSize() {
            return 0;
        }
    }

    private static class StreamWriterStub implements Http2StreamWriter {
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
        public int writeHeaders(
                Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl) {
            writtenHeaders.add(headers);
            return 0;
        }

        @Override
        public int writeHeaders(
                Http2Headers headers,
                int streamId,
                Http2Flag.HeaderFlags flags,
                Http2FrameData dataFrame,
                FlowControl.Outbound flowControl) {
            writtenHeaders.add(headers);
            writtenDataFrames.add(dataFrame);
            return 0;
        }
    }

    private static final class PbjConfigStub implements PbjConfig {

        @Override
        public int maxMessageSizeBytes() {
            return 1000;
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

    private static final class ServiceInterfaceStub implements ServiceInterface {
        private Method calledMethod;
        private RequestOptions opts;
        private List<Bytes> receivedBytes = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        static final Method METHOD = () -> "m";

        @NonNull
        @Override
        public String serviceName() {
            return "s";
        }

        @NonNull
        @Override
        public String fullName() {
            return "s/m";
        }

        @NonNull
        @Override
        public List<Method> methods() {
            return List.of(METHOD);
        }

        @NonNull
        @Override
        public Pipeline<? super Bytes> open(
                @NonNull Method method, @NonNull RequestOptions opts, @NonNull Pipeline<? super Bytes> responses)
                throws GrpcException {
            this.calledMethod = method;
            this.opts = opts;
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // ignored
                }

                @Override
                public void onNext(Bytes item) {
                    receivedBytes.add(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    error = throwable;
                }

                @Override
                public void onComplete() {
                    completed = true;
                }
            };
        }
    }
}
