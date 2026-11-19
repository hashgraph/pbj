// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The parsers' local variables for a message's fields, {@code temp_<field>}. A oneof stored unboxed
 * ({@code pbj.oneof_storage = "unboxed"}) is kept as two locals, its kind and its value, so parsing allocates no
 * {@code OneOf} when the model has a constructor taking them.
 */
final class ParseTemps {

    private ParseTemps() {}

    /** @return the local holding an unboxed oneof's kind */
    static String kind(final OneOfField oneOf) {
        return "temp_" + oneOf.name() + "__kind";
    }

    /** @return the local holding an unboxed oneof's value */
    static String value(final OneOfField oneOf) {
        return "temp_" + oneOf.name() + "__value";
    }

    /**
     * @param field a field
     * @return the declaration of the field's locals, initialized to its default
     */
    static String declare(final Field field) {
        if (field instanceof OneOfField oneOf && oneOf.unboxed()) {
            return "%s %s = %s.UNSET;%nObject %s = null;"
                    .formatted(oneOf.getEnumClassRef(), kind(oneOf), oneOf.getEnumClassRef(), value(oneOf));
        }
        return "%s temp_%s = %s;"
                .formatted(
                        field.type() == Field.FieldType.ENUM
                                ? field.repeated() ? "List" : "Object"
                                : field.javaFieldType(),
                        field.name(),
                        field.javaDefault());
    }

    /**
     * @param alternative a oneof alternative
     * @param value the expression for its value
     * @return the code setting the oneof's locals to the alternative and value
     */
    static String set(final Field alternative, final String value) {
        final OneOfField oneOf = alternative.parent();
        final String kind = oneOf.getEnumClassRef() + "."
                + com.hedera.pbj.compiler.impl.Common.camelToUpperSnake(alternative.name());
        return oneOf.unboxed()
                ? "%s = %s;%n%s = %s;%n".formatted(kind(oneOf), kind, value(oneOf), value)
                : "temp_%s = new %s<>(%s, %s);%n".formatted(oneOf.name(), oneOf.className(), kind, value);
    }

    /**
     * @param oneOf a oneof
     * @return the condition that one of the oneof's alternatives was already parsed
     */
    static String isSet(final OneOfField oneOf) {
        return oneOf.unboxed()
                ? "%s != %s.UNSET".formatted(kind(oneOf), oneOf.getEnumClassRef())
                : "!temp_%s.equals(%s)".formatted(oneOf.name(), oneOf.javaDefault());
    }

    /**
     * The code building a {@code OneOf} local, {@code temp_<oneof>}, for each unboxed oneof, for models without the
     * constructor that takes kinds and values, and for the parse cache.
     *
     * @param fields the message's fields
     * @return the code, empty if there are no unboxed oneofs
     */
    static String box(final List<Field> fields) {
        return fields.stream()
                .filter(field -> field instanceof OneOfField oneOf && oneOf.unboxed())
                .map(field -> {
                    final OneOfField oneOf = (OneOfField) field;
                    return "final %s temp_%s = new %s<>(%s, %s);%n"
                            .formatted(
                                    oneOf.javaFieldType(), oneOf.name(), oneOf.className(), kind(oneOf), value(oneOf));
                })
                .collect(Collectors.joining());
    }

    /**
     * @param fields the message's fields
     * @param unboxedConstructor true to pass each unboxed oneof as its kind and value
     * @return the constructor arguments for the fields, without the unknown fields
     */
    static String arguments(final List<Field> fields, final boolean unboxedConstructor) {
        return fields.stream()
                .map(field -> unboxedConstructor && field instanceof OneOfField oneOf && oneOf.unboxed()
                        ? kind(oneOf) + ", " + value(oneOf)
                        : "temp_" + field.name())
                .collect(Collectors.joining(", "));
    }
}
