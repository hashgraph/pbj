// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import java.util.Collection;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Drop-in replacement for {@code org.openjdk.jmh.Main} that appends a percentage std-dev summary
 * table after JMH's normal output.
 */
public final class JmhMain {

    private JmhMain() {}

    public static void main(String[] argv) throws Exception {
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
