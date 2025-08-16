// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.Hasheval2;
import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ModelObjHashCodeBench {

    @Benchmark
    public void simpleObject(Blackhole blackhole) {
        TimestampTest tst = new TimestampTest(987L, 123);
        blackhole.consume(tst.hashCode());
    }

    @Benchmark
    public void everythingObject(Blackhole blackhole) {
        Everything e = EverythingTestData.EVERYTHING.copyBuilder().build();
        blackhole.consume(e.hashCode());
    }

    @Benchmark
    public void bigObject(Blackhole blackhole) {
        Hasheval2 complexObj = new Hasheval2(
                13,
                -1262,
                2236,
                326,
                -27,
                123f,
                7L,
                -7L,
                123L,
                234L,
                -345L,
                456.789D,
                true,
                Suit.ACES,
                new Hasheval(
                        1109840,
                        -1414,
                        25151,
                        31515,
                        -236,
                        123f,
                        7347L,
                        -7L,
                        1233474347347L,
                        234L,
                        -345L,
                        456.789D,
                        true,
                        Suit.ACES,
                        new TimestampTest(987L, 123),
                        "FooBarKKKKHHHHOIOIOI",
                        Bytes.wrap(new byte[] {127, 2, 3, 123, 48, 6, 7, (byte) 255})),
                "FooBarKKKKHHHHOIOIOI",
                Bytes.wrap(new byte[] {81, 52, 13, 94, 85, 66, 7, (byte) 255}));
        blackhole.consume(complexObj.hashCode());
    }
}
