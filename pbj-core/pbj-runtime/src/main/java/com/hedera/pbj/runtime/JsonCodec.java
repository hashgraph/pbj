// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.PbjReader;
import com.hedera.pbj.runtime.io.buffer.PbjWriter;
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
public abstract class JsonCodec<T> extends Codec<T> {

    /** {@inheritDoc} */
    @Override
    protected final T parseImpl(
            @NonNull PbjReader input,
            final boolean strictMode,
            final boolean parseUnknownFields,
            final int maxDepth,
            final int maxSize) {
        return parseImpl(JsonTools.parseJson(input), input, strictMode, maxDepth, maxSize);
    }

    /**
     * The actual parsing logic for a specific codec, invoked by the {@link #parseNoEx} methods through a single,
     * consistent entry point. Subclasses implement this method rather than {@code parseNoEx} directly, since
     * {@code parseNoEx} may perform additional work before and after delegating to this implementation.
     *
     * @see #parseNoEx(JSONParser.ObjContext, PbjReader, boolean, int, int) for a description of each parameter
     * @return The parsed object, or {@code null} if an error was set on {@code input}
     */
    protected abstract T parseImpl(
            @Nullable final JSONParser.ObjContext root,
            @NonNull final PbjReader input,
            final boolean strictMode,
            final int maxDepth,
            final int maxSize);

    /**
     * Parses a HashObject object from JSON parse tree for object JSONParser.ObjContext. Sets an error on
     * {@code input} in strict mode ONLY. Same as parse, except doesn't throw. Check for error by using
     * {@code input.error() != 0} (or {@code > 0}), or {@code input.ok()}. Return value may be null.
     * <p>
     * The {@code maxSize} specifies a custom value for the default `Codec.DEFAULT_MAX_SIZE` limit. IMPORTANT:
     * specifying a value larger than the default one can put the application at risk because a maliciously-crafted
     * payload can cause the parser to allocate too much memory which can result in OutOfMemory and/or crashes.
     * It's important to carefully estimate the maximum size limit that a particular protobuf model type should support,
     * and then pass that value as a parameter. Note that the estimated limit should apply to the **type** as a whole,
     * rather than to individual instances of the model. In other words, this value should be a constant, or a config
     * value that is controlled by the application, rather than come from the input that the application reads.
     * When in doubt, use the other overloaded versions of this method that use the default `Codec.DEFAULT_MAX_SIZE`.
     *
     * @param root The JSON parsed object tree to parse data from
     * @param input the {@link PbjReader} used solely to carry error state for this parse; sets
     *              {@link PbjReader#PARSE} if the size of a delimited field exceeds the limit
     * @return Parsed HashObject model object, or {@code null} if data input was null or empty, or an error was set
     */
    public final T parseNoEx(
            @Nullable final JSONParser.ObjContext root,
            @NonNull final PbjReader input,
            final boolean strictMode,
            final int maxDepth,
            final int maxSize) {
        return parseImpl(root, input, strictMode, maxDepth, maxSize);
    }

    /** {@inheritDoc} */
    @Override
    protected final void writeImpl(@NonNull T item, @NonNull PbjWriter output) {
        output.writeStringNoTag(toJSON(item));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Writes directly via {@code output.writeUTF8(...)} instead of routing through a {@link PbjWriter}, since some
     * {@link WritableSequentialData} implementations only support the string-level {@code writeUTF8} hook and not
     * raw byte writes.
     */
    @Override
    public void write(@NonNull T item, @NonNull WritableSequentialData output) throws IOException {
        output.writeUTF8(toJSON(item));
    }
    /**
     * Returns JSON string representing an item.
     *
     * @param item      The item to convert. Must not be null.
     */
    public final String toJSON(@NonNull T item) {
        return toJSON(item, "", false);
    }

    /**
     * The actual JSON-writing logic for a specific codec, invoked by the {@link #toJSON} methods through a single,
     * consistent entry point. Subclasses implement this method rather than {@code toJSON} directly, since
     * {@code toJSON} may perform additional work before and after delegating to this implementation.
     *
     * @see #toJSON(Object, String, boolean) for a description
     */
    protected abstract String toJSONImpl(@NonNull T item, String indent, boolean inline);

    /**
     * Returns JSON string representing an item.
     *
     * @param item      The item to convert. Must not be null.
     * @param indent    The indent to use for pretty printing
     * @param inline    When true the output will start with indent end with a new line otherwise
     *                        it will just be the object "{...}"
     */
    public final String toJSON(@NonNull T item, String indent, boolean inline) {
        return toJSONImpl(item, indent, inline);
    }

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
    @Override
    public final int measure(@NonNull PbjReader input) throws ParseException {
        final long startPosition = input.position();
        parse(input);
        return (int) (input.position() - startPosition);
    }

    /**
     * Compute number of bytes that would be written when calling {@code write()} method.
     * <p>
     * This is not an efficient implementation, but it is not considered performance critical for JSON.
     *
     * @param item The input model data to measure write bytes for
     * @return The length in bytes that would be written
     */
    public final int measureRecord(T item) {
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
    @Override
    public final boolean fastEquals(@NonNull T item, @NonNull PbjReader input) throws ParseException {
        return Objects.equals(item, parse(input));
    }

    @Override
    public final T getDefaultInstance() {
        return null;
    }
}
