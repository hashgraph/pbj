package com.hedera.pbj.grpc.helidon;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2StreamState;

import java.util.function.UnaryOperator;

public interface GrpcDataProcessor {
    void data(@NonNull final Http2FrameHeader header, @NonNull final BufferData data);
    void setCurrentStreamState(UnaryOperator<Http2StreamState> operator);
    Http2StreamState getCurrentStreamState();
}
