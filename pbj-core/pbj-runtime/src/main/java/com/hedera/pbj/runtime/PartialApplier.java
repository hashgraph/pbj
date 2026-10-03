// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Schema-free top-level protobuf replacement. Inputs must use ascending field order and canonical
 * varints. All occurrences of a field form one replacement run. Empty length-delimited values retain
 * their presence; payloads are opaque. Oneof switches MUST explicitly clear the old alternative.
 * This utility cannot validate schema-dependent defaults, oneofs, packed contents, or nested messages.
 */
public final class PartialApplier {
    private PartialApplier() {}

    /**
     * Replace complete field runs, drop explicit clears, and preserve all other bytes.
     *
     * @param prior canonical prior encoding
     * @param partial canonical encoding of replacement fields
     * @param cleared strictly increasing positive protobuf field numbers
     * @return merged bytes, without parsing or re-encoding field payloads
     * @throws IllegalArgumentException on malformed framing, ordering, clears, or replace/clear overlap
     */
    public static Bytes splice(Bytes prior, Bytes partial, int[] cleared) {
        Objects.requireNonNull(prior);
        Objects.requireNonNull(partial);
        validateCleared(cleared);
        final List<Run> old = scan(prior);
        final List<Run> patch = scan(partial);
        final List<Run> result = new ArrayList<>(old.size() + patch.size());
        int a = 0;
        int b = 0;
        int c = 0;
        long size = 0;
        while (a < old.size() || b < patch.size()) {
            final int number = Math.min(
                    a < old.size() ? old.get(a).field : Integer.MAX_VALUE,
                    b < patch.size() ? patch.get(b).field : Integer.MAX_VALUE);
            while (c < cleared.length && cleared[c] < number) c++;
            final boolean clear = c < cleared.length && cleared[c] == number;
            Run selected = null;
            if (b < patch.size() && patch.get(b).field == number) {
                if (clear) throw new IllegalArgumentException("Field is both replaced and cleared: " + number);
                selected = patch.get(b++);
            }
            if (a < old.size() && old.get(a).field == number) {
                if (selected == null && !clear) selected = old.get(a);
                a++;
            }
            if (selected != null) {
                size += selected.end - selected.start;
                result.add(selected);
            }
        }
        if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("Result is too large");
        if (size == 0) return Bytes.EMPTY;
        final byte[] output = new byte[(int) size];
        int offset = 0;
        for (Run run : result) {
            final int length = run.end - run.start;
            run.bytes.getBytes(run.start, output, offset, length);
            offset += length;
        }
        return Bytes.wrap(output);
    }

    /** Validate the canonical clear-list format shared by generated and byte-level appliers. */
    public static void validateCleared(int[] cleared) {
        Objects.requireNonNull(cleared);
        int previous = 0;
        for (int number : cleared) {
            if (number <= previous || number > 0x1fffffff) {
                throw new IllegalArgumentException("Cleared fields must be valid, unique and increasing");
            }
            previous = number;
        }
    }

    private record Run(int field, Bytes bytes, int start, int end) {}

    private static List<Run> scan(Bytes bytes) {
        final Cursor cursor = new Cursor(bytes);
        final List<Run> runs = new ArrayList<>();
        int field = 0;
        int start = 0;
        while (cursor.position < bytes.length()) {
            final int tagStart = cursor.position;
            final long tag = cursor.varint();
            if (tag <= 0 || tag > 0xffffffffL || (tag >>> 3) == 0) {
                throw new IllegalArgumentException("Invalid protobuf tag");
            }
            final int number = (int) (tag >>> 3);
            if (number < field) throw new IllegalArgumentException("Fields are not in ascending order");
            if (number != field) {
                if (field != 0) runs.add(new Run(field, bytes, start, tagStart));
                field = number;
                start = tagStart;
            }
            switch ((int) (tag & 7)) {
                case 0 -> cursor.varint();
                case 1 -> cursor.skip(8);
                case 2 -> cursor.skip(cursor.varint());
                case 5 -> cursor.skip(4);
                default -> throw new IllegalArgumentException("Unsupported protobuf wire type");
            }
        }
        if (field != 0) runs.add(new Run(field, bytes, start, cursor.position));
        return runs;
    }

    private static final class Cursor {
        private final Bytes bytes;
        private int position;

        private Cursor(Bytes bytes) {
            this.bytes = bytes;
        }

        private long varint() {
            long value = 0;
            for (int i = 0; i < 10; i++) {
                if (position >= bytes.length()) throw new IllegalArgumentException("Truncated varint");
                final int b = Byte.toUnsignedInt(bytes.getByte(position++));
                if (i == 9 && b > 1) throw new IllegalArgumentException("Varint overflow");
                value |= (long) (b & 127) << (7 * i);
                if ((b & 128) == 0) {
                    if (i > 0 && b == 0) throw new IllegalArgumentException("Noncanonical varint");
                    return value;
                }
            }
            throw new IllegalArgumentException("Varint overflow");
        }

        private void skip(long count) {
            if (count < 0 || count > bytes.length() - position) {
                throw new IllegalArgumentException("Truncated or overflowing field length");
            }
            position += (int) count;
        }
    }
}
