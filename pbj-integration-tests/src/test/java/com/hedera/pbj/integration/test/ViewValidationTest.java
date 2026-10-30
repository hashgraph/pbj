// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.ViewCheckDenied;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.ViewCheckInner;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.ViewCheckLeaf;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.ViewCheckOuter;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.ViewCheckWithMap;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Views validate as parsing does, with the caller's limits and strict mode, and reuse child views safely. */
final class ViewValidationTest {
    private static final int VARINT = 0;
    private static final int DELIMITED = 2;

    /** Encoded fields, built by hand to make malformed or unusual messages. */
    private static final class Fields {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Fields varint(final long value) {
            long v = value;
            while ((v & ~0x7FL) != 0) {
                out.write((int) (v & 0x7F) | 0x80);
                v >>>= 7;
            }
            out.write((int) v);
            return this;
        }

        Fields tag(final int field, final int wireType) {
            return varint(((long) field << 3) | wireType);
        }

        Fields delimited(final int field, final byte[] value) {
            tag(field, DELIMITED).varint(value.length);
            out.writeBytes(value);
            return this;
        }

        Fields delimited(final int field, final Bytes value) {
            return delimited(field, value.toByteArray());
        }

        Bytes bytes() {
            return Bytes.wrap(out.toByteArray());
        }
    }

    private static Bytes inner(final int value) {
        return ViewCheckInner.PROTOBUF.toBytes(ViewCheckInner.newBuilder()
                .value(value)
                .leaf(new ViewCheckLeaf(value * 10))
                .build());
    }

    @Test
    void strictModeRejectsUnknownFields() throws Exception {
        final Bytes bytes = new Fields().tag(1000, VARINT).varint(1).bytes();
        assertEquals(ViewCheckOuter.DEFAULT, ViewCheckOuter.PROTOBUF.parse(bytes));
        new ViewCheckOuter.View().wrap(bytes);
        assertThrows(ParseException.class, () -> ViewCheckOuter.PROTOBUF.parseStrict(bytes));
        assertThrows(ParseException.class, () -> new ViewCheckOuter.View()
                .wrap(bytes, true, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE));
    }

    @Test
    void strictModeRejectsARepeatedSingularMessage() throws Exception {
        final Bytes twice =
                new Fields().delimited(1, inner(1)).delimited(1, inner(2)).bytes();
        // not strict: the last one wins, as in parsing
        final ViewCheckOuter.View view = new ViewCheckOuter.View().wrap(twice);
        assertEquals(
                ViewCheckOuter.PROTOBUF.parse(twice).inner().value(),
                view.inner().value());
        assertEquals(2, view.inner().value());
        assertThrows(ParseException.class, () -> ViewCheckOuter.PROTOBUF.parseStrict(twice));
        assertThrows(ParseException.class, () -> new ViewCheckOuter.View()
                .wrap(twice, true, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE));

        // a oneof whose alternative is a message, after another alternative
        final Bytes oneOfTwice = new Fields()
                .delimited(3, "text".getBytes())
                .delimited(2, inner(3))
                .bytes();
        assertEquals(3, new ViewCheckOuter.View().wrap(oneOfTwice).innerChoice().value());
        assertThrows(ParseException.class, () -> ViewCheckOuter.PROTOBUF.parseStrict(oneOfTwice));
        assertThrows(ParseException.class, () -> new ViewCheckOuter.View()
                .wrap(oneOfTwice, true, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE));
    }

    @Test
    void maxSizeLimitsFieldsAndRepeatedElements() throws Exception {
        // a 100-byte ViewCheckInner: its value field 50 times
        final Fields hundredBytes = new Fields();
        for (int i = 0; i < 50; i++) {
            hundredBytes.tag(1, VARINT).varint(1);
        }
        final Bytes longName = new Fields().delimited(6, new byte[100]).bytes();
        final Bytes longString = new Fields().delimited(5, new byte[100]).bytes();
        final Bytes longMessage =
                new Fields().delimited(7, hundredBytes.bytes()).bytes();
        for (final Bytes bytes : List.of(longName, longString, longMessage)) {
            ViewCheckOuter.PROTOBUF.parse(bytes.toReadableSequentialData(), false, false, Codec.DEFAULT_MAX_DEPTH, 200);
            new ViewCheckOuter.View().wrap(bytes, false, Codec.DEFAULT_MAX_DEPTH, 200);
            assertThrows(
                    ParseException.class,
                    () -> ViewCheckOuter.PROTOBUF.parse(
                            bytes.toReadableSequentialData(), false, false, Codec.DEFAULT_MAX_DEPTH, 50));
            assertThrows(ParseException.class, () -> new ViewCheckOuter.View()
                    .wrap(bytes, false, Codec.DEFAULT_MAX_DEPTH, 50));
        }
    }

    @Test
    void maxDepthLimitsNestedViews() throws Exception {
        final Bytes bytes = new Fields().delimited(1, inner(4)).bytes();
        ViewCheckOuter.PROTOBUF.parse(bytes.toReadableSequentialData(), false, false, 2, Codec.DEFAULT_MAX_SIZE);
        assertThrows(
                ParseException.class,
                () -> ViewCheckOuter.PROTOBUF.parse(
                        bytes.toReadableSequentialData(), false, false, 1, Codec.DEFAULT_MAX_SIZE));
        assertEquals(
                40,
                new ViewCheckOuter.View()
                        .wrap(bytes, false, 2, Codec.DEFAULT_MAX_SIZE)
                        .inner()
                        .leaf()
                        .value());
        // nested messages are validated when they are read
        final ViewCheckInner.View inner = new ViewCheckOuter.View()
                .wrap(bytes, false, 1, Codec.DEFAULT_MAX_SIZE)
                .inner();
        assertThrows(ParseException.class, inner::leaf);
    }

    @Test
    void packedAndUnpackedElementsMixInOneRepeatedField() throws Exception {
        // packed [1, -2], then 3 unpacked, then packed [-4], then 5 unpacked
        final Bytes bytes = new Fields()
                .delimited(4, new Fields().varint(2).varint(3).bytes())
                .tag(4, VARINT)
                .varint(6)
                .delimited(4, new Fields().varint(7).bytes())
                .tag(4, VARINT)
                .varint(10)
                .bytes();
        assertEquals(
                List.of(1L, -2L, 3L, -4L, 5L),
                ViewCheckOuter.PROTOBUF.parse(bytes).numbers());
        final ViewCheckOuter.View view = new ViewCheckOuter.View().wrap(bytes);
        assertEquals(5, view.numbersCount());
        final List<Long> numbers = new ArrayList<>();
        for (final var it = view.numbers(); it.next(); ) {
            numbers.add(it.value());
        }
        assertEquals(List.of(1L, -2L, 3L, -4L, 5L), numbers);
    }

    @Test
    void malformedUtf8InRepeatedStringsIsRejected() {
        final Bytes bytes = new Fields()
                .delimited(5, "fine".getBytes())
                .delimited(5, new byte[] {(byte) 0xFF})
                .bytes();
        assertThrows(ParseException.class, () -> ViewCheckOuter.PROTOBUF.parse(bytes));
        assertThrows(ParseException.class, () -> new ViewCheckOuter.View().wrap(bytes));
    }

    @Test
    void nestedViewsAreIndexedOnceAndFollowTheirParent() throws Exception {
        final byte[] buffer = new Fields().delimited(1, inner(5)).bytes().toByteArray();
        final ViewCheckOuter.View view = new ViewCheckOuter.View().wrap(buffer, 0, buffer.length);
        final ViewCheckInner.View first = view.inner();
        assertSame(first, view.inner());
        assertEquals(5, view.inner().value());

        // the caller re-wraps the child view: the parent wraps it again over its own field
        first.wrap(inner(6));
        assertEquals(6, first.value());
        assertEquals(5, view.inner().value());

        // the same buffer, reused for another message of the same layout: re-wrapping the parent indexes the child
        // again
        final byte[] other = new Fields().delimited(1, inner(7)).bytes().toByteArray();
        assertEquals(buffer.length, other.length);
        System.arraycopy(other, 0, buffer, 0, buffer.length);
        view.wrap(buffer, 0, buffer.length);
        assertEquals(7, view.inner().value());
        assertEquals(70, view.inner().leaf().value());
    }

    @Test
    void messagesThatCannotHaveViewsGetNone() {
        for (final Class<?> model : List.of(ViewCheckWithMap.class, ViewCheckDenied.class)) {
            assertFalse(
                    List.of(model.getDeclaredClasses()).stream()
                            .anyMatch(c -> c.getSimpleName().equals("View")),
                    model.getName());
        }
        assertTrue(List.of(ViewCheckOuter.class.getDeclaredClasses()).stream()
                .anyMatch(c -> c.getSimpleName().equals("View")));
    }

    @Test
    void aViewRoundTripsToTheModel() throws Exception {
        final ViewCheckOuter outer = ViewCheckOuter.newBuilder()
                .inner(new ViewCheckInner(1, new ViewCheckLeaf(2)))
                .textChoice("text")
                .numbers(List.of(3L, -4L))
                .names(List.of("a", "é"))
                .name("name")
                .inners(List.of(new ViewCheckInner(5, null), ViewCheckInner.DEFAULT))
                .build();
        final Bytes bytes = ViewCheckOuter.PROTOBUF.toBytes(outer);
        final ViewCheckOuter.View view = new ViewCheckOuter.View().wrap(bytes);
        assertEquals(outer, view.toModel());
        assertEquals("text", view.textChoice());
        assertNotNull(view.inner());
        final List<String> names = new ArrayList<>();
        for (final var it = view.names(); it.next(); ) {
            names.add(it.value());
        }
        assertArrayEquals(new String[] {"a", "é"}, names.toArray());
    }
}
