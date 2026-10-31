// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import com.hedera.pbj.test.proto.pbj.Everything;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every wrapper type ({@code google.protobuf.XxxValue}), as a field and as a oneof alternative, with the values at the
 * edges of its encoding: every writer must write the same bytes, as many as measured, and protoc must read them and
 * write them back the same. In particular {@code -0.0} (the default, as everywhere in the model, see #1020) and
 * {@code uint32} values of 2^31 or more, which are negative as Java ints.
 */
final class WrapperEdgeValuesTest {

    private static final List<Integer> INTS = List.of(0, 1, 127, 128, -1, -128, Integer.MAX_VALUE, Integer.MIN_VALUE);
    private static final List<Long> LONGS = List.of(0L, 1L, 127L, 128L, -1L, Long.MAX_VALUE, Long.MIN_VALUE);
    private static final List<Float> FLOATS =
            List.of(0f, -0f, 1.5f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_VALUE);
    private static final List<Double> DOUBLES =
            List.of(0d, -0d, 1.5d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE);
    private static final List<Boolean> BOOLEANS = List.of(false, true);
    private static final List<Bytes> BYTES =
            List.of(Bytes.EMPTY, Bytes.wrap(new byte[] {1}), Bytes.wrap(new byte[200]));
    private static final List<String> STRINGS = List.of("", "a", "é".repeat(100));

    private static <T> void add(
            final List<Arguments> out,
            final String name,
            final List<T> values,
            final Function<T, Everything.Builder> field,
            final Function<T, Everything.Builder> oneOf) {
        for (final T value : values) {
            out.add(Arguments.of(name + " = " + value, field.apply(value).build()));
            out.add(Arguments.of(name + "OneOf = " + value, oneOf.apply(value).build()));
        }
    }

    static Stream<Arguments> cases() {
        final List<Arguments> out = new ArrayList<>();
        add(out, "int32Boxed", INTS, v -> Everything.newBuilder().int32Boxed(v), v -> Everything.newBuilder()
                .int32BoxedOneOf(v));
        add(out, "uint32Boxed", INTS, v -> Everything.newBuilder().uint32Boxed(v), v -> Everything.newBuilder()
                .uint32BoxedOneOf(v));
        add(out, "int64Boxed", LONGS, v -> Everything.newBuilder().int64Boxed(v), v -> Everything.newBuilder()
                .int64BoxedOneOf(v));
        add(out, "uint64Boxed", LONGS, v -> Everything.newBuilder().uint64Boxed(v), v -> Everything.newBuilder()
                .uint64BoxedOneOf(v));
        add(out, "floatBoxed", FLOATS, v -> Everything.newBuilder().floatBoxed(v), v -> Everything.newBuilder()
                .floatBoxedOneOf(v));
        add(out, "doubleBoxed", DOUBLES, v -> Everything.newBuilder().doubleBoxed(v), v -> Everything.newBuilder()
                .doubleBoxedOneOf(v));
        add(out, "boolBoxed", BOOLEANS, v -> Everything.newBuilder().boolBoxed(v), v -> Everything.newBuilder()
                .boolBoxedOneOf(v));
        add(out, "bytesBoxed", BYTES, v -> Everything.newBuilder().bytesBoxed(v), v -> Everything.newBuilder()
                .bytesBoxedOneOf(v));
        add(out, "stringBoxed", STRINGS, v -> Everything.newBuilder().stringBoxed(v), v -> Everything.newBuilder()
                .stringBoxedOneOf(v));
        return out.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyWriterWritesTheSameBytesAsMeasured(final String name, final Everything value) throws Exception {
        final BufferedData stream = BufferedData.allocate(1024);
        Everything.PROTOBUF.write(value, stream);
        final Bytes expected = stream.getBytes(0, stream.position());

        final byte[] array = new byte[1024];
        final int written = Everything.PROTOBUF.write(value, array, 0);
        assertEquals(expected, Bytes.wrap(array, 0, written), "write(T, byte[], int)");
        assertEquals(expected, Everything.PROTOBUF.toBytes(value), "toBytes");
        assertEquals(expected.length(), Everything.PROTOBUF.measureRecord(value), "measureRecord");
        final ReverseBuffer reverse = new ReverseBuffer(16);
        Everything.PROTOBUF.writeReverse(value, reverse);
        assertEquals(expected, reverse.toBytes(), "writeReverse");

        // protoc reads exactly what was written, and writes it back the same
        final byte[] protoc = com.hedera.pbj.test.proto.java.Everything.parseFrom(expected.toByteArray())
                .toByteArray();
        assertArrayEquals(expected.toByteArray(), protoc, "protoc");
        // and so does PBJ
        assertEquals(expected, Everything.PROTOBUF.toBytes(Everything.PROTOBUF.parse(expected)), "PBJ round trip");
    }
}
