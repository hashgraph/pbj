// SPDX-License-Identifier: Apache-2.0
/** PBJ gRPC client implementation. */
module com.hedera.pbj.grpc.client.helidon {
    requires transitive com.hedera.pbj.runtime;
    requires transitive io.helidon.common.tls;
    requires transitive io.helidon.webclient.api;
    requires io.helidon.builder.api;
    requires io.helidon.common.buffers;
    requires io.helidon.common.socket;
    requires io.helidon.http.http2;
    requires io.helidon.http;
    requires io.helidon.webclient.http2;

    exports com.hedera.pbj.grpc.client.helidon;
}
