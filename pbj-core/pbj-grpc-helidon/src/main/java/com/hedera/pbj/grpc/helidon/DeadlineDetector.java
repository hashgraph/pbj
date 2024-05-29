package com.hedera.pbj.grpc.helidon;

import java.util.concurrent.ScheduledFuture;

interface DeadlineDetector {
    ScheduledFuture<?> scheduleDeadline(long deadline, Runnable onDeadlineExceeded);
}
