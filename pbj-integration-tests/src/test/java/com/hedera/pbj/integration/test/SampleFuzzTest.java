// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import com.hedera.hapi.node.base.tests.AccountIDTest;
import com.hedera.hapi.node.base.tests.ContractIDTest;
import com.hedera.pbj.integration.fuzz.Elapsed;
import com.hedera.pbj.integration.fuzz.FuzzTest;
import com.hedera.pbj.integration.fuzz.FuzzTestResult;
import com.hedera.pbj.integration.fuzz.FuzzUtil;
import com.hedera.pbj.integration.fuzz.SingleFuzzTest;
import com.hedera.pbj.integration.fuzz.SingleFuzzTestResult;
import com.hedera.pbj.test.proto.pbj.tests.EverythingTest;
import com.hedera.pbj.test.proto.pbj.tests.HashevalTest;
import com.hedera.pbj.test.proto.pbj.tests.InnerEverythingTest;
import com.hedera.pbj.test.proto.pbj.tests.MessageWithStringTest;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTest2Test;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestSeconds2Test;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestSecondsTest;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.text.NumberFormat;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * This is a sample fuzz test just to demonstrate the usage of the FuzzTest class.
 * It will be replaced with a more elaborate fuzz testing framework in the future.
 * See javadoc for FuzzTest for more details.
 *
 * Three thresholds defined at the beginning of the class below
 * determine whether an individual test for a specific model object
 * is considered passed and whether the fuzz test as a whole
 * is considered passed or failed.
 */
public class SampleFuzzTest {
    // Flip to true to print out results stats for every tested model object.
    // When false, only the fuzz test summary is printed to stdout.
    private static final boolean debug = false;

    /**
     * A percentage threshold for the share of DESERIALIZATION_FAILED outcomes
     * when running tests for a given model object.
     *
     * A test for that specific model object is considered passed
     * if random modifications of the object's payload produce
     * that many DESERIALIZATION_FAILED outcomes.
     */
    private static final double THRESHOLD = .95;

    /**
     * A percentage threshold for the pass rate across tests
     * for all model objects.
     *
     * The fuzz test as a whole is considered passed
     * if that many individual model tests pass.
     */
    private static final double PASS_RATE_THRESHOLD = 1.;

    /**
     * A threshold for the mean value of the shares of DESERIALIZATION_FAILED
     * outcomes across tests for all model objects.
     *
     * The fuzz test as a whole is considered passed
     * if the mean value of all the individual DESERIALIZATION_FAILED
     * shares is greater than this threshold.
     */
    private static final double DESERIALIZATION_FAILED_MEAN_THRESHOLD = .9829;

    /**
     * Fuzz tests are tagged with this tag to allow Gradle/JUnit
     * to disable assertions when running these tests.
     * This enables us to catch the actual codec failures.
     */
    private static final String FUZZ_TEST_TAG = "FUZZ_TEST";

    /**
     * A fixed seed for a random numbers generator when
     * we want to run the tests in a reproducible way.
     *
     * Use the randomFuzzTest Gradle target to use a random seed
     * instead, which will run the tests in a random way
     * allowing one to potentially discover new and unknown issues.
     *
     * This number is completely random. However, the threshold
     * values above may need changing if this value changes.
     */
    private static final long FIXED_RANDOM_SEED = 837582698436792L;

    private static final List<Class<?>> MODEL_TEST_CLASSES = List.of(
            AccountIDTest.class,
            ContractIDTest.class,
            EverythingTest.class,
            HashevalTest.class,
            InnerEverythingTest.class,
            MessageWithStringTest.class,
            TimestampTest2Test.class,
            TimestampTestSeconds2Test.class,
            TimestampTestSecondsTest.class,
            TimestampTestTest.class
    );

    private static record FuzzTestParams<T, P>(
            T object,
            Class<P> protocModelClass
    ) {
    }

    private static Stream<? extends FuzzTestParams<?, ?>> testCases() {
        return MODEL_TEST_CLASSES.stream()
                .flatMap(clz -> {
                    final Class<?> protocModelClass = FuzzUtil.getStaticFieldValue(clz, "PROTOC_MODEL_CLASS");

                    return FuzzUtil.<List<?>>getStaticFieldValue(clz, "ARGUMENTS")
                            .stream()
                            .map(object -> new FuzzTestParams<>(
                                    object,
                                    protocModelClass
                            ));
                });
    }

    private static record ResultStats(
            double passRate,
            double deserializationFailedMean
    ) {
        private static final NumberFormat PERCENTAGE_FORMAT = NumberFormat.getPercentInstance();

        static {
            PERCENTAGE_FORMAT.setMinimumFractionDigits(2);
        }

        boolean passed() {
            return passRate >= PASS_RATE_THRESHOLD
                    && deserializationFailedMean >= DESERIALIZATION_FAILED_MEAN_THRESHOLD;
        }

        String format() {
            return "Fuzz tests " + (passed() ? "PASSED" : "FAILED")
                    + " with passRate = " + PERCENTAGE_FORMAT.format(passRate)
                    + " and deserializationFailedMean = " + PERCENTAGE_FORMAT.format(deserializationFailedMean);
        }
    }

    @Test
    @Tag(SampleFuzzTest.FUZZ_TEST_TAG)
    void fuzzTest() {
        assumeFalse(
                this.getClass().desiredAssertionStatus(),
                "Fuzz tests run with assertions disabled only. Use the fuzzTest Gradle target."
        );

        final Random random = buildRandom();

        Elapsed<ResultStats> elapsedResultStats = Elapsed.time(() -> {
            final List<? extends FuzzTestResult<?>> results = testCases()
                    // Note that we must run this stream sequentially to enable
                    // reproducing the tests for a given random seed.
                    .map(testCase -> FuzzTest.fuzzTest(
                            testCase.object(),
                            THRESHOLD,
                            random,
                            testCase.protocModelClass()))
                    .peek(result -> { if (debug) System.out.println(result.format()); })
                    .collect(Collectors.toList());

            return results.stream()
                    .map(result -> new ResultStats(
                                    result.passed() ? 1. : 0.,
                                    result.percentageMap().getOrDefault(SingleFuzzTestResult.DESERIALIZATION_FAILED, 0.)
                            )
                    )
                    .reduce(
                            (r1, r2) -> new ResultStats(
                                    r1.passRate() + r2.passRate(),
                                    r1.deserializationFailedMean() + r2.deserializationFailedMean())
                    )
                    .map(stats -> new ResultStats(
                                    stats.passRate() / (double) results.size(),
                                    stats.deserializationFailedMean() / (double) results.size()
                            )
                    )
                    .orElse(new ResultStats(0., 0.));

        });

        final String statsMessage = elapsedResultStats.result().format();
        System.out.println(statsMessage);
        System.out.println("Total number of SingleFuzzTest runs: " + SingleFuzzTest.getNumberOfRuns());
        System.out.println("Elapsed time: " + elapsedResultStats.format());

        assertTrue(elapsedResultStats.result().passed(), statsMessage);
    }

    private Random buildRandom() {
        final boolean useRandomSeed
                = Boolean.valueOf(System.getProperty("com.hedera.pbj.integration.test.fuzz.useRandomSeed"));
        final long seed = useRandomSeed ? new Random().nextLong() : FIXED_RANDOM_SEED;

        System.out.println("Fuzz tests are configured to use a "
                + (useRandomSeed ? "RANDOM" : "FIXED")
                + " seed for `new Random(seed)`, and the seed value for this run is: "
                + seed
        );

        return new Random(seed);
    }

}
