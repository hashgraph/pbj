// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc.block;

import com.hedera.pbj.grpc.helidon.PbjGrpcServiceConfig;
import com.hedera.pbj.integration.grpc.GrpcTestUtils;
import com.hedera.pbj.integration.grpc.PortsAllocator;
import com.hedera.pbj.integration.jmh.grpc.NetworkLatencySimulator;
import com.hedera.pbj.integration.jmh.grpc.PbjGrpcBench;
import com.hedera.pbj.integration.jmh.grpc.ZstdGrpcTransformer;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.Pipeline;
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
import pbj.integration.tests.pbj.integration.tests.TestBlock;
import pbj.integration.tests.pbj.integration.tests.TestBlockRequest;
import pbj.integration.tests.pbj.integration.tests.TestBlockStreamerInterface;

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
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class TestBlockGrpcBench {
    private static final int INVOCATIONS = 2_000;

    static {
        new ZstdGrpcTransformer(-5).register("zstd-5");
        new ZstdGrpcTransformer(0).register("zstd0");
        new ZstdGrpcTransformer(3).register("zstd"); // the default level

        // 1Gbps network:
        NetworkLatencySimulator.simulate(1_000, true);
    }

    static TestBlockStreamerInterface.TestBlockStreamerClient createClient(final int port, final String[] encodings) {
        final GrpcClient grpcClient;
        if (encodings == null || encodings.length == 0) {
            grpcClient = GrpcTestUtils.createGrpcClient(port, GrpcTestUtils.PROTO_OPTIONS);
        } else {
            grpcClient =
                    GrpcTestUtils.createGrpcClient(port, GrpcTestUtils.PROTO_OPTIONS, encodings[0], Set.of(encodings));
        }

        return new TestBlockStreamerInterface.TestBlockStreamerClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);
    }

    @State(Scope.Thread)
    public static class BenchState {
        @Param({"102400", "524288", "2048000"})
        int maxBlockSize;

        @Param({"identity", "gzip", "zstd", "zstd0", "zstd-5"})
        String encodings;

        PortsAllocator.Port port;
        PbjGrpcBench.ServerHandle server;
        TestBlockStreamerInterface.TestBlockStreamerClient client;

        @Setup(Level.Invocation)
        public void setup() {
            final String[] splitEncodings = encodings.split(",");
            final PbjGrpcServiceConfig serviceConfig =
                    new PbjGrpcServiceConfig(splitEncodings[0], Set.of(splitEncodings));
            port = GrpcTestUtils.PORTS.acquire();
            server = PbjGrpcBench.ServerHandle.start(
                    port.port(), new TestBlockStreamerService(maxBlockSize), serviceConfig);
            client = createClient(port.port(), splitEncodings);
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
    public void benchUnary(final BenchState state, final Blackhole blackhole) {
        try {
            for (int i = 1; i <= INVOCATIONS; i++) {
                blackhole.consume(state.client.getBlock(
                        TestBlockRequest.newBuilder().num(i).build()));
            }
        } catch (Exception e) {
            // Keep running because network may fail sometimes.
            e.printStackTrace();
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchBidiStreaming(final BenchState state, final Blackhole blackhole) {
        try {
            final CountDownLatch latch = new CountDownLatch(INVOCATIONS);
            final Pipeline<? super TestBlockRequest> requests = state.client.streamBlocks(new Pipeline<>() {
                @Override
                public void onNext(TestBlock item) throws RuntimeException {
                    blackhole.consume(item);
                    latch.countDown();
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(INVOCATIONS);
                }

                @Override
                public void onError(Throwable throwable) {
                    new RuntimeException(throwable).printStackTrace();
                    latch.countDown();
                }

                @Override
                public void onComplete() {}
            });
            requests.onNext(TestBlockRequest.newBuilder().num(INVOCATIONS).build());
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            // Keep running because network may fail sometimes.
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(TestBlockGrpcBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
