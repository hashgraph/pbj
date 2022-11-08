package com.hedera.hashgraph.protoparse;

/**
 * When a protobuf schema defines a field as "oneof", it is often useful
 * for parsers to represent the field as a {@link OneOf} because there is
 * often no useful supertype common to all fields within the "oneof". This
 * class takes the field num and an enum (defined by the parser) representing
 * the different possible types in this "oneof", and the actual value as
 * an object.
 *
 * @param fieldNum The field number as defined in the protobuf schema. Must be non-negative.
 * @param kind     An enum representing the kind of data being represented. Must not be null.
 * @param value    The actual value in the "oneof". May be null.
 * @param <E>      The enum type
 * @param <T>      The value type
 */
public record OneOf<E, T>(int fieldNum, E kind, T value) {
    public OneOf {
        if (fieldNum < 0) {
            throw new IllegalArgumentException("Field number must be non-negative");
        }

        if (kind == null) {
            throw new NullPointerException("An enum 'kind' must be supplied");
        }
    }

    public <V> V as() {
        //noinspection unchecked
        return (V) value;
    }
}
