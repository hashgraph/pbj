// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.grpc.GrpcCall;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientImpl;
import io.helidon.webclient.http2.Http2ClientStream;
import io.helidon.webclient.http2.Http2StreamConfig;
import io.helidon.webclient.http2.StreamTimeoutException;
import java.time.Duration;

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

    private final PbjGrpcClient grpcClient;
    private final Codec<RequestT> requestCodec;
    private final Codec<ReplyT> replyCodec;
    private final Pipeline<ReplyT> pipeline;

    private final Http2ClientStream clientStream;

    /**
     * Create a new GRPC call, start a replies receiving loop in the underlying Helidon WebClient executor,
     * and send client HTTP2 headers.
     * @param grpcClient GRPC client
     * @param clientConnection a client connection
     * @param requestOptions options such as the authority, content type, etc.
     * @param fullMethodName a full GRPC method name that includes the fully-qualified service name and the method name
     * @param requestCodec a PBJ codec for requests that MUST correspond to the content type in the requestOptions
     * @param replyCodec a PBJ codec for replies that MUST correspond to the content type in the requestOptions
     * @param pipeline a pipeline for receiving replies
     */
    PbjGrpcCall(
            final PbjGrpcClient grpcClient,
            final ClientConnection clientConnection,
            final ServiceInterface.RequestOptions requestOptions,
            final String fullMethodName,
            final Codec<RequestT> requestCodec,
            final Codec<ReplyT> replyCodec,
            final Pipeline<ReplyT> pipeline) {
        this.grpcClient = grpcClient;
        this.requestCodec = requestCodec;
        this.replyCodec = replyCodec;
        this.pipeline = pipeline;

        final HelidonSocket socket = clientConnection.helidonSocket();
        final Http2ClientConnection connection =
                Http2ClientConnection.create((Http2ClientImpl) grpcClient.getHttp2Client(), clientConnection, true);

        this.clientStream = new PbjGrpcClientStream(
                connection,
                Http2Settings.create(),
                socket,
                new Http2StreamConfig() {
                    @Override
                    public boolean priorKnowledge() {
                        return true;
                    }

                    @Override
                    public int priority() {
                        return 0;
                    }

                    @Override
                    public Duration readTimeout() {
                        return grpcClient.getConfig().readTimeout();
                    }
                },
                null,
                connection.streamIdSequence());

        // send HEADERS frame
        final WritableHeaders<?> headers = WritableHeaders.create();
        requestOptions.authority().ifPresent(authority -> headers.add(Http2Headers.AUTHORITY_NAME, authority));
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
    public void halfClose() {
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
                }
            } while (!headersRead);

            // read data from stream
            final PbjGrpcDatagramReader datagramReader = new PbjGrpcDatagramReader();
            while (isStreamOpen()) {
                if (clientStream.trailers().isDone() || !clientStream.hasEntity()) {
                    // Trailers or EndOfStream received
                    break;
                }

                final Http2FrameData frameData;
                try {
                    frameData = clientStream.readOne(grpcClient.getConfig().readTimeout());
                } catch (StreamTimeoutException e) {
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
                        }
                    }
                    // If there's no any complete datagrams yet, then simply keep spinning this loop until
                    // enough data is received.
                }
            }
        } catch (Throwable t) {
            // This method runs in the Helidon WebClient executor, so there's no need to re-throw the exception
            // as this won't produce any useful effects. We only report it to the replies pipeline here for
            // the application code to handle it:
            pipeline.onError(t);
        }
        clientStream.close();
        pipeline.onComplete();
    }
}
