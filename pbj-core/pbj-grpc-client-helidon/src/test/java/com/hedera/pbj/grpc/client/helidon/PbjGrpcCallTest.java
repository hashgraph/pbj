// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.tls.Tls;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.StreamTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PbjGrpcCallTest {
    private static final String METHOD_NAME = "testMethodName";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);
    private static final HeaderName GRPC_STATUS = HeaderNames.createFromLowercase("grpc-status");

    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    private static final Options OPTIONS =
            new Options(Optional.of("expected-authority"), ServiceInterface.RequestOptions.APPLICATION_GRPC);

    @Mock
    private PbjGrpcClient grpcClient;

    @Mock
    private ClientConnection clientConnection;

    @Mock
    private Http2ClientConnection connection;

    @Mock
    private PbjGrpcClientStream grpcClientStream;

    @Mock
    private Tls tls;

    @Mock
    private WebClient webClient;

    @Mock
    private ExecutorService executor;

    @Mock
    private Codec requestCodec;

    @Mock
    private Codec replyCodec;

    @Mock
    private Pipeline pipeline;

    @Mock
    private Http2Headers http2Headers;

    @Mock
    private Headers headers;

    private PbjGrpcCall createCall(final ServiceInterface.RequestOptions options) {
        doReturn(webClient).when(grpcClient).getWebClient();
        doReturn(executor).when(webClient).executor();

        final PbjGrpcClientConfig config =
                new PbjGrpcClientConfig(READ_TIMEOUT, tls, OPTIONS.authority(), OPTIONS.contentType());
        // The config is only read in the receiving loop:
        lenient().doReturn(config).when(grpcClient).getConfig();

        return new PbjGrpcCall(grpcClient, grpcClientStream, options, METHOD_NAME, requestCodec, replyCodec, pipeline);
    }

    @ParameterizedTest
    @ValueSource(strings = {"test authority"})
    public void testConstructor(final String authority) {
        final PbjGrpcCall call = createCall(
                new Options(Optional.ofNullable(authority), ServiceInterface.RequestOptions.APPLICATION_GRPC));
        assertNotNull(call);

        final ArgumentCaptor<Http2Headers> http2HeadersCaptor = ArgumentCaptor.forClass(Http2Headers.class);
        verify(grpcClientStream, times(1)).writeHeaders(http2HeadersCaptor.capture(), eq(false));
        final Http2Headers http2Headers = http2HeadersCaptor.getValue();
        assertEquals(authority, http2Headers.authority());
        assertEquals(Method.POST, http2Headers.method());
        assertEquals("/" + METHOD_NAME, http2Headers.path());
        assertEquals("http", http2Headers.scheme());
        assertEquals(
                OPTIONS.contentType(),
                http2Headers.httpHeaders().get(HeaderNames.CONTENT_TYPE).allValues().stream()
                        .collect(Collectors.joining("|||")));
        assertEquals(
                "identity",
                http2Headers.httpHeaders().get(HeaderNames.createFromLowercase("grpc-encoding")).allValues().stream()
                        .collect(Collectors.joining("|||")));

        // It submits a private method reference, so this is the best we can do:
        verify(executor, times(1)).submit(any(Runnable.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testSendRequest(final boolean endOfStream) {
        final PbjGrpcCall call = createCall(OPTIONS);

        final Object request = mock(Object.class);

        final Bytes bytes = Bytes.wrap("test bytes string");
        doReturn(bytes).when(requestCodec).toBytes(request);

        call.sendRequest(request, endOfStream);

        final ArgumentCaptor<BufferData> bufferDataCaptor = ArgumentCaptor.forClass(BufferData.class);
        verify(grpcClientStream, times(1)).writeData(bufferDataCaptor.capture(), eq(endOfStream));
        final BufferData bufferData = bufferDataCaptor.getValue();
        assertEquals(5 + bytes.length(), bufferData.available());
        final byte[] output = bufferData.readBytes();
        assertEquals(0, output[0]);
        assertEquals(0, output[1]);
        assertEquals(0, output[2]);
        assertEquals(0, output[3]);
        assertEquals(bytes.length(), output[4]);
        assertEquals(bytes.asUtf8String(), new String(Arrays.copyOfRange(output, 5, output.length)));
    }

    @Test
    public void testCompleteRequests() {
        final PbjGrpcCall call = createCall(OPTIONS);
        call.completeRequests();

        final ArgumentCaptor<BufferData> bufferDataCaptor = ArgumentCaptor.forClass(BufferData.class);
        verify(grpcClientStream, times(1)).writeData(bufferDataCaptor.capture(), eq(true));
        assertEquals(0, bufferDataCaptor.getValue().available());
    }

    private Runnable fetchReceiveRepliesLoop() {
        final PbjGrpcCall call = createCall(OPTIONS);

        lenient().doReturn(http2Headers).when(grpcClientStream).readHeaders();
        lenient().doReturn(headers).when(http2Headers).httpHeaders();

        final ArgumentCaptor<Runnable> receiveRepliesLoopCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(1)).submit(receiveRepliesLoopCaptor.capture());

        assertNotNull(receiveRepliesLoopCaptor.getValue());
        return receiveRepliesLoopCaptor.getValue();
    }

    @ParameterizedTest
    @EnumSource(
            value = Http2StreamState.class,
            names = {"CLOSED", "HALF_CLOSED_REMOTE"})
    public void testReceiveRepliesLoopStreamClosed(final Http2StreamState closedState) {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(CompletableFuture.completedFuture(headers))
                .when(grpcClientStream)
                .trailers();
        doReturn(closedState).when(grpcClientStream).streamState();

        runnable.run();

        verify(pipeline, times(1)).onComplete();
        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopPingAndStreamClosed() {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(CompletableFuture.completedFuture(headers))
                .when(grpcClientStream)
                .trailers();
        final AtomicBoolean readHeadersCalled = new AtomicBoolean(false);
        doAnswer(invocation -> {
                    if (readHeadersCalled.compareAndSet(false, true)) {
                        throw mock(StreamTimeoutException.class);
                    }
                    return http2Headers;
                })
                .when(grpcClientStream)
                .readHeaders();

        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        runnable.run();

        verify(grpcClientStream, times(1)).sendPing();
        verify(pipeline, times(1)).onComplete();
        verifyNoMoreInteractions(pipeline);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    // isTimeout = true, test readOne() throwing a timeout exception
    // isTimeout = false, test readOne() returning null
    public void testReceiveRepliesLoopSingleReply(final boolean isTimeout) throws Exception {
        final Runnable runnable = fetchReceiveRepliesLoop();

        final CompletableFuture<Headers> headersCompletableFuture = new CompletableFuture<>();
        doReturn(headersCompletableFuture).when(grpcClientStream).trailers();

        final Http2FrameData data = mock(Http2FrameData.class);
        final AtomicBoolean readOneCalled = new AtomicBoolean(false);
        doAnswer(invocation -> {
                    if (readOneCalled.compareAndSet(false, true)) {
                        if (isTimeout) {
                            throw mock(StreamTimeoutException.class);
                        } else {
                            return null;
                        }
                    }
                    return data;
                })
                .when(grpcClientStream)
                .readOne(READ_TIMEOUT);

        final AtomicInteger hasEntityCalled = new AtomicInteger(0);
        doAnswer(invocation -> hasEntityCalled.getAndIncrement() < 2)
                .when(grpcClientStream)
                .hasEntity();

        // A datagram of 1 byte length with the byte 6 as the data payload:
        final BufferData bufferData = BufferData.create(new byte[] {0, 0, 0, 0, 1, 6});
        doReturn(bufferData).when(data).data();

        final Object reply = mock(Object.class);
        doReturn(reply).when(replyCodec).parse(eq(Bytes.wrap(new byte[] {6})));

        runnable.run();

        verify(pipeline, times(1)).onNext(reply);
        verify(pipeline, times(1)).onComplete();
        if (isTimeout) verify(grpcClientStream, times(1)).sendPing();
        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopException() throws Exception {
        final Runnable runnable = fetchReceiveRepliesLoop();

        final CompletableFuture<Headers> headersCompletableFuture = new CompletableFuture<>();
        doReturn(headersCompletableFuture).when(grpcClientStream).trailers();

        final Http2FrameData data = mock(Http2FrameData.class);
        // Simulate a network failure or similar unknown exception
        doThrow(new RuntimeException("test socket exception"))
                .when(grpcClientStream)
                .readOne(READ_TIMEOUT);

        doReturn(true).when(grpcClientStream).hasEntity();

        runnable.run();

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(pipeline, times(1)).onError(captor.capture());
        final Throwable throwable = captor.getValue();
        assertInstanceOf(RuntimeException.class, throwable);
        assertEquals("test socket exception", throwable.getMessage());
        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopParseException() throws Exception {
        final Runnable runnable = fetchReceiveRepliesLoop();

        final CompletableFuture<Headers> headersCompletableFuture = new CompletableFuture<>();
        doReturn(headersCompletableFuture).when(grpcClientStream).trailers();

        final Http2FrameData data = mock(Http2FrameData.class);
        doReturn(data).when(grpcClientStream).readOne(READ_TIMEOUT);

        final AtomicInteger hasEntityCalled = new AtomicInteger(0);
        doAnswer(invocation -> hasEntityCalled.getAndIncrement() < 1)
                .when(grpcClientStream)
                .hasEntity();

        // A datagram of 1 byte length with the byte 6 as the data payload:
        final BufferData bufferData = BufferData.create(new byte[] {0, 0, 0, 0, 1, 6});
        doReturn(bufferData).when(data).data();

        final ParseException exception = new ParseException("test");
        doThrow(exception).when(replyCodec).parse(eq(Bytes.wrap(new byte[] {6})));

        runnable.run();

        verify(pipeline, times(1)).onError(exception);
        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopProcessHeadersStatusZero() {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(CompletableFuture.completedFuture(headers))
                .when(grpcClientStream)
                .trailers();
        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        doReturn(false).when(headers).contains(any(HeaderName.class));
        doReturn(true).when(headers).contains(eq(GRPC_STATUS));
        final Header statusHeader = mock(Header.class);
        doReturn(List.of("0")).when(statusHeader).allValues();
        doReturn(statusHeader).when(headers).get(eq(GRPC_STATUS));

        runnable.run();

        verify(pipeline, times(1)).onComplete();
        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopProcessHeadersStatusOne() {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        doReturn(false).when(headers).contains(any(HeaderName.class));
        doReturn(true).when(headers).contains(eq(GRPC_STATUS));
        final Header statusHeader = mock(Header.class);
        doReturn(List.of("1")).when(statusHeader).allValues();
        doReturn(statusHeader).when(headers).get(eq(GRPC_STATUS));

        runnable.run();

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(pipeline, times(1)).onError(captor.capture());
        final Throwable throwable = captor.getValue();
        assertInstanceOf(GrpcException.class, throwable);
        final GrpcException grpcException = (GrpcException) throwable;
        assertEquals(GrpcStatus.CANCELLED, grpcException.status());
        assertEquals("", grpcException.getMessage());

        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopProcessHeadersStatusUnknown() {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        doReturn(false).when(headers).contains(any(HeaderName.class));
        doReturn(true).when(headers).contains(eq(GRPC_STATUS));
        final Header statusHeader = mock(Header.class);
        doReturn(List.of("666")).when(statusHeader).allValues();
        doReturn(statusHeader).when(headers).get(eq(GRPC_STATUS));

        runnable.run();

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(pipeline, times(1)).onError(captor.capture());
        final Throwable throwable = captor.getValue();
        assertInstanceOf(GrpcException.class, throwable);
        final GrpcException grpcException = (GrpcException) throwable;
        assertEquals(GrpcStatus.UNKNOWN, grpcException.status());
        assertEquals("", grpcException.getMessage());

        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopProcessHeadersStatusMalformed() {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        doReturn(false).when(headers).contains(any(HeaderName.class));
        doReturn(true).when(headers).contains(eq(GRPC_STATUS));
        final Header statusHeader = mock(Header.class);
        doReturn(List.of("NotANumber")).when(statusHeader).allValues();
        doReturn(statusHeader).when(headers).get(eq(GRPC_STATUS));

        runnable.run();

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(pipeline, times(1)).onError(captor.capture());
        final Throwable throwable = captor.getValue();
        assertInstanceOf(RuntimeException.class, throwable);
        assertEquals("Invalid GRPC_STATUS: NotANumber with message ", throwable.getMessage());

        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopTrailersStatusNonZero() throws Exception {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        doReturn(false).when(headers).contains(any(HeaderName.class));
        doReturn(true).when(headers).contains(eq(GRPC_STATUS));
        final Header statusHeader = mock(Header.class);
        doReturn(List.of("1")).when(statusHeader).allValues();
        doReturn(statusHeader).when(headers).get(eq(GRPC_STATUS));

        doReturn(CompletableFuture.completedFuture(headers))
                .when(grpcClientStream)
                .trailers();

        doReturn(mock(Headers.class)).when(http2Headers).httpHeaders();

        runnable.run();

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(pipeline, times(1)).onError(captor.capture());
        final Throwable throwable = captor.getValue();
        assertInstanceOf(GrpcException.class, throwable);
        final GrpcException grpcException = (GrpcException) throwable;
        assertEquals(GrpcStatus.CANCELLED, grpcException.status());
        assertEquals("", grpcException.getMessage());

        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopTrailersInterrupted() throws Exception {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        final CompletableFuture<Http2FrameData> trailersFuture = mock(CompletableFuture.class);
        doReturn(trailersFuture).when(grpcClientStream).trailers();
        doThrow(InterruptedException.class)
                .when(trailersFuture)
                .get(eq(READ_TIMEOUT.toMillis()), eq(TimeUnit.MILLISECONDS));

        doReturn(mock(Headers.class)).when(http2Headers).httpHeaders();

        runnable.run();

        verify(pipeline, times(1)).onComplete();
        verifyNoMoreInteractions(pipeline);
    }

    @Test
    public void testReceiveRepliesLoopTrailersFailed() throws Exception {
        final Runnable runnable = fetchReceiveRepliesLoop();

        doReturn(Http2StreamState.CLOSED).when(grpcClientStream).streamState();

        final CompletableFuture<Http2FrameData> trailersFuture = mock(CompletableFuture.class);
        doReturn(trailersFuture).when(grpcClientStream).trailers();
        doThrow(new ExecutionException("test execution exception", new RuntimeException("inner")))
                .when(trailersFuture)
                .get(eq(READ_TIMEOUT.toMillis()), eq(TimeUnit.MILLISECONDS));

        doReturn(mock(Headers.class)).when(http2Headers).httpHeaders();

        runnable.run();

        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(pipeline, times(1)).onError(captor.capture());
        final Throwable throwable = captor.getValue();
        assertInstanceOf(ExecutionException.class, throwable);
        assertEquals("test execution exception", throwable.getMessage());
        assertEquals("inner", throwable.getCause().getMessage());

        verifyNoMoreInteractions(pipeline);
    }
}
