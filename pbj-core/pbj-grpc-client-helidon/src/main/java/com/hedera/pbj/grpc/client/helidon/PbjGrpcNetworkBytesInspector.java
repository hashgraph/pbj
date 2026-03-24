// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * An interface for inspecting raw bytes of data payloads sent/received over GRPC.
 * <p>
 * The bytes are encoded/compressed if the GRPC encoding isn't `identity`. The bytes only represent the data payload
 * and don't include any system, non-user bytes, such as GRPC datagram size/compression flag or any headers sent
 * over the GRPC/HTTP2 connection.
 * <p>
 * The primary purpose of this interface is to help estimate the amount of user data being transferred over
 * a network connection during GRPC interactions. Currently, this interface doesn't allow one to associate
 * a particular Bytes object with a specific GRPC request. In the future, this interface may be extended or
 * even modified to allow for such association. Therefore, before using this interface, application developers
 * should very carefully consider if they're ready to keep up with the interface changes and update their
 * applications accordingly.
 * <p>
 * It is STRONGLY RECOMMENDED to consider this interface being a PBJ internal implementation detail that is
 * not generally suited for use in regular applications.
 */
public interface PbjGrpcNetworkBytesInspector {
    /**
     * Inspect bytes to be sent to a remote peer, after compressing them and prior to actually sending them.
     * @param bytes the bytes sent, potentially compressed.
     */
    default void sent(Bytes bytes) {}

    /**
     * Inspect bytes just received from a remote peer, prior to decompressing them and passing them to the application.
     * @param bytes the bytes sent, potentially compressed.
     */
    default void received(Bytes bytes) {}
}
