// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import java.util.ArrayList;
import java.util.Collection;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Drop-in replacement for {@code org.openjdk.jmh.Main} that appends a percentage std-dev summary
 * table after JMH's normal output. Pass {@code -P <n>} to run benchmarks across n parallel
 * processes and aggregate the results.
 */
public final class JmhMain {

    private JmhMain() {}

    public static void main(String[] argv) throws Exception {
        // Strip -P <n>, --compare <jar>, and -J<jvmarg> before JMH sees the args
        int parallelRuns = 1;
        String compareJar = null;
        ArrayList<String> filtered = new ArrayList<>();
        ArrayList<String> jvmArgs  = new ArrayList<>();
        for (int i = 0; i < argv.length; i++) {
            if(      "-P".equals(argv[i])        && i + 1 < argv.length ) parallelRuns = Integer.parseInt(argv[++i]);
            else if( "--compare".equals(argv[i]) && i + 1 < argv.length ) compareJar = argv[++i];
            else if( argv[i].startsWith("-J") )                            jvmArgs.add(argv[i].substring(2));
            else filtered.add(argv[i]);
        }
        String[] remaining = filtered.toArray(new String[0]);
        String[] extraJvmArgs = jvmArgs.toArray(new String[0]);
        if( compareJar != null ) {
            CompareBenchMain.run(parallelRuns, compareJar, extraJvmArgs, remaining);
            return;
        }
        if( parallelRuns > 1 ) {
            ParallelBenchMain.run(parallelRuns, extraJvmArgs, remaining);
            return;
        }
        argv = remaining;

        CommandLineOptions cmdOptions = new CommandLineOptions(argv);

        if (cmdOptions.shouldHelp()) {
            cmdOptions.showHelp();
            return;
        }
        if (cmdOptions.shouldList()) {
            new Runner(cmdOptions).list();
            return;
        }
        if (cmdOptions.shouldListWithParams()) {
            new Runner(cmdOptions).listWithParams(cmdOptions);
            return;
        }
        if (cmdOptions.shouldListProfilers()) {
            cmdOptions.listProfilers();
            return;
        }
        if (cmdOptions.shouldListResultFormats()) {
            cmdOptions.listResultFormats();
            return;
        }

        Runner runner = new Runner(new OptionsBuilder()
                .parent(cmdOptions)
                .exclude(".*GrpcBench.*")
                .exclude(".*GrpcStress.*")
                .build());
        Collection<RunResult> results = runner.run();

        if (results == null) {
            System.exit(1);
        }

        BenchmarkReporter.printResults(results);
    }
}
