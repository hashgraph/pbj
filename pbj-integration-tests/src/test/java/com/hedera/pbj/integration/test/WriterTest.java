// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer.LengthReservation;
import com.hedera.pbj.test.custom.proto.pbj.CustomWriterCycles;
import com.hedera.pbj.test.custom.proto.pbj.CustomWriterHolder;
import com.hedera.pbj.test.custom.proto.pbj.CustomWriterLocal;
import com.hedera.pbj.test.custom.proto.pbj.WriterNode;
import com.hedera.pbj.test.custom.proto.pbj.WriterPairA;
import com.hedera.pbj.test.custom.proto.pbj.WriterPairB;
import com.hedera.pbj.test.custom.proto.pbj.WriterTriX;
import com.hedera.pbj.test.custom.proto.pbj.WriterTriY;
import com.hedera.pbj.test.custom.proto.pbj.WriterTriZ;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterChoice;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterColor;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterLeaf;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterMaps;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterNames;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterRepeated;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterRoot;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterScalars;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.WriterWrappers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pbj.integ.test.writer.shared.pbj.integration.tests.SharedLeaf;

/**
 * The generated message writers ({@code pbj.writers}): every kind of field, written through the writer's API, gives
 * exactly the bytes of the model's {@code toBytes} for the same values; misuse throws; nested messages' length prefixes
 * and byte ranges are right whatever their size.
 */
final class WriterTest {

    /** Write a message with a fresh writer into a fresh buffer, and return the bytes. */
    private static <W> Bytes write(final W writer, final Consumer<W> fields) {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        start(writer, buffer);
        fields.accept(writer);
        ((com.hedera.pbj.runtime.io.buffer.MessageWriter) writer).end();
        return buffer.toBytes();
    }

    private static void start(final Object writer, final ProtoWriteBuffer buffer) {
        switch (writer) {
            case WriterScalars.Writer w -> w.start(buffer);
            case WriterRepeated.Writer w -> w.start(buffer);
            case WriterMaps.Writer w -> w.start(buffer);
            case WriterChoice.Writer w -> w.start(buffer);
            case WriterWrappers.Writer w -> w.start(buffer);
            case WriterNames.Writer w -> w.start(buffer);
            case WriterRoot.Writer w -> w.start(buffer);
            case WriterNode.Writer w -> w.start(buffer);
            case CustomWriterHolder.Writer w -> w.start(buffer);
            case CustomWriterCycles.Writer w -> w.start(buffer);
            default -> throw new IllegalArgumentException(writer.getClass().getName());
        }
    }

    /**
     * Assert that the writer's output is the model's encoding of {@code expected}, and parses back to it when the parser's
     * default limits allow.
     */
    private static <T> void assertWrites(final Codec<T> codec, final T expected, final Bytes written) throws Exception {
        assertEquals(codec.toBytes(expected), written);
        if (written.length() < Codec.DEFAULT_MAX_SIZE) {
            assertEquals(expected, codec.parse(written));
        }
    }

    // ----------------------------------------------------------------------------------------------- scalars

    @Test
    void defaultValuesAreLeftOut() throws Exception {
        final Bytes written = write(new WriterScalars.Writer(), w -> w.int32Value(0)
                .int64Value(0)
                .uint32Value(0)
                .uint64Value(0)
                .sint32Value(0)
                .sint64Value(0)
                .fixed32Value(0)
                .fixed64Value(0)
                .sfixed32Value(0)
                .sfixed64Value(0)
                .floatValue(0)
                .doubleValue(-0.0)
                .boolValue(false)
                .stringValue("")
                .bytesValue(Bytes.EMPTY)
                .color(WriterColor.WRITER_COLOR_NONE)
                .farValue(0));
        assertEquals(0, written.length());
        // null strings and enums are defaults too, and so are empty ranges
        assertEquals(
                0,
                write(new WriterScalars.Writer(), w -> w.stringValue((String) null)
                                .bytesValue(new byte[3], 1, 0)
                                .color(null))
                        .length());
        assertWrites(WriterScalars.PROTOBUF, WriterScalars.DEFAULT, written);
    }

    @Test
    void everyScalarType() throws Exception {
        final WriterScalars expected = WriterScalars.newBuilder()
                .int32Value(-1)
                .int64Value(Long.MIN_VALUE)
                .uint32Value(-1)
                .uint64Value(-1L)
                .sint32Value(Integer.MIN_VALUE)
                .sint64Value(-2L)
                .fixed32Value(-3)
                .fixed64Value(Long.MAX_VALUE)
                .sfixed32Value(Integer.MIN_VALUE)
                .sfixed64Value(-5L)
                .floatValue(Float.NaN)
                .doubleValue(Double.NaN)
                .boolValue(true)
                .stringValue("héllo ✓")
                .bytesValue(Bytes.wrap(new byte[] {1, 2, 3}))
                .color(WriterColor.WRITER_COLOR_BLUE)
                .farValue(300)
                .build();
        final Bytes written = write(new WriterScalars.Writer(), w -> w.int32Value(-1)
                .int64Value(Long.MIN_VALUE)
                .uint32Value(-1)
                .uint64Value(-1L)
                .sint32Value(Integer.MIN_VALUE)
                .sint64Value(-2L)
                .fixed32Value(-3)
                .fixed64Value(Long.MAX_VALUE)
                .sfixed32Value(Integer.MIN_VALUE)
                .sfixed64Value(-5L)
                .floatValue(Float.NaN)
                .doubleValue(Double.NaN)
                .boolValue(true)
                .stringValue("héllo ✓")
                .bytesValue(Bytes.wrap(new byte[] {1, 2, 3}))
                .color(WriterColor.WRITER_COLOR_BLUE)
                .farValue(300));
        // the model's equals doesn't hold for NaN, so only the bytes are compared
        assertEquals(WriterScalars.PROTOBUF.toBytes(expected), written);
        // a negative int32 is a ten-byte varint
        assertEquals(
                Bytes.wrap(new byte[] {0x08, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1}),
                write(new WriterScalars.Writer(), w -> w.int32Value(-1)));
        // the five-byte tag of field 2^29 - 1
        assertEquals(
                Bytes.wrap(new byte[] {(byte) 0xF8, -1, -1, -1, 0x0F, 1}),
                write(new WriterScalars.Writer(), w -> w.farValue(1)));
    }

    @Test
    void stringsAndBytesFromArrays() throws Exception {
        final byte[] utf8 = "xxhéllo ✓xx".getBytes(StandardCharsets.UTF_8);
        final WriterScalars expected = WriterScalars.newBuilder()
                .stringValue("héllo ✓")
                .bytesValue(Bytes.wrap(new byte[] {2, 3}))
                .color(WriterColor.WRITER_COLOR_RED)
                .build();
        assertWrites(WriterScalars.PROTOBUF, expected, write(new WriterScalars.Writer(), w -> w.stringValue(
                        utf8, 2, utf8.length - 4)
                .bytesValue(new byte[] {1, 2, 3, 4}, 1, 2)
                .colorProtoOrdinal(1)));
        // a string's bytes must be UTF-8
        final WriterScalars.Writer writer = new WriterScalars.Writer().start(new ProtoWriteBuffer(16));
        assertThrows(IllegalArgumentException.class, () -> writer.stringValue(new byte[] {(byte) 0xC3}, 0, 1));
        // nothing was written, so the field can still be written
        writer.stringValue("ok");
    }

    // ---------------------------------------------------------------------------------------------- repeated

    @Test
    void everyRepeatedType() throws Exception {
        final WriterLeaf leaf1 = WriterLeaf.newBuilder().id(1).stamp(10).build();
        final WriterLeaf leaf2 = WriterLeaf.newBuilder().id(2).build();
        final WriterRepeated expected = WriterRepeated.newBuilder()
                .int32Values(1, -1, 300)
                .int64Values(Long.MIN_VALUE, 0L)
                .uint32Values(-1, 1)
                .uint64Values(-1L)
                .sint32Values(-1, 1)
                .sint64Values(Long.MIN_VALUE)
                .fixed32Values(7)
                .fixed64Values(8L)
                .sfixed32Values(-9)
                .sfixed64Values(-10L)
                .floatValues(1.5f, -0.0f, Float.NaN)
                .doubleValues(2.5, -0.0, Double.NaN)
                .boolValues(true, false)
                .stringValues("", "a", "✓")
                .bytesValues(Bytes.EMPTY, Bytes.wrap(new byte[] {5}))
                .colors(WriterColor.WRITER_COLOR_RED, WriterColor.WRITER_COLOR_NONE)
                .leaves(leaf1, WriterLeaf.DEFAULT, leaf2, leaf1)
                .build();
        // every packed field in one call from an array, and nested messages each way
        final Bytes inOneCall = write(new WriterRepeated.Writer(), w -> {
            w.int32Values(new int[] {9, 1, -1, 300}, 1, 3)
                    .int64Values(new long[] {Long.MIN_VALUE, 0L}, 0, 2)
                    .uint32Values(new int[] {-1, 1}, 0, 2)
                    .uint64Values(new long[] {-1L}, 0, 1)
                    .sint32Values(new int[] {-1, 1}, 0, 2)
                    .sint64Values(new long[] {Long.MIN_VALUE}, 0, 1)
                    .fixed32Values(new int[] {7}, 0, 1)
                    .fixed64Values(new long[] {8L}, 0, 1)
                    .sfixed32Values(new int[] {-9}, 0, 1)
                    .sfixed64Values(new long[] {-10L}, 0, 1)
                    .floatValues(new float[] {1.5f, -0.0f, Float.NaN}, 0, 3)
                    .doubleValues(new double[] {2.5, -0.0, Double.NaN}, 0, 3)
                    .boolValues(new boolean[] {true, false}, 0, 2)
                    .addStringValues("")
                    .addStringValues("a")
                    .addStringValues("✓".getBytes(StandardCharsets.UTF_8), 0, 3)
                    .addBytesValues(Bytes.EMPTY)
                    .addBytesValues(new byte[] {4, 5}, 1, 1)
                    .colors(new int[] {1, 0}, 0, 2);
            w.addLeaves().id(1).stamp(10);
            w.addLeaves().end();
            w.addLeaves(leaf2).addLeavesRaw(WriterLeaf.PROTOBUF.toBytes(leaf1));
        });
        assertWrites(WriterRepeated.PROTOBUF, expected, inOneCall);
        // packed fields element by element: ended explicitly, or by the next field
        final Bytes elementByElement = write(new WriterRepeated.Writer(), w -> {
            w.int32Values().add(1).add(-1).add(300).end();
            w.int64Values().add(Long.MIN_VALUE).add(0L);
            w.uint32Values().add(-1).add(1);
            w.uint64Values().add(-1L);
            w.sint32Values().add(-1).add(1);
            w.sint64Values().add(Long.MIN_VALUE);
            w.fixed32Values().add(7);
            w.fixed64Values().add(8L);
            w.sfixed32Values().add(-9);
            w.sfixed64Values().add(-10L);
            w.floatValues().add(1.5f).add(-0.0f).add(Float.NaN);
            w.doubleValues().add(2.5).add(-0.0).add(Double.NaN);
            w.boolValues().add(true).add(false);
            w.addStringValues("").addStringValues("a").addStringValues("✓");
            w.addBytesValues(Bytes.EMPTY).addBytesValues(Bytes.wrap(new byte[] {5}));
            w.colors().add(1).add(0);
            w.addLeaves(leaf1).addLeaves(WriterLeaf.DEFAULT);
            w.addLeaves().id(2);
            w.addLeaves().id(1).stamp(10);
        });
        assertEquals(inOneCall, elementByElement);
    }

    @Test
    void emptyPackedFieldsAreLeftOut() {
        assertEquals(
                0,
                write(new WriterRepeated.Writer(), w -> {
                            w.int32Values(new int[] {1}, 0, 0);
                            w.int64Values().end();
                            w.uint32Values();
                            w.colors();
                        })
                        .length());
    }

    // -------------------------------------------------------------------------------------------------- maps

    @Test
    void mapsInTheModelsKeyOrder() throws Exception {
        final WriterLeaf leaf = WriterLeaf.newBuilder().id(4).build();
        final WriterMaps expected = WriterMaps.newBuilder()
                .intToString(Map.of(-5, "neg", 0, "", 7, "seven"))
                .stringToLeaf(Map.of("a", leaf, "b", WriterLeaf.DEFAULT, "é", leaf))
                .boolToDouble(Map.of(false, 0.0, true, 1.5))
                .uint64ToBytes(Map.of(-1L, Bytes.EMPTY, 1L, Bytes.wrap(new byte[] {1})))
                .sint64ToFloat(Map.of(-3L, 0.0f, 3L, -2.5f))
                .stringToString(Map.of("", "", "k", "v"))
                .fixed32ToInt64(Map.of(-1, 0L, 0, -1L))
                .int64ToBool(Map.of(Long.MIN_VALUE, false, 0L, true))
                .build();
        final Bytes written = write(new WriterMaps.Writer(), w -> {
            // the keys' Java order: signed, for unsigned types too
            w.putIntToString(-5, "neg").putIntToString(0, "").putIntToString(7, "seven");
            w.putStringToLeaf("a").id(4);
            w.putStringToLeaf("b", WriterLeaf.DEFAULT);
            w.putStringToLeaf("é").writeFrom(leaf).end();
            w.putBoolToDouble(false, 0.0).putBoolToDouble(true, 1.5);
            w.putUint64ToBytes(-1L, Bytes.EMPTY).putUint64ToBytes(1L, Bytes.wrap(new byte[] {1}));
            w.putSint64ToFloat(-3L, 0.0f).putSint64ToFloat(3L, -2.5f);
            w.putStringToString("", "").putStringToString("k", "v");
            w.putFixed32ToInt64(-1, 0L).putFixed32ToInt64(0, -1L);
            w.putInt64ToBool(Long.MIN_VALUE, false).putInt64ToBool(0L, true);
        });
        assertWrites(WriterMaps.PROTOBUF, expected, written);
    }

    @Test
    void mapKeysOutOfOrderOrRepeatedThrow() {
        final WriterMaps.Writer w = new WriterMaps.Writer().start(new ProtoWriteBuffer(16));
        w.putIntToString(1, "a");
        assertTrue(assertThrows(IllegalStateException.class, () -> w.putIntToString(1, "b"))
                .getMessage()
                .contains("ascending order"));
        assertThrows(IllegalStateException.class, () -> w.putIntToString(0, "b"));
        // a new map starts its own order
        w.putStringToLeaf("z", WriterLeaf.DEFAULT);
        assertThrows(IllegalStateException.class, () -> w.putStringToLeaf("y"));
        w.putBoolToDouble(true, 1);
        assertThrows(IllegalStateException.class, () -> w.putBoolToDouble(false, 1));
    }

    // ------------------------------------------------------------------------------------------------ oneofs

    @Test
    void oneofAlternativesAreWrittenEvenWithDefaultValues() throws Exception {
        final List<WriterChoice> expected = List.of(
                WriterChoice.newBuilder().intChoice(0).build(),
                WriterChoice.newBuilder().stringChoice("").build(),
                WriterChoice.newBuilder().bytesChoice(Bytes.EMPTY).build(),
                WriterChoice.newBuilder().leafChoice(WriterLeaf.DEFAULT).build(),
                WriterChoice.newBuilder()
                        .colorChoice(WriterColor.WRITER_COLOR_NONE)
                        .build(),
                WriterChoice.newBuilder().boxedChoice(0L).build(),
                WriterChoice.newBuilder().boolChoice(false).build(),
                WriterChoice.newBuilder().doubleChoice(0.0).build(),
                WriterChoice.newBuilder().secondNumber(0L).build());
        final List<Consumer<WriterChoice.Writer>> writes = List.of(
                w -> w.intChoice(0),
                w -> w.stringChoice(""),
                w -> w.bytesChoice(new byte[0], 0, 0),
                w -> w.leafChoice().end(),
                w -> w.colorChoice(WriterColor.WRITER_COLOR_NONE),
                w -> w.boxedChoice(0L),
                w -> w.boolChoice(false),
                w -> w.doubleChoice(0.0),
                w -> w.secondNumber(0L));
        for (int i = 0; i < expected.size(); i++) {
            final Bytes written = write(new WriterChoice.Writer(), writes.get(i));
            assertTrue(written.length() > 0, "alternative " + i);
            assertWrites(WriterChoice.PROTOBUF, expected.get(i), written);
        }
        // a null message alternative is written as an empty one, as the model writes it
        assertEquals(
                WriterChoice.PROTOBUF.toBytes(
                        WriterChoice.newBuilder().leafChoice(WriterLeaf.DEFAULT).build()),
                write(new WriterChoice.Writer(), w -> w.leafChoice((WriterLeaf) null)));
    }

    @Test
    void oneofsBetweenOtherFields() throws Exception {
        final WriterLeaf leaf = WriterLeaf.newBuilder().id(3).build();
        final WriterChoice expected = WriterChoice.newBuilder()
                .before(1)
                .leafChoice(leaf)
                .after(2)
                .secondLeaf(leaf)
                .build();
        assertWrites(WriterChoice.PROTOBUF, expected, write(new WriterChoice.Writer(), w -> {
            w.before(1).leafChoiceRaw(WriterLeaf.PROTOBUF.toBytes(leaf)).after(2);
            w.secondLeaf().id(3);
        }));
    }

    @Test
    void aSecondOneofAlternativeThrows() {
        final WriterChoice.Writer w = new WriterChoice.Writer().start(new ProtoWriteBuffer(16));
        w.intChoice(1);
        assertTrue(assertThrows(IllegalStateException.class, () -> w.stringChoice("x"))
                .getMessage()
                .contains("Only one alternative of oneof value"));
        // the other oneof is independent
        w.after(1).secondNumber(2);
        assertThrows(IllegalStateException.class, () -> w.secondLeaf((WriterLeaf) null));
    }

    // ---------------------------------------------------------------------------------------------- wrappers

    @Test
    void wrapperTypes() throws Exception {
        final WriterWrappers expected = WriterWrappers.newBuilder()
                .stringValue("s")
                .bytesValue(Bytes.wrap(new byte[] {1}))
                .int32Value(-1)
                .uint32Value(5)
                .int64Value(Long.MIN_VALUE)
                .uint64Value(-1L)
                .floatValue(1.5f)
                .doubleValue(-2.5)
                .boolValue(true)
                .build();
        assertWrites(WriterWrappers.PROTOBUF, expected, write(new WriterWrappers.Writer(), w -> w.stringValue("s")
                .bytesValue(Bytes.wrap(new byte[] {1}))
                .int32Value(-1)
                .uint32Value(5)
                .int64Value(Long.MIN_VALUE)
                .uint64Value(-1L)
                .floatValue(1.5f)
                .doubleValue(-2.5)
                .boolValue(true)));
        // default values are written, as empty wrappers, so they are not null when parsed
        final WriterWrappers defaults = WriterWrappers.newBuilder()
                .stringValue("")
                .bytesValue(Bytes.EMPTY)
                .int32Value(0)
                .uint32Value(0)
                .int64Value(0L)
                .uint64Value(0L)
                .floatValue(0f)
                .doubleValue(0.0)
                .boolValue(false)
                .build();
        final Bytes written = write(new WriterWrappers.Writer(), w -> w.stringValue("")
                .bytesValue(Bytes.EMPTY)
                .int32Value(0)
                .uint32Value(0)
                .int64Value(0L)
                .uint64Value(0L)
                .floatValue(0f)
                .doubleValue(0.0)
                .boolValue(false));
        assertEquals(18, written.length());
        assertWrites(WriterWrappers.PROTOBUF, defaults, written);
        // left out, they are null
        assertWrites(WriterWrappers.PROTOBUF, WriterWrappers.DEFAULT, write(new WriterWrappers.Writer(), w -> {}));
    }

    /**
     * Two wrapper values the model can't encode, because its size and its writer disagree: {@code FloatValue} and
     * {@code DoubleValue} of -0.0, and {@code UInt32Value} of 2^31 or more. Writers write them canonically, as protoc
     * does, and they parse back to the same values.
     */
    @Test
    void wrapperValuesTheModelMeasuresWrongAreWrittenAsProtocDoes() throws Exception {
        final Bytes written = write(
                new WriterWrappers.Writer(),
                w -> w.uint32Value(-1).floatValue(-0.0f).doubleValue(-0.0));
        final byte[] protoc = com.hedera.pbj.test.proto.java.WriterWrappers.newBuilder()
                .setUint32Value(com.google.protobuf.UInt32Value.of(-1))
                .setFloatValue(com.google.protobuf.FloatValue.of(-0.0f))
                .setDoubleValue(com.google.protobuf.DoubleValue.of(-0.0))
                .build()
                .toByteArray();
        assertArrayEquals(protoc, written.toByteArray());
        final WriterWrappers parsed = WriterWrappers.PROTOBUF.parse(written);
        assertEquals(-1, parsed.uint32Value());
        assertEquals(Float.floatToIntBits(-0.0f), Float.floatToIntBits(parsed.floatValue()));
        assertEquals(Double.doubleToLongBits(-0.0), Double.doubleToLongBits(parsed.doubleValue()));
    }

    // ------------------------------------------------------------------------------------------------- names

    @Test
    void fieldsNamedLikeWriterMethods() throws Exception {
        final WriterLeaf leaf = WriterLeaf.newBuilder().id(1).build();
        final WriterNames expected = WriterNames.newBuilder()
                .length(5)
                .offset(leaf)
                .end(WriterLeaf.DEFAULT)
                .start(1, 2)
                .field("f")
                .build();
        assertWrites(WriterNames.PROTOBUF, expected, write(new WriterNames.Writer(), w -> {
            w.lengthField(5).offsetField().id(1);
            w.endField().end();
            w.startField(new int[] {1, 2}, 0, 2).field("f");
        }));
    }

    // ------------------------------------------------------------------------------------------ misuse checks

    @Test
    void fieldsOutOfOrderOrRepeatedThrow() {
        final WriterScalars.Writer w = new WriterScalars.Writer().start(new ProtoWriteBuffer(16));
        w.int64Value(1);
        assertTrue(assertThrows(IllegalStateException.class, () -> w.int32Value(1))
                .getMessage()
                .contains("Field 1 written after field 2"));
        // a singular field, even skipped as a default, can't be written twice
        assertThrows(IllegalStateException.class, () -> w.int64Value(0));
        // nor can a packed field
        final WriterRepeated.Writer r = new WriterRepeated.Writer().start(new ProtoWriteBuffer(16));
        r.int32Values(new int[] {1}, 0, 1);
        assertThrows(IllegalStateException.class, () -> r.int32Values().add(1));
        // repeated elements must be consecutive
        r.addStringValues("a").addBytesValues(Bytes.EMPTY);
        assertThrows(IllegalStateException.class, () -> r.addStringValues("b"));
    }

    @Test
    void useAfterEndThrows() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final WriterRoot.Writer root = new WriterRoot.Writer().start(buffer);
        final WriterScalars.Writer scalars = root.scalars();
        scalars.int32Value(1);
        root.end();
        assertFalse(scalars.isOpen());
        assertThrows(IllegalStateException.class, () -> scalars.int64Value(1));
        assertThrows(IllegalStateException.class, () -> root.names());
        assertThrows(IllegalStateException.class, root::end);
        assertTrue(assertThrows(IllegalStateException.class, () -> new WriterRoot.Writer().scalars())
                .getMessage()
                .contains("not been started"));
    }

    @Test
    void autoCloseEndsChildrenAtTheNextCallOnTheParent() throws Exception {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final WriterRoot.Writer root = new WriterRoot.Writer().start(buffer);
        final WriterRepeated.Writer repeated = root.repeated();
        final WriterLeaf.Writer leaf = repeated.addLeaves().id(1);
        // writing to the grandparent ends the leaf and the repeated message
        final WriterChoice.Writer choice = root.choice();
        assertFalse(leaf.isOpen());
        assertFalse(repeated.isOpen());
        assertThrows(IllegalStateException.class, () -> leaf.stamp(1));
        assertThrows(IllegalStateException.class, () -> repeated.addLeaves());
        choice.intChoice(1);
        root.end();
        assertFalse(choice.isOpen());
        final WriterRoot expected = WriterRoot.newBuilder()
                .repeated(WriterRepeated.newBuilder()
                        .leaves(WriterLeaf.newBuilder().id(1).build())
                        .build())
                .choice(WriterChoice.newBuilder().intChoice(1).build())
                .build();
        assertWrites(WriterRoot.PROTOBUF, expected, buffer.toBytes());
    }

    @Test
    void writeFromWritesEverythingOnce() throws Exception {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final WriterLeaf leaf = WriterLeaf.newBuilder().id(1).build();
        final WriterLeaf.Writer w = new WriterLeaf.Writer().start(buffer);
        w.id(1);
        assertThrows(IllegalStateException.class, () -> w.writeFrom(leaf));
        buffer.reset();
        w.start(buffer).writeFrom(leaf);
        assertTrue(assertThrows(IllegalStateException.class, () -> w.stamp(1))
                .getMessage()
                .contains("after writeFrom"));
        w.end();
        assertWrites(WriterLeaf.PROTOBUF, leaf, buffer.toBytes());
    }

    // ------------------------------------------------------------------------------ nesting and item ranges

    @ParameterizedTest
    @EnumSource(LengthReservation.class)
    void nestedLengthsAtEveryVarintBoundary(final LengthReservation reservation) throws Exception {
        for (final int length : new int[] {0, 1, 120, 121, 122, 127, 128, 16_383, 16_384, 2_100_000}) {
            final byte[] payload = new byte[length];
            new Random(length).nextBytes(payload);
            final WriterNode expected = WriterNode.newBuilder()
                    .child(WriterNode.newBuilder().payload(Bytes.wrap(payload)).build())
                    .value(1)
                    .build();
            final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1, reservation);
            final WriterNode.Writer root = new WriterNode.Writer().start(buffer).value(1);
            final WriterNode.Writer child = root.child().payload(payload, 0, length);
            child.end();
            root.end();
            assertWrites(WriterNode.PROTOBUF, expected, buffer.toBytes());
            assertEquals(WriterNode.PROTOBUF.toBytes(expected.child()), buffer.bytes(child.offset(), child.length()));
        }
    }

    @Test
    void deepNesting() throws Exception {
        WriterNode expected = WriterNode.newBuilder().value(-1).build();
        for (int i = 0; i < 100; i++) {
            expected = WriterNode.newBuilder().child(expected).value(i).build();
        }
        // writeFrom through a chain of child writers 100 deep, beyond the buffer's initial stack of open messages
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        new WriterNode.Writer().start(buffer).writeFrom(expected).end();
        assertWrites(WriterNode.PROTOBUF, expected, buffer.toBytes());
        // and by hand, leaving every level to be ended by the root
        final ProtoWriteBuffer byHand = new ProtoWriteBuffer(1);
        final WriterNode.Writer root = new WriterNode.Writer().start(byHand);
        WriterNode.Writer level = root;
        for (int i = 99; i >= 0; i--) {
            level = level.value(i).child();
        }
        level.value(-1);
        root.end();
        assertEquals(buffer.toBytes(), byHand.toBytes());
    }

    @Test
    void endedItemsKeepTheirBytesWhileLaterOnesAreWritten() throws Exception {
        // children of a top-level message, ended one by one, as block items are in a block
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1, LengthReservation.ONE_BYTE);
        final WriterNode.Writer root = new WriterNode.Writer().start(buffer);
        final List<WriterNode> items = new ArrayList<>();
        final List<int[]> ranges = new ArrayList<>();
        final Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            final byte[] payload = new byte[random.nextInt(i * 50 + 1)];
            random.nextBytes(payload);
            final WriterNode item = WriterNode.newBuilder()
                    .value(i)
                    .children(
                            WriterNode.newBuilder().payload(Bytes.wrap(payload)).build())
                    .build();
            items.add(item);
            final WriterNode.Writer child = root.addChildren().writeFrom(item);
            child.end();
            ranges.add(new int[] {child.offset(), child.length()});
            // hashing or sending an item can happen as soon as it ends
            assertEquals(WriterNode.PROTOBUF.toBytes(item), buffer.bytes(child.offset(), child.length()));
        }
        root.end();
        // and every range is still right once the message is complete
        for (int i = 0; i < items.size(); i++) {
            assertEquals(WriterNode.PROTOBUF.toBytes(items.get(i)), buffer.bytes(ranges.get(i)[0], ranges.get(i)[1]));
        }
        assertWrites(
                WriterNode.PROTOBUF, WriterNode.newBuilder().children(items).build(), buffer.toBytes());
    }

    @Test
    void messagesThatContainEachOther() throws Exception {
        final WriterPairA pair = WriterPairA.newBuilder()
                .name("a")
                .b(WriterPairB.newBuilder()
                        .a(WriterPairA.newBuilder().name("inner").build())
                        .as(
                                WriterPairA.DEFAULT,
                                WriterPairA.newBuilder().name("x").build())
                        .build())
                .build();
        final WriterTriX tri = WriterTriX.newBuilder()
                .y(WriterTriY.newBuilder()
                        .z(WriterTriZ.newBuilder()
                                .x(WriterTriX.newBuilder()
                                        .y(WriterTriY.newBuilder()
                                                .z(WriterTriZ.newBuilder()
                                                        .text("end")
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();
        final CustomWriterCycles expected = CustomWriterCycles.newBuilder()
                .node(WriterNode.newBuilder().value(1).build())
                .pair(pair)
                .tri(tri)
                .build();
        final Bytes written = write(new CustomWriterCycles.Writer(), w -> w.writeFrom(expected));
        assertWrites(CustomWriterCycles.PROTOBUF, expected, written);
        // by hand
        final Bytes byHand = write(new CustomWriterCycles.Writer(), w -> {
            w.node().value(1);
            final WriterPairB.Writer b = w.pair().b();
            b.a().name("inner");
            b.addAs().end();
            b.addAs().name("x");
            // pair's name comes after b: field order is per message, so this closes b first
            final WriterTriZ.Writer z = w.tri().y().z();
            z.x().y().z().text("end");
        });
        // the pair's name was not written by hand, so compare with that
        assertWrites(
                CustomWriterCycles.PROTOBUF,
                expected.copyBuilder().pair(pair.copyBuilder().name("").build()).build(),
                byHand);
    }

    // ------------------------------------------------------------------------------------- other modules

    @Test
    void fieldsOfMessagesFromAnotherModule() throws Exception {
        final SharedLeaf leaf = SharedLeaf.newBuilder().id(1).label("one").build();
        final CustomWriterHolder expected = CustomWriterHolder.newBuilder()
                .id(5)
                .leaf(leaf)
                .leaves(leaf, SharedLeaf.DEFAULT)
                .leafChoice(SharedLeaf.DEFAULT)
                .leafMap(new TreeMap<>(Map.of("a", leaf, "b", SharedLeaf.DEFAULT)))
                .local(CustomWriterLocal.newBuilder().name("local").build())
                .build();
        assertWrites(CustomWriterHolder.PROTOBUF, expected, write(new CustomWriterHolder.Writer(), w -> w.id(5)
                .leaf(leaf)
                .addLeaves(leaf)
                .addLeavesRaw(Bytes.EMPTY)
                .leafChoice((SharedLeaf) null)
                .putLeafMap("a", leaf)
                .putLeafMap("b", SharedLeaf.DEFAULT)
                .local()
                .name("local")));
        assertWrites(
                CustomWriterHolder.PROTOBUF,
                expected,
                write(new CustomWriterHolder.Writer(), w -> w.writeFrom(expected)));
    }

    // ------------------------------------------------------------------------------------------------- fuzz

    /** A random WriterScalars, with each field set or left at its default. */
    private static WriterScalars randomScalars(final Random random) {
        final WriterScalars.Builder b = WriterScalars.newBuilder();
        if (random.nextBoolean()) b.int32Value(random.nextInt());
        if (random.nextBoolean()) b.int64Value(random.nextLong());
        if (random.nextBoolean()) b.uint32Value(random.nextInt());
        if (random.nextBoolean()) b.uint64Value(random.nextLong());
        if (random.nextBoolean()) b.sint32Value(random.nextInt());
        if (random.nextBoolean()) b.sint64Value(random.nextLong());
        if (random.nextBoolean()) b.fixed32Value(random.nextInt());
        if (random.nextBoolean()) b.fixed64Value(random.nextLong());
        if (random.nextBoolean()) b.sfixed32Value(random.nextInt());
        if (random.nextBoolean()) b.sfixed64Value(random.nextLong());
        if (random.nextBoolean()) b.floatValue(random.nextFloat() - 0.5f);
        if (random.nextBoolean()) b.doubleValue(random.nextDouble() - 0.5);
        if (random.nextBoolean()) b.boolValue(true);
        if (random.nextBoolean()) b.stringValue("s" + random.nextInt());
        if (random.nextBoolean()) b.bytesValue(Bytes.wrap(new byte[random.nextInt(300)]));
        if (random.nextBoolean()) b.color(WriterColor.values()[random.nextInt(3)]);
        if (random.nextBoolean()) b.farValue(random.nextInt());
        return b.build();
    }

    /** One call writing a field of a WriterScalars, by field number, through a random variant of the API. */
    private static void writeScalar(
            final WriterScalars.Writer w, final WriterScalars s, final int field, final Random random) {
        switch (field) {
            case 1 -> w.int32Value(s.int32Value());
            case 2 -> w.int64Value(s.int64Value());
            case 3 -> w.uint32Value(s.uint32Value());
            case 4 -> w.uint64Value(s.uint64Value());
            case 5 -> w.sint32Value(s.sint32Value());
            case 6 -> w.sint64Value(s.sint64Value());
            case 7 -> w.fixed32Value(s.fixed32Value());
            case 8 -> w.fixed64Value(s.fixed64Value());
            case 9 -> w.sfixed32Value(s.sfixed32Value());
            case 10 -> w.sfixed64Value(s.sfixed64Value());
            case 11 -> w.floatValue(s.floatValue());
            case 12 -> w.doubleValue(s.doubleValue());
            case 13 -> w.boolValue(s.boolValue());
            case 14 -> {
                if (random.nextBoolean()) {
                    w.stringValue(s.stringValue());
                } else {
                    final byte[] utf8 = s.stringValue().getBytes(StandardCharsets.UTF_8);
                    w.stringValue(utf8, 0, utf8.length);
                }
            }
            case 15 -> {
                if (random.nextBoolean()) {
                    w.bytesValue(s.bytesValue());
                } else {
                    final byte[] bytes = s.bytesValue().toByteArray();
                    w.bytesValue(bytes, 0, bytes.length);
                }
            }
            case 16 -> {
                if (random.nextBoolean()) {
                    w.color(s.color());
                } else {
                    w.colorProtoOrdinal(s.colorProtoOrdinal());
                }
            }
            default -> w.farValue(s.farValue());
        }
    }

    private static final int[] SCALAR_FIELDS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 536870911};

    /**
     * Random valid call sequences, built by walking WriterRoot's schema with random choices between the ways of writing
     * each field (child writer, object, pre-encoded bytes; arrays or element by element; explicit or automatic end),
     * give the model's bytes. Random invalid sequences (fields out of order, or written twice) throw at the first
     * invalid call, and never produce bytes.
     */
    @Test
    void fuzzCallSequences() throws Exception {
        final Random random = new Random(1018);
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        final WriterRoot.Writer rootWriter = new WriterRoot.Writer();
        for (int iteration = 0; iteration < 2_000; iteration++) {
            final WriterScalars scalars = randomScalars(random);
            final List<WriterLeaf> leaves = new ArrayList<>();
            for (int i = random.nextInt(4); i > 0; i--) {
                leaves.add(WriterLeaf.newBuilder()
                        .id(random.nextInt())
                        .stamp(random.nextLong())
                        .build());
            }
            final WriterRepeated repeated = WriterRepeated.newBuilder()
                    .leaves(leaves)
                    .int64Values(random.longs(random.nextInt(5)).boxed().toList())
                    .build();
            final WriterRoot root = WriterRoot.newBuilder()
                    .scalars(random.nextBoolean() ? scalars : null)
                    .repeated(random.nextBoolean() ? repeated : null)
                    .build();
            buffer.reset();
            final WriterRoot.Writer w = rootWriter.start(buffer);
            if (root.scalars() != null) {
                switch (random.nextInt(3)) {
                    case 0 -> w.scalars(root.scalars());
                    case 1 -> w.scalarsRaw(WriterScalars.PROTOBUF.toBytes(root.scalars()));
                    default -> {
                        final WriterScalars.Writer sw = w.scalars();
                        for (final int field : SCALAR_FIELDS) {
                            writeScalar(sw, root.scalars(), field, random);
                        }
                        if (random.nextBoolean()) {
                            sw.end();
                        }
                    }
                }
            }
            if (root.repeated() != null) {
                final WriterRepeated.Writer rw = w.repeated();
                final List<Long> longs = root.repeated().int64Values();
                if (random.nextBoolean()) {
                    rw.int64Values(longs.stream().mapToLong(Long::longValue).toArray(), 0, longs.size());
                } else {
                    final var packed = rw.int64Values();
                    longs.forEach(packed::add);
                }
                for (final WriterLeaf leaf : root.repeated().leaves()) {
                    switch (random.nextInt(3)) {
                        case 0 -> rw.addLeaves(leaf);
                        case 1 -> rw.addLeavesRaw(WriterLeaf.PROTOBUF.toBytes(leaf));
                        default -> rw.addLeaves().id(leaf.id()).stamp(leaf.stamp());
                    }
                }
            }
            w.end();
            assertEquals(WriterRoot.PROTOBUF.toBytes(root), buffer.toBytes(), "iteration " + iteration);
        }
        // invalid sequences: shuffled, and sometimes with a field repeated
        final WriterScalars.Writer sw = new WriterScalars.Writer();
        for (int iteration = 0; iteration < 2_000; iteration++) {
            final WriterScalars scalars = randomScalars(random);
            final List<Integer> fields =
                    new ArrayList<>(Arrays.stream(SCALAR_FIELDS).boxed().toList());
            java.util.Collections.shuffle(fields, random);
            if (random.nextBoolean()) {
                fields.add(random.nextInt(fields.size()), fields.get(random.nextInt(fields.size())));
            }
            buffer.reset();
            sw.start(buffer);
            int last = 0;
            boolean threw = false;
            for (final int field : fields) {
                if (field <= last) {
                    // the first call out of order throws, before writing anything
                    final int before = buffer.length();
                    assertThrows(IllegalStateException.class, () -> writeScalar(sw, scalars, field, random));
                    assertEquals(before, buffer.length());
                    threw = true;
                    break;
                }
                writeScalar(sw, scalars, field, random);
                last = field;
            }
            if (!threw) {
                sw.end();
                assertEquals(WriterScalars.PROTOBUF.toBytes(scalars), buffer.toBytes());
            }
        }
    }
}
