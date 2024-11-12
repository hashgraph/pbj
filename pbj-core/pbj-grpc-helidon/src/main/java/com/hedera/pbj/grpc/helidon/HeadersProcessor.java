package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

interface HeadersProcessor {
    void setPipeline(@NonNull final Pipeline<? super Bytes> pipeline);
    void cancelDeadlineFuture(boolean isCancelled);
    ServiceInterface.RequestOptions options();
}
