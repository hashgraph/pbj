package com.hedera.pbj.grpc.helidon;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;

final class GrpcHeaders {
    static final HeaderName GRPC_TIMEOUT = HeaderNames.create("grpc-timeout");
    static final HeaderName GRPC_ENCODING = HeaderNames.create("grpc-encoding");
    static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");
}
