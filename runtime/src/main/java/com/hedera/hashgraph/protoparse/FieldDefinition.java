package com.hedera.hashgraph.protoparse;

/**
 * Contains a definition of a field of a protobuf Message, as originally defined
 * in a protobuf schema.
 *
 * <p>For example, given the following message definition:
 *
 * <pre>
 *     message Foo {
 *         string bar = 1;
 *         repeated int32 baz = 15;
 *     }
 * </pre>
 *
 * <p>The field definition for "bar" would be
 * 'new FieldDefinition("bar", FieldType.STRING, false, 1)'.
 *
 * @param name     The name of the field as contained in the schema. Cannot be null.
 * @param type     The type of the field as contained in the schema. Cannot be null.
 * @param repeated Whether this is a "repeated" field
 * @param optional Whether this is a "optional" field - which uses Protobuf built in value types to wrap raw value
 * @param oneOf    Whether this is a field is part of a oneOf
 * @param number   The field number. Must be &gt;= 0.
 */
public record FieldDefinition(String name, FieldType type, boolean repeated, boolean optional, boolean oneOf, int number) {
    public FieldDefinition {
        if (name == null) {
            throw new NullPointerException("Name must be specified on a FieldDefinition");
        }

        if (type == null) {
            throw new NullPointerException("Type must be specified on a FieldDefinition");
        }

        if (number < 0) {
            throw new IllegalArgumentException("The field number must be >= 0");
        }
    }

    /**
     * Simple constructor for non-optional types
     *
     * @param name The name of the field as contained in the schema. Cannot be null.
     * @param type The type of the field as contained in the schema. Cannot be null.
     * @param repeated Whether this is a "repeated" field
     * @param number The field number. Must be &gt;= 0.
     */
    public FieldDefinition(String name, FieldType type, boolean repeated, int number) {
        this(name, type, repeated, false, false, number);
    }
}
