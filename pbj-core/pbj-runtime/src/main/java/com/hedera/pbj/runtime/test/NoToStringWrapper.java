// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.test;

import java.util.Objects;

/**
 * Wrapper for arguments to avoid expensive toString() calls during junit tests
 */
public final class NoToStringWrapper<T> {
    private final T value;
    private final String toString;

    /**
     * Construct new NoToStringWrapper
     *
     * @param value the value to wrap
     */
    public NoToStringWrapper(T value) {
        this.value = Objects.requireNonNull(value);
        this.toString = "NoToStringWrapper{" + value.getClass().getName() + '}';
    }

    /**
     * Get the wrapped value
     *
     * @return the wrapped value
     */
    public T getValue() {
        return value;
    }

    /**
     * Simple light weight toString
     *
     * @return static simple toString
     */
    @Override
    public String toString() {
        return toString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NoToStringWrapper<?> that = (NoToStringWrapper<?>) o;
        return value.equals(that.value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
