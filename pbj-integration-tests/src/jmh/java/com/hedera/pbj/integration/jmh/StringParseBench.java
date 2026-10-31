// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** Parsing a HAPI message whose cost is mostly its string fields: a token create with name, symbol and memo. */
@SuppressWarnings("unused")
@State(Scope.Thread)
@Fork(3)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class StringParseBench {
    /** ASCII strings, or strings with non-ASCII characters (which need UTF-8 validation). */
    @Param({"ascii", "utf8"})
    public String text;

    private Bytes bytes;
    private BufferedData buffer;

    @Setup
    public void setup() {
        final boolean ascii = text.equals("ascii");
        final TokenCreateTransactionBody body = TokenCreateTransactionBody.newBuilder()
                .name(ascii ? "Hiero Example Token" : "Hiero Exämple Tökén ✓")
                .symbol(ascii ? "HEXT" : "HËXT")
                .memo(ascii ? "Created for the string parsing benchmark" : "Créé pour le banc d'essai — 文字列")
                .treasury(AccountID.newBuilder().accountNum(1001).build())
                .decimals(8)
                .initialSupply(1_000_000_000L)
                .build();
        bytes = TokenCreateTransactionBody.PROTOBUF.toBytes(body);
        buffer = BufferedData.wrap(bytes.toByteArray());
    }

    @Benchmark
    public TokenCreateTransactionBody parseBytes() throws ParseException {
        return TokenCreateTransactionBody.PROTOBUF.parse(bytes);
    }

    @Benchmark
    public TokenCreateTransactionBody parseBufferedData() throws ParseException {
        buffer.resetPosition();
        return TokenCreateTransactionBody.PROTOBUF.parse(buffer);
    }
}
