// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.test;

/**
 * A utility class that implements sneakyThrow().
 */
public final class Sneaky {
    /**
     * Throw a checked exception pretending that it's unchecked,
     * and also pretend to return a value for convenience.
     *
     * A non-void method could perform `return sneakyThrow(ex);` to avoid
     * adding an extra line of code with a no-op return statement.
     * A void method could just call this method and not worry about the return value.
     *
     * @param throwable any exception, even a checked exception
     * @return this method never really returns a value, but javac thinks it could
     * @param <E> an exception type that javac assumes is an unchecked exception
     * @param <R> a fake return type for convenience of calling this from non-void methods
     * @throws E this method always throws its argument throwable regardless of its type,
     *           but javac thinks it's of type E, which it assumes to be an unchecked exception.
     */
    public static <E extends Throwable, R> R sneakyThrow(final Throwable throwable) throws E {
        throw (E) throwable;
    }
}
