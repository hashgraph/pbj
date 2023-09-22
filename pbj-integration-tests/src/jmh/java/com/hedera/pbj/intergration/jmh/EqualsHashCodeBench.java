package com.hedera.pbj.intergration.jmh;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.intergration.test.TestHashFunctions;
import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.pbj.test.proto.pbj.tests.HashevalTest;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class EqualsHashCodeBench {
    private TimestampTest testStamp;
    private TimestampTest testStamp1;

    public EqualsHashCodeBench() {
        testStamp = new TimestampTest(987L, 123);
        testStamp1 = new TimestampTest(987L, 122);
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchHashCode(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            testStamp.hashCode();
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchEquals(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            testStamp.equals(testStamp);
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchNotEquals(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            testStamp.equals(testStamp1);
        }
    }
}