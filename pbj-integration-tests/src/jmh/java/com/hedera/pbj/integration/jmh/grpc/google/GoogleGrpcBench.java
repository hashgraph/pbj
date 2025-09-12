// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc.google;

import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
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
import pbj.integration.tests.GreeterGrpc;
import pbj.integration.tests.HelloReply;
import pbj.integration.tests.HelloRequest;

/**
 * A Google GRPC-based implementation of the GrpcBench, so that we can compare the performance
 * between Google and PBJ.
 * While the Google and PBJ APIs differ, the implementations are supposed to be otherwise identical,
 * so that we compare apples to apples.
 */
@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class GoogleGrpcBench {
    private static final int INVOCATIONS = 20_000;

    private record ServerHandle(Server server) implements AutoCloseable {
        @Override
        public void close() {
            try {
                server.shutdownNow().awaitTermination();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        static ServerHandle start(final int port, final GoogleGreeterService service) {
            try {
                return new ServerHandle(Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                        .addService(service)
                        .build()
                        .start());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private record ClientHandle(ManagedChannel channel, GreeterGrpc.GreeterStub client) implements AutoCloseable {
        @Override
        public void close() {
            channel.shutdown();
        }

        static ClientHandle createClient(final int port) {
            final ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();
            return new ClientHandle(channel, GreeterGrpc.newStub(channel));
        }
    }

    @State(Scope.Thread)
    public static class UnaryState {
        @Param
        GooglePayloadWeight weight;

        PortsAllocator.Port port;
        ServerHandle server;
        ClientHandle client;

        void setup(int streamCount) {
            port = GrpcTestUtils.PORTS.acquire();
            server = ServerHandle.start(port.port(), new GoogleGreeterService(weight, streamCount));
            client = ClientHandle.createClient(port.port());
        }

        @Setup(Level.Invocation)
        public void setup() {
            setup(1);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            client.close();
            client = null;
            server.close();
            server = null;
            port.close();
            port = null;
        }
    }

    @State(Scope.Thread)
    public static class StreamingState {
        @Param
        GooglePayloadWeight weight;

        @Param({"1", "10"})
        int streamCount;

        PortsAllocator.Port port;
        ServerHandle server;
        ClientHandle client;

        void setup(int streamCount) {
            port = GrpcTestUtils.PORTS.acquire();
            server = ServerHandle.start(port.port(), new GoogleGreeterService(weight, streamCount));
            client = ClientHandle.createClient(port.port());
        }

        @Setup(Level.Invocation)
        public void setup() {
            setup(streamCount);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            client.close();
            client = null;
            server.close();
            server = null;
            port.close();
            port = null;
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchUnary(final UnaryState state, final Blackhole blackhole) {
        for (int i = 1; i <= INVOCATIONS; i++) {
            try {
                final CountDownLatch latch = new CountDownLatch(1);
                state.client.client.sayHello(state.weight.requestSupplier.get(), new StreamObserver<HelloReply>() {
                    @Override
                    public void onNext(HelloReply helloReply) {
                        blackhole.consume(helloReply);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        new RuntimeException(throwable).printStackTrace();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                // Keep running because network may fail sometimes.
                new RuntimeException(e).printStackTrace();
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchServerStreaming(final StreamingState state, final Blackhole blackhole) {
        for (int i = 1; i <= INVOCATIONS; i++) {
            try {
                final CountDownLatch latch = new CountDownLatch(1);
                state.client.client.sayHelloStreamReply(
                        state.weight.requestSupplier.get(), new StreamObserver<HelloReply>() {
                            @Override
                            public void onNext(HelloReply helloReply) {
                                blackhole.consume(helloReply);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                new RuntimeException(throwable).printStackTrace();
                            }

                            @Override
                            public void onCompleted() {
                                latch.countDown();
                            }
                        });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                // Keep running because network may fail sometimes.
                new RuntimeException(e).printStackTrace();
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchClientStreaming(final StreamingState state, final Blackhole blackhole) {
        for (int i = 1; i <= INVOCATIONS; i++) {
            try {
                final CountDownLatch latch = new CountDownLatch(1);
                final StreamObserver<HelloRequest> requests =
                        state.client.client.sayHelloStreamRequest(new StreamObserver<HelloReply>() {
                            @Override
                            public void onNext(HelloReply helloReply) {
                                blackhole.consume(helloReply);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                new RuntimeException(throwable).printStackTrace();
                            }

                            @Override
                            public void onCompleted() {
                                latch.countDown();
                            }
                        });
                for (int j = 0; j < state.streamCount; j++) {
                    requests.onNext(state.weight.requestSupplier.get());
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                // Keep running because network may fail sometimes.
                new RuntimeException(e).printStackTrace();
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchBidiStreaming(final StreamingState state, final Blackhole blackhole) {
        for (int i = 1; i <= INVOCATIONS; i++) {
            try {
                final CountDownLatch latch = new CountDownLatch(1);
                final StreamObserver<HelloRequest> requests =
                        state.client.client.sayHelloStreamBidi(new StreamObserver<HelloReply>() {
                            @Override
                            public void onNext(HelloReply helloReply) {
                                blackhole.consume(helloReply);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                new RuntimeException(throwable).printStackTrace();
                            }

                            @Override
                            public void onCompleted() {
                                latch.countDown();
                            }
                        });
                for (int j = 0; j < state.streamCount; j++) {
                    requests.onNext(state.weight.requestSupplier.get());
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                // Keep running because network may fail sometimes.
                new RuntimeException(e).printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(GoogleGrpcBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
