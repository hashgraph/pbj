package com.hedera.pbj.grpc.helidon;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import java.util.function.Consumer;

class FatalGrpcException extends Exception {
    final transient Consumer<WritableHeaders<?>> headerCallback;

    FatalGrpcException(final @NonNull Consumer<WritableHeaders<?>> headerCallback) {
        this.headerCallback = headerCallback;
    }

    FatalGrpcException(final @NonNull Status status) {
        this(w -> w.set(Http2Headers.STATUS_NAME, status.code()));
    }

    final Consumer<WritableHeaders<?>> headerCallback() {
        return headerCallback;
    }
}
