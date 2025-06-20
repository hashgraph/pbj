// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.grpc.GrpcCall;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientImpl;
import java.util.Collections;
import java.util.Optional;

/**
 * A PBJ GRPC client that uses the Helidon WebClient and its HTTP2 client implementation to call remote GRPC services.
 */
public final class PbjGrpcClient implements GrpcClient {
    private final WebClient webClient;
    private final PbjGrpcClientConfig config;

    private final Http2Client http2Client;

    /**
     * Create a new PBJ GRPC client.
     * @param webClient Helidon WebClient instance that MUST specify the baseURI for the service
     * @param config a configuration for this client
     */
    public PbjGrpcClient(final WebClient webClient, final PbjGrpcClientConfig config) {
        this.webClient = webClient;
        this.config = config;
        this.http2Client = webClient.client(Http2Client.PROTOCOL);
    }

    /**
     * Create a new GRPC call.
     *
     * @param <RequestT> request type
     * @param <ReplyT> reply type
     * @param fullMethodName a full GRPC method name that includes the fully-qualified service name and the method name
     * @param requestCodec a PBJ codec for requests that MUST correspond to the content type in the PbjGrpcClientConfig
     * @param replyCodec a PBJ codec for replies that MUST correspond to the content type in the PbjGrpcClientConfig
     * @param pipeline a pipeline for receiving replies
     */
    @Override
    public <RequestT, ReplyT> GrpcCall<RequestT, ReplyT> createCall(
            final String fullMethodName,
            final Codec<RequestT> requestCodec,
            final Codec<ReplyT> replyCodec,
            final Pipeline<ReplyT> pipeline) {
        // FUTURE WORK: should probably cache the connection and re-use it for subsequent createCall() calls.
        // Also, might have to pull some connection initialization code out of the Call class, so that the latter
        // only ever creates streams over an existing TCP/HTTP2 connection.
        final ClientConnection clientConnection = createClientConnection();
        return new PbjGrpcCall(
                this,
                clientConnection,
                new Options(config.authority(), config.contentType()),
                fullMethodName,
                requestCodec,
                replyCodec,
                pipeline);
    }

    WebClient getWebClient() {
        return webClient;
    }

    Http2Client getHttp2Client() {
        return http2Client;
    }

    /**
     * @return the PbjGrpcClientConfig of this client
     */
    public PbjGrpcClientConfig getConfig() {
        return config;
    }

    /** Simple implementation of the {@link ServiceInterface.RequestOptions} interface. */
    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    private ClientConnection createClientConnection() {
        final ClientUri clientUri = ((Http2ClientImpl) http2Client)
                .prototype()
                .baseUri()
                .orElseThrow(() -> new IllegalStateException("No base URI provided in the WebClient."));
        final ConnectionKey connectionKey = new ConnectionKey(
                clientUri.scheme(),
                clientUri.host(),
                clientUri.port(),
                config.readTimeout(),
                config.tls(),
                DefaultDnsResolver.create(),
                DnsAddressLookup.defaultLookup(),
                Proxy.noProxy());
        return TcpClientConnection.create(
                        webClient, connectionKey, Collections.emptyList(), connection -> false, connection -> {})
                .connect();
    }
}
