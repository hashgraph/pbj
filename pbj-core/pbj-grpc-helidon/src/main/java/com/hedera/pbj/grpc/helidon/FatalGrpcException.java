package com.hedera.pbj.grpc.helidon;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.http.Header;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import java.util.function.Consumer;

class FatalGrpcException extends Exception {
    final Consumer<WritableHeaders<?>> headerCallback;

    FatalGrpcException(final @NonNull Consumer<WritableHeaders<?>> headerCallback) {
        this.headerCallback = headerCallback;
    }

    FatalGrpcException(final @NonNull Header grpcStatus) {
        this(w -> {
            w.set(Http2Headers.STATUS_NAME, Status.OK_200.code());
            w.set(grpcStatus);
        });
    }

    FatalGrpcException(final @NonNull Status status) {
        this(w -> w.set(Http2Headers.STATUS_NAME, status.code()));
    }

    FatalGrpcException(final @NonNull Status status, final @NonNull Header grpcStatus) {
        this(w -> {
            w.set(Http2Headers.STATUS_NAME, status.code());
            w.set(grpcStatus);
        });
    }

    final Consumer<WritableHeaders<?>> headerCallback() {
        return headerCallback;
    }
}
