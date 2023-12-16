package com.hedera.pbj.integration.fuzz;

import com.hedera.pbj.runtime.Codec;

import java.lang.reflect.Field;
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
    public static <T> FuzzTestResult<T> fuzzTest(final T object, final double threshold) {
        final long startNanoTime = System.nanoTime();

        final Codec<T> codec = getCodec(object);
        final Random random = new Random();
        final int repeatCount = estimateRepeatCount(object, codec);

        if (repeatCount == 0) {
            // Certain objects result in zero-size payload, so there's nothing to test.
            // Mark it as passed.
            return new FuzzTestResult<>(
                    object,
                    true,
                    Map.of(),
                    repeatCount,
                    System.nanoTime() - startNanoTime
            );
        }

        final Map<SingleFuzzTestResult, Long> resultCounts = IntStream.range(0, repeatCount)
                .parallel()
                .mapToObj(n -> SingleFuzzTest.fuzzTest(object, codec, random))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        final Map<SingleFuzzTestResult, Double> statsMap = computePercentageMap(resultCounts, repeatCount);

        return new FuzzTestResult<>(
                object,
                statsMap.getOrDefault(SingleFuzzTestResult.DESERIALIZATION_FAILED, 0.) >= threshold,
                statsMap,
                repeatCount,
                System.nanoTime() - startNanoTime
        );
    }

    private static <T> int estimateRepeatCount(final T object, final Codec<T> codec) {
        final int size = codec.measureRecord(object);
        if (size == 0) {
            // There's no way to test objects that don't have any bytes.
            return 0;
        }
        // Limit this value from above, otherwise tests will take forever for large objects.
        // Limit this value from below, otherwise there's not enough stats for small objects.
        return Math.max(2000, Math.min(4000, size * 20));
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

    private static <T> Codec<T> getCodec(final T object) {
        try {
            Field codecField = object.getClass().getField("PROTOBUF");
            return (Codec<T>) codecField.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new FuzzTestException("Failed to get a codec from the static PROTOBUF field", e);
        }
    }
}
