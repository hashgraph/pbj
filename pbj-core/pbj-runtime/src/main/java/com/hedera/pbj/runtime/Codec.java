// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.ArrayCursor;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Encapsulates Serialization, Deserialization and other IO operations.
 *
 * @param <T> The type of object to serialize and deserialize
 */
public abstract class Codec<T> {

    /**
     * The default maximum size of a repeated or length-encoded field (Bytes, String, Message, etc.).
     * The size should not be increased beyond the current limit because of the safety concerns.
     * An application can override this limit when calling the `Codec.parse()` method for a specific
     * protobuf model type if that model is allowed to contain larger fields.
     */
    public static final int DEFAULT_MAX_SIZE = 2 * 1024 * 1024;

    /**
     * The default maximum depth of nested messages before the `parse()` method would error out.
     * Applications can always override the maxDepth by supplying an argument to the main `Codec.parse()` method.
     * The default depth should not be increased beyond the current limit because of the safety concerns.
     */
    public static final int DEFAULT_MAX_DEPTH = 128;

    /**
     * The actual parsing logic for a specific codec, invoked by the {@link #parse} methods through a single,
     * consistent entry point. Subclasses implement this method rather than {@code parse} directly, since
     * {@code parse} may perform additional work before and after delegating to this implementation.
     *
     * @see #parse(ReadableSequentialData, boolean, boolean, int, int) for a description
     */
    @NonNull
    protected abstract T parseImpl(
            @NonNull ReadableSequentialData input,
            boolean strictMode,
            boolean parseUnknownFields,
            int maxDepth,
            int maxSize)
            throws ParseException;

    /**
     * Whether this codec has a byte-array parser, generated for messages with {@code pbj.fast_parse}. When it does,
     * {@link #parse} uses {@link #parseArrayImpl} for array-backed input.
     *
     * @return true if {@link #parseArrayImpl} is implemented
     */
    protected boolean hasArrayParser() {
        return false;
    }

    /**
     * Parse from a byte array, with the same results and errors as {@link #parseImpl}. Implemented by codecs for which
     * {@link #hasArrayParser()} is true.
     *
     * @see #parse(ReadableSequentialData, boolean, boolean, int, int) for a description of the parameters
     */
    @NonNull
    protected T parseArrayImpl(
            @NonNull ArrayCursor input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException {
        throw new UnsupportedOperationException(
                "No array parser in " + getClass().getName());
    }

    /**
     * Parses an object from the {@link ReadableSequentialData} and returns it.
     * <p>
     * If {@code strictMode} is {@code true}, then throws an exception if fields
     * have been defined on the encoded object that are not supported by the parser. This
     * breaks forwards compatibility (an older parser cannot parse a newer encoded object),
     * which is sometimes requires to avoid parsing an object that is newer than the code
     * parsing it is prepared to handle.
     * <p>
     * The {@code maxDepth} specifies the maximum allowed depth of nested messages. The parsing
     * will fail with a ParseException if the maximum depth is reached.
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
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
     * @param parseUnknownFields when {@code true} and strictMode is {@code false}, the parser will collect unknown
     *                           fields in the unknownFields list in the model; otherwise they'll be simply skipped.
     * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
     * @param maxSize a ParseException will be thrown if the size of a delimited field exceeds the limit
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(
            @NonNull ReadableSequentialData input,
            boolean strictMode,
            boolean parseUnknownFields,
            int maxDepth,
            int maxSize)
            throws ParseException {
        if (hasArrayParser()) {
            // array-backed input goes to the generated byte-array parser, which then advances the input's position
            final ArrayCursor cursor = ArrayCursor.of(input);
            if (cursor != null) {
                final T value = parseArrayImpl(cursor, strictMode, parseUnknownFields, maxDepth, maxSize);
                input.skip(cursor.consumed());
                return value;
            }
        }
        // Generated parsers narrow the input's limit to each nested message and, for speed, do not restore it when
        // parsing fails part way. Restore it here, so callers see the same limit after a failure as before the call.
        final long limit = input.limit();
        try {
            return parseImpl(input, strictMode, parseUnknownFields, maxDepth, maxSize);
        } catch (final ParseException | RuntimeException e) {
            input.limit(limit);
            throw e;
        }
    }

    /**
     * Parses an object from the {@link ReadableSequentialData} and returns it.
     * <p>
     * If {@code strictMode} is {@code true}, then throws an exception if fields
     * have been defined on the encoded object that are not supported by the parser. This
     * breaks forwards compatibility (an older parser cannot parse a newer encoded object),
     * which is sometimes requires to avoid parsing an object that is newer than the code
     * parsing it is prepared to handle.
     * <p>
     * The {@code maxDepth} specifies the maximum allowed depth of nested messages. The parsing
     * will fail with a ParseException if the maximum depth is reached.
     * <p>
     * This default implementation uses the default limit of `Codec.DEFAULT_MAX_SIZE` for `maxSize`
     *
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
     * @param parseUnknownFields when {@code true} and strictMode is {@code false}, the parser will collect unknown
     *                           fields in the unknownFields list in the model; otherwise they'll be simply skipped.
     * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(
            @NonNull ReadableSequentialData input, boolean strictMode, boolean parseUnknownFields, int maxDepth)
            throws ParseException {
        return parse(input, strictMode, parseUnknownFields, maxDepth, DEFAULT_MAX_SIZE);
    }
    /**
     * Parses an object from the {@link ReadableSequentialData} and returns it.
     * <p>
     * If {@code strictMode} is {@code true}, then throws an exception if fields
     * have been defined on the encoded object that are not supported by the parser. This
     * breaks forwards compatibility (an older parser cannot parse a newer encoded object),
     * which is sometimes requires to avoid parsing an object that is newer than the code
     * parsing it is prepared to handle.
     * <p>
     * The {@code maxDepth} specifies the maximum allowed depth of nested messages. The parsing
     * will fail with a ParseException if the maximum depth is reached.
     *
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
     * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(@NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth)
            throws ParseException {
        return parse(input, strictMode, false, maxDepth);
    }

    /**
     * Parses an object from the {@link Bytes} and returns it.
     * <p>
     * If {@code strictMode} is {@code true}, then throws an exception if fields
     * have been defined on the encoded object that are not supported by the parser. This
     * breaks forwards compatibility (an older parser cannot parse a newer encoded object),
     * which is sometimes requires to avoid parsing an object that is newer than the code
     * parsing it is prepared to handle.
     * <p>
     * The {@code maxDepth} specifies the maximum allowed depth of nested messages. The parsing
     * will fail with a ParseException if the maximum depth is reached.
     *
     * @param bytes The {@link Bytes} from which to read the data to construct an object
     * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
     * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(@NonNull Bytes bytes, final boolean strictMode, final int maxDepth) throws ParseException {
        if (hasArrayParser()) {
            return parseArrayImpl(ArrayCursor.of(bytes), strictMode, false, maxDepth, DEFAULT_MAX_SIZE);
        }
        return parse(bytes.toReadableSequentialData(), strictMode, maxDepth);
    }

    /**
     * Parses an object from the {@link ReadableSequentialData} and returns it.
     *
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(@NonNull ReadableSequentialData input) throws ParseException {
        return parse(input, false, DEFAULT_MAX_DEPTH);
    }

    /**
     * Parses an object from the {@link Bytes} and returns it.
     *
     * @param bytes The {@link Bytes} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(@NonNull Bytes bytes) throws ParseException {
        return parse(bytes, false, DEFAULT_MAX_DEPTH);
    }

    /**
     * Parses an object from the {@link ReadableSequentialData} and returns it. Throws an exception if fields
     * have been defined on the encoded object that are not supported by the parser. This
     * breaks forwards compatibility (an older parser cannot parse a newer encoded object),
     * which is sometimes requires to avoid parsing an object that is newer than the code
     * parsing it is prepared to handle.
     *
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parseStrict(@NonNull ReadableSequentialData input) throws ParseException {
        return parse(input, true, DEFAULT_MAX_DEPTH);
    }

    /**
     * Parses an object from the {@link Bytes} and returns it. Throws an exception if fields
     * have been defined on the encoded object that are not supported by the parser. This
     * breaks forwards compatibility (an older parser cannot parse a newer encoded object),
     * which is sometimes requires to avoid parsing an object that is newer than the code
     * parsing it is prepared to handle.
     *
     * @param bytes The {@link Bytes} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parseStrict(@NonNull Bytes bytes) throws ParseException {
        return parse(bytes, true, DEFAULT_MAX_DEPTH);
    }

    /**
     * The actual writing logic for a specific codec, invoked by the {@link #write} methods through a single,
     * consistent entry point. Subclasses implement this method rather than {@code write} directly, since
     * {@code write} may perform additional work before and after delegating to this implementation.
     *
     * @see #write(Object, WritableSequentialData) for a description
     */
    protected abstract void writeImpl(@NonNull T item, @NonNull WritableSequentialData output) throws IOException;

    /**
     * Writes an item to the given {@link WritableSequentialData}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link WritableSequentialData} to write to.
     * @throws IOException If the {@link WritableSequentialData} cannot be written to.
     */
    public final void write(@NonNull T item, @NonNull WritableSequentialData output) throws IOException {
        writeImpl(item, output);
    }

    /**
     * Writes an item to the given byte array, this is a performance focused method. In non-performance centric use
     * cases there are simpler methods such as {@link #toBytes(T)} or writing to a {@link WritableStreamingData}.
     *
     * @param item The item to write. Must not be null.
     * @param output The byte array to write to, this must be large enough to hold the entire item.
     * @param startOffset The offset in the output array to start writing at.
     * @return The number of bytes written to the output array.
     * @throws UncheckedIOException If the there is a problem writing to the output array.
     * @throws IndexOutOfBoundsException If the output array is not large enough to hold the entire item.
     */
    public final int write(@NonNull T item, @NonNull byte[] output, final int startOffset) {
        return writeImpl(item, output, startOffset);
    }

    /**
     * CodecWriteByteArrayMethodGenerator.java generates this method for Codecs but not JsonCodecs
     * We provide a default implementation here so hand written codecs don't need to
     *
     * The actual byte-array writing logic for a specific codec, invoked by the {@link #write(Object, byte[], int)}
     * methods through a single, consistent entry point. Generated codecs override this method with a
     * performance-focused implementation; this default implementation delegates to
     * {@link #writeImpl(Object, WritableSequentialData)} so that hand-written codecs don't need to provide one.
     *
     * @see #write(Object, byte[], int) for a description
     */
    protected int writeImpl(@NonNull T item, @NonNull byte[] output, final int startOffset) {
        final BufferedData bufferedData = BufferedData.wrap(output, startOffset, output.length - startOffset);
        try {
            write(item, bufferedData);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // the wrapped buffer's position counts from the start of the array, not from startOffset
        return (int) bufferedData.position() - startOffset;
    }

    /**
     * Write an item back to front into a reusable {@link ReverseBuffer}. The output, {@code buffer.bytes()}, is the same
     * bytes {@link #toBytes} returns. Codecs generated with {@code pbj.fast_write} write in reverse, so nothing is
     * measured first; this default implementation measures the item and writes it forward into the buffer.
     *
     * <p>The buffer is not reset: call {@link ReverseBuffer#reset()} first to write one item on its own.
     *
     * @param item the item to write
     * @param buffer the buffer to write into
     */
    public void writeReverse(@NonNull final T item, @NonNull final ReverseBuffer buffer) {
        final int size = measureRecord(item);
        final int offset = buffer.reserve(size);
        // the array holds output after the reserved bytes, so a writer that disagrees with measureRecord must fail
        // rather than return output with bytes overwritten or left over from an earlier message
        final int written = write(item, buffer.array(), offset);
        if (written != size) {
            throw new IllegalStateException("Wrote " + written + " bytes, but measured " + size);
        }
    }

    /**
     * Compute number of bytes that would be written when calling {@code write()} method.
     *
     * @param item The input model data to measure write bytes for
     * @return The length in bytes that would be written
     */
    public abstract int measureRecord(T item);

    /**
     * Converts a Record into a Bytes object
     *
     * @param item The input model data to convert into a Bytes object.
     * @return The new Bytes object.
     * @throws RuntimeException wrapping an IOException If it is impossible
     * to write to the {@link WritableStreamingData}
     */
    public Bytes toBytes(@NonNull T item) {
        // it is cheaper performance wise to measure the size of the object first than grow a buffer as needed
        final byte[] bytes = new byte[measureRecord(item)];
        // the byte array writer, which generated codecs implement with their fastest code
        write(item, bytes, 0);
        return Bytes.wrap(bytes);
    }

    /**
     * Get the default value for the model class.
     *
     * @return The default value for the model class
     */
    public abstract T getDefaultInstance();
}
