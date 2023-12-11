package com.hedera.pbj.integration.fuzz;

/**
 * An enum describing possible outcomes of a fuzz test run for a Protobuf codec.
 *
 * A typical fuzz test performs the following actions:
 * <ol>
 * <p> 1. Serializes a valid object into a byte array
 * <p> 2. Modifies a random element of the byte array randomly
 * <p> 3. Deserializes the modified byte array. <b>This is where an exception thrown
 * by the codec is considered to be the best possible outcome of a test run.</b>
 * <p> 4. Compares the measured size of the deserialized object to the measured size
 * of the original object. The test ends if they differ.
 * <p> 5. Reserializes the previously deserialized object into a new byte array.
 * The test ends if codec throws an exception.
 * <p> 6. Compares the bytes of the modified array from step #2 and the new array
 * from step #5. The test ends if the contents differ.
 * <p> 7. Finally, the test ends unconditionally w/o any conclusion because the
 * test was unable to make the codec fail.
 * </ol>
 */
public enum SingleFuzzTestResult {
    /**
     * codec.parse() threw an exception at step #3.
     *
     * This indicates that the codec fails on malformed data
     * which is exactly what we want it to do.
     */
    DESERIALIZATION_FAILED,

    /**
     * codec.parse() with fuzz bytes returned an object whose measured size
     * differs from the measured size of the original object at step #4.
     *
     * This indicates that the fuzz data appears to be a correct
     * binary message for an object that may differ from the original input object.
     * There may or may not be bugs in the codec, but this test run
     * failed to ultimately reveal any.
     */
    DESERIALIZED_SIZE_MISMATCHED,

    /**
     * codec.write() threw an exception for a previously deserialized object at step #5.
     *
     * This means that the deserialized object produced at step #3 is invalid from the serializer
     * perspective, which means that the deserializer can read malformed data and produce
     * such malformed objects which may be a potential bug in the deserializer.
     */
    RESERIALIZATION_FAILED,

    /**
     * codec.write() produced bytes different from the fuzz bytes at step #6.
     *
     * This means that the deserializer at step #3 may have ignored fuzz data
     * producing an object that doesn't match its binary representation from step #2.
     * Alternatively, the serializer at step #5 may have ignored a certain invalid
     * state of the deserialized object from step #3.
     * This may be a potential bug in the codec.
     */
    RESERIALIZATION_MISMATCHED,

    /**
     * codec.write() produced bytes identical to the fuzz bytes at step #6.
     *
     * This means that the fuzz data resulted in a correct binary message.
     * It's unclear if there are any bugs, but this test run was unable to
     * reveal any.
     */
    RESERIALIZATION_PASSED;

}
