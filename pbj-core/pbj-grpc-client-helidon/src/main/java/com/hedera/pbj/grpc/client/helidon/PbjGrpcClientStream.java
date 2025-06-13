// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import io.helidon.common.socket.SocketContext;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.http2.Http2ClientConfig;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientStream;
import io.helidon.webclient.http2.Http2StreamConfig;
import io.helidon.webclient.http2.LockingStreamIdSequence;

/**
 * A package-private class that extends a Helidon client stream class only for the purpose
 * of accessing its only protected constructor. While the Http2ClientStream is marked as "not for applications use"
 * in Helidon, a GRPC client implementation must use this stream class in order to implement the GRPC protocol.
 */
class PbjGrpcClientStream extends Http2ClientStream {
    PbjGrpcClientStream(
            final Http2ClientConnection connection,
            final Http2Settings serverSettings,
            final SocketContext ctx,
            final Http2StreamConfig http2StreamConfig,
            final Http2ClientConfig http2ClientConfig,
            final LockingStreamIdSequence streamIdSeq) {
        super(connection, serverSettings, ctx, http2StreamConfig, http2ClientConfig, streamIdSeq);
    }
}
