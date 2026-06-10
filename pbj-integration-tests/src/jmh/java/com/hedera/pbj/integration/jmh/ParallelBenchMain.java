// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the same JMH benchmark suite N times in parallel, each in its own forked JVM, then prints
 * an aggregated summary. Invoked via {@code java -jar jmh.jar -P <n> [jmh-args...]}.
 */
public final class ParallelBenchMain {

    private ParallelBenchMain() {}

    record RunData(String benchmark, long[] warmup, long[] timed) {}
    record RoundResult(ArrayList<List<RunData>> iterations, ArrayList<Path> jsonFiles) {}

    public static void main(String[] argv) throws Exception {
        int n = 3;
        ArrayList<String> remaining = new ArrayList<>();
        ArrayList<String> jvmArgs  = new ArrayList<>();
        for (int i = 0; i < argv.length; i++) {
            if(      "-P".equals(argv[i]) && i + 1 < argv.length ) n = Integer.parseInt(argv[++i]);
            else if( argv[i].startsWith("-J") )                     jvmArgs.add(argv[i].substring(2));
            else remaining.add(argv[i]);
        }
        run(n, jvmArgs.toArray(new String[0]), remaining.toArray(new String[0]));
    }

    public static void run(int n, String[] extraJvmArgs, String[] jmhArgs) throws Exception {
        URI loc = ParallelBenchMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        String jarPath = Path.of(loc).toString();
        String javaExe = ProcessHandle.current().info().command().orElse("java");

        RoundResult result = runRound(n, jarPath, javaExe, "parallel", extraJvmArgs, jmhArgs);
        printIterationTable(n, result.iterations());
        BenchmarkReporter.printAggregatedResults(result.jsonFiles());
        for (Path f : result.jsonFiles()) Files.deleteIfExists(f);
    }

    static RoundResult runRound(int n, String jarPath, String javaExe, String label, String[] extraJvmArgs, String[] jmhArgs) throws Exception {
        ArrayList<Path> resultFiles = new ArrayList<>();
        for (int i = 0; i < n; i++) resultFiles.add(Files.createTempFile("jmh-" + label + "-" + i + "-", ".json"));

        System.out.printf("Starting %d %s run(s)...%n", n, label);

        ExecutorService exec = Executors.newFixedThreadPool(n);
        ArrayList<Future<List<RunData>>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            int runIdx = i;
            Path resultFile = resultFiles.get(i);
            futures.add(exec.submit(() -> {
                ArrayList<String> cmd = new ArrayList<>();
                cmd.add(javaExe);
                cmd.add("-Djmh.ignoreLock=true");
                for (String jvmArg : extraJvmArgs) cmd.add(jvmArg);
                cmd.add("-jar");
                cmd.add(jarPath);
                for (String arg : jmhArgs) cmd.add(arg);
                cmd.add("-rf");  cmd.add("json");
                cmd.add("-rff"); cmd.add(resultFile.toString());

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                System.out.printf("  [%s Run %d] started%n", label, runIdx + 1);

                List<RunData> runData;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    runData = parseIterations(reader);
                }
                proc.waitFor();
                System.out.printf("  [%s Run %d] done%n", label, runIdx + 1);
                return runData;
            }));
        }
        exec.shutdown();

        ArrayList<List<RunData>> allRunData = new ArrayList<>();
        for (Future<List<RunData>> f : futures) allRunData.add(f.get());
        return new RoundResult(allRunData, resultFiles);
    }

    static List<RunData> parseIterations(BufferedReader reader) throws Exception {
        ArrayList<RunData> results = new ArrayList<>();
        String benchmark = null;
        ArrayList<Long> warmup = new ArrayList<>();
        ArrayList<Long> timed = new ArrayList<>();

        Pattern benchP  = Pattern.compile("^# Benchmark:\\s+(.+)$");
        Pattern warmupP = Pattern.compile("^# Warmup Iteration\\s+\\d+:\\s+([\\d.]+)");
        Pattern timedP  = Pattern.compile("^Iteration\\s+\\d+:\\s+([\\d.]+)");

        String line;
        while ((line = reader.readLine()) != null) {
            Matcher m;
            if ((m = benchP.matcher(line)).find()) {
                if( benchmark != null ) results.add(toRunData(benchmark, warmup, timed));
                benchmark = m.group(1).trim();
                warmup = new ArrayList<>();
                timed  = new ArrayList<>();
            } else if ((m = warmupP.matcher(line)).find()) {
                warmup.add((long) Double.parseDouble(m.group(1)));
            } else if ((m = timedP.matcher(line)).find()) {
                timed.add((long) Double.parseDouble(m.group(1)));
            }
        }
        if( benchmark != null ) results.add(toRunData(benchmark, warmup, timed));
        return results;
    }

    static RunData toRunData(String benchmark, List<Long> warmup, List<Long> timed) {
        long[] w = warmup.stream().mapToLong(Long::longValue).toArray();
        long[] t = timed.stream().mapToLong(Long::longValue).toArray();
        return new RunData(benchmark, w, t);
    }

    static void printIterationTable(int n, List<List<RunData>> allRunData) {
        LinkedHashMap<String, ArrayList<RunData>> byBench = new LinkedHashMap<>();
        for (List<RunData> runData : allRunData) {
            for (RunData rd : runData) {
                byBench.computeIfAbsent(rd.benchmark(), k -> new ArrayList<>()).add(rd);
            }
        }

        int runNumWidth = String.valueOf(n).length();

        for (var entry : byBench.entrySet()) {
            ArrayList<RunData> runs = entry.getValue();

            long maxVal = 1;
            for (RunData rd : runs) {
                for (long v : rd.warmup()) maxVal = Math.max(maxVal, v);
                for (long v : rd.timed())  maxVal = Math.max(maxVal, v);
            }
            int colW = String.valueOf(maxVal).length();
            String fmt = "%" + colW + "d";

            System.out.println(BenchmarkReporter.shortName(entry.getKey()) + ":");
            for (int i = 0; i < runs.size(); i++) {
                RunData rd = runs.get(i);
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("  [Run %" + runNumWidth + "d] Warm: [", i + 1));
                for (int j = 0; j < rd.warmup().length; j++) {
                    if( j > 0 ) sb.append(",");
                    sb.append(String.format(fmt, rd.warmup()[j]));
                }
                sb.append("]  Timed: [");
                for (int j = 0; j < rd.timed().length; j++) {
                    if( j > 0 ) sb.append(",");
                    sb.append(String.format(fmt, rd.timed()[j]));
                }
                sb.append("]");
                if (rd.timed().length > 0) {
                    double mean = 0;
                    for (long v : rd.timed()) mean += v;
                    mean /= rd.timed().length;
                    double var = 0;
                    for (long v : rd.timed()) { double d = v - mean; var += d * d; }
                    double pct = rd.timed().length > 1 ? Math.sqrt(var / (rd.timed().length - 1)) / mean * 100 : Double.NaN;
                    sb.append(String.format("  mean=%s", String.format(fmt, (long) mean)));
                    if (!Double.isNaN(pct)) sb.append(String.format(" ±%4.1f%%", pct));
                }
                System.out.println(sb);
            }
            System.out.println();
        }
    }
}
