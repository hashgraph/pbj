// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static tools and test cases used by generated test classes.
 * <p>
 * It was very slow in testing when new buffers were created each test, so there is a thread local cache of buffers
 * here. That are used in unit tests. This saves a huge amount of GC work and reduced test time from hours to minutes.
 * </p>
 */
public final class ProtoTestTools {

    /** Size for reusable test buffers */
    private static final int BUFFER_SIZE = 1024 * 1024;

    /** Size for reusable test char buffers */
    private static final int CHAR_BUFFER_SIZE = 16 * 1024 * 1024;

    /** Instance should never be created */
    private ProtoTestTools() {}
    /** Thread local set of reusable buffers */
    private static final ThreadLocal<BufferedData> THREAD_LOCAL_BUFFERS =
            ThreadLocal.withInitial(() -> BufferedData.allocate(BUFFER_SIZE));

    /** Thread local set of reusable buffers, second buffer for each thread */
    private static final ThreadLocal<BufferedData> THREAD_LOCAL_BUFFERS_2 =
            ThreadLocal.withInitial(() -> BufferedData.allocate(BUFFER_SIZE));

    /** Thread local set of reusable buffers */
    private static final ThreadLocal<ByteBuffer> THREAD_LOCAL_BYTE_BUFFERS =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(BUFFER_SIZE));

    /** Thread local set of reusable char buffers */
    private static final ThreadLocal<CharBuffer> THREAD_LOCAL_CHAR_BUFFERS =
            ThreadLocal.withInitial(() -> CharBuffer.allocate(CHAR_BUFFER_SIZE));

    /** Thread local set of reusable char buffers */
    private static final ThreadLocal<CharBuffer> THREAD_LOCAL_CHAR_BUFFERS_2 =
            ThreadLocal.withInitial(() -> CharBuffer.allocate(CHAR_BUFFER_SIZE));

    /** Thread local set of reusable byte arrays */
    private static final ThreadLocal<byte[]> THREAD_LOCAL_BYTE_ARRAY =
            ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    /**
     * Get the thread local instance of byte[].
     *
     * @return a byte[] that can be reused by current thread
     */
    public static byte[] getThreadLocalByteArray() {
        return THREAD_LOCAL_BYTE_ARRAY.get();
    }

    /**
     * Get the thread local instance of DataBuffer, reset and ready to use.
     *
     * @return a DataBuffer that can be reused by current thread
     */
    public static BufferedData getThreadLocalDataBuffer() {
        final var local = THREAD_LOCAL_BUFFERS.get();
        local.reset();
        return local;
    }

    /**
     * Get the second thread local instance of DataBuffer, reset and ready to use.
     *
     * @return a DataBuffer that can be reused by current thread
     */
    public static BufferedData getThreadLocalDataBuffer2() {
        final var local = THREAD_LOCAL_BUFFERS_2.get();
        local.reset();
        return local;
    }

    /**
     * Get the thread local instance of ByteBuffer, reset and ready to use.
     *
     * @return a ByteBuffer that can be reused by current thread
     */
    public static ByteBuffer getThreadLocalByteBuffer() {
        final var local = THREAD_LOCAL_BYTE_BUFFERS.get();
        local.clear();
        return local;
    }

    /**
     * Get the thread local instance of CharBuffer, reset and ready to use.
     *
     * @return a ByteBuffer that can be reused by current thread
     */
    public static CharBuffer getThreadLocalCharBuffer() {
        final var local = THREAD_LOCAL_CHAR_BUFFERS.get();
        local.clear();
        return local;
    }

    /**
     * Get the thread local instance of CharBuffer, reset and ready to use.
     *
     * @return a ByteBuffer that can be reused by current thread
     */
    public static CharBuffer getThreadLocalCharBuffer2() {
        final var local = THREAD_LOCAL_CHAR_BUFFERS_2.get();
        local.clear();
        return local;
    }

    /**
     * Util method to take a list and append {@code null} on the front.
     *
     * @param list Input list
     * @return new list with null added
     * @param <T> the type for lists
     */
    public static <T> List<T> addNull(final List<T> list) {
        ArrayList<T> newList = new ArrayList<>(list.size() + 1);
        newList.add(null);
        newList.addAll(list);
        return newList;
    }

    /**
     * Util method to create a list of lists of objects. Given a list of test cases it creates an empty list and then a
     * splits the rest into sub-lists of length max 5.
     * <p>
     *     This was changed has it is way faster for tests to do many small chunks than a few huge objects.
     * </p>
     *
     * @param list Input list
     * @return list of lists derived from input list
     * @param <T> the type for lists
     */
    public static <T> List<List<T>> generateListArguments(final List<T> list) {
        ArrayList<List<T>> outputList = new ArrayList<>((list.size() / 5) + 1);
        outputList.add(Collections.emptyList());
        int i = 0;
        while (i < list.size()) {
            final int itemsToUse = Math.min(5, list.size() - i);
            outputList.add(list.subList(i, i + itemsToUse));
            i += itemsToUse;
        }
        return outputList;
    }

    // =================================================================================================================
    // Non-canonical encodings

    /**
     * Reverses the order of the top-level fields of an encoded message. Occurrences of the same field number keep
     * their relative order, so repeated fields and maps decode to the same values; only the order of the distinct
     * field numbers is reversed. The result decodes to the same message, but is not in the canonical (ascending field
     * number) order that PBJ and protoc write, so it exercises a parser's handling of out-of-order fields.
     *
     * @param message the encoded message, in any order
     * @return the same fields with the order of distinct field numbers reversed
     */
    public static Bytes reverseTopLevelFieldOrder(final Bytes message) {
        final byte[] in = message.toByteArray();
        // field number to the [start, end) ranges of its occurrences, in order of first appearance
        final Map<Integer, List<int[]>> fields = new LinkedHashMap<>();
        int p = 0;
        while (p < in.length) {
            final int start = p;
            final long tag = readVarLong(in, p);
            p += varLongSize(tag);
            final int wireType = (int) (tag & ProtoConstants.TAG_WIRE_TYPE_MASK);
            p = switch (wireType) {
                case 0 -> p + varLongSize(readVarLong(in, p));
                case 1 -> p + Long.BYTES;
                case 2 -> {
                    final long length = readVarLong(in, p);
                    final int lengthStart = p + varLongSize(length);
                    yield lengthStart + (int) length;
                }
                case 5 -> p + Integer.BYTES;
                default -> throw new IllegalArgumentException("Unsupported wire type " + wireType + " at " + start);
            };
            if (p > in.length) {
                throw new IllegalArgumentException("Truncated field at " + start);
            }
            final int fieldNumber = (int) (tag >>> ProtoParserTools.TAG_FIELD_OFFSET);
            fields.computeIfAbsent(fieldNumber, k -> new ArrayList<>()).add(new int[] {start, p});
        }
        final List<List<int[]>> groups = new ArrayList<>(fields.values());
        Collections.reverse(groups);
        final byte[] out = new byte[in.length];
        int o = 0;
        for (final List<int[]> group : groups) {
            for (final int[] range : group) {
                System.arraycopy(in, range[0], out, o, range[1] - range[0]);
                o += range[1] - range[0];
            }
        }
        return Bytes.wrap(out);
    }

    /**
     * Appends a varint field with the given field number and the value 1 to an encoded message. Pass a field number
     * the message does not define, and the result is the message with one unknown field at the end.
     *
     * @param message the encoded message
     * @param fieldNumber the field number of the appended field
     * @return the message followed by the varint field
     */
    public static Bytes appendVarIntField(final Bytes message, final int fieldNumber) {
        final long tag = ((long) fieldNumber << ProtoParserTools.TAG_FIELD_OFFSET)
                | ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
        final byte[] field = new byte[varLongSize(tag) + 1];
        int o = 0;
        long v = tag;
        while ((v & ~0x7FL) != 0) {
            field[o++] = (byte) ((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        field[o++] = (byte) v;
        field[o] = 1;
        return message.append(Bytes.wrap(field));
    }

    /** Reads an unsigned varint at {@code in[p]}. */
    private static long readVarLong(final byte[] in, final int p) {
        long value = 0;
        for (int i = 0; i < 10; i++) {
            if (p + i >= in.length) {
                throw new IllegalArgumentException("Truncated varint at " + p);
            }
            final byte b = in[p + i];
            value |= (long) (b & 0x7F) << (7 * i);
            if (b >= 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("Malformed varint at " + p);
    }

    /** The number of bytes in the minimal varint encoding of {@code value}. */
    private static int varLongSize(final long value) {
        return Math.max(1, (64 - Long.numberOfLeadingZeros(value) + 6) / 7);
    }

    // =================================================================================================================
    // Standard lists of values to test with

    /** integer type test cases */
    public static final List<Integer> INTEGER_TESTS_LIST =
            List.of(Integer.MIN_VALUE, -42, -21, 0, 21, 42, Integer.MAX_VALUE);
    /** unsigned integer type test cases */
    public static final List<Integer> UNSIGNED_INTEGER_TESTS_LIST = List.of(0, 1, 2, Integer.MAX_VALUE);
    /** long type test cases */
    public static final List<Long> LONG_TESTS_LIST = List.of(Long.MIN_VALUE, -42L, -21L, 0L, 21L, 42L, Long.MAX_VALUE);
    /** unsigned long type test cases */
    public static final List<Long> UNSIGNED_LONG_TESTS_LIST = List.of(0L, 21L, 42L, Long.MAX_VALUE);
    /** bytes float test cases */
    public static final List<Float> FLOAT_TESTS_LIST =
            List.of(Float.MIN_NORMAL, -102.7f, -5f, 1.7f, 0f, 3f, 5.2f, 42.1f, Float.MAX_VALUE);
    /** double type test cases */
    public static final List<Double> DOUBLE_TESTS_LIST =
            List.of(Double.MIN_NORMAL, -102.7d, -5d, 1.7d, 0d, 3d, 5.2d, 42.1d, Double.MAX_VALUE);
    /** boolean type test cases */
    public static final List<Boolean> BOOLEAN_TESTS_LIST = List.of(true, false);
    /** bytes type test cases */
    public static final List<Bytes> BYTES_TESTS_LIST =
            List.of(Bytes.wrap(new byte[0]), Bytes.wrap(new byte[] {0b001}), Bytes.wrap(new byte[] {
                0b001, 0b010, 0b011, (byte) 0xFF, Byte.MIN_VALUE, Byte.MAX_VALUE
            }));

    /** string type test cases, small as possible to make tests fast, there is a separate integration test with extra tests  */
    public static final List<String> STRING_TESTS_LIST = List.of("", """
            This a small to speed tests
            Couple extended chars ©« あめ بِها
            """);
}
