// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.Optional;

/**
 * Simple implementation of the {@link ServiceInterface.RequestOptions} interface, used by server transports to pass
 * the details of a request to {@link ServiceInterface#open}.
 *
 * @param authority the authority of the request, if any
 * @param contentType the normalized content type of the request
 * @param maxMessageSizeBytes the maximum size of a single message
 * @param metadata the custom metadata sent by the client
 * @param remoteAddress the address of the remote peer, or null if unknown
 * @param remoteCertificateChain the TLS certificate chain of the remote peer, if any
 */
record GrpcRequestOptions(
        Optional<String> authority,
        String contentType,
        int maxMessageSizeBytes,
        Map<String, String> metadata,
        SocketAddress remoteAddress,
        Optional<Certificate[]> remoteCertificateChain)
        implements ServiceInterface.RequestOptions {}
