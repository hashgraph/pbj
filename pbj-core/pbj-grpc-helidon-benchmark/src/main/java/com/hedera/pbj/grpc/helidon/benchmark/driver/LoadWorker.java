package com.hedera.pbj.grpc.helidon.benchmark.driver;

import com.hedera.pbj.grpc.helidon.benchmark.driver.proto.WorkerService;

/**
 * A load worker process which a driver can use to create clients and servers. The worker
 * implements the contract defined in 'control.proto'.
 */
public class LoadWorker {

    private static final class WorkerServiceImpl implements WorkerService {

    }
}
