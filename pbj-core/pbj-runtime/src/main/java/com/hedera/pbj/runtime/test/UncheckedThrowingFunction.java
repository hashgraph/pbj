// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.test;

import java.util.function.Function;

/**
 * A utility wrapper for functions that throw checked exceptions.
 *
 * @param function a throwing function
 * @param <T> function argument type
 * @param <R> function return type
 */
public final record UncheckedThrowingFunction<T, R>(ThrowingFunction<T, R> function) implements Function<T, R> {

    /** A function that can throw checked exceptions. */
    public static interface ThrowingFunction<T, R> {
        R apply(T arg) throws Throwable;
    }

    @Override
    public R apply(T t) {
        try {
            return function.apply(t);
        } catch (Throwable e) {
            return Sneaky.sneakyThrow(e);
        }
    }
}
