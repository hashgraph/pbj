// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.MixedFastOuter;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.codec.MixedFastOuterProtoCodec;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The array parser must accept and reject exactly the varints the stream parser does, so a message parses the same
 * way whichever parser the input's type selects. A ten-byte varint only has room for bit 63 in its tenth byte.
 */
final class ArrayParserVarintTest {

    /** Parses with the generated stream parser, over the same array. */
    private static MixedFastOuter parseStream(final byte[] bytes) throws ParseException {
        return MixedFastOuterProtoCodec.parseFrom(
                BufferedData.wrap(bytes), false, false, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE);
    }

    /** Parses with the array parser, which MixedFastOuter has through pbj.fast_parse. */
    private static MixedFastOuter parseArray(final byte[] bytes) throws ParseException {
        return MixedFastOuter.PROTOBUF.parse(Bytes.wrap(bytes));
    }

    /**
     * @param tagAndPrefix the bytes before the varint
     * @param tenth the varint's tenth byte, after nine continuation bytes
     * @param suffix the bytes after the varint
     */
    private static byte[] tenByteVarint(final byte[] tagAndPrefix, final int tenth, final byte[] suffix) {
        final byte[] bytes = new byte[tagAndPrefix.length + 10 + suffix.length];
        System.arraycopy(tagAndPrefix, 0, bytes, 0, tagAndPrefix.length);
        Arrays.fill(bytes, tagAndPrefix.length, tagAndPrefix.length + 9, (byte) 0x80);
        bytes[tagAndPrefix.length + 9] = (byte) tenth;
        System.arraycopy(suffix, 0, bytes, tagAndPrefix.length + 10, suffix.length);
        return bytes;
    }

    private static void assertSameOutcome(final byte[] bytes, final String description) {
        MixedFastOuter expected;
        try {
            expected = parseStream(bytes);
        } catch (final ParseException e) {
            assertThrows(ParseException.class, () -> parseArray(bytes), description);
            return;
        }
        try {
            assertEquals(expected, parseArray(bytes), description);
        } catch (final ParseException e) {
            throw new AssertionError(description + ": the array parser rejected what the stream parser accepted", e);
        }
    }

    @Test
    void tenByteFieldValuesParseTheSameInBothParsers() {
        // id = 1 (int64), then name = 3 so the varint is not the last bytes of the input
        final byte[] name = {0x1A, 0x01, 'x'};
        for (int tenth = 0; tenth < 256; tenth++) {
            assertSameOutcome(tenByteVarint(new byte[] {0x08}, tenth, name), "tenth byte " + tenth);
            assertSameOutcome(tenByteVarint(new byte[] {0x08}, tenth, new byte[0]), "last, tenth byte " + tenth);
        }
    }

    @Test
    void anEvenTenthByteIsRejected() {
        // nine continuation bytes and a tenth byte of zero: an over-long varint that leaves bit 63 unset
        final byte[] bytes = tenByteVarint(new byte[] {0x08}, 0x00, new byte[] {0x1A, 0x01, 'x'});
        assertThrows(ParseException.class, () -> parseStream(bytes));
        assertThrows(ParseException.class, () -> parseArray(bytes));
    }

    @Test
    void tenByteTagsParseTheSameInBothParsers() {
        for (int tenth = 0; tenth < 256; tenth++) {
            assertSameOutcome(tenByteVarint(new byte[0], tenth, new byte[] {0x00}), "tag, tenth byte " + tenth);
        }
    }
}
