// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.grpc.GrpcCall;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webclient.http2.Http2ClientStream;
import io.helidon.webclient.http2.StreamTimeoutException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * A blocking, non-buffering wrapper around an HTTP2 stream that serves a single GRPC call.
 * A client can send requests via the sendRequest() method, and receive replies via a pipeline
 * that this call has been created with via the PbjGrpcClient.createCall() method.
 * <p>
 * The blocking nature of the implementation allows us to delegate the flow control to the underlying
 * HTTP2 layer entirely. This, of course, assumes that the Pipeline.onNext() method is blocking as well.
 * <p>
 * If the onNext() method is not blocking and instead simply buffers incoming replies, then the pipeline
 * implementation is responsible for the flow control implementation by making the onNext() call block
 * sometimes, and/or delaying sending more requests via the sendRequest() method until buffered replies
 * have been processed. Without a proper flow control, a non-blocking pipeline risks running into OOMs
 * or otherwise causing resources starvation.
 *
 * @param <RequestT> request type
 * @param <ReplyT> reply type
 */
public class PbjGrpcCall<RequestT, ReplyT> implements GrpcCall<RequestT, ReplyT> {

    private static final BufferData EMPTY_BUFFER_DATA = BufferData.empty();
    private static final HeaderName GRPC_STATUS = HeaderNames.createFromLowercase("grpc-status");
    private static final HeaderName GRPC_MESSAGE = HeaderNames.createFromLowercase("grpc-message");
    private static final HeaderName GRPC_ENCODING = HeaderNames.createFromLowercase("grpc-encoding");
    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.createFromLowercase("grpc-accept-encoding");

    private static final PbjGrpcNetworkBytesInspector NO_OP_NETWORK_BYTES_INSPECTOR =
            new PbjGrpcNetworkBytesInspector() {};

    private static PbjGrpcNetworkBytesInspector networkBytesInspector = NO_OP_NETWORK_BYTES_INSPECTOR;

    /**
     * Install a PbjGrpcNetworkBytesInspector, which may be null to reset it to no-op.
     * This is an internal API that isn't suited for general-purpose applications.
     * See `PbjGrpcNetworkBytesInspector` javadoc for more details.
     * This method is not thread-safe and relies on eventual consistency to take effect. So it's best to invoke it early
     * in the application startup.
     * @param networkBytesInspector a PbjGrpcNetworkBytesInspector instance
     */
    public static void setNetworkBytesInspector(PbjGrpcNetworkBytesInspector networkBytesInspector) {
        PbjGrpcCall.networkBytesInspector =
                networkBytesInspector != null ? networkBytesInspector : NO_OP_NETWORK_BYTES_INSPECTOR;
    }

    private final PbjGrpcClient grpcClient;
    private final Codec<RequestT> requestCodec;
    private final Codec<ReplyT> replyCodec;
    private final Pipeline<ReplyT> pipeline;

    private final Http2ClientStream clientStream;

    // grpc-encoding to use for sending requests, e.g. "identity", or "gzip" (w/o quotes)
    private final String grpcOutgoingEncoding;

    /**
     * Create a new GRPC call, start a replies receiving loop in the underlying Helidon WebClient executor,
     * and send client HTTP2 headers.
     * @param grpcClient GRPC client
     * @param clientStream a client stream
     * @param requestOptions options such as the authority, content type, etc.
     * @param fullMethodName a full GRPC method name that includes the fully-qualified service name and the method name
     * @param requestCodec a PBJ codec for requests that MUST correspond to the content type in the requestOptions
     * @param replyCodec a PBJ codec for replies that MUST correspond to the content type in the requestOptions
     * @param pipeline a pipeline for receiving replies
     */
    PbjGrpcCall(
            final PbjGrpcClient grpcClient,
            final Http2ClientStream clientStream,
            final ServiceInterface.RequestOptions requestOptions,
            final String fullMethodName,
            final Codec<RequestT> requestCodec,
            final Codec<ReplyT> replyCodec,
            final Pipeline<ReplyT> pipeline) {
        this.grpcClient = grpcClient;
        this.requestCodec = requestCodec;
        this.replyCodec = replyCodec;
        this.pipeline = pipeline;

        this.clientStream = clientStream;

        if (GrpcCompression.getCompressor(grpcClient.getConfig().encoding()) != null) {
            this.grpcOutgoingEncoding = grpcClient.getConfig().encoding();
        } else {
            this.grpcOutgoingEncoding = GrpcCompression.IDENTITY;
        }

        // send HEADERS frame
        final WritableHeaders<?> headers = WritableHeaders.create();
        final String authority = requestOptions
                .authority()
                .orElseThrow(() -> new IllegalStateException("gRPC request requires an :authority value."));
        headers.add(Http2Headers.AUTHORITY_NAME, authority);
        headers.add(Http2Headers.METHOD_NAME, "POST");
        headers.add(Http2Headers.PATH_NAME, "/" + fullMethodName);
        headers.add(Http2Headers.SCHEME_NAME, "http");
        headers.add(HeaderValues.create(HeaderNames.CONTENT_TYPE, requestOptions.contentType()));
        headers.add(HeaderValues.create(
                GRPC_ACCEPT_ENCODING, String.join(",", grpcClient.getConfig().acceptEncodings())));
        headers.add(HeaderValues.create(GRPC_ENCODING, grpcOutgoingEncoding));

        if (requestOptions.metadata() != null && !requestOptions.metadata().isEmpty()) {
            for (String key : requestOptions.metadata().keySet()) {
                if (key.startsWith("grpc-")) {
                    throw new IllegalArgumentException(
                            "Custom metadata key names must not start with grpc- prefix, got: " + key);
                }

                String value = requestOptions.metadata().get(key);
                if (value != null) {
                    headers.add(HeaderNames.create(key), value);
                }
            }
        }

        clientStream.writeHeaders(Http2Headers.create(headers), false);

        // We must start this loop only AFTER writing headers above because that operation initializes
        // an internal buffer in the clientStream. W/o that, we get NPEs when calling clientStream APIs.
        grpcClient.getWebClient().executor().submit(this::receiveRepliesLoop);
    }

    /**
     * Send a request to the service.
     * @param request a request object
     * @param endOfStream a flag indicating if this is the last request, useful for unary or server-streaming methods
     */
    @Override
    public void sendRequest(final RequestT request, final boolean endOfStream) {
        final Bytes requestBytes = requestCodec.toBytes(request);
        final Bytes bytes = GrpcCompression.getCompressor(grpcOutgoingEncoding).compress(requestBytes);
        PbjGrpcCall.networkBytesInspector.sent(bytes);
        final BufferData bufferData =
                BufferData.create(PbjGrpcDatagramReader.PREFIX_LENGTH + Math.toIntExact(bytes.length()));

        // GRPC datagram header
        bufferData.write(GrpcCompression.IDENTITY.equals(grpcOutgoingEncoding) ? 0 : 1);
        bufferData.writeUnsignedInt32(Math.toIntExact(bytes.length()));

        // GRPC datagram data payload
        bufferData.write(bytes.toByteArray());

        clientStream.writeData(bufferData, endOfStream);
    }

    @Override
    public void completeRequests() {
        clientStream.writeData(EMPTY_BUFFER_DATA, true);
    }

    private boolean isStreamOpen() {
        return clientStream.streamState() != Http2StreamState.HALF_CLOSED_REMOTE
                && clientStream.streamState() != Http2StreamState.CLOSED;
    }

    /**
     * Send a ping to the server.
     * <p>
     * Do NOT use Http2ClientStream.sendPing()! It works once. A second ping results in sending garbage frames
     * to the server (indirectly), and the server closes the connection. The exact cause is still unknown, but it may
     * be related to the usage of this connection's flowControl object for sending the pings which may
     * interfere with the regular data transfers occurring via this same connection concurrently with the ping.
     * Another reason for this may be the fact that it uses a static HTTP2_PING object for sending pings, but
     * it never rewind()'s the buffer that holds the ping payload, so the server may read bytes from a subsequent
     * regular data frame and interpret them as the ping payload, which should break the HTTP2 connection as a whole.
     * <p>
     * There's Http2ClientConnection.ping() method that explicitly uses the FlowControl.Outbound.NOOP for sending
     * new ping objects. However, that method is package-private.
     * <p>
     * So we implement our own sendPing() here that uses new Http2Ping objects and doesn't use the flowControl.
     * <p>
     * NOTE: Http2ClientStream methods use an Http2ConnectionWriter object via Http2ClientConnection.writer()
     * to write data, and it's a wrapper around the ClientConnection's DataWriter object.
     * And the Http2ConnectionWriter has some additional synchronization around DataWriter.write() calls.
     * However, ironically, it doesn't synchronize access to the flowControl object. Regardless, there's no public
     * methods to obtain a reference to the Http2ConnectionWriter or its internal lock. So we have to write
     * to the ClientConnection's DataWriter object directly. Stress-testing hasn't revealed any thread-races so far.
     * <p>
     * It's difficult to imagine a situation where the thread-race could occur. Perhaps a single PbjGrpcClient
     * (aka a single HTTP2 connection) and two streaming PbjGrpcCalls (aka HTTP2 streams) open concurrently,
     * one being very chatty and another one being very silent. The latter may start sending pings while the former
     * is sending requests to the server. However, this scenario seems very rare. If we ever encounter this issue,
     * then it's easy to work-around by creating separate PbjGrpcClients for the two calls on the client side.
     * To fix it, ideally we'd work with Helidon to expose the necessary APIs for synchronous writes. Alternatively,
     * we could introduce a PbjGrpcClient-level outgoing queue and send all requests and pings through it as
     * a work-around. However, this work-around may not fully cover the issue because Helidon can write window update
     * frames for the flowControl changes concurrently still as it reads data from the stream/socket.
     */
    private void sendPing() {
        final Http2Ping ping = Http2Ping.create();
        final Http2FrameData frameData = ping.toFrameData();
        final Http2FrameHeader frameHeader = frameData.header();
        if (frameHeader.length() == 0) {
            throw new IllegalStateException("Ping with zero length. This should never happen.");
        } else {
            final BufferData headerData = frameHeader.write();
            final BufferData data = frameData.data().copy();
            try {
                grpcClient.getClientConnection().writer().writeNow(BufferData.create(headerData, data));
            } catch (IllegalStateException e) {
                // It may throw IllegalStateException: Attempt to call writer() on a closed connection
                // But callers usually expect an UncheckedIOException:
                throw new UncheckedIOException(new IOException("sendPing failed", e));
            }
        }
    }

    private void receiveRepliesLoop() {
        try {
            Http2Headers http2Headers = null;
            do {
                try {
                    http2Headers = clientStream.readHeaders();
                    // FUTURE WORK: examine the headers to check the content type, encoding, custom headers, etc.
                } catch (StreamTimeoutException ignored) {
                    // FUTURE WORK: implement an uber timeout to return

                    // Ping the server on every timeout. This seems to create a bit of extra traffic.
                    // However, this seems to be the only way to detect a broken connection. Otherwise, we'd never know
                    // if the server died.
                    // FUTURE WORK: consider a separate KeepAlive timeout for these pings, so that we don't flood the
                    // network.
                    sendPing();
                }
            } while (http2Headers == null && isStreamOpen());

            final GrpcCompression.Decompressor decompressor = GrpcCompression.determineDecompressor(
                    http2Headers != null && http2Headers.httpHeaders() != null
                            ? fetchHeader(http2Headers.httpHeaders(), GRPC_ENCODING)
                            : null);

            // read data from stream
            final PbjGrpcDatagramReader datagramReader =
                    new PbjGrpcDatagramReader(grpcClient.getConfig().maxIncomingBufferSize());
            while (isStreamOpen() && !clientStream.trailers().isDone() && clientStream.hasEntity()) {
                final Http2FrameData frameData;
                try {
                    frameData = clientStream.readOne(grpcClient.getConfig().readTimeout());
                } catch (StreamTimeoutException e) {
                    // Check if the connection is alive. See a comment above about the KeepAlive timeout.
                    sendPing();
                    // FUTURE WORK: implement an uber timeout to return
                    continue;
                }
                if (frameData != null) {
                    BufferData bufferData = frameData.data();

                    // Add data to the reader...
                    datagramReader.add(bufferData);

                    // ...and then feed all complete GRPC datagrams to the pipeline:
                    PbjGrpcDatagramReader.Datagram datagram;
                    while ((datagram = datagramReader.extractNextDatagram()) != null) {
                        if (datagram.compressedFlag() != 0 && datagram.compressedFlag() != 1) {
                            throw new IllegalStateException("GRPC datagram compressed flag " + datagram.compressedFlag()
                                    + " is unsupported. Only 0 and 1 are valid.");
                        }
                        BufferData data = datagram.data();
                        final byte[] array = data.readBytes();
                        final Bytes bytes = Bytes.wrap(array);
                        PbjGrpcCall.networkBytesInspector.received(bytes);
                        // If the compressedFlag is 0, then per the specification, the message isn't compressed
                        // regardless of the grpc-encoding value:
                        // https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
                        final Bytes replyBytes =
                                datagram.compressedFlag() == 1 ? decompressor.decompress(bytes) : bytes;

                        try {
                            final ReplyT reply = replyCodec.parse(
                                    replyBytes.toReadableSequentialData(),
                                    false,
                                    false,
                                    Codec.DEFAULT_MAX_DEPTH,
                                    grpcClient.getConfig().maxSize());
                            pipeline.onNext(reply);
                        } catch (ParseException e) {
                            pipeline.onError(e);
                            // We won't be able to proceed probably because parsing failed.
                            // Also, we've just reported an error to the pipeline, which
                            // means the GRPC call is done. So we finish the call.
                            // We don't even bother processing headers/trailers at this point.
                            // The goal is to avoid calling pipeline.onComplete() below.
                            return;
                        }
                    }
                    // If there's no any complete datagrams yet, then simply keep spinning this loop until
                    // enough data is received.
                }
            }

            // Google GRPC server can report an erroneous grpc-status in the headers.
            if (processHeaders(clientStream.readHeaders().httpHeaders())) {
                return;
            }
            // PBJ GRPC server reports erroneous grpc-status in the trailer headers, because GRPC specification...
            // Google GRPC server can also use trailers to report the status in certain circumstances, such as when it
            // dies.
            // However, when it dies, the connection is often closed before we manage to receive anything, so we end up
            // with no errors and no replies whatsoever. In fact, we may never even receive the headers in a loop above,
            // we'll exit the loop because the stream gets closed though.
            try {
                final Headers trailers = clientStream
                        .trailers()
                        .get(grpcClient.getConfig().readTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (processHeaders(trailers)) {
                    return;
                }
            } catch (InterruptedException ignored) {
                // This is okayish. Reporting this as a replies error doesn't make sense. Re-throwing has no useful
                // effect.
            } catch (ExecutionException e) {
                pipeline.onError(e);
                return;
            } catch (TimeoutException ignored) {
                // This is okay, the server doesn't support trailers or died, or the trailers got lost.
            }
            // Luckily, there's no any other places through which a grpc-status can be reported,
            // so the above two calls should cover all the possible cases.

            pipeline.onComplete();
        } catch (Throwable t) {
            // This method runs in the Helidon WebClient executor, so there's no need to re-throw the exception
            // as this won't produce any useful effects. We only report it to the replies pipeline here for
            // the application code to handle it:
            pipeline.onError(t);
        } finally {
            clientStream.close();
        }
    }

    /** Returns all values of a header, or empty list if header is missing. */
    private static List<String> fetchHeader(final Headers headers, final HeaderName header) {
        return headers.contains(header) ? headers.get(header).allValues() : List.of();
    }

    /**
     * Process HTTP headers. This method checks the grpc-status header and reports errors to the pipeline
     * if the status isn't OK. In the future, this method could process other headers as well.
     * @param headers HTTP headers
     * @return true if pipeline.onError() was called
     */
    private boolean processHeaders(final Headers headers) {
        boolean onErrorCalled = false;
        final String message = fetchHeader(headers, GRPC_MESSAGE).stream().collect(Collectors.joining(". "));

        for (String value : fetchHeader(headers, GRPC_STATUS)) {
            try {
                final int grpcStatus = Integer.parseInt(value);
                if (grpcStatus != 0) {
                    // Not OK
                    pipeline.onError(new GrpcException(
                            grpcStatus < GrpcStatus.values().length
                                    ? GrpcStatus.values()[grpcStatus]
                                    : GrpcStatus.UNKNOWN,
                            message));
                    onErrorCalled = true;
                }
            } catch (NumberFormatException ignored) {
                // a bad server sent an invalid header. This shouldn't happen really.
                pipeline.onError(
                        new RuntimeException(String.format("Invalid GRPC_STATUS: %s with message %s", value, message)));
                onErrorCalled = true;
            }
        }

        return onErrorCalled;
    }
}
