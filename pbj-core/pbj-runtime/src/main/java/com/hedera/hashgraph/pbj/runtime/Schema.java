package com.hedera.hashgraph.pbj.runtime;

/**
 * Interface for Schemas, schemas are a programmatic model of protobuf schema. Used in parsing, writing protobuf,
 * to/from record objects. It is just a marker interface as all methods are static. Implementing classes are expected to
 * provide static methods with the following signatures:
 *<ul>
 *     <li><code>public static boolean valid(FieldDefinition f) {...}</code></li>
 *     <li><code>public static FieldDefinition getField(final int fieldNumber) {...}</code></li>
 *</ul>
 */
public interface Schema {
}
