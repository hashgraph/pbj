// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc;

import com.hedera.pbj.grpc.helidon.PbjGrpcServiceConfig;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.webserver.WebServer;
import java.io.InputStream;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;
import org.jspecify.annotations.NonNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/** A stress-test for PBJ GRPC server and client. */
@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class PbjGrpcStressTest {
    private static final int DATA_SIZE_BYTES = 16 * 1024;
    private static final int REPLIES_PER_REQUEST = 20_000;

    private final AtomicInteger requestCounter = new AtomicInteger(0);

    private static volatile boolean testCompleted = false;

    public static class StressGreeterService implements GreeterInterface {
        private static final String REPLY_STRING;

        static {
            final Random random = new Random();
            StringBuilder sb = new StringBuilder(DATA_SIZE_BYTES);
            for (int i = 0; i < DATA_SIZE_BYTES; i++) {
                sb.append((char) (32 + random.nextInt(127 - 32)));
            }
            REPLY_STRING = sb.toString();
        }

        @Override
        public @NonNull HelloReply sayHello(@NonNull HelloRequest request) {
            return null;
        }

        @Override
        public void sayHelloStreamReply(@NonNull HelloRequest request, @NonNull Pipeline<? super HelloReply> replies) {}

        @Override
        public @NonNull Pipeline<? super HelloRequest> sayHelloStreamRequest(
                @NonNull Pipeline<? super HelloReply> replies) {
            return null;
        }

        @Override
        public @NonNull Pipeline<? super HelloRequest> sayHelloStreamBidi(
                @NonNull Pipeline<? super HelloReply> replies) {
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // no-op
                }

                @Override
                public void onError(Throwable throwable) {
                    new RuntimeException(throwable).printStackTrace();
                }

                @Override
                public void onComplete() {}

                @Override
                public void onNext(HelloRequest request) throws RuntimeException {
                    System.err.println("Received request: " + request);
                    for (int i = 0; i < REPLIES_PER_REQUEST; i++) {
                        final String s = "for " + request + ": sending reply " + i;
                        try {
                            replies.onNext(HelloReply.newBuilder()
                                    .message(s + "        " + REPLY_STRING)
                                    .build());
                        } catch (Throwable throwable) {
                            new RuntimeException(throwable).printStackTrace();
                            throw throwable;
                        }
                    }
                    System.err.println("Finished request: " + request);
                }
            };
        }
    }

    private record ServerHandle(WebServer server) implements AutoCloseable {
        @Override
        public void close() {
            server.stop();
        }

        static ServerHandle start(
                final int port, final ServiceInterface service, final PbjGrpcServiceConfig serviceConfig) {
            final int maxPayloadSize = 20 * 1024 * 1024;
            final PbjConfig pbjConfig = PbjConfig.builder()
                    .name("pbj")
                    .maxMessageSizeBytes(maxPayloadSize)
                    .build();
            return new ServerHandle(WebServer.builder()
                    .port(port)
                    .addProtocol(pbjConfig)
                    .addRouting(PbjRouting.builder().service(service, serviceConfig))
                    .maxPayloadSize(maxPayloadSize)
                    .build()
                    .start());
        }
    }

    static GreeterInterface.GreeterClient createClient(final int port) {
        final GrpcClient grpcClient;
        grpcClient = GrpcTestUtils.createGrpcClient(
                port,
                GrpcTestUtils.PROTO_OPTIONS,
                GrpcCompression.IDENTITY,
                Set.of(GrpcCompression.IDENTITY),
                Codec.DEFAULT_MAX_SIZE,
                Codec.DEFAULT_MAX_SIZE * 5);

        return new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);
    }

    @State(Scope.Thread)
    public static class StressState {
        PortsAllocator.Port port;
        ServerHandle server;
        GreeterInterface.GreeterClient client;

        @Setup(Level.Trial)
        public void setup() {
            final PbjGrpcServiceConfig serviceConfig =
                    new PbjGrpcServiceConfig(GrpcCompression.IDENTITY, Set.of(GrpcCompression.IDENTITY));
            port = GrpcTestUtils.PORTS.acquire();
            server = ServerHandle.start(port.port(), new StressGreeterService(), serviceConfig);
            client = createClient(port.port());
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                client.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            client = null;
            try {
                server.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            server = null;
            try {
                port.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            port = null;
        }
    }

    @Benchmark
    public void benchBidiStreaming(final StressState state, final Blackhole blackhole) {
        try {
            final CountDownLatch latch = new CountDownLatch(REPLIES_PER_REQUEST);
            final Pipeline<? super HelloRequest> requests = state.client.sayHelloStreamBidi(new Pipeline<>() {
                @Override
                public void onNext(HelloReply item) throws RuntimeException {
                    blackhole.consume(item);
                    latch.countDown();
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(REPLIES_PER_REQUEST);
                }

                @Override
                public void onError(Throwable throwable) {
                    if (!testCompleted) {
                        // Only error out if the test is still in progress:
                        System.err.println("STRESS TEST FAILED!!!");
                        new RuntimeException(throwable).printStackTrace();
                        System.exit(1);
                    }
                }

                @Override
                public void onComplete() {}
            });
            final int i = requestCounter.incrementAndGet();
            System.err.println("Sending request " + i);
            requests.onNext(HelloRequest.newBuilder().name("request " + i).build());

            // The onComplete() call alone closes the stream and prevents sending pings from already processed requests.
            // However, in this test, we omit the call on purpose to cause the old requests to start sending pings.
            // The pings start to appear at about the time when the request 5 (our of 50) is being processed.
            // This way we generate many pings during the test and ensure they don't break the connection to the server:
            // requests.onComplete();  <-- COMMENTED OUT BY DESIGN

            try {
                latch.await();
                System.err.println("Latch released for request " + i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            // Keep running because network may fail sometimes.
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        // Enable logging for Helidon. See logging.properties in resources/.
        final InputStream logProps = PbjGrpcStressTest.class.getClassLoader().getResourceAsStream("logging.properties");
        LogManager.getLogManager().readConfiguration(logProps);

        final PbjGrpcStressTest test = new PbjGrpcStressTest();
        final StressState state = new StressState();
        final Blackhole blackhole = new Blackhole(
                "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
        state.setup();
        try {
            for (int i = 0; i < 50; i++) {
                test.benchBidiStreaming(state, blackhole);
            }
            // At this point errors are okay because we shut down the server, so the connections will be broken.
            // But the test has already completed, so those errors don't matter anymore.
            testCompleted = true;
            System.err.println("finished loop");
        } finally {
            System.err.println("before teardown");
            state.tearDown();
            System.err.println("after teardown");
        }
    }
}
