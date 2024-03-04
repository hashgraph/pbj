package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hedera.pbj.runtime.jsonparser.JSONParser;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Extends Codec to support indenting.
 *
 * @param <T> The type of object to serialize and deserialize
 */
public interface JsonCodec<T /*extends Record*/> extends Codec<T> {
    // NOTE: When services has finished migrating to protobuf based objects in state,
    // then we should strongly enforce Codec works with Records. This will reduce bugs
    // where people try to use a mutable object.


    /** @inheritDoc */
    default @NonNull T parse(@NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth) throws ParseException {
        try {
            return parse(JsonTools.parseJson(input), strictMode, maxDepth);
        } catch (IOException ex) {
            throw new ParseException(ex);
        }
    }

    /**
     * Parses a HashObject object from JSON parse tree for object JSONParser.ObjContext. Throws if in strict mode ONLY.
     *
     * @param root The JSON parsed object tree to parse data from
     * @return Parsed HashObject model object or null if data input was null or empty
     * @throws ParseException If parsing fails
     */
    @NonNull T parse(
            @Nullable final JSONParser.ObjContext root,
            final boolean strictMode,
            final int maxDepth) throws ParseException;

    /**
     * Writes an item to the given {@link WritableSequentialData}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link WritableSequentialData} to write to.
     * @throws IOException If the {@link WritableSequentialData} cannot be written to.
     */
    default void write(@NonNull T item, @NonNull WritableSequentialData output) throws IOException {
        output.writeUTF8(toJSON(item));
    }

    /**
     * Returns JSON string representing an item.
     *
     * @param item      The item to convert. Must not be null.
     */
    default String toJSON(@NonNull T item) {
        return toJSON(item, "", false);
    }

    /**
     * Returns JSON string representing an item.
     *
     * @param item      The item to convert. Must not be null.
     * @param indent    The indent to use for pretty printing
     * @param inline    When true the output will start with indent end with a new line otherwise
     *                        it will just be the object "{...}"
     */
    String toJSON(@NonNull T item, String indent, boolean inline);

    /**
     * Reads from this data input the length of the data within the input. The implementation may
     * read all the data, or just some special serialized data, as needed to find out the length of
     * the data.
     * <p>
     * This is not an efficient implementation, but it is not considered performance critical for JSON.
     *
     * @param input The input to use
     * @return The length of the data item in the input
     * @throws ParseException If parsing fails
     */
    default int measure(@NonNull ReadableSequentialData input) throws ParseException {
        final long startPosition = input.position();
        parse(input);
        return (int)(input.position() - startPosition);
    }

    /**
     * Compute number of bytes that would be written when calling {@code write()} method.
     * <p>
     * This is not an efficient implementation, but it is not considered performance critical for JSON.
     *
     * @param item The input model data to measure write bytes for
     * @return The length in bytes that would be written
     */
    default int measureRecord(T item) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        WritableStreamingData out = new WritableStreamingData(bout);
        try {
            write(item, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bout.size();
    }

    /**
     * Compares the given item with the bytes in the input, and returns false if it determines that
     * the bytes in the input could not be equal to the given item. Sometimes we need to compare an
     * item in memory with serialized bytes and don't want to incur the cost of deserializing the
     * entire object, when we could have determined the bytes do not represent the same object very
     * cheaply and quickly.
     * <p>
     * This is not an efficient implementation, but it is not considered performance critical for JSON.
     *
     * @param item The item to compare. Cannot be null.
     * @param input The input with the bytes to compare
     * @return true if the bytes represent the item, false otherwise.
     * @throws ParseException If parsing fails
     */
    default boolean fastEquals(@NonNull T item, @NonNull ReadableSequentialData input) throws ParseException {
        return Objects.equals(item, parse(input));
    }
}
