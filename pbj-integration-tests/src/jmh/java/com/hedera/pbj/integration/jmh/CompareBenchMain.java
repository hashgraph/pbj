// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 2-way sequential benchmark comparison: current jar (slim variants) → baseline jar (matching non-slim on main).
 * User supplies slim benchmark patterns; the tool derives the corresponding non-slim patterns for main.
 * Invoked via: {@code java -jar jmh.jar --compare <baseline-jar> [-P <n>] [jmh-args...]}
 */
public final class CompareBenchMain {

    private CompareBenchMain() {}

    public static void run(int n, String baselineJar, String[] extraJvmArgs, String[] jmhArgs) throws Exception {
        URI loc = CompareBenchMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        String currentJar = Path.of(loc).toString();
        String javaExe = ProcessHandle.current().info().command().orElse("java");

        // 1. Slim — run current jar with user's args
        ParallelBenchMain.RoundResult slim = ParallelBenchMain.runRound(n, currentJar, javaExe, "slim", extraJvmArgs, jmhArgs);

        // 2. Baseline — derive non-slim pattern by stripping "_slim" from slim FQNs
        String[] baselineArgs = deriveBaselineArgs(jmhArgs, slim.jsonFiles());
        ParallelBenchMain.RoundResult baseline = null;
        if (baselineArgs != null) {
            baseline = ParallelBenchMain.runRound(n, baselineJar, javaExe, "baseline", extraJvmArgs, baselineArgs);
            if (BenchmarkReporter.aggregateFiles(baseline.jsonFiles()).isEmpty()) baseline = null;
        }

        // Print iteration tables
        System.out.println("\n=== Slim iteration scores ===");
        ParallelBenchMain.printIterationTable(n, slim.iterations());
        if (baseline != null) {
            System.out.println("=== Baseline iteration scores ===");
            ParallelBenchMain.printIterationTable(n, baseline.iterations());
        }

        // Comparison table
        printComparisonTable(slim, baseline);

        // Cleanup
        for (Path f : slim.jsonFiles()) Files.deleteIfExists(f);
        if (baseline != null) for (Path f : baseline.jsonFiles()) Files.deleteIfExists(f);
    }

    /** Derive baseline jmhArgs: same flag args, new include pattern = slim FQNs with "_slim" stripped, joined as regex. */
    private static String[] deriveBaselineArgs(String[] jmhArgs, List<Path> slimJsonFiles) throws IOException {
        LinkedHashSet<String> fqns = new LinkedHashSet<>();
        for (Path file : slimJsonFiles) fqns.addAll(BenchmarkReporter.aggregateFiles(List.of(file)).keySet());
        if (fqns.isEmpty()) return null;

        String baselinePattern = fqns.stream()
                .map(fqn -> Pattern.quote(fqn.endsWith("_slim") ? fqn.substring(0, fqn.length() - 5) : fqn))
                .collect(Collectors.joining("|"));

        // Keep flag args (starting with '-' and their values); drop positional include-pattern args.
        ArrayList<String> flagArgs = new ArrayList<>();
        boolean prevWasFlag = false;
        for (String arg : jmhArgs) {
            if (arg.startsWith("-")) {
                flagArgs.add(arg);
                prevWasFlag = true;
            } else if (prevWasFlag) {
                flagArgs.add(arg);
                prevWasFlag = false;
            }
        }
        flagArgs.add(baselinePattern);
        return flagArgs.toArray(new String[0]);
    }

    private static void printComparisonTable(
            ParallelBenchMain.RoundResult slim,
            ParallelBenchMain.RoundResult baseline) throws IOException {

        LinkedHashMap<String, BenchmarkReporter.BenchRawResult> slimAgg = BenchmarkReporter.aggregateFiles(slim.jsonFiles());
        LinkedHashMap<String, BenchmarkReporter.BenchRawResult> basAgg  = baseline != null
                ? BenchmarkReporter.aggregateFiles(baseline.jsonFiles())
                : new LinkedHashMap<>();

        if (slimAgg.isEmpty()) return;

        int nameWidth = slimAgg.keySet().stream()
                .mapToInt(fqn -> BenchmarkReporter.shortName(fqn).length())
                .max().orElse(40);

        System.out.printf("%n=== Comparison ===%n%n");
        System.out.printf("%-" + nameWidth + "s  %10s  %10s  %5s  %10s%n",
                "Benchmark", "baseline", "slim", "Units", "base→slim");
        System.out.println("-".repeat(nameWidth + 42));

        for (var entry : slimAgg.entrySet()) {
            String fqn   = entry.getKey();
            String name  = BenchmarkReporter.shortName(fqn);
            double slimVal = mean(entry.getValue().values());
            String units = entry.getValue().unit();

            String baseFqn = fqn.endsWith("_slim") ? fqn.substring(0, fqn.length() - 5) : fqn;
            BenchmarkReporter.BenchRawResult basResult = basAgg.get(baseFqn);
            double bas = basResult != null ? mean(basResult.values()) : Double.NaN;

            double baseToSlim = (!Double.isNaN(bas) && bas != 0) ? (slimVal - bas) / bas * 100 : Double.NaN;

            String basStr      = Double.isNaN(bas)        ? "       N/A" : String.format("%10.1f", bas);
            String baseSlimStr = Double.isNaN(baseToSlim) ? "       N/A" : String.format("%+9.2f%%", baseToSlim);
            System.out.printf("%-" + nameWidth + "s  %s  %10.1f  %5s  %10s%n",
                    name, basStr, slimVal, units, baseSlimStr);
        }
        System.out.println();
    }

    private static double mean(double[] v) {
        double sum = 0;
        for (double x : v) sum += x;
        return v.length > 0 ? sum / v.length : Double.NaN;
    }
}
