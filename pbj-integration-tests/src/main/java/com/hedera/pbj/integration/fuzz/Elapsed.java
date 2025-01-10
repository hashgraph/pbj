// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.fuzz;

import com.hedera.pbj.runtime.test.Sneaky;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * A utility class to measure elapsed time of a running code.
 * @param result the return value of the code, if any
 * @param nanos the time the code took to run, in nanos
 * @param <T> the return type of the code, or Void for Runnables.
 */
public final record Elapsed<T>(T result, long nanos) {

    /**
     * Measure the time the provided Callable takes to run.
     *
     * @param callable a callable
     * @return an Elapsed record with the return value of the callable, and the time
     * @param <T> the Callable's return value type
     */
    public static <T> Elapsed<T> time(final Callable<T> callable) {
        long start = System.nanoTime();
        try {
            final T result = callable.call();
            return new Elapsed<>(result, System.nanoTime() - start);
        } catch (Exception ex) {
            return Sneaky.sneakyThrow(ex);
        }
    }

    /**
     * Measure the time the provided Runnable takes to run.
     *
     * @param runnable a runnable
     * @return an Elapsed record with the time. The result is set to null.
     */
    public static Elapsed<Void> time(final Runnable runnable) {
        return time(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Format the elapsed time in a human-readable form.
     *
     * The current implementation translates the nanos to seconds
     * and returns a string of the form "X seconds".
     *
     * The returned value is suitable for reporting/logging purposes only.
     * Callers should NOT rely on the exact format of the returned
     * string because it may change in the future.
     *
     * @return a string describing the elapsed time
     */
    public String format() {
        return TimeUnit.SECONDS.convert(nanos(), TimeUnit.NANOSECONDS) + " seconds";
    }
}
