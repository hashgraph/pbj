// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.fuzz;

import java.text.NumberFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A record that describes the result of running a fuzz test.
 * @param object an object for which this test was run.
 * @param passed indicates if the test passed or not. See the FuzzTest class for the definition.
 * @param percentageMap a map with percentage statistics of occurred outcomes.
 * @param <T> the type of the object for which the test was run
 */
public record FuzzTestResult<T>(
        T object,
        boolean passed,
        Map<SingleFuzzTestResult, Double> percentageMap,
        int repeatCount,
        long nanoDuration
) {
    private static final NumberFormat PERCENTAGE_FORMAT = NumberFormat.getPercentInstance();

    /**
     * Format the FuzzTestResult object for printing/logging.
     */
    public String format() {
        return "A fuzz test " + (passed ? "PASSED" : "FAILED")
                + " with " + repeatCount + " runs took "
                + TimeUnit.MILLISECONDS.convert(nanoDuration, TimeUnit.NANOSECONDS) + " ms"
                + " for " + object
                + " with:" + System.lineSeparator()
                + formatResultsStats();
    }

    private String formatResultsStats() {
        return percentageMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().name() + ": " + PERCENTAGE_FORMAT.format(entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
