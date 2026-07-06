// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static com.hedera.pbj.runtime.Codec.DEFAULT_MAX_DEPTH;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Reproduces a bug where deeply nested Key/KeyList structures do not produce a ParseException
 * with the message "Reached maximum allowed depth" when the depth limit is exceeded.
 *
 * <p>Original failing test:
 * com.hedera.hapi.node.base.codec.KeyProtoCodecTest.deeplyNestedSerializedKeyUnderSixKiBIsRejectedByDefaultDepthLimit
 * in hiero-consensus-node.
 */
public class KeyDepthLimitTest {
    private static final int MAX_TRANSACTION_BYTES = 6 * 1024;
    private static final int PBJ_MESSAGE_FRAMES_PER_KEY_LIST_LEVEL = 2;
    private static final int DEEPEST_DEFAULT_ALLOWED_KEY_LIST_LEVELS =
            DEFAULT_MAX_DEPTH / PBJ_MESSAGE_FRAMES_PER_KEY_LIST_LEVEL;
    private static final int FIRST_KEY_LIST_LEVEL_REJECTED_BY_DEFAULT_DEPTH =
            DEEPEST_DEFAULT_ALLOWED_KEY_LIST_LEVELS + 1;
    private static final byte KEY_LIST_TAG = 50;
    private static final byte KEY_LIST_KEYS_TAG = 10;
    private static final byte ED25519_TAG = 18;
    private static final byte[] ED25519_KEY = lengthDelimited(ED25519_TAG, new byte[32]);

    /**
     * A deeply nested Key/KeyList structure under 6KiB must be rejected by the default depth limit,
     * and the ParseException message must contain "Reached maximum allowed depth".
     *
     * <p>This test currently FAILS because the ParseException is thrown with an empty message
     * instead of the expected "Reached maximum allowed depth" message.
     */
    @Test
    void deeplyNestedSerializedKeyUnderSixKiBIsRejectedByDefaultDepthLimit() {
        final var serializedKey = deepestKeyListNestUnder(MAX_TRANSACTION_BYTES);

        assertTrue(serializedKey.bytes().length <= MAX_TRANSACTION_BYTES);
        assertTrue(serializedKey.nestingLevels() > FIRST_KEY_LIST_LEVEL_REJECTED_BY_DEFAULT_DEPTH);

        final ParseException thrown = assertThrows(
                ParseException.class,
                () -> Key.PROTOBUF.parse(
                        Bytes.wrap(serializedKey.bytes()).toReadableSequentialData(),
                        false,
                        false,
                        DEFAULT_MAX_DEPTH,
                        MAX_TRANSACTION_BYTES));
        assertTrue(
                thrown.getMessage() != null && thrown.getMessage().contains("Reached maximum allowed depth"),
                "Expected ParseException message to contain 'Reached maximum allowed depth', but got: "
                        + thrown.getMessage());
    }

    @Test
    void pbjParserRejectsFirstKeyListLevelBeyondDefaultDepthWithParseException() {
        final var serializedKey = keyListNest(FIRST_KEY_LIST_LEVEL_REJECTED_BY_DEFAULT_DEPTH);

        assertTrue(serializedKey.bytes().length <= MAX_TRANSACTION_BYTES);
        final ParseException thrown =
                assertThrows(ParseException.class, () -> Key.PROTOBUF.parse(Bytes.wrap(serializedKey.bytes())));
        assertTrue(
                thrown.getMessage() != null && thrown.getMessage().contains("Reached maximum allowed depth"),
                "Expected ParseException message to contain 'Reached maximum allowed depth', but got: "
                        + thrown.getMessage());
    }

    @Test
    void deeplyNestedSerializedKeyUnderSixKiBCanOverflowUnboundedParserStack() throws InterruptedException {
        final var serializedKey = deepestKeyListNestUnder(MAX_TRANSACTION_BYTES);

        assertTrue(serializedKey.bytes().length <= MAX_TRANSACTION_BYTES);
        assertTrue(serializedKey.nestingLevels() > FIRST_KEY_LIST_LEVEL_REJECTED_BY_DEFAULT_DEPTH);

        final Throwable result = parseWithUnboundedDepthOnConstrainedStack(serializedKey.bytes());
        assertInstanceOf(StackOverflowError.class, result);
    }

    private static Throwable parseWithUnboundedDepthOnConstrainedStack(final byte[] serializedKey)
            throws InterruptedException {
        return parseOnStack(serializedKey, Integer.MAX_VALUE, 64 * 1024);
    }

    private static Throwable parseOnStack(final byte[] serializedKey, final int maxDepth, final int stackSize)
            throws InterruptedException {
        final var thrown = new AtomicReference<Throwable>();
        final var thread = new Thread(
                null,
                () -> {
                    try {
                        Key.PROTOBUF.parse(
                                Bytes.wrap(serializedKey).toReadableSequentialData(),
                                false,
                                false,
                                maxDepth,
                                MAX_TRANSACTION_BYTES);
                    } catch (final Throwable t) {
                        thrown.set(t);
                    }
                },
                "pbj-key-stack-overflow-test",
                stackSize);
        thread.setUncaughtExceptionHandler((ignored, t) -> thrown.set(t));

        thread.start();
        thread.join(Duration.ofSeconds(10).toMillis());
        if (thread.isAlive()) {
            thread.interrupt();
            fail("Timed out while parsing deeply nested key");
        }
        return thrown.get();
    }

    private static SerializedKey deepestKeyListNestUnder(final int maxBytes) {
        byte[] bytes = ED25519_KEY;
        int nestingLevels = 0;
        while (true) {
            final var next = keyWithSingleNestedKeyList(bytes);
            if (next.length > maxBytes) {
                return new SerializedKey(bytes, nestingLevels);
            }
            bytes = next;
            nestingLevels++;
        }
    }

    private static SerializedKey keyListNest(final int nestingLevels) {
        byte[] bytes = ED25519_KEY;
        for (int i = 0; i < nestingLevels; i++) {
            bytes = keyWithSingleNestedKeyList(bytes);
        }
        return new SerializedKey(bytes, nestingLevels);
    }

    private static byte[] keyWithSingleNestedKeyList(final byte[] nestedKey) {
        final var keyList = lengthDelimited(KEY_LIST_KEYS_TAG, nestedKey);
        return lengthDelimited(KEY_LIST_TAG, keyList);
    }

    private static byte[] lengthDelimited(final byte tag, final byte[] contents) {
        final var out = new ByteArrayOutputStream(1 + varIntSize(contents.length) + contents.length);
        out.write(tag);
        writeVarInt(out, contents.length);
        out.writeBytes(contents);
        return out.toByteArray();
    }

    private static void writeVarInt(final ByteArrayOutputStream out, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    private record SerializedKey(byte[] bytes, int nestingLevels) {}
}
