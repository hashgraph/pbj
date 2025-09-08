// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc.specialized.dying;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import com.hedera.pbj.integration.test.grpc.GrpcServerGreeterHandle;
import com.hedera.pbj.integration.test.grpc.specialized.SeparateJVMRunner;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.Pipeline;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

@ParameterizedClass
@MethodSource("testClassArguments")
public class DyingServerTest {
    private final Class<? extends GrpcServerGreeterHandle> serverClass;

    public DyingServerTest(Class<? extends GrpcServerGreeterHandle> serverClass) {
        this.serverClass = serverClass;
    }

    static Stream<Arguments> testClassArguments() {
        return Stream.of(
                Arguments.of(PbjGrpcServerGreeterClientStreamingHandle.class),
                Arguments.of(GoogleProtobufGrpcServerGreeterClientStreamingHandle.class));
    }

    /**
     * Test when a server dies in the middle of client streaming requests, or in its onComplete.
     * @param dieInOnComplete true if server dies in onComplete, false if it dies in the middle
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testClientStreamingMethodServerDiesInTheMiddle(final boolean dieInOnComplete) {
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle server =
                        new SeparateJVMRunner(serverClass, port.port(), List.of(Boolean.toString(dieInOnComplete)))) {
            server.start();

            final GrpcClient grpcClient = GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client =
                    new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

            final List<HelloReply> replies = new ArrayList<>();
            final List<Throwable> errors = new ArrayList<>();
            final AtomicBoolean completed = new AtomicBoolean(false);
            final Pipeline<? super HelloRequest> requests = client.sayHelloStreamRequest(new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // no-op
                }

                @Override
                public void onError(Throwable throwable) {
                    errors.add(throwable);
                }

                @Override
                public void onComplete() {
                    completed.set(true);
                }

                @Override
                public void onNext(HelloReply item) throws RuntimeException {
                    replies.add(item);
                }
            });

            requests.onNext(HelloRequest.newBuilder().name("test name 1").build());
            requests.onNext(HelloRequest.newBuilder().name("test name 2").build());
            if (!dieInOnComplete) { // == die in the middle then
                // Here we kill the server process:
                server.stopNow();
                assertThrows(
                        UncheckedIOException.class,
                        () -> requests.onNext(
                                HelloRequest.newBuilder().name("test name 3").build()));
            } else {
                requests.onNext(HelloRequest.newBuilder().name("test name 3").build());
                requests.onComplete();
            }

            GrpcTestUtils.sleep(grpcClient);

            assertEquals(List.of(), replies);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0) instanceof UncheckedIOException);

            assertFalse(completed.get());
        }
    }
}
