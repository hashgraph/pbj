package com.hedera.hashgraph.pbj.runtime;

/**
 * When a protobuf schema defines a field as "oneof", it is often useful
 * for parsers to represent the field as a {@link com.hedera.hashgraph.pbj.runtime.OneOf} because there is
 * often no useful supertype common to all fields within the "oneof". This
 * class takes the field num and an enum (defined by the parser) representing
 * the different possible types in this "oneof", and the actual value as
 * an object.
 *
 * @param kind     An enum representing the kind of data being represented. Must not be null.
 * @param value    The actual value in the "oneof". May be null.
 * @param <E>      The enum type
 */
public record OneOf<E>(E kind, Object value) {
    /**
     * Construct a new OneOf
     *
     * @param kind     An enum representing the kind of data being represented. Must not be null.
     * @param value    The actual value in the "oneof". May be null.
     */
    public OneOf {
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
}

