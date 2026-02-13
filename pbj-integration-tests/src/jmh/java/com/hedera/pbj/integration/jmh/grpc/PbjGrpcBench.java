// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc;

import com.hedera.pbj.grpc.helidon.PbjGrpcServiceConfig;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.webserver.WebServer;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/**
 * A benchmark for PBJ GRPC Client and Server.
 *
 * Global parameters:
 *  - the warmup and measurement iterations in annotations below
 *  - INVOCATIONS constant at the top of the class below
 * Benchmark state parameters:
 *  - PayloadWeight enum
 *  - encodings (e.g. "gzip", or "gzip,identity")
 *  - streamCount in StreamingState below
 */
@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class PbjGrpcBench {
    private static final int INVOCATIONS = 20_000;

    private record ServerHandle(WebServer server) implements AutoCloseable {
        @Override
        public void close() {
            server.stop();
        }

        static ServerHandle start(
                final int port, final ServiceInterface service, final PbjGrpcServiceConfig serviceConfig) {
            return new ServerHandle(WebServer.builder()
                    .port(port)
                    .addRouting(PbjRouting.builder().service(service, serviceConfig))
                    .maxPayloadSize(10000)
                    .build()
                    .start());
        }
    }

    static GreeterInterface.GreeterClient createClient(final int port, final String[] encodings) {
        final GrpcClient grpcClient;
        if (encodings == null || encodings.length == 0) {
            grpcClient = GrpcTestUtils.createGrpcClient(port, GrpcTestUtils.PROTO_OPTIONS);
        } else {
            grpcClient =
                    GrpcTestUtils.createGrpcClient(port, GrpcTestUtils.PROTO_OPTIONS, encodings[0], Set.of(encodings));
        }

        return new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);
    }

    @State(Scope.Thread)
    public static class UnaryState {
        @Param
        PayloadWeight weight;

        @Param({"identity", "gzip"})
        String encodings;

        PortsAllocator.Port port;
        ServerHandle server;
        GreeterInterface.GreeterClient client;

        void setup(int streamCount) {
            final String[] splitEncodings = encodings.split(",");
            final PbjGrpcServiceConfig serviceConfig =
                    new PbjGrpcServiceConfig(splitEncodings[0], Set.of(splitEncodings));
            port = GrpcTestUtils.PORTS.acquire();
            server = ServerHandle.start(port.port(), new GreeterService(weight, streamCount), serviceConfig);
            client = createClient(port.port(), splitEncodings);
        }

        @Setup(Level.Invocation)
        public void setup() {
            setup(1);
        }

        @TearDown(Level.Invocation)
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

    // There's code duplicated from UnaryState above. It's because JMH is having troubles when states use inheritance.
    @State(Scope.Thread)
    public static class StreamingState {
        @Param
        PayloadWeight weight;

        @Param({"identity", "gzip"})
        String encodings;

        @Param({"1", "10"})
        int streamCount;

        PortsAllocator.Port port;
        ServerHandle server;
        GreeterInterface.GreeterClient client;

        void setup(int streamCount) {
            final String[] splitEncodings = encodings.split(",");
            final PbjGrpcServiceConfig serviceConfig =
                    new PbjGrpcServiceConfig(splitEncodings[0], Set.of(splitEncodings));
            port = GrpcTestUtils.PORTS.acquire();
            server = ServerHandle.start(port.port(), new GreeterService(weight, streamCount), serviceConfig);
            client = createClient(port.port(), splitEncodings);
        }

        @Setup(Level.Invocation)
        public void setup() {
            setup(streamCount);
        }

        @TearDown(Level.Invocation)
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
    @OperationsPerInvocation(INVOCATIONS)
    public void benchUnary(final UnaryState state, final Blackhole blackhole) {
        try {
            for (int i = 1; i <= INVOCATIONS; i++) {
                blackhole.consume(state.client.sayHello(state.weight.requestSupplier.get()));
            }
        } catch (Exception e) {
            // Keep running because network may fail sometimes.
            e.printStackTrace();
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchServerStreaming(final StreamingState state, final Blackhole blackhole) {
        try {
            for (int i = 1; i <= INVOCATIONS; i++) {
                final CountDownLatch latch = new CountDownLatch(1);
                state.client.sayHelloStreamReply(state.weight.requestSupplier.get(), new Pipeline<>() {
                    @Override
                    public void onNext(HelloReply item) throws RuntimeException {
                        blackhole.consume(item);
                    }

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {}

                    @Override
                    public void onError(Throwable throwable) {
                        new RuntimeException(throwable).printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            // Keep running because network may fail sometimes.
            e.printStackTrace();
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchClientStreaming(final StreamingState state, final Blackhole blackhole) {
        try {
            for (int i = 1; i <= INVOCATIONS; i++) {
                final CountDownLatch latch = new CountDownLatch(1);
                final Pipeline<? super HelloRequest> requests = state.client.sayHelloStreamRequest(new Pipeline<>() {
                    @Override
                    public void onNext(HelloReply item) throws RuntimeException {
                        blackhole.consume(item);
                    }

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(state.streamCount);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        new RuntimeException(throwable).printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
                for (int j = 0; j < state.streamCount; j++) {
                    requests.onNext(state.weight.requestSupplier.get());
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            // Keep running because network may fail sometimes.
            e.printStackTrace();
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchBidiStreaming(final StreamingState state, final Blackhole blackhole) {
        try {
            for (int i = 1; i <= INVOCATIONS; i++) {
                final CountDownLatch latch = new CountDownLatch(1);
                final Pipeline<? super HelloRequest> requests = state.client.sayHelloStreamBidi(new Pipeline<>() {
                    @Override
                    public void onNext(HelloReply item) throws RuntimeException {
                        blackhole.consume(item);
                    }

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(state.streamCount);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        new RuntimeException(throwable).printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
                for (int j = 0; j < state.streamCount; j++) {
                    requests.onNext(state.weight.requestSupplier.get());
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            // Keep running because network may fail sometimes.
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt =
                new OptionsBuilder().include(PbjGrpcBench.class.getSimpleName()).build();

        new Runner(opt).run();
    }
}
