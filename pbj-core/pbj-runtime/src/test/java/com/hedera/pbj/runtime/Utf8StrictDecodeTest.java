// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link Utf8Tools#decodeUtf8Strict} accepts and rejects exactly what the JDK decoder does in REPORT mode. */
final class Utf8StrictDecodeTest {
    private final CharsetDecoder jdk = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    /** The JDK's result, or null if it rejects the bytes. */
    private String jdkDecode(final byte[] bytes, final int length) {
        try {
            return jdk.reset().decode(ByteBuffer.wrap(bytes, 0, length)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private void check(final byte[] bytes, final int length) {
        final String expected = jdkDecode(bytes, length);
        assertEquals(expected != null, Utf8Tools.isWellFormedUtf8(bytes, 0, length), () -> hex(bytes, length));
        if (expected == null) {
            assertThrows(MalformedProtobufException.class, () -> Utf8Tools.decodeUtf8Strict(bytes, 0, length));
        } else {
            try {
                assertEquals(expected, Utf8Tools.decodeUtf8Strict(bytes, 0, length));
            } catch (MalformedProtobufException e) {
                throw new AssertionError(hex(bytes, length), e);
            }
        }
    }

    private static String hex(final byte[] bytes, final int length) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append("%02X ".formatted(bytes[i]));
        return sb.toString();
    }

    @Test
    void everySequenceOfUpToThreeBytes() {
        final byte[] bytes = new byte[3];
        for (int a = 0; a < 256; a++) {
            bytes[0] = (byte) a;
            check(bytes, 1);
            for (int b = 0; b < 256; b++) {
                bytes[1] = (byte) b;
                check(bytes, 2);
                // three-byte sequences only need checking from lead bytes that can start one, plus a sample of others
                if (a >= 0xE0 || (a & 0x0F) == 0) {
                    for (int c = 0; c < 256; c++) {
                        bytes[2] = (byte) c;
                        check(bytes, 3);
                    }
                }
            }
        }
    }

    @Test
    void fourByteSequencesAroundTheirLimits() {
        final Random random = new Random(7);
        final byte[] bytes = new byte[4];
        for (int a = 0xEF; a <= 0xF8; a++) {
            for (int b = 0x70; b <= 0xC0; b++) {
                for (int i = 0; i < 200; i++) {
                    bytes[0] = (byte) a;
                    bytes[1] = (byte) b;
                    bytes[2] = (byte) (0x70 + random.nextInt(0x60));
                    bytes[3] = (byte) (0x70 + random.nextInt(0x60));
                    check(bytes, 4);
                }
            }
        }
    }

    @Test
    void randomStrings() {
        final Random random = new Random(11);
        final byte[] bytes = new byte[64];
        for (int i = 0; i < 200_000; i++) {
            final int length = random.nextInt(bytes.length);
            for (int j = 0; j < length; j++) {
                // mostly valid text, with some random bytes mixed in
                bytes[j] = (byte) (random.nextInt(10) == 0 ? random.nextInt(256) : random.nextInt(128));
            }
            final String text = randomText(random);
            final byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            check(bytes, length);
            check(utf8, utf8.length);
        }
    }

    private static String randomText(final Random random) {
        final StringBuilder sb = new StringBuilder();
        for (int i = random.nextInt(12); i > 0; i--) {
            sb.appendCodePoint(
                    switch (random.nextInt(4)) {
                        case 0 -> random.nextInt(0x80);
                        case 1 -> 0x80 + random.nextInt(0x780);
                        case 2 -> 0xE000 + random.nextInt(0x1000);
                        default -> 0x10000 + random.nextInt(0x10FFFF - 0x10000);
                    });
        }
        return sb.toString();
    }

    @Test
    void emptyIsTheEmptyString() throws MalformedProtobufException {
        assertSame("", Utf8Tools.decodeUtf8Strict(new byte[0], 0, 0));
    }
}
