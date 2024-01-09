package com.hedera.pbj.integration.fuzz;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.BufferedData;

import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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
    // When set to true, the test will print debugging info to System.out,
    // including payloads, for every single run. This may produce a lot of console output.
    private final static boolean debug = false;

    private final static AtomicInteger TEST_ID_GENERATOR = new AtomicInteger(0);

    public static int getNumberOfRuns() {
        return TEST_ID_GENERATOR.get();
    }

    private static <T> BufferedData write(final T object, final Codec<T> codec, final int size) throws Exception {
        final BufferedData dataBuffer = BufferedData.allocate(size);
        codec.write(object, dataBuffer);
        return dataBuffer;
    }

    private static void tryProtocParser(
            final String prefix,
            final Object originalObject,
            final BufferedData dataBuffer,
            final Object deserializedObject,
            final Function<InputStream, ?> protocParser,
            Exception pbjException,
            boolean doThrow
    ) {
        dataBuffer.reset();
        try {
            Object protocObject = protocParser.apply(dataBuffer.toInputStream());
            if (pbjException != null) {
                System.out.println(prefix + "NOTE: Protoc was able to parse this payload w/o exceptions as "
                        + protocObject
                        + " , but PBJ errored out with "
                        + pbjException.toString()
                );
            }
        } catch (Exception ex) {
            // Protoc didn't like the bytes.
            if (doThrow) {
                throw new FuzzTestException(
                        prefix + "Protoc threw an exception "
                                // Fetch the actual cause because this was a call via Java Reflection:
                                + ex.getCause().getCause()
                                + ", while PBJ didn't for original object: "
                                + originalObject
                                + " and fuzzBytes " + dataBuffer
                                + " that PBJ parsed as: " + deserializedObject
                        ,
                        ex
                );
            }
        }
    }

    private static int estimateNumberOfBytesToModify(final Random random, final int size) {
        // Ideally, we want to modify a random number of bytes from 1 to size:
        final int numberOfBytesToModify = (1 + random.nextInt(size - 1));
        // However:
        // 1. The size of the object may be large either because it has many fields,
        //    or it has long byte arrays. So we want to put a hard upper limit,
        //    otherwise tests will take forever to run.
        // 2. A large object may in fact have a single field - a long byte array.
        //    Randomly modifying a few bytes in such an object would likely result
        //    in a perfectly valid object because this won't corrupt the metadata.
        //    So we want to put a hard lower limit to try and produce some corruptions.
        // 3. Further to #2, a single-field object may also be small, in an extreme
        //    case it can occupy just two bytes - a byte of metadata and the actual
        //    data. We certainly don't want to limit the number of modifications
        //    from below by the size of the object because this may result in too few
        //    modifications (e.g. a single one), which is unlikely to produce a malformed
        //    payload as often as we need to accumulate the necessary statistics.
        return Math.max(64, Math.min(1000, numberOfBytesToModify));
    }

    /**
     * Perform a fuzz test for a given input object of type T and its codec
     * using a provided random numbers generator.
     * <p>
     * The input object is expected to be valid (i.e. serializable using the given codec),
     * otherwise an exception is thrown.
     * <p>
     * A comparison with Google Protoc parser is performed as well. A log output is generated
     * if PBJ fails to parse data that Protoc is able to parse. Conversely, the test run
     * fails with an exception if Protoc fails to parse malformed data that PBJ parses successfully.
     * <p>
     * The test run produces debugging output on stdout with a prefix that is unique
     * to this particular run, allowing one to identify all the debugging output related
     * to this specific run even if multiple runs are running concurrently.
     *
     * @return a SingleFuzzTestResult
     */
    public static <T> SingleFuzzTestResult fuzzTest(
            final T object,
            final Codec<T> codec,
            final Random random,
            final Function<InputStream, ?> protocParser) {
        // Generate a unique test ID prefix for this particular run to tag debugging output:
        final String prefix = SingleFuzzTest.class.getSimpleName() + " " + TEST_ID_GENERATOR.getAndIncrement() + ": ";

        if (debug) System.out.println(prefix + "Object: " + object);
        final int size = codec.measureRecord(object);
        final BufferedData dataBuffer = createBufferedData(object, codec, size, prefix);
        if (debug) System.out.println(prefix + "Bytes: " + dataBuffer);

        modifyBufferedData(random, size, dataBuffer);
        if (debug) System.out.println(prefix + "Fuzz bytes: " + dataBuffer);

        dataBuffer.reset();
        final T deserializedObject;
        try {
            deserializedObject = codec.parse(dataBuffer);
        } catch (Exception ex) {
            // Note that the codec may throw the IOException, as well as various nio exceptions
            // such as the BufferUnderflowException. We're good as long as any exception is thrown.
            if (debug) {
                System.out.println(prefix + "Fuzz exception: " + ex.getMessage());

                // Debug output if protoc is able to parse this w/o exceptions:
                tryProtocParser(prefix, object, dataBuffer, null, protocParser, ex, false);
            }

            return SingleFuzzTestResult.DESERIALIZATION_FAILED;
        }

        if (debug) System.out.println(prefix + "deserializedObject: " + deserializedObject);

        // Fail the test if protoc throws an exception but PBJ hasn't thrown an exception above.
        // This indicates that PBJ is able to parse malformed input which it shouldn't.
        tryProtocParser(prefix, object, dataBuffer, deserializedObject, protocParser, null, true);

        final int deserializedSize = codec.measureRecord(deserializedObject);
        if (deserializedSize != size) {
            if (debug) System.out.println(prefix + "Original size: " + size + " , fuzz size: " + deserializedSize);
            return SingleFuzzTestResult.DESERIALIZED_SIZE_MISMATCHED;
        }

        final BufferedData reserializedBuffer;
        try {
            reserializedBuffer = write(deserializedObject, codec, deserializedSize);
        } catch (Exception ex) {
            // Note that the codec may throw the IOException, as well as various nio exceptions
            // such as the BufferUnderflowException. We're good as long as any exception is thrown.
            if (debug) System.out.println(prefix + "Reserialization exception: " + ex.getMessage());
            return SingleFuzzTestResult.RESERIALIZATION_FAILED;
        }

        if (debug) System.out.println(prefix + "Reserialized bytes: " + reserializedBuffer);
        if (!reserializedBuffer.equals(dataBuffer)) {
            return SingleFuzzTestResult.RESERIALIZATION_MISMATCHED;
        }

        return SingleFuzzTestResult.RESERIALIZATION_PASSED;
    }

    private static void modifyBufferedData(final Random random, final int size, final BufferedData dataBuffer) {
        final int actualNumberOfBytesToModify = estimateNumberOfBytesToModify(random, size);
        for (int i = 0; i < actualNumberOfBytesToModify; i++) {
            final int randomPosition = random.nextInt(size);
            final byte randomByte = (byte) random.nextInt(256);

            dataBuffer.position(randomPosition);
            dataBuffer.writeByte(randomByte);
        }
    }

    private static <T> BufferedData createBufferedData(
            final T object,
            final Codec<T> codec,
            final int size,
            final String prefix) {
        final BufferedData dataBuffer;
        try {
            dataBuffer = write(object, codec, size);
        } catch (Exception ex) {
            // The test expects a valid input object, so we don't expect this to happen here
            throw new FuzzTestException(prefix + "Unable to write the object", ex);
        }
        return dataBuffer;
    }

}
