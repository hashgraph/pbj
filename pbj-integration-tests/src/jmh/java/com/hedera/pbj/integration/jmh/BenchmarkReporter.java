// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import java.util.Collection;
import org.openjdk.jmh.results.RunResult;

/** Prints a JMH result summary with percentage standard deviation instead of absolute ± error. */
public final class BenchmarkReporter {

    private BenchmarkReporter() {}

    private static String shortName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        int prev = (dot > 0) ? fqn.lastIndexOf('.', dot - 1) : -1;
        return (prev >= 0) ? fqn.substring(prev + 1) : fqn;
    }

    public static void printResults(Collection<RunResult> results) {
        if (results.isEmpty()) return;

        int nameWidth = results.stream()
                .mapToInt(r -> shortName(r.getParams().getBenchmark()).length())
                .max()
                .orElse(40);

        System.out.printf("%n%-" + nameWidth + "s  %12s  %10s  %s%n", "Benchmark", "Score", "%StdDev", "Units");
        System.out.println("-".repeat(nameWidth + 32));

        for (RunResult result : results) {
            String name = shortName(result.getParams().getBenchmark());
            double mean = result.getPrimaryResult().getScore();
            double stddev = result.getPrimaryResult().getStatistics().getStandardDeviation();
            double pctStddev = (mean != 0.0) ? (stddev / mean) * 100.0 : Double.NaN;
            String units = result.getPrimaryResult().getScoreUnit();
            System.out.printf("%-" + nameWidth + "s  %12.3f  %9.2f%%  %s%n", name, mean, pctStddev, units);
        }
        System.out.println();
    }
}
