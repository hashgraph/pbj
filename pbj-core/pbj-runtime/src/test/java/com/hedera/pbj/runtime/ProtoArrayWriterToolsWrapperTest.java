// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToIntBiFunction;
import org.junit.jupiter.api.Test;

/**
 * The byte-array writers of wrapper types ({@code google.protobuf.XxxValue}) must write the same bytes as the stream
 * writers, and as many as the size methods measure, for every value: in particular {@code -0.0}, which the model treats
 * as the default, and {@code uint32} values of 2^31 or more, which are negative as Java ints.
 */
final class ProtoArrayWriterToolsWrapperTest {

    static {
        // ProtoWriterToolsTest needs ProtoWriterTools loaded without assertions, and this test may load it first
        ProtoWriterTools.class.getClassLoader().setClassAssertionStatus(ProtoWriterTools.class.getName(), false);
    }

    private static final List<Integer> INTS = List.of(0, 1, 127, 128, Integer.MAX_VALUE, Integer.MIN_VALUE, -1, -128);
    private static final List<Long> LONGS = List.of(0L, 1L, 127L, 128L, Long.MAX_VALUE, Long.MIN_VALUE, -1L);
    private static final List<Float> FLOATS =
            List.of(0f, -0f, 1.5f, -1.5f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_VALUE);
    private static final List<Double> DOUBLES = List.of(
            0d, -0d, 1.5d, -1.5d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE);

    private static FieldDefinition wrapper(final FieldType type) {
        return new FieldDefinition("wrapper", type, false, true, false, 1002);
    }

    /** Write with both writers, and check they agree with each other and with the measured size. */
    private static <T> void check(
            final T value,
            final FieldDefinition field,
            final BiConsumer<BufferedData, T> streamWriter,
            final ArrayWriter<T> arrayWriter,
            final ToIntBiFunction<FieldDefinition, T> sizer) {
        final BufferedData stream = BufferedData.allocate(64);
        streamWriter.accept(stream, value);
        final Bytes expected = stream.getBytes(0, stream.position());

        final byte[] array = new byte[64];
        final int written = arrayWriter.write(array, 3, field, value);
        final String what = field.type() + " " + value;
        assertEquals(expected, Bytes.wrap(array, 3, written), what);
        assertEquals(expected.length(), sizer.applyAsInt(field, value), what);
    }

    @FunctionalInterface
    private interface ArrayWriter<T> {
        int write(byte[] output, int offset, FieldDefinition field, T value);
    }

    @Test
    void int32Values() {
        final FieldDefinition field = wrapper(FieldType.INT32);
        for (final Integer value : INTS) {
            check(
                    value,
                    field,
                    (out, v) -> ProtoWriterTools.writeOptionalInteger(out, field, v),
                    ProtoArrayWriterTools::writeOptionalInt32Value,
                    ProtoWriterTools::sizeOfOptionalInteger);
        }
    }

    @Test
    void uint32Values() {
        final FieldDefinition field = wrapper(FieldType.UINT32);
        for (final Integer value : INTS) {
            check(
                    value,
                    field,
                    (out, v) -> ProtoWriterTools.writeOptionalInteger(out, field, v),
                    ProtoArrayWriterTools::writeOptionalUInt32Value,
                    ProtoWriterTools::sizeOfOptionalInteger);
        }
    }

    @Test
    void int64AndUint64Values() {
        for (final FieldType type : List.of(FieldType.INT64, FieldType.UINT64)) {
            final FieldDefinition field = wrapper(type);
            for (final Long value : LONGS) {
                check(
                        value,
                        field,
                        (out, v) -> ProtoWriterTools.writeOptionalLong(out, field, v),
                        ProtoArrayWriterTools::writeOptionalInt64Value,
                        ProtoWriterTools::sizeOfOptionalLong);
            }
        }
    }

    @Test
    void floatValues() {
        final FieldDefinition field = wrapper(FieldType.FLOAT);
        for (final Float value : FLOATS) {
            check(
                    value,
                    field,
                    (out, v) -> ProtoWriterTools.writeOptionalFloat(out, field, v),
                    ProtoArrayWriterTools::writeOptionalFloat,
                    ProtoWriterTools::sizeOfOptionalFloat);
        }
    }

    @Test
    void doubleValues() {
        final FieldDefinition field = wrapper(FieldType.DOUBLE);
        for (final Double value : DOUBLES) {
            check(
                    value,
                    field,
                    (out, v) -> ProtoWriterTools.writeOptionalDouble(out, field, v),
                    ProtoArrayWriterTools::writeOptionalDouble,
                    ProtoWriterTools::sizeOfOptionalDouble);
        }
    }

    @Test
    void boolValues() {
        final FieldDefinition field = wrapper(FieldType.BOOL);
        for (final Boolean value : List.of(true, false)) {
            check(
                    value,
                    field,
                    (out, v) -> ProtoWriterTools.writeOptionalBoolean(out, field, v),
                    ProtoArrayWriterTools::writeOptionalBoolean,
                    ProtoWriterTools::sizeOfOptionalBoolean);
        }
    }
}
