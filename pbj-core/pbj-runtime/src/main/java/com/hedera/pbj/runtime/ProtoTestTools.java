package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.Bytes;
import com.hedera.pbj.runtime.io.DataBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Static tools and test cases used by generated test classes.
 * <p>
 * It was very slow in testing when new buffers were created each test, so there is a thread local cache of buffers
 * here. That are used in unit tests. This saves a huge amount of GC work and reduced test time from hours to minutes.
 * </p>
 */
public final class ProtoTestTools {

    /** Size for reusable test buffers */
    private static final int BUFFER_SIZE = 24*1024*1024;

    /** Instance should never be created */
    private ProtoTestTools() {}
    /** Thread local set of reusable buffers */
    private static final ThreadLocal<DataBuffer> THREAD_LOCAL_BUFFERS =
            ThreadLocal.withInitial(() -> DataBuffer.allocate(BUFFER_SIZE, false));

    /** Thread local set of reusable buffers, second buffer for each thread */
    private static final ThreadLocal<DataBuffer> THREAD_LOCAL_BUFFERS_2 =
            ThreadLocal.withInitial(() -> DataBuffer.allocate(BUFFER_SIZE, false));

    /**
     * Get the thread local instance of DataBuffer, reset and ready to use.
     *
     * @return a DataBuffer that can be reused by current thread
     */
    public static DataBuffer getThreadLocalDataBuffer() {
        final var local = THREAD_LOCAL_BUFFERS.get();
        local.reset();
        return local;
    }

    /**
     * Get the second thread local instance of DataBuffer, reset and ready to use.
     *
     * @return a DataBuffer that can be reused by current thread
     */
    public static DataBuffer getThreadLocalDataBuffer2() {
        final var local = THREAD_LOCAL_BUFFERS_2.get();
        local.reset();
        return local;
    }

    /** Thread local set of reusable buffers */
    private static final ThreadLocal<ByteBuffer> THREAD_LOCAL_BYTE_BUFFERS =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(BUFFER_SIZE));

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
     * Take a list of objects and create a new list with those objects wrapped in optionals and adding a empty optional.
     *
     * @param list List of objects to wrap
     * @return list of optionals
     * @param <T> type of objects to wrap
     */
    public static <T> List<Optional<T>> makeListOptionals(List<T> list) {
        ArrayList<Optional<T>> optionals = new ArrayList<>(list.size()+1);
        optionals.add(Optional.empty());
        for (T value:list) {
            optionals.add(Optional.ofNullable(value));
        }
        return optionals;
    }

    /**
     * Util method to create a list of lists of objects. Given a list of test cases it creates an empty list and then a
     * sub list of first 3 elements of input {code list}, then the complete input {@code list}. So result is a list of
     * 3 lists.
     *
     * @param list Input list
     * @return list of lists derived from input list
     * @param <T> the type for lists
     */
    public static <T> List<List<T>> generateListArguments(final List<T> list) {
        return List.of(
            Collections.emptyList(),
            list.subList(0,Math.min(3, list.size())),
            list
        );
    }

    // =================================================================================================================
    // Standard lists of values to test with

    /** integer type test cases */
    public static final List<Integer> INTEGER_TESTS_LIST = List.of(Integer.MIN_VALUE, -42, -21, 0, 21, 42, Integer.MAX_VALUE);
    /** unsigned integer type test cases */
    public static final List<Integer> UNSIGNED_INTEGER_TESTS_LIST = List.of(0, 1, 2, Integer.MAX_VALUE);
    /** long type test cases */
    public static final List<Long> LONG_TESTS_LIST = List.of(Long.MIN_VALUE, -42L, -21L, 0L, 21L, 42L, Long.MAX_VALUE);
    /** unsigned long type test cases */
    public static final List<Long> UNSIGNED_LONG_TESTS_LIST = List.of(0L, 21L, 42L, Long.MAX_VALUE);
    /** bytes float test cases */
    public static final List<Float> FLOAT_TESTS_LIST = List.of(Float.MIN_NORMAL, -102.7f, -5f, 1.7f, 0f, 3f, 5.2f, 42.1f, Float.MAX_VALUE);
    /** double type test cases */
    public static final List<Double> DOUBLE_TESTS_LIST = List.of(Double.MIN_NORMAL, -102.7d, -5d, 1.7d, 0d, 3d, 5.2d, 42.1d, Double.MAX_VALUE);
    /** boolean type test cases */
    public static final List<Boolean> BOOLEAN_TESTS_LIST = List.of(true, false);
    /** bytes type test cases */
    public static final List<Bytes> BYTES_TESTS_LIST = List.of(
            Bytes.wrap(new byte[0]),
            Bytes.wrap(new byte[]{0b001}),
            Bytes.wrap(new byte[]{0b001, 0b010, 0b011, (byte)0xFF, Byte.MIN_VALUE, Byte.MAX_VALUE})
    );

    /** string type test cases, small as possible to make tests fast, there is a separate integration test with extra tests  */
    public static final List<String> STRING_TESTS_LIST = List.of(
            "",
            """
            This a small to speed tests
            Couple extended chars ©« あめ بِها
            """
    );
}
