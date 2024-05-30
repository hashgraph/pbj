package com.hedera.pbj.integration.fuzz;

import java.lang.reflect.Field;

/**
 * A utility class used in the fuzz testing framework.
 */
public final class FuzzUtil {
    /**
     * Get a value of a static field named `name` in a class `clz`.
     *
     * @param clz a class
     * @param name a field name
     * @return the field value
     * @param <T> the type of the field value
     */
    public static <T> T getStaticFieldValue(final Class<?> clz, final String name) {
        try {
            final Field field = clz.getField(name);
            return (T) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new FuzzTestException("Failed to get field " + name + " from " + clz.getName(), e);
        }
    }
}
