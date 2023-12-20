package com.hedera.pbj.intergration.test;

import com.hedera.hapi.node.base.tests.AccountIDTest;
import com.hedera.hapi.node.base.tests.ContractIDTest;
import com.hedera.pbj.integration.fuzz.FuzzTest;
import com.hedera.pbj.integration.fuzz.FuzzTestResult;
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
    private static final double THRESHOLD = .8;

    /**
     * A percentage threshold for the pass rate across tests
     * for all model objects.
     *
     * The fuzz test as a whole is considered passed
     * if that many individual model tests pass.
     */
    private static final double PASS_RATE_THRESHOLD = .9;

    /**
     * A threshold for the mean value of the shares of DESERIALIZATION_FAILED
     * outcomes across tests for all model objects.
     *
     * The fuzz test as a whole is considered passed
     * if the mean value of all the individual DESERIALIZATION_FAILED
     * shares is greater than this threshold.
     */
    private static final double DESERIALIZATION_FAILED_MEAN_THRESHOLD = .9;

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

    private static final List<List<?>> MODEL_TEST_OBJECTS = List.of(
            AccountIDTest.ARGUMENTS,
            ContractIDTest.ARGUMENTS,
            EverythingTest.ARGUMENTS,
            HashevalTest.ARGUMENTS,
            InnerEverythingTest.ARGUMENTS,
            MessageWithStringTest.ARGUMENTS,
            TimestampTest2Test.ARGUMENTS,
            TimestampTestSeconds2Test.ARGUMENTS,
            TimestampTestSecondsTest.ARGUMENTS,
            TimestampTestTest.ARGUMENTS
    );

    private static Stream<?> objectTestCases() {
        return MODEL_TEST_OBJECTS.stream()
                .flatMap(List::stream);
    }

    private static record ResultStats(
            double passRate,
            double deserializationFailedMean
    ) {
        private static final NumberFormat PERCENTAGE_FORMAT = NumberFormat.getPercentInstance();

        boolean passed() {
            return passRate > PASS_RATE_THRESHOLD
                    && deserializationFailedMean > DESERIALIZATION_FAILED_MEAN_THRESHOLD;
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

        final List<? extends FuzzTestResult<?>> results = objectTestCases()
                // Note that we must run this stream sequentially to enable
                // reproducing the tests for a given random seed.
                .map(object -> FuzzTest.fuzzTest(object, THRESHOLD, random))
                .peek(result -> { if (debug) System.out.println(result.format()); })
                .collect(Collectors.toList());

        final ResultStats resultStats = results.stream()
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

        final String statsMessage = resultStats.format();
        System.out.println(statsMessage);
        assertTrue(resultStats.passed(), statsMessage);
    }

    private Random buildRandom() {
        final boolean useRandomSeed
                = Boolean.valueOf(System.getProperty("com.hedera.pbj.intergration.test.fuzz.useRandomSeed"));
        final long seed = useRandomSeed ? new Random().nextLong() : FIXED_RANDOM_SEED;

        System.out.println("Fuzz tests are configured to use a "
                + (useRandomSeed ? "RANDOM" : "FIXED")
                + " seed for `new Random(seed)`, and the seed value for this run is: "
                + seed
        );

        return new Random(seed);
    }

}
