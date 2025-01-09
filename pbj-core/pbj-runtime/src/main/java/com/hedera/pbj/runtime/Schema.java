// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * Interface for Schemas, schemas are a programmatic model of protobuf schema. Used in parsing,
 * writing protobuf, to/from record objects. It is just a marker interface as all methods are
 * static. Implementing classes are expected to provide static methods with the following
 * signatures:
 *
 * <ul>
 *   <li><code>public static boolean valid(FieldDefinition f) {...}</code>
 *   <li><code>public static FieldDefinition getField(final int fieldNumber) {...}</code>
 * </ul>
 */
public interface Schema {}
