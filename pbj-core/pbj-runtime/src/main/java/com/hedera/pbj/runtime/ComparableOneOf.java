package com.hedera.pbj.runtime;

import java.util.Objects;

/**
 *
 * This is a version of {@link OneOf} that implements `Comparable` interface to allow sorting of lists of ComparableOneOf objects.
 * It requires that the value implements `Comparable` interface as well.
 *
 * @param kind     An enum representing the kind of data being represented. Must not be null.
 * @param value    The actual value in the "oneof". May be null.
 * @param <E>      The enum type
 */
public record ComparableOneOf<E extends Enum<E>>(E kind, Comparable value) implements Comparable<ComparableOneOf<E>> {
    /**
     * Construct a new ComparableOneOf
     *
     * @param kind     An enum representing the kind of data being represented. Must not be null.
     * @param value    The actual value in the "oneof". May be null.
     */
    public ComparableOneOf {
        if (kind == null) {
            throw new NullPointerException("An enum 'kind' must be supplied");
        }
    }

    /**
     * Get the value with auto casting
     *
     * @return value
     * @param <V> the type to cast value to
     */
    @SuppressWarnings("unchecked")
    public <V> V as() {
        return (V) value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComparableOneOf<?> oneOf)) return false;
        return kind.equals(oneOf.kind) && Objects.equals(value, oneOf.value);
    }

    @Override
    public int hashCode() {
        // name().hashCode() and NOT Enum.hashCode() because the latter changes between runs
        return (31 + kind.name().hashCode()) * 31 + (value == null ? 0 : value.hashCode());
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compareTo(ComparableOneOf<E> thatObj) {
        if (thatObj == null) {
            return 1;
        }
        final int kindCompare = kind.compareTo(thatObj.kind);
        if (kindCompare != 0) {
            return kindCompare;
        }
        return value.compareTo(thatObj.value);
    }
}

