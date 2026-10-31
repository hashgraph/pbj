// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.BoxedChoice;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.OneofColor;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.OneofLeaf;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.UnboxedChoice;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * A message with {@code pbj.oneof_storage = "unboxed"} behaves like the same message stored boxed: the same bytes,
 * getters, {@code equals}, {@code hashCode} and {@code toString}. Only {@code choice()} differs: it builds a new
 * {@code OneOf} on every call.
 */
final class OneofStorageTest {

    /** Each alternative, including defaults and an empty wrapper, and the oneof unset. */
    private static final List<Function<UnboxedChoice.Builder, UnboxedChoice.Builder>> UNBOXED = List.of(
            b -> b.text("text"),
            b -> b.text(""),
            b -> b.leaf(new OneofLeaf(7)),
            b -> b.leaf(OneofLeaf.DEFAULT),
            b -> b.wrapped("wrapped"),
            b -> b.wrapped(""),
            b -> b.color(OneofColor.ONEOF_GREEN),
            b -> b.color(OneofColor.ONEOF_RED),
            b -> b.number(-5),
            b -> b);

    private static final List<Function<BoxedChoice.Builder, BoxedChoice.Builder>> BOXED = List.of(
            b -> b.text("text"),
            b -> b.text(""),
            b -> b.leaf(new OneofLeaf(7)),
            b -> b.leaf(OneofLeaf.DEFAULT),
            b -> b.wrapped("wrapped"),
            b -> b.wrapped(""),
            b -> b.color(OneofColor.ONEOF_GREEN),
            b -> b.color(OneofColor.ONEOF_RED),
            b -> b.number(-5),
            b -> b);

    @Test
    void unboxedBehavesLikeBoxed() throws Exception {
        for (int i = 0; i < UNBOXED.size(); i++) {
            final UnboxedChoice unboxed =
                    UNBOXED.get(i).apply(UnboxedChoice.newBuilder().id(42)).build();
            final BoxedChoice boxed =
                    BOXED.get(i).apply(BoxedChoice.newBuilder().id(42)).build();
            final Bytes bytes = UnboxedChoice.PROTOBUF.toBytes(unboxed);
            assertEquals(BoxedChoice.PROTOBUF.toBytes(boxed), bytes, "case " + i);
            assertEquals(unboxed, UnboxedChoice.PROTOBUF.parse(bytes), "case " + i);
            assertEquals(boxed, BoxedChoice.PROTOBUF.parse(bytes), "case " + i);
            assertEquals(unboxed, unboxed.copyBuilder().build(), "case " + i);
            assertEquals(UnboxedChoice.JSON.toJSON(unboxed), BoxedChoice.JSON.toJSON(boxed), "case " + i);
            assertEquals(unboxed, UnboxedChoice.JSON.parse(Bytes.wrap(UnboxedChoice.JSON.toJSON(unboxed))));

            // the kind, the typed getters and the OneOf
            assertEquals(boxed.choice().kind().name(), unboxed.choiceKind().name(), "case " + i);
            assertEquals(boxed.choice().kind().name(), unboxed.choice().kind().name(), "case " + i);
            assertEquals(boxed.choice().value(), unboxed.choice().value(), "case " + i);
            assertEquals(boxed.text(), unboxed.text());
            assertEquals(boxed.leaf(), unboxed.leaf());
            assertEquals(boxed.wrapped(), unboxed.wrapped());
            assertEquals(boxed.color(), unboxed.color());
            assertEquals(boxed.number(), unboxed.number());
            assertEquals(boxed.hasText(), unboxed.hasText());

            // the same hashCode and toString, apart from the class name
            assertEquals(boxed.hashCode(), unboxed.hashCode(), "case " + i);
            assertEquals(
                    boxed.toString().replace("BoxedChoice", "Choice"),
                    unboxed.toString().replace("UnboxedChoice", "Choice"),
                    "case " + i);

            // only choice() differs: a new, equal OneOf on every call
            assertSame(boxed.choice(), boxed.choice());
            assertNotSame(unboxed.choice(), unboxed.choice());
            assertEquals(unboxed.choice(), unboxed.choice());
        }
    }

    @Test
    void equalsComparesKindAndValue() {
        final UnboxedChoice text = UnboxedChoice.newBuilder().text("a").build();
        assertEquals(text, UnboxedChoice.newBuilder().text("a").build());
        assertEquals(
                text.hashCode(), UnboxedChoice.newBuilder().text("a").build().hashCode());
        assertNotEquals(text, UnboxedChoice.newBuilder().text("b").build());
        // the same value in another alternative is a different message
        assertNotEquals(text, UnboxedChoice.newBuilder().wrapped("a").build());
        assertNotEquals(text, UnboxedChoice.DEFAULT);
        assertEquals(UnboxedChoice.ChoiceOneOfType.UNSET, UnboxedChoice.DEFAULT.choiceKind());
    }

    @Test
    void constructorsTakeAOneOfOrAKindAndValue() {
        final OneOf<UnboxedChoice.ChoiceOneOfType> leaf =
                new OneOf<>(UnboxedChoice.ChoiceOneOfType.LEAF, new OneofLeaf(3));
        final UnboxedChoice fromOneOf = new UnboxedChoice(1, leaf);
        final UnboxedChoice fromKindAndValue =
                new UnboxedChoice(1, UnboxedChoice.ChoiceOneOfType.LEAF, new OneofLeaf(3), null);
        assertEquals(fromOneOf, fromKindAndValue);
        assertEquals(new OneofLeaf(3), fromKindAndValue.leaf());

        // as for boxed oneofs, a wrapper type alternative with no value is unset: protobuf can't tell them apart
        final UnboxedChoice emptyWrapper = new UnboxedChoice(1, UnboxedChoice.ChoiceOneOfType.WRAPPED, null, null);
        assertEquals(UnboxedChoice.ChoiceOneOfType.UNSET, emptyWrapper.choiceKind());
        assertNull(emptyWrapper.wrapped());
        assertEquals(
                new BoxedChoice(1, new OneOf<>(BoxedChoice.ChoiceOneOfType.WRAPPED, null))
                        .choice()
                        .kind()
                        .name(),
                emptyWrapper.choiceKind().name());
        assertEquals(
                UnboxedChoice.ChoiceOneOfType.UNSET,
                new UnboxedChoice(1, new OneOf<>(UnboxedChoice.ChoiceOneOfType.WRAPPED, null)).choiceKind());
        assertTrue(new UnboxedChoice(1, UnboxedChoice.ChoiceOneOfType.TEXT, "t", null).hasText());
    }
}
