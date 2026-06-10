// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openjdk.jmh.results.RunResult;

/** Prints a JMH result summary with percentage standard deviation instead of absolute ± error. */
public final class BenchmarkReporter {

    private BenchmarkReporter() {}

    static String shortName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        int prev = (dot > 0) ? fqn.lastIndexOf('.', dot - 1) : -1;
        return (prev >= 0) ? fqn.substring(prev + 1) : fqn;
    }

    private static double mean(double[] v) {
        double sum = 0;
        for (double x : v) sum += x;
        return v.length > 0 ? sum / v.length : 0.0;
    }

    private static double stddev(double[] v, double mean) {
        if (v.length < 2) return Double.NaN;
        double variance = 0;
        for (double x : v) { double d = x - mean; variance += d * d; }
        return Math.sqrt(variance / (v.length - 1));
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

    // ---- parallel-run aggregation ----

    record BenchRawResult(String unit, double[] values) {}

    static HashMap<String, BenchRawResult> parseJsonFile(Path file) throws IOException {
        String json = Files.readString(file);
        HashMap<String, BenchRawResult> results = new LinkedHashMap<>();

        // Split top-level array into one JSON object per benchmark (bracket-counting, handles nesting)
        List<String> objects = new ArrayList<>();
        int depth = 0, start = -1;
        boolean inString = false;
        char prev = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') { if (depth++ == 0) start = i; }
                else if (c == '}') { if (--depth == 0 && start >= 0) { objects.add(json.substring(start, i + 1)); start = -1; } }
            }
            prev = c;
        }

        for (String obj : objects) {
            Matcher nm = Pattern.compile("\"benchmark\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
            if (!nm.find()) continue;
            String name = nm.group(1);

            Matcher um = Pattern.compile("\"scoreUnit\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
            String unit = um.find() ? um.group(1) : "?";

            // Extract all numbers from the rawData block (handles multiple forks)
            int rawIdx = obj.indexOf("\"rawData\"");
            if (rawIdx < 0) continue;
            int outerOpen = obj.indexOf('[', rawIdx);
            int d = 0, rawEnd = outerOpen;
            for (int k = outerOpen; k < obj.length(); k++) {
                char c = obj.charAt(k);
                if (c == '[') d++;
                else if (c == ']') { if (--d == 0) { rawEnd = k; break; } }
            }
            String rawBlock = obj.substring(outerOpen, rawEnd + 1);
            Matcher numM = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?").matcher(rawBlock);
            ArrayList<Double> values = new ArrayList<>();
            while (numM.find()) values.add(Double.parseDouble(numM.group()));

            double[] arr = new double[values.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = values.get(i);
            results.put(name, new BenchRawResult(unit, arr));
        }
        return results;
    }

    static LinkedHashMap<String, BenchRawResult> aggregateFiles(List<Path> jsonFiles) throws IOException {
        LinkedHashMap<String, ArrayList<Double>> allData = new LinkedHashMap<>();
        HashMap<String, String> units = new HashMap<>();
        for (Path file : jsonFiles) {
            for (var entry : parseJsonFile(file).entrySet()) {
                units.put(entry.getKey(), entry.getValue().unit());
                ArrayList<Double> list = allData.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                for (double v : entry.getValue().values()) list.add(v);
            }
        }
        LinkedHashMap<String, BenchRawResult> result = new LinkedHashMap<>();
        for (var entry : allData.entrySet()) {
            double[] arr = entry.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            result.put(entry.getKey(), new BenchRawResult(units.get(entry.getKey()), arr));
        }
        return result;
    }

    public static void printAggregatedResults(List<Path> jsonFiles) throws IOException {
        LinkedHashMap<String, ArrayList<Double>> allData = new LinkedHashMap<>();
        HashMap<String, String> units = new HashMap<>();
        HashMap<String, ArrayList<Double>> perRunMeans = new LinkedHashMap<>();

        for (Path file : jsonFiles) {
            HashMap<String, BenchRawResult> fileResults = parseJsonFile(file);
            for (var entry : fileResults.entrySet()) {
                String key = entry.getKey();
                BenchRawResult r = entry.getValue();
                units.put(key, r.unit());
                ArrayList<Double> all = allData.computeIfAbsent(key, k -> new ArrayList<>());
                ArrayList<Double> runMeanList = perRunMeans.computeIfAbsent(key, k -> new ArrayList<>());
                double sum = 0;
                for (double v : r.values()) { all.add(v); sum += v; }
                if (r.values().length > 0) runMeanList.add(sum / r.values().length);
            }
        }

        if (allData.isEmpty()) return;

        int nameWidth = allData.keySet().stream()
                .mapToInt(n -> shortName(n).length())
                .max()
                .orElse(40);

        System.out.printf("%n=== Parallel run summary (%d processes) ===%n%n", jsonFiles.size());
        System.out.printf("%-" + nameWidth + "s  %12s  %10s  %22s  %s%n",
                "Benchmark", "Score", "%StdDev", "Run means (min..max)", "Units");
        System.out.println("-".repeat(nameWidth + 54));

        for (var entry : allData.entrySet()) {
            String name = shortName(entry.getKey());
            double[] values = entry.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            double m = mean(values);
            double pct = (m != 0.0) ? (stddev(values, m) / m) * 100.0 : Double.NaN;
            String unit = units.get(entry.getKey());
            ArrayList<Double> runMeans = perRunMeans.get(entry.getKey());
            double minMean = runMeans.stream().mapToDouble(Double::doubleValue).min().orElse(m);
            double maxMean = runMeans.stream().mapToDouble(Double::doubleValue).max().orElse(m);
            System.out.printf("%-" + nameWidth + "s  %12.3f  %9.2f%%  [%9.1f .. %9.1f]  %s%n",
                    name, m, pct, minMean, maxMean, unit);
        }
        System.out.println();
    }
}
