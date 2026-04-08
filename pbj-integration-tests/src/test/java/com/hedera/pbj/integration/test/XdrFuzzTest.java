// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.pbj.test.proto.pbj.tests.EverythingTest;
import com.hedera.pbj.test.proto.pbj.tests.HashevalTest;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestTest;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Fuzz tests for the XDR codec. Verifies that no crashes (OOM, AIOOB, infinite loops, or
 * other non-Exception Throwables) occur when the XDR parser is fed randomly mutated byte
 * sequences.
 *
 * <p>These tests are tagged {@value FUZZ_TEST_TAG} so they only run under the
 * {@code fuzzTest} Gradle target, which disables Java assertions. Outside that target, each
 * test method aborts via {@link org.junit.jupiter.api.Assumptions#assumeFalse}.
 */
public class XdrFuzzTest {

    private static final String FUZZ_TEST_TAG = "FUZZ_TEST";
    private static final long FIXED_RANDOM_SEED = 912837465L;
    private static final int MUTATIONS_PER_OBJECT = 200;

    /**
     * Fuzz-tests the XDR codec for {@link TimestampTest}. For each test object, encodes it
     * with the XDR codec, randomly mutates one byte per iteration, and attempts to parse.
     * Parsing may succeed or throw an {@link Exception} — both outcomes are acceptable.
     * Only a non-{@link Exception} {@link Throwable} (e.g. {@link OutOfMemoryError},
     * {@link ArrayIndexOutOfBoundsError}) constitutes a test failure.
     */
    @Test
    @Tag(FUZZ_TEST_TAG)
    void fuzzXdrCodec_TimestampTest() {
        assumeFalse(
                getClass().desiredAssertionStatus(),
                "Fuzz tests run with assertions disabled only. Use the fuzzTest Gradle target.");
        final Random random = new Random(FIXED_RANDOM_SEED);
        for (final TimestampTest obj : TimestampTestTest.ARGUMENTS) {
            final byte[] buf = TimestampTest.XDR.toBytes(obj).toByteArray();
            if (buf.length == 0) continue;
            for (int i = 0; i < MUTATIONS_PER_OBJECT; i++) {
                final byte[] mutated = buf.clone();
                mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
                try {
                    TimestampTest.XDR.parse(Bytes.wrap(mutated).toReadableSequentialData());
                } catch (Exception e) {
                    // DESERIALIZATION_FAILED — expected and acceptable
                } catch (Throwable t) {
                    fail("XDR fuzz on TimestampTest threw non-Exception Throwable: " + t);
                }
            }
        }
    }

    /**
     * Fuzz-tests the XDR codec for {@link Hasheval}. For each test object, encodes it
     * with the XDR codec, randomly mutates one byte per iteration, and attempts to parse.
     * Parsing may succeed or throw an {@link Exception} — both outcomes are acceptable.
     * Only a non-{@link Exception} {@link Throwable} (e.g. {@link OutOfMemoryError},
     * {@link ArrayIndexOutOfBoundsError}) constitutes a test failure.
     */
    @Test
    @Tag(FUZZ_TEST_TAG)
    void fuzzXdrCodec_Hasheval() {
        assumeFalse(
                getClass().desiredAssertionStatus(),
                "Fuzz tests run with assertions disabled only. Use the fuzzTest Gradle target.");
        final Random random = new Random(FIXED_RANDOM_SEED);
        for (final Hasheval obj : HashevalTest.ARGUMENTS) {
            final byte[] buf = Hasheval.XDR.toBytes(obj).toByteArray();
            if (buf.length == 0) continue;
            for (int i = 0; i < MUTATIONS_PER_OBJECT; i++) {
                final byte[] mutated = buf.clone();
                mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
                try {
                    Hasheval.XDR.parse(Bytes.wrap(mutated).toReadableSequentialData());
                } catch (Exception e) {
                    // DESERIALIZATION_FAILED — expected and acceptable
                } catch (Throwable t) {
                    fail("XDR fuzz on Hasheval threw non-Exception Throwable: " + t);
                }
            }
        }
    }

    /**
     * Fuzz-tests the XDR codec for {@link Everything}. For each test object, encodes it
     * with the XDR codec, randomly mutates one byte per iteration, and attempts to parse.
     * Parsing may succeed or throw an {@link Exception} — both outcomes are acceptable.
     * Only a non-{@link Exception} {@link Throwable} (e.g. {@link OutOfMemoryError},
     * {@link ArrayIndexOutOfBoundsError}) constitutes a test failure.
     */
    @Test
    @Tag(FUZZ_TEST_TAG)
    void fuzzXdrCodec_Everything() {
        assumeFalse(
                getClass().desiredAssertionStatus(),
                "Fuzz tests run with assertions disabled only. Use the fuzzTest Gradle target.");
        final Random random = new Random(FIXED_RANDOM_SEED);
        for (final Everything obj : EverythingTest.ARGUMENTS) {
            final byte[] buf = Everything.XDR.toBytes(obj).toByteArray();
            if (buf.length == 0) continue;
            for (int i = 0; i < MUTATIONS_PER_OBJECT; i++) {
                final byte[] mutated = buf.clone();
                mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
                try {
                    Everything.XDR.parse(Bytes.wrap(mutated).toReadableSequentialData());
                } catch (Exception e) {
                    // DESERIALIZATION_FAILED — expected and acceptable
                } catch (Throwable t) {
                    fail("XDR fuzz on Everything threw non-Exception Throwable: " + t);
                }
            }
        }
    }
}
