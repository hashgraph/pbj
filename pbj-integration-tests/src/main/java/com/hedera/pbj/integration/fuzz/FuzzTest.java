package com.hedera.pbj.integration.fuzz;

import com.hedera.pbj.runtime.Codec;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A fuzz test runner for a single object/codec.
 * <p>
 * This class exposes a single public static method that runs a comprehensive fuzz test
 * for a given object and its codec. Note that the codec must be valid for the given
 * object (see the SingleFuzzTest javadoc for more details.)
 * <p>
 * Ultimately, the result of the test is a map that describes how often a particular
 * SingleFuzzTest outcome occurred in percentages. The provided threshold specifies
 * the percentage of the DESERIALIZATION_FAILED outcome for the test to be considered
 * as passed.
 * <p>
 * The method returns a FuzzTestResult record that describes the results in full.
 */
public class FuzzTest {

    /**
     * Run a fuzz test for a given object and codec, and use the provided threshold
     * for the most desirable DESERIALIZATION_FAILED outcome to determine
     * if the test passed or not.
     */
    public static <T> FuzzTestResult<T> fuzzTest(final T object, final Codec<T> codec, final double threshold) {
        final Random random = new Random();
        final int repeatCount = estimateRepeatCount(object, codec);

        final Map<SingleFuzzTestResult, Long> resultCounts = IntStream.range(0, repeatCount)
                .parallel()
                .mapToObj(n -> SingleFuzzTest.fuzzTest(object, codec, random))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        final Map<SingleFuzzTestResult, Double> statsMap = computePercentageMap(resultCounts, repeatCount);

        return new FuzzTestResult<>(
                object,
                statsMap.getOrDefault(SingleFuzzTestResult.DESERIALIZATION_FAILED, 0.) >= threshold,
                statsMap
        );
    }

    private static <T> int estimateRepeatCount(final T object, final Codec<T> codec) {
        final int size = codec.measureRecord(object);
        // This is purely a heuristic that seems to produce good results.
        // We may want to limit this value from above for extra large objects.
        return size * 20;
    }

    private static Map<SingleFuzzTestResult, Double> computePercentageMap(
            final Map<SingleFuzzTestResult, Long> resultCounts,
            final int repeatCount) {
        return resultCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().doubleValue() / (double) repeatCount)
                );
    }
}
