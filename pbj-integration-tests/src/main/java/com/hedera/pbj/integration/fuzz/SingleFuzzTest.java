package com.hedera.pbj.integration.fuzz;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.BufferedData;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single fuzz test.
 * <p>
 * This class exposes a single public static method that allows a caller to pass
 * a valid object, a Protobuf codec for this object, as well as a random numbers
 * generator. The method will then perform a single fuzz test run with the given
 * data and return a SingleFuzzTestResult describing the outcome of the test run.
 * <p>
 * A fuzz testing framework is expected to use this class to execute multiple runs
 * of the same test (each being random per the given random numbers generator)
 * until the set of outcomes satisfies the testing framework threshold,
 * or the framework runs out of the maximum number of attempts, or a timeout
 * occurs.
 * <p>
 * This class is thread-safe and can be called concurrently from multiple threads
 * as long as the input parameters are immutable or thread-safe.
 */
public final class SingleFuzzTest {
    private final static AtomicInteger TEST_ID_GENERATOR = new AtomicInteger(0);

    private static <T> BufferedData write(final T object, final Codec<T> codec, final int size) throws Exception {
        final BufferedData dataBuffer = BufferedData.allocate(size);
        codec.write(object, dataBuffer);
        return dataBuffer;
    }

    /**
     * Perform a fuzz test for a given input object of type T and its codec
     * using a provided random numbers generator.
     * <p>
     * The input object is expected to be valid (i.e. serializable using the given codec),
     * otherwise an exception is thrown.
     *<p>
     * The test run produces debugging output on stdout with a prefix that is unique
     * to this particular run, allowing one to identify all the debugging output related
     * to this specific run even if multiple runs are running concurrently.
     *
     * @return a SingleFuzzTestResult
     */
    public static <T> SingleFuzzTestResult fuzzTest(final T object, final Codec<T> codec, final Random random) {
        // Generate a unique test ID prefix for this particular run to tag debugging output:
        final String prefix = SingleFuzzTest.class.getSimpleName() + " " + TEST_ID_GENERATOR.getAndIncrement() + ": ";

        System.out.println(prefix + "Object: " + object);
        final int size = codec.measureRecord(object);
        final BufferedData dataBuffer;
        try {
            dataBuffer = write(object, codec, size);
        } catch (Exception ex) {
            // The test expects a valid input object, so we don't expect this to happen here
            throw new FuzzTestException("Unable to write the object", ex);
        }

        System.out.println(prefix + "Bytes: " + dataBuffer);

        // Set a random byte to a random value
        final int randomPosition = random.nextInt(size);
        final byte randomByte = (byte) random.nextInt(256);

        dataBuffer.position(randomPosition);
        dataBuffer.writeByte(randomByte);

        System.out.println(prefix + "Fuzz bytes: " + dataBuffer);

        dataBuffer.reset();
        final T deserializedObject;
        try {
            deserializedObject = codec.parse(dataBuffer);
        } catch (Exception ex) {
            // Note that the codec may throw the IOException, as well as various nio exceptions
            // such as the BufferUnderflowException. We're good as long as any exception is thrown.
            System.out.println(prefix + "Fuzz exception: " + ex.getMessage());
            return SingleFuzzTestResult.DESERIALIZATION_FAILED;
        }

        final int deserializedSize = codec.measureRecord(deserializedObject);
        if (deserializedSize != size) {
            System.out.println(prefix + "Original size: " + size + " , fuzz size: " + deserializedSize);
            return SingleFuzzTestResult.DESERIALIZED_SIZE_MISMATCHED;
        }


        final BufferedData reserializedBuffer;
        try {
            reserializedBuffer = write(deserializedObject, codec, deserializedSize);
        } catch (Exception ex) {
            // Note that the codec may throw the IOException, as well as various nio exceptions
            // such as the BufferUnderflowException. We're good as long as any exception is thrown.
            System.out.println(prefix + "Reserialization exception: " + ex.getMessage());
            return SingleFuzzTestResult.RESERIALIZATION_FAILED;
        }

        System.out.println(prefix + "Reserialized bytes: " + reserializedBuffer);
        if (!reserializedBuffer.equals(dataBuffer)) {
            return SingleFuzzTestResult.RESERIALIZATION_MISMATCHED;
        }

        return SingleFuzzTestResult.RESERIALIZATION_PASSED;
    }

}
