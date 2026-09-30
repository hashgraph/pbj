// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.PartialApplier;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.*;
import com.hedera.pbj.test.proto.pbj.tests.EverythingTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class PartialDiffTest {
    /** Generated fixtures cover HAPI state values plus PBJ's scalar/collection/presence models. */
    @TestFactory
    List<DynamicTest> roundTripsAcrossGeneratedStateTypes() throws Exception {
        final var root = Path.of(EverythingTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        final var result = new ArrayList<DynamicTest>();
        try (var files = Files.walk(root)) {
            for (var file : files.filter(p -> p.toString().endsWith("Test.class"))
                    .sorted()
                    .toList()) {
                final String name =
                        root.relativize(file).toString().replace('/', '.').replace(".class", "");
                if (!(name.startsWith("com.hedera.hapi.node.state.")
                        || name.startsWith("com.hedera.pbj.test.proto.pbj.tests."))) continue;
                final Class<?> fixture = Class.forName(name);
                try {
                    final var arguments =
                            (List<?>) fixture.getField("ARGUMENTS").get(null);
                    result.add(DynamicTest.dynamicTest(name, () -> {
                        final Random random = new Random(1234);
                        for (int i = 0; i < Math.max(30, arguments.size()); i++) {
                            final Object prior = arguments.get(i % arguments.size());
                            final Object next = arguments.get(random.nextInt(arguments.size()));
                            check(prior, next);
                            check(prior, prior.getClass().getField("DEFAULT").get(null));
                        }
                    }));
                } catch (NoSuchFieldException ignored) {
                    // Nested generated types and enum tests do not expose this fixture field.
                }
            }
        }
        assertTrue(result.size() > 40, "Must discover the state fixtures, not silently skip them");
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void check(Object prior, Object next) throws Exception {
        final Class<?> type = prior.getClass();
        final Codec<Object> codec = (Codec<Object>) type.getField("PROTOBUF").get(null);
        final var diff = type.getMethod("diff", type, type);
        final Object mask = diff.invoke(null, prior, next);
        final Class<?> maskType = diff.getReturnType();
        final Object partial = type.getMethod("partial", type, maskType).invoke(null, next, mask);
        final int[] clears =
                (int[]) type.getMethod("clearedFields", type, type, maskType).invoke(null, prior, next, mask);
        final Bytes expected = codec.toBytes(next);
        assertEquals(expected, PartialApplier.splice(codec.toBytes(prior), codec.toBytes(partial), clears));
        final Object applied =
                type.getMethod("applyPartial", type, type, int[].class).invoke(null, prior, partial, clears);
        assertEquals(next, applied);
        assertEquals(expected, codec.toBytes(applied));
        final int[] broadClears =
                (int[]) type.getMethod("clearedFields", type, maskType).invoke(null, next, mask);
        assertEquals(expected, PartialApplier.splice(codec.toBytes(prior), codec.toBytes(partial), broadClears));
        assertEquals(
                next,
                type.getMethod("applyPartial", type, type, int[].class).invoke(null, prior, partial, broadClears));
        final long saving = expected.length() - codec.toBytes(partial).length();
        final var worth = type.getMethod("worthPartial", type, maskType, int.class, int.class);
        for (int threshold : new int[] {0, 1, 32, 64, 128, 4096}) {
            assertEquals(saving >= threshold, worth.invoke(null, next, mask, threshold, 0));
            assertEquals(saving >= (long) threshold + 17, worth.invoke(null, next, mask, threshold, 17));
        }
        assertEquals(false, worth.invoke(null, next, mask, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void oneofCrossProductAndExplicitEmptyPresence() throws Exception {
        final var values = List.of(
                Everything.DEFAULT,
                Everything.newBuilder().textOneOf("").build(),
                Everything.newBuilder().int32NumberOneOf(0).build(),
                Everything.newBuilder().bytesFieldOneOf(Bytes.EMPTY).build(),
                Everything.newBuilder().subObjectOneOf(TimestampTest.DEFAULT).build(),
                Everything.newBuilder().int32BoxedOneOf(0).build(),
                Everything.newBuilder().floatNumberOneOf(-0.0f).build(),
                Everything.newBuilder().floatNumberOneOf(0.0f).build(),
                Everything.newBuilder().doubleNumberOneOf(-0.0d).build(),
                Everything.newBuilder().doubleNumberOneOf(0.0d).build(),
                Everything.newBuilder()
                        .int32Boxed(0)
                        .stringBoxed("")
                        .bytesBoxed(Bytes.EMPTY)
                        .textList("", "x", "")
                        .bytesExampleList(Bytes.EMPTY)
                        .subObject(TimestampTest.DEFAULT)
                        .build());
        for (var prior : values) for (var next : values) check(prior, next);
        final var prior = values.get(1);
        final var next = values.get(2);
        assertArrayEquals(new int[] {100016}, Everything.clearedFields(prior, next, Everything.diff(prior, next)));
        assertThrows(IllegalArgumentException.class, () -> Everything.applyPartial(prior, next, new int[0]));
    }

    @Test
    void lineageFallbackRestoredValuesAndCandidateMasks() throws Exception {
        final var prior = Everything.newBuilder().int32Number(1).text("old").build();
        final var intermediate = prior.copyBuilder().int32Number(2).build();
        final var next = intermediate.copyBuilder().int32Number(1).text("new").build();
        assertEquals(Everything.diff(prior, next.$untracked()), Everything.diff(prior, next));
        assertEquals(0, Everything.diff(prior, next) & 1);
        assertEquals(0, Everything.diff(prior, next, 1));
        check(prior, next);
        check(intermediate, next);
        check(Everything.PROTOBUF.parse(Everything.PROTOBUF.toBytes(prior)), next);
        final var wide = WideTrackedModel.DEFAULT
                .copyBuilder()
                .value64(64)
                .value65(65)
                .value70(70)
                .number(0)
                .build();
        final long[] candidates = wide.$copyBuilderChangedMask();
        candidates[1] = 0;
        assertTrue(wide.$copyBuilderFieldChanged(169));
        final var mask = WideTrackedModel.diff(WideTrackedModel.DEFAULT, wide);
        assertEquals(1L << 63, mask[0]);
        assertEquals(1L | (1L << 5) | (1L << 6), mask[1]);
        check(WideTrackedModel.DEFAULT, wide);
        check(wide, WideTrackedModel.DEFAULT);
    }

    @Test
    void rejectsUnknownFieldsAndInvalidPatches() throws Exception {
        final var bytes =
                MessageWithBytesAndString.PROTOBUF.toBytes(new MessageWithBytesAndString(Bytes.EMPTY, "unknown"));
        final var unknown =
                MessageWithBytes.PROTOBUF.parse(bytes.toReadableSequentialData(), false, true, Codec.DEFAULT_MAX_DEPTH);
        assertThrows(
                UnsupportedOperationException.class, () -> MessageWithBytes.diff(MessageWithBytes.DEFAULT, unknown));
        assertThrows(
                UnsupportedOperationException.class, () -> MessageWithBytes.diff(unknown, MessageWithBytes.DEFAULT));
        assertThrows(
                IllegalArgumentException.class,
                () -> Everything.applyPartial(
                        Everything.DEFAULT,
                        Everything.newBuilder().int32Number(1).build(),
                        new int[] {1}));
        assertThrows(
                IllegalArgumentException.class,
                () -> Everything.applyPartial(Everything.DEFAULT, Everything.DEFAULT, new int[] {99}));
        assertThrows(
                IllegalArgumentException.class,
                () -> Everything.applyPartial(Everything.DEFAULT, Everything.DEFAULT, new int[] {2, 1}));
        assertThrows(IllegalArgumentException.class, () -> Everything.worthPartial(Everything.DEFAULT, 0, -1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> WideTrackedModel.diff(WideTrackedModel.DEFAULT, WideTrackedModel.DEFAULT, new long[0]));
    }

    @Test
    void distinctNaNPayloadsSurviveEveryContainerAndNestedReplacement() throws Exception {
        final float f1 = Float.intBitsToFloat(0x7fc00001);
        final float f2 = Float.intBitsToFloat(0x7fc00002);
        final double d1 = Double.longBitsToDouble(0x7ff8000000000001L);
        final double d2 = Double.longBitsToDouble(0x7ff8000000000002L);
        final var nested1 = InnerEverything.newBuilder()
                .floatNumberList(f1)
                .doubleNumberList(d1)
                .build();
        final var nested2 = InnerEverything.newBuilder()
                .floatNumberList(f2)
                .doubleNumberList(d2)
                .build();
        final var prior = Everything.newBuilder()
                .floatNumber(f1)
                .doubleNumber(d1)
                .floatNumberList(f1)
                .doubleNumberList(d1)
                .floatBoxed(f1)
                .doubleBoxed(d1)
                .mapBoolToDouble(Map.of(true, d1))
                .floatNumberOneOf(f1)
                .innerEverything(nested1)
                .mapStringToMessage(Map.of("nested", nested1))
                .build();
        final var next = Everything.newBuilder()
                .floatNumber(f2)
                .doubleNumber(d2)
                .floatNumberList(f2)
                .doubleNumberList(d2)
                .floatBoxed(f2)
                .doubleBoxed(d2)
                .mapBoolToDouble(Map.of(true, d2))
                .floatNumberOneOf(f2)
                .innerEverything(nested2)
                .mapStringToMessage(Map.of("nested", nested2))
                .build();
        final long mask = Everything.diff(prior, next);
        final var partial = Everything.partial(next, mask);
        final int[] clear = Everything.clearedFields(prior, next, mask);
        final var expected = Everything.PROTOBUF.toBytes(next);
        assertNotEquals(Everything.PROTOBUF.toBytes(prior), expected);
        assertEquals(
                expected,
                PartialApplier.splice(Everything.PROTOBUF.toBytes(prior), Everything.PROTOBUF.toBytes(partial), clear));
        assertEquals(expected, Everything.PROTOBUF.toBytes(Everything.applyPartial(prior, partial, clear)));
    }

    @Test
    void unknownEnumOrdinalsAreRetainedAndNullEnumsEncodeAsDefault() throws Exception {
        final var type = pbj.integ.test.enumeration.reserved.pbj.integration.tests.MessageWithUnrecognizedEnum1.DEFAULT;
        final var codec =
                pbj.integ.test.enumeration.reserved.pbj.integration.tests.MessageWithUnrecognizedEnum1.PROTOBUF;
        final var future = codec.parse(Bytes.wrap(new byte[] {8, 99, 18, 3, 0, 99, 1}));
        check(type, future);
        check(future, type);
        final var prior = Everything.newBuilder().enumSuit(Suit.SPADES).build();
        final var next = Everything.newBuilder().enumSuit(null).build();
        final long mask = Everything.diff(prior, next);
        final var partial = Everything.partial(next, mask);
        final int[] clears = Everything.clearedFields(prior, next, mask);
        assertArrayEquals(new int[] {14}, clears);
        assertEquals(
                Everything.PROTOBUF.toBytes(next),
                PartialApplier.splice(
                        Everything.PROTOBUF.toBytes(prior), Everything.PROTOBUF.toBytes(partial), clears));
        assertEquals(
                Everything.PROTOBUF.toBytes(next),
                Everything.PROTOBUF.toBytes(Everything.applyPartial(prior, partial, clears)));
        assertEquals(0, Everything.diff(Everything.DEFAULT, next));
    }

    @Test
    void savingsCheckStopsBeforeNestedSizingAndNeverMeasuresTheWholeValue() throws Exception {
        final var child = TimestampTest.newBuilder().seconds(5).build();
        final var next = Everything.newBuilder()
                .int64Number(Long.MAX_VALUE)
                .subObject(child)
                .build();
        final var childCache = TimestampTest.class.getDeclaredField("$protobufEncodedSize");
        final var parentCache = Everything.class.getDeclaredField("$protobufEncodedSize");
        childCache.setAccessible(true);
        parentCache.setAccessible(true);
        assertEquals(-1, childCache.getInt(child));
        assertTrue(Everything.worthPartial(next, 0, 1, 0));
        assertEquals(-1, childCache.getInt(child));
        assertEquals(-1, parentCache.getInt(next));
    }

    @Test
    void nestedAndCollectionChangesReplaceTheWholeTopLevelField() throws Exception {
        final var prior = Everything.newBuilder()
                .subObject(TimestampTest.newBuilder().seconds(1).nanos(2))
                .mapInt32ToString(Map.of(1, "one", 2, "two"))
                .textList("a", "b")
                .build();
        final var next = prior.copyBuilder()
                .subObject(prior.subObject().copyBuilder().nanos(3))
                .mapInt32ToString(Map.of(1, "changed"))
                .textList("a", "b", "c")
                .build();
        final var partial = Everything.partial(next, Everything.diff(prior, next));
        assertSame(next.subObject(), partial.subObject());
        assertEquals(next.textList(), partial.textList());
        assertEquals(next.mapInt32ToString(), partial.mapInt32ToString());
        check(prior, next);
    }
}
