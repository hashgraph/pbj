// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.metrics.api.Counter;
import java.util.List;
import org.junit.jupiter.api.Test;

class PbjMethodRouteTest {
    @Test
    void nativeAndWebTransportsHaveSeparateCountersForTheSameServiceMethod() {
        final ServiceInterface service = new GreeterServiceImpl();
        final PbjMethodRoute nativeRoute =
                new PbjMethodRoute(service, PbjGrpcServiceConfig.DEFAULT, GreeterService.GreeterMethod.sayHello);
        final PbjMethodRoute webRoute = new PbjMethodRoute(
                service, PbjGrpcServiceConfig.DEFAULT, GreeterService.GreeterMethod.sayHello, "grpc-web");
        final List<Counter> nativeCounters = counters(nativeRoute);
        final List<Counter> webCounters = counters(webRoute);
        for (int i = 0; i < nativeCounters.size(); i++) {
            final Counter nativeCounter = nativeCounters.get(i);
            final Counter webCounter = webCounters.get(i);
            assertEquals(nativeCounter.id().name(), webCounter.id().name());
            assertEquals("grpc", nativeCounter.id().tagsMap().get("transport"));
            assertEquals("grpc-web", webCounter.id().tagsMap().get("transport"));
            assertNotSame(nativeCounter, webCounter);
        }
    }

    private static List<Counter> counters(final PbjMethodRoute route) {
        return List.of(
                route.requestCounter(),
                route.failedGrpcRequestCounter(),
                route.failedHttpRequestCounter(),
                route.failedUnknownRequestCounter(),
                route.failedResponseCounter(),
                route.deadlineExceededCounter());
    }
}
