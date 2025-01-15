// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.runtime.grpc.GrpcStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ScheduledFuture;

/**
 * A gRPC client may specify a 'deadline' for a request, which is measured in elapsed time from the
 * start of the request. If the deadline is exceeded, then the request will fail with {@link
 * GrpcStatus#DEADLINE_EXCEEDED}. An implementation of this interface is responsible for detecting
 * when the deadline has been exceeded, and invoking a callback to handle the failure.
 */
interface DeadlineDetector {
    /**
     * Schedule a callback to be invoked when the deadline has been exceeded. Please note that no
     * operating system can actually measure elapsed time with nanosecond precision, so the actual
     * deadline may be exceeded by a small amount of time measuring in the microseconds or even
     * milliseconds.
     *
     * @param deadlineNanos The deadline, in nanoseconds, from now.
     * @param onDeadlineExceeded The callback to invoke when the deadline has been exceeded.
     * @return A {@link ScheduledFuture} that can be used to cancel the deadline.
     */
    @NonNull
    ScheduledFuture<?> scheduleDeadline(long deadlineNanos, @NonNull Runnable onDeadlineExceeded);
}
