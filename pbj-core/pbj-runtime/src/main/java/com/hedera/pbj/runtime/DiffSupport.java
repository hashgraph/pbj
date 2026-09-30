// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import java.util.List;
import java.util.Map;

/** Equality used by generated diffs, retaining floating-point payloads that Java equals can erase. */
public final class DiffSupport {
    private DiffSupport() {}

    /** Generated message comparison; adds no state to model instances. */
    public interface Value {
        /**
         * Compare fields for safe whole-field replacement. Returning true guarantees equal PBJ encodings;
         * false may be conservative. This does not create nested patches or change Object.equals.
         * @param other value to compare
         * @return whether the field can safely be omitted from a diff
         */
        boolean $equalsForDiff(Object other);
    }

    /**
     * Compare immutable PBJ field values, taking identity shortcuts and preserving raw NaN payloads.
     * Generated messages compare their fields; collections and oneofs preserve element presence.
     * @param a first value
     * @param b second value
     * @return whether the values can safely be treated as unchanged
     */
    public static boolean equal(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Float x && b instanceof Float y) {
            return Float.floatToRawIntBits(x) == Float.floatToRawIntBits(y);
        }
        if (a instanceof Double x && b instanceof Double y) {
            return Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y);
        }
        if (a instanceof Value value) return value.$equalsForDiff(b);
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) return false;
            final var xi = x.iterator();
            final var yi = y.iterator();
            while (xi.hasNext()) if (!equal(xi.next(), yi.next())) return false;
            return true;
        }
        if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
            if (x.size() != y.size()) return false;
            for (var entry : x.entrySet()) {
                if (!y.containsKey(entry.getKey()) || !equal(entry.getValue(), y.get(entry.getKey()))) return false;
            }
            return true;
        }
        if (a instanceof OneOf<?> x && b instanceof OneOf<?> y) {
            return x.kind() == y.kind() && equal(x.value(), y.value());
        }
        if (a instanceof ComparableOneOf<?> x && b instanceof ComparableOneOf<?> y) {
            return x.kind() == y.kind() && equal(x.value(), y.value());
        }
        if ((a instanceof EnumWithProtoMetadata || b instanceof EnumWithProtoMetadata)
                && (a instanceof EnumWithProtoMetadata || a instanceof Integer)
                && (b instanceof EnumWithProtoMetadata || b instanceof Integer)) {
            return EnumWithProtoMetadata.protoOrdinal(a) == EnumWithProtoMetadata.protoOrdinal(b);
        }
        return a.equals(b);
    }
}
