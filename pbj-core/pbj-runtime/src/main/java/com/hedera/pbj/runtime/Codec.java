package com.hedera.pbj.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.NoSuchElementException;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates Serialization, Deserialization and other IO operations.
 *
 * @param <T> The type of object to serialize and deserialize
 */
public interface Codec<T /*extends Record*/> {
    // NOTE: When services has finished migrating to protobuf based objects in state,
    // then we should strongly enforce Codec works with Records. This will reduce bugs
    // where people try to use a mutable object.
    /**
     * Parses an object from the {@link ReadableSequentialData} and returns it.
     *
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws IOException If it is impossible to read from the {@link ReadableSequentialData}
     * @throws NoSuchElementException If there is no element of type T that can be parsed from this
     *     input
     */
    @NonNull T parse(@NonNull ReadableSequentialData input) throws IOException;

    /**
     * Parses an object from the {@link ReadableSequentialData} and returns it. Throws an exception if fields
     * have been defined on the encoded object that are not supported by the parser. This
     * breaks forwards compatibility (an older parser cannot parse a newer encoded object),
     * which is sometimes requires to avoid parsing an object that is newer than the code
     * parsing it is prepared to handle.
     *
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws IOException If it is impossible to read from the {@link ReadableSequentialData}
     * @throws UnknownFieldException If an unknown field is encountered while parsing the object
     * @throws NoSuchElementException If there is no element of type T that can be parsed from this
     *     input
     */
    @NonNull T parseStrict(@NonNull ReadableSequentialData input) throws IOException;

    /**
     * Writes an item to the given {@link WritableSequentialData}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link WritableSequentialData} to write to.
     * @throws IOException If the {@link WritableSequentialData} cannot be written to.
     */
    void write(@NonNull T item, @NonNull WritableSequentialData output) throws IOException;

    /**
     * Reads from this data input the length of the data within the input. The implementation may
     * read all the data, or just some special serialized data, as needed to find out the length of
     * the data.
     *
     * @param input The input to use
     * @return The length of the data item in the input
     * @throws IOException If it is impossible to read from the {@link ReadableSequentialData}
     */
    int measure(@NonNull ReadableSequentialData input) throws IOException;

    /**
     * Compute number of bytes that would be written when calling {@code write()} method.
     *
     * @param item The input model data to measure write bytes for
     * @return The length in bytes that would be written
     */
    int measureRecord(T item);

    /**
     * Compares the given item with the bytes in the input, and returns false if it determines that
     * the bytes in the input could not be equal to the given item. Sometimes we need to compare an
     * item in memory with serialized bytes and don't want to incur the cost of deserializing the
     * entire object, when we could have determined the bytes do not represent the same object very
     * cheaply and quickly.
     *
     * @param item The item to compare. Cannot be null.
     * @param input The input with the bytes to compare
     * @return true if the bytes represent the item, false otherwise.
     * @throws IOException If it is impossible to read from the {@link ReadableSequentialData}
     */
    boolean fastEquals(@NonNull T item, @NonNull ReadableSequentialData input) throws IOException;

    /**
     * Converts a Record into a Bytes object
     *
     * @param item The input model data to convert into a Bytes object.
     * @return The new Bytes object.
     * @throws RuntimeException wrapping an IOException If it is impossible
     * to write to the {@link WritableStreamingData}
     */
    default Bytes toBytes(@NonNull T item) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        WritableStreamingData writableStreamingData = new WritableStreamingData(byteArrayOutputStream);
        try {
            write(item, writableStreamingData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Bytes.wrap(byteArrayOutputStream.toByteArray());
    }
}
