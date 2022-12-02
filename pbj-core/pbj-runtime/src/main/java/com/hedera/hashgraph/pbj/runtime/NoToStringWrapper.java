package com.hedera.hashgraph.pbj.runtime;

import java.util.Objects;

/**
 * Wrapper for arguments to avoid expensive toString() calls during junit tests
 */
public final class NoToStringWrapper<T> {
    private final T value;
    private final String toString;

    public NoToStringWrapper(T value) {
        this.value = Objects.requireNonNull(value);
        this.toString = "NoToStringWrapper{" + value.getClass().getName() + '}';
    }

    public T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return toString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NoToStringWrapper<?> that = (NoToStringWrapper<?>) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}