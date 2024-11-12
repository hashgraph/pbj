package com.hedera.pbj.grpc.helidon;

import io.helidon.http.Header;
import io.helidon.http.HeaderValues;

public final class Constants {
    private Constants() {}

    /** The only grpc-encoding supported by this implementation. */
    public static final String IDENTITY = "identity";

    /** A pre-created and cached *response* header for "grpc-encoding: identity". */
    public static final Header GRPC_ENCODING_IDENTITY =
            HeaderValues.createCached("grpc-encoding", IDENTITY);
}
