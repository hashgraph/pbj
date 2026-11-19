// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.MixedFastInner;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.MixedKind;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** Reading every field of a message: through a reusable zero-copy view, against parsing it into a model object. */
@SuppressWarnings("unused")
@State(Scope.Thread)
@Fork(3)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ViewBench {
    private final Bytes bytes = MixedFastInner.PROTOBUF.toBytes(MixedFastInner.newBuilder()
            .delta(-123_456_789L)
            .flag(true)
            .ratio(0.25)
            .fixed(42)
            .kind(MixedKind.MIXED_B)
            .big(Long.MIN_VALUE)
            .scale(1.5f)
            .offset(-7)
            .far("far away")
            .build());
    private final MixedFastInner.View view = new MixedFastInner.View();

    @Benchmark
    public long readEveryFieldWithView() throws ParseException {
        final MixedFastInner.View v = view.wrap(bytes);
        return v.delta()
                + (v.flag() ? 1 : 0)
                + (long) v.ratio()
                + v.fixed()
                + v.kindProtoOrdinal()
                + v.big()
                + (long) v.scale()
                + v.offset()
                + v.farLength();
    }

    @Benchmark
    public long readEveryFieldWithParse() throws ParseException {
        final MixedFastInner m = MixedFastInner.PROTOBUF.parse(bytes);
        return m.delta()
                + (m.flag() ? 1 : 0)
                + (long) m.ratio()
                + m.fixed()
                + m.kindProtoOrdinal()
                + m.big()
                + (long) m.scale()
                + m.offset()
                + m.far().length();
    }
}
