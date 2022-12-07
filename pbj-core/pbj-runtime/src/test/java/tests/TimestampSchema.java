package tests;

import com.hedera.hashgraph.pbj.runtime.FieldDefinition;
import com.hedera.hashgraph.pbj.runtime.FieldType;

/**
 * Schema for Timestamp model object. Generate based on protobuf schema.
 */
public final class TimestampSchema {
    // -- FIELD DEFINITIONS ---------------------------------------------

    public static final FieldDefinition SECONDS = new FieldDefinition("seconds", FieldType.INT64, false, false, false, 1);
    public static final FieldDefinition NANOS = new FieldDefinition("nanos", FieldType.INT32, false, false, false, 2);

    // -- OTHER METHODS -------------------------------------------------

    /**
     * Check if a field definition belongs to this schema.
     *
     * @param f field def to check
     * @return true if it belongs to this schema
     */
    public static boolean valid(FieldDefinition f) {
        return f != null && getField(f.number()) == f;
    }

    /**
     * Get a field definition given a field number
     *
     * @param fieldNumber the fields number to get def for
     * @return field def or null if field number does not exist
     */
    public static FieldDefinition getField(final int fieldNumber) {
        return switch(fieldNumber) {
            case 1 -> SECONDS;
            case 2 -> NANOS;
            default -> null;
        };
    }

}