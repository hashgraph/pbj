// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.grpc.GrpcCall;
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
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webclient.http2.Http2ClientStream;
import io.helidon.webclient.http2.StreamTimeoutException;
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

    private final PbjGrpcClient grpcClient;
    private final Codec<RequestT> requestCodec;
    private final Codec<ReplyT> replyCodec;
    private final Pipeline<ReplyT> pipeline;

    private final Http2ClientStream clientStream;

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
        headers.add(HeaderValues.create(HeaderNames.ACCEPT_ENCODING, "identity"));
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
        final Bytes bytes = requestCodec.toBytes(request);
        final BufferData bufferData =
                BufferData.create(PbjGrpcDatagramReader.PREFIX_LENGTH + Math.toIntExact(bytes.length()));

        // GRPC datagram header
        bufferData.write(0); // 0 means no compression
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

    private void receiveRepliesLoop() {
        try {
            boolean headersRead = false;
            do {
                try {
                    clientStream.readHeaders();
                    // FUTURE WORK: examine the headers to check the content type, encoding, custom headers, etc.
                    headersRead = true;
                } catch (StreamTimeoutException ignored) {
                    // FUTURE WORK: implement an uber timeout to return

                    // Ping the server on every timeout. This seems to create a bit of extra traffic.
                    // However, this seems to be the only way to detect a broken connection. Otherwise, we'd never know
                    // if the server died.
                    // FUTURE WORK: consider a separate KeepAlive timeout for these pings, so that we don't flood the
                    // network.
                    // NOTE: Google GRPC server drops the connection if pinged right after receiving the headers - it
                    // complains about the frame size of 64K with 16K max allowed. It's unclear how/why this even
                    // happens. However, pinging on timeout seems to work smoothly so far.
                    clientStream.sendPing();
                }
            } while (!headersRead && isStreamOpen());

            // read data from stream
            final PbjGrpcDatagramReader datagramReader = new PbjGrpcDatagramReader();
            while (isStreamOpen() && !clientStream.trailers().isDone() && clientStream.hasEntity()) {
                final Http2FrameData frameData;
                try {
                    frameData = clientStream.readOne(grpcClient.getConfig().readTimeout());
                } catch (StreamTimeoutException e) {
                    // Check if the connection is alive. See a comment above about the KeepAlive timeout.
                    clientStream.sendPing();
                    // FUTURE WORK: implement an uber timeout to return
                    continue;
                }
                if (frameData != null) {
                    BufferData bufferData = frameData.data();

                    // Add data to the reader...
                    datagramReader.add(bufferData);

                    // ...and then feed all complete GRPC datagrams to the pipeline:
                    BufferData data;
                    while ((data = datagramReader.extractNextDatagram()) != null) {
                        final byte[] array = data.readBytes();
                        final Bytes bytes = Bytes.wrap(array);

                        try {
                            final ReplyT reply = replyCodec.parse(bytes);
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
