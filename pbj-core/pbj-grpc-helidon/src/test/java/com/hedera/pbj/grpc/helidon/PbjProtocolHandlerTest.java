// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
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
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.netty.handler.codec.http2.Http2Flags;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.SocketAddress;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    private ConnectionContext connectionContext;

    private PeerInfo remotePeer;

    @BeforeEach
    void setUp() {
        Metrics.globalRegistry().close(); // reset before creating counters for this test
        headers = Http2Headers.create(WritableHeaders.create().add(HeaderNames.CONTENT_TYPE, "application/grpc+proto"));
        streamWriter = new StreamWriterStub();
        streamId = 1;
        flowControl = new OutboundFlowControlStub();
        currentStreamState = Http2StreamState.OPEN;
        config = new PbjConfigStub();
        service = new ServiceInterfaceStub();
        route = new PbjMethodRoute(service, PbjGrpcServiceConfig.DEFAULT, ServiceInterfaceStub.METHOD);
        deadlineDetector = new DeadlineDetectorStub();

        record PeerInfoRecord(
                SocketAddress address,
                String host,
                int port,
                Optional<Principal> tlsPrincipal,
                Optional<Certificate[]> tlsCertificates)
                implements PeerInfo {}
        remotePeer = new PeerInfoRecord(null, null, 0, Optional.empty(), Optional.empty());

        connectionContext = new ConnectionContext() {
            @Override
            public ListenerContext listenerContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ExecutorService executor() {
                throw new UnsupportedOperationException();
            }

            @Override
            public DataWriter dataWriter() {
                throw new UnsupportedOperationException();
            }

            @Override
            public DataReader dataReader() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Router router() {
                throw new UnsupportedOperationException();
            }

            @Override
            public PeerInfo remotePeer() {
                return remotePeer;
            }

            @Override
            public PeerInfo localPeer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isSecure() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String socketId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String childSocketId() {
                throw new UnsupportedOperationException();
            }
        };

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
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
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
        // Grpc-Message: Unsupported gRPC request content type
        // Grpc-Status: 3
        final var responseHeaders =
                responseHeaderFrame.httpHeaders().stream().collect(Collectors.toMap(Header::name, Header::values));
        assertThat(responseHeaders)
                .contains(
                        entry("grpc-status", "" + GrpcStatus.INVALID_ARGUMENT.ordinal()),
                        entry("grpc-message", UriEncoding.encodeUri("Unsupported gRPC request content type")),
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
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
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
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
    }

    /**
     * Passing valid empty request is handled correctly. Such case is for example unary api asking for status, without passing anything in the request body.
     */
    @Test
    void requestIsSuccessfulOnEmptyData() {
        final WritableHeaders<?> h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc");
        headers = Http2Headers.create(h);
        final PbjProtocolHandler handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
        handler.init();

        final Bytes emptyData = createRequestData("");
        sendAllData(handler, emptyData);

        assertThat(service.calledMethod).isSameAs(ServiceInterfaceStub.METHOD);
        assertThat(service.receivedBytes).contains(emptyData);
        assertThat(service.opts.contentType()).isEqualTo("application/grpc+proto");

        assertThat(route.failedGrpcRequestCounter().count()).isZero();
        assertThat(route.failedHttpRequestCounter().count()).isZero();
        assertThat(route.failedUnknownRequestCounter().count()).isZero();
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
    }

    @Test
    void acceptEncodingIncludesIdentityIfServerConfigIsEmpty() {
        final WritableHeaders<?> h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc");
        headers = Http2Headers.create(h);
        final PbjProtocolHandler handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                new PbjMethodRoute(
                        service, new PbjGrpcServiceConfig("identity", Set.of()), ServiceInterfaceStub.METHOD),
                deadlineDetector,
                connectionContext);
        handler.init();

        final Bytes emptyData = createRequestData("");
        sendAllData(handler, emptyData);

        assertThat(service.calledMethod).isSameAs(ServiceInterfaceStub.METHOD);
        assertThat(service.receivedBytes).contains(emptyData);
        assertThat(service.opts.contentType()).isEqualTo("application/grpc+proto");

        assertThat(route.failedGrpcRequestCounter().count()).isZero();
        assertThat(route.failedHttpRequestCounter().count()).isZero();
        assertThat(route.failedUnknownRequestCounter().count()).isZero();
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);

        final var responseHeaderFrame = streamWriter.writtenHeaders.getFirst();
        assertThat(responseHeaderFrame.status()).isEqualTo(Status.OK_200);

        final var responseHeaders =
                responseHeaderFrame.httpHeaders().stream().collect(Collectors.toMap(Header::name, Header::values));
        assertThat(responseHeaders)
                .contains(
                        entry("Content-Type", "application/grpc"),
                        entry("grpc-encoding", "identity"),
                        entry("grpc-accept-encoding", "identity"));

        // This test creates a new route which registers metrics similar to what the default route registers.
        // This somehow causes the @BeforeEach method to see non-zero metric counters even after a reset.
        // So we reset the counters at the end of this test manually:
        Metrics.globalRegistry().close();
    }

    /**
     * These are perfectly valid encodings, but none of them are supported at this time.
     *
     * @param encoding the encoding to test with.
     */
    @ValueSource(strings = {"compress", "deflate", "br", "rar", "gzip, deflate;q=0.5"})
    @ParameterizedTest
    void unsupportedGrpcEncodings(String encoding) {
        final var h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc+proto");
        h.add(HeaderNames.create("grpc-encoding"), encoding);
        headers = Http2Headers.create(h);

        // Initializing the handler will throw an error because the content types are unsupported
        final var handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
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
        // Grpc-Message: Unsupported gRPC request encoding
        // Grpc-Status: 12
        final var responseHeaders =
                responseHeaderFrame.httpHeaders().stream().collect(Collectors.toMap(Header::name, Header::values));
        assertThat(responseHeaders)
                .contains(
                        entry("grpc-status", "" + GrpcStatus.UNIMPLEMENTED.ordinal()),
                        entry("grpc-message", UriEncoding.encodeUri("Unsupported gRPC request encoding")),
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
        h.add(HeaderNames.create("grpc-accept-encoding"), encoding);
        headers = Http2Headers.create(h);

        // Initializing the handler will throw an error because the content types are unsupported
        final var handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
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
        assertThat(responseHeaders).contains(entry("Content-Type", "application/grpc+proto"));
        assertThat(responseHeaders).containsKey("grpc-accept-encoding");
        assertThat(responseHeaders.get("grpc-accept-encoding")).contains("identity", "gzip");

        // The stream should be closed
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
    }

    @Test
    void compressionFlagNotZeroOrOneProducesError() {
        final WritableHeaders<?> h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc");
        headers = Http2Headers.create(h);
        final PbjProtocolHandler handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
        handler.init();

        final Bytes bytes = createRequestData("");
        final var frameHeader = createDataFrameHeader((int) bytes.length());
        final var buf = createDataFrameBytes(2, bytes);
        handler.data(frameHeader, buf);

        assertThat(service.calledMethod).isSameAs(ServiceInterfaceStub.METHOD);
        assertThat(service.opts.contentType()).isEqualTo("application/grpc+proto");
        assertThat(service.error).isInstanceOf(GrpcException.class);
        final GrpcException error = (GrpcException) service.error;
        assertThat(error.status()).isEqualTo(GrpcStatus.UNIMPLEMENTED);
        assertThat(error.getMessage()).isEqualTo("Compression flag 2 is not supported. Only 0 and 1 are supported.");
    }

    @Test
    void compressedPayloadCallsDecompressor() {
        final WritableHeaders<?> h = WritableHeaders.create();
        h.add(HeaderNames.CONTENT_TYPE, "application/grpc");
        h.add(HeaderNames.createFromLowercase("grpc-encoding"), "gzip");
        headers = Http2Headers.create(h);
        final PbjProtocolHandler handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
        handler.init();

        final Bytes uncompressedBytes = createRequestData("test");
        final Bytes bytes = GrpcCompression.getCompressor("gzip").compress(uncompressedBytes);
        final var frameHeader = createDataFrameHeader((int) bytes.length());
        final var buf = createDataFrameBytes(1, bytes);
        handler.data(frameHeader, buf);

        assertThat(service.calledMethod).isSameAs(ServiceInterfaceStub.METHOD);
        assertThat(service.opts.contentType()).isEqualTo("application/grpc+proto");
        assertThat(service.error).isNull();
        assertThat(service.receivedBytes).contains(uncompressedBytes);

        assertThat(route.failedGrpcRequestCounter().count()).isZero();
        assertThat(route.failedHttpRequestCounter().count()).isZero();
        assertThat(route.failedUnknownRequestCounter().count()).isZero();
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
    }

    /**
     * Verifies that {@code scheduleDeadline} passes the timeout to the detector as a delay from now, not as an
     * absolute {@link System#nanoTime()} value, which the detector would treat as a delay of days.
     */
    @Test
    void scheduleDeadlinePassesTimeoutNanosToDetector() {
        final long timeoutNanos = TimeUnit.SECONDS.toNanos(30);
        headers = Http2Headers.create(WritableHeaders.create()
                .add(HeaderNames.CONTENT_TYPE, "application/grpc+proto")
                .add(HeaderNames.create("grpc-timeout"), "30S"));

        final var handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
        handler.init();

        assertThat(deadlineDetector.capturedTimeoutNanos).isPositive().isLessThan(timeoutNanos);
    }

    @Test
    void deadlineDuringServiceOpenClosesResponseAndNotifiesPipelineOnce() {
        headers = deadlineHeaders();
        final PbjProtocolHandler handler = deadlineHandler();
        service.onOpen = () -> {
            deadlineDetector.callback.run();
            assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
            assertThat(service.error).isNull(); // open has not returned a request pipeline yet
            service.replies.onNext(Bytes.EMPTY);
            service.replies.onComplete();
        };

        handler.init();
        deadlineDetector.callback.run();
        service.replies.onError(new GrpcException(GrpcStatus.INTERNAL));

        assertThat(service.error).isInstanceOfSatisfying(GrpcException.class, e -> assertThat(e.status())
                .isEqualTo(GrpcStatus.DEADLINE_EXCEEDED));
        assertThat(service.errorCount).isEqualTo(1);
        assertThat(route.deadlineExceededCounter().count()).isEqualTo(1);
        assertThat(streamWriter.writtenDataFrames).isEmpty();
        assertSingleTerminalStatus(GrpcStatus.DEADLINE_EXCEEDED);
    }

    @Test
    void immediateDeadlineCancelsFutureReturnedAfterCallback() {
        headers = deadlineHeaders();
        deadlineDetector.runImmediately = true;
        final PbjProtocolHandler handler = deadlineHandler();

        handler.init();

        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
        assertThat(service.calledMethod).isNull();
        assertThat(deadlineDetector.cancelled).isTrue();
        assertSingleTerminalStatus(GrpcStatus.DEADLINE_EXCEEDED);
    }

    @Test
    void initializationFailureAfterDeadlineDoesNotSendAnotherStatus() {
        headers = deadlineHeaders();
        service.onOpen = () -> {
            deadlineDetector.callback.run();
            throw new GrpcException(GrpcStatus.INTERNAL);
        };
        final PbjProtocolHandler handler = deadlineHandler();

        handler.init();

        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
        assertThat(route.deadlineExceededCounter().count()).isEqualTo(1);
        assertSingleTerminalStatus(GrpcStatus.DEADLINE_EXCEEDED);
    }

    @Test
    void completionDuringServiceOpenWinsAgainstLateDeadline() {
        headers = deadlineHeaders();
        service.onOpen = () -> service.replies.onComplete();
        final PbjProtocolHandler handler = deadlineHandler();

        handler.init();
        deadlineDetector.callback.run();
        service.replies.onError(new GrpcException(GrpcStatus.INTERNAL));

        assertThat(service.error).isNull();
        assertThat(route.deadlineExceededCounter().count()).isZero();
        assertThat(deadlineDetector.cancelled).isTrue();
        assertSingleTerminalStatus(GrpcStatus.OK);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dataAfterInitializationFailureDoesNotDereferenceAMissingPipeline(final boolean failsDuringOpen) {
        if (failsDuringOpen) {
            service.onOpen = () -> {
                throw new IllegalStateException("Service failed to open");
            };
        } else {
            headers = Http2Headers.create(WritableHeaders.create().add(HeaderNames.CONTENT_TYPE, "text/plain"));
        }
        final PbjProtocolHandler handler = deadlineHandler();
        handler.init();
        handler.data(createDataFrameHeader(0), BufferData.create(new byte[] {0, 0, 0, 0, 0}));
        assertThat(service.receivedBytes).isEmpty();
        assertThat(handler.streamState()).isEqualTo(Http2StreamState.CLOSED);
        assertThat(streamWriter.writtenHeaders).hasSize(failsDuringOpen ? 2 : 1);
    }

    @ParameterizedTest
    @EnumSource(
            value = ServiceInterface.MethodType.class,
            names = {"UNARY", "SERVER_STREAMING"})
    void singleRequestMethodsRejectAnEmptyBody(final ServiceInterface.MethodType type) {
        useMethodType(type);
        final PbjProtocolHandler handler = deadlineHandler();
        handler.init();
        final Http2FrameHeader end = Http2FrameHeader.create(
                0, Http2FrameTypes.DATA, Http2Flag.DataFlags.create(Http2Flags.END_STREAM), streamId);
        handler.data(end, BufferData.create(0));
        assertThat(service.receivedBytes).isEmpty();
        assertSingleTerminalStatus(GrpcStatus.INVALID_ARGUMENT);
        assertThat(service.error).isInstanceOf(GrpcException.class);
    }

    @Test
    void incompleteMessageAtEndOfStreamIsRejected() {
        final PbjProtocolHandler handler = deadlineHandler();
        handler.init();
        handler.data(createDataFrameHeader(1), BufferData.create(new byte[] {0, 0, 0, 0, 3, 42}));
        assertThat(service.receivedBytes).isEmpty();
        assertSingleTerminalStatus(GrpcStatus.INVALID_ARGUMENT);
        assertThat(service.error).isInstanceOf(GrpcException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = ServiceInterface.MethodType.class,
            names = {"UNARY", "SERVER_STREAMING"})
    void singleRequestMethodsNeverDispatchASecondMessage(final ServiceInterface.MethodType type) {
        useMethodType(type);
        final PbjProtocolHandler handler = deadlineHandler();
        handler.init();
        handler.data(createDataFrameHeader(5), BufferData.create(new byte[10]));
        assertThat(service.receivedBytes).containsExactly(Bytes.EMPTY);
        assertSingleTerminalStatus(GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @EnumSource(
            value = ServiceInterface.MethodType.class,
            names = {"CLIENT_STREAMING", "BIDI_STREAMING", "UNKNOWN"})
    void streamingAndLegacyMethodsContinueToAcceptMultipleMessages(final ServiceInterface.MethodType type) {
        useMethodType(type);
        final PbjProtocolHandler handler = deadlineHandler();
        handler.init();
        handler.data(createDataFrameHeader(5), BufferData.create(new byte[10]));
        assertThat(service.receivedBytes).containsExactly(Bytes.EMPTY, Bytes.EMPTY);
        assertThat(service.error).isNull();
    }

    @Test
    void deadlineErrorIsSerializedAfterAnActiveServiceCallback() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean active = new AtomicBoolean();
        service.onMessage = () -> {
            active.set(true);
            entered.countDown();
            try {
                assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                active.set(false);
            }
        };
        service.onError = () -> assertThat(active).isFalse();
        headers = deadlineHeaders();
        final PbjProtocolHandler handler = deadlineHandler();
        handler.init();
        final FutureTask<Void> message = new FutureTask<>(() -> {
            handler.data(createDataFrameHeader(0), BufferData.create(new byte[5]));
            return null;
        });
        Thread.startVirtualThread(message);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            deadlineDetector.callback.run();
            assertThat(service.error).isNull();
            assertSingleTerminalStatus(GrpcStatus.DEADLINE_EXCEEDED);
        } finally {
            release.countDown();
        }
        message.get(2, TimeUnit.SECONDS);
        assertThat(service.error).isInstanceOf(GrpcException.class);
        assertThat(service.errorCount).isEqualTo(1);
    }

    private void useMethodType(final ServiceInterface.MethodType type) {
        final ServiceInterface.Method method = new ServiceInterface.Method() {
            @Override
            public String name() {
                return "m";
            }

            @Override
            public ServiceInterface.MethodType type() {
                return type;
            }
        };
        route = new PbjMethodRoute(service, PbjGrpcServiceConfig.DEFAULT, method);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finalTrailersWaitForAnInFlightMessage(final boolean deadline) throws Exception {
        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch releaseWrite = new CountDownLatch(1);
        final CountDownLatch terminalSent = new CountDownLatch(1);
        final ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();
        streamWriter = new StreamWriterStub() {
            @Override
            public void writeData(final Http2FrameData frame, final FlowControl.Outbound control) {
                writing.countDown();
                try {
                    assertThat(releaseWrite.await(2, TimeUnit.SECONDS)).isTrue();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                super.writeData(frame, control);
                order.add("message");
            }

            @Override
            public int writeHeaders(
                    final Http2Headers values,
                    final int id,
                    final Http2Flag.HeaderFlags flags,
                    final FlowControl.Outbound control) {
                if (values.httpHeaders().contains(HeaderNames.create("grpc-status"))) {
                    order.add("trailers");
                    terminalSent.countDown();
                }
                return super.writeHeaders(values, id, flags, control);
            }
        };
        headers = deadlineHeaders();
        final PbjProtocolHandler handler = deadlineHandler();
        handler.init();
        final FutureTask<Void> message = new FutureTask<>(() -> {
            service.replies.onNext(Bytes.EMPTY);
            return null;
        });
        final FutureTask<Void> terminal = new FutureTask<>(() -> {
            if (deadline) {
                deadlineDetector.callback.run();
            } else {
                service.replies.onComplete();
            }
            return null;
        });
        Thread.startVirtualThread(message);
        try {
            assertThat(writing.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.startVirtualThread(terminal);
            assertThat(terminalSent.await(100, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseWrite.countDown();
        }
        message.get(2, TimeUnit.SECONDS);
        terminal.get(2, TimeUnit.SECONDS);
        service.replies.onNext(Bytes.EMPTY);
        assertThat(order).containsExactly("message", "trailers");
        assertSingleTerminalStatus(deadline ? GrpcStatus.DEADLINE_EXCEEDED : GrpcStatus.OK);
    }

    private Http2Headers deadlineHeaders() {
        return Http2Headers.create(WritableHeaders.create()
                .add(HeaderNames.CONTENT_TYPE, "application/grpc+proto")
                .add(HeaderNames.create("grpc-timeout"), "30S"));
    }

    private PbjProtocolHandler deadlineHandler() {
        return new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
    }

    private void assertSingleTerminalStatus(final GrpcStatus status) {
        assertThat(streamWriter.writtenHeaders).hasSize(2);
        assertThat(streamWriter.writtenHeaders.getLast().httpHeaders().value(HeaderNames.create("grpc-status")))
                .contains(Integer.toString(status.ordinal()));
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
                PbjGrpcServiceConfig.DEFAULT,
                GreeterService.GreeterMethod.sayHelloStreamReply);

        final var handler = new PbjProtocolHandler(
                headers,
                streamWriter,
                streamId,
                flowControl,
                currentStreamState,
                config,
                route,
                deadlineDetector,
                connectionContext);
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
        final var buf = createDataFrameBytes(0, bytes);
        handler.data(frameHeader, buf);
    }

    private BufferData createDataFrameBytes(int compressedFlag, Bytes data) {
        try {
            final var buf = new ByteArrayOutputStream((int) data.length() + 5);
            final var s = new DataOutputStream(buf);
            s.writeByte(compressedFlag);
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
        long capturedTimeoutNanos = Long.MIN_VALUE;
        Runnable callback;
        boolean runImmediately;
        boolean cancelled;

        @NonNull
        @Override
        public ScheduledFuture<?> scheduleDeadline(long timeoutNanos, @NonNull Runnable onDeadlineExceeded) {
            capturedTimeoutNanos = timeoutNanos;
            callback = onDeadlineExceeded;
            if (runImmediately) {
                callback.run();
            }
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
                    cancelled = true;
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
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
        private int errorCount;
        private boolean completed;
        private Runnable onOpen = () -> {};
        private Runnable onMessage = () -> {};
        private Runnable onError = () -> {};
        private Pipeline<? super Bytes> replies;

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
            this.replies = responses;
            onOpen.run();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // ignored
                }

                @Override
                public void onNext(Bytes item) {
                    receivedBytes.add(item);
                    onMessage.run();
                }

                @Override
                public void onError(Throwable throwable) {
                    onError.run();
                    error = throwable;
                    errorCount++;
                }

                @Override
                public void onComplete() {
                    completed = true;
                }
            };
        }
    }
}
