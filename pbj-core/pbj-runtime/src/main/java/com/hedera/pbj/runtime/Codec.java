// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.PbjReader;
import com.hedera.pbj.runtime.io.buffer.PbjWriter;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

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

    private static final class WriteCache {
        PbjWriter writer = new PbjWriter();
        /**  For recursive situations if the caller ever requires it */
        boolean inUse = false;
    }

    private static final class ReadCache {
        PbjReader reader = new PbjReader(Bytes.EMPTY);
        /**  For recursive situations if the caller ever requires it */
        boolean inUse = false;
    }

    private static final ThreadLocal<WriteCache> tlsWriter = ThreadLocal.withInitial(WriteCache::new);
    private static final ThreadLocal<ReadCache> tlsReader = ThreadLocal.withInitial(ReadCache::new);

    /**
     * The actual parsing logic for a specific codec, invoked by the {@link #parse} methods through a single,
     * consistent entry point. Subclasses implement this method rather than {@code parse} directly, since
     * {@code parse} may perform additional work before and after delegating to this implementation.
     *
     * The only difference is it's allowed to return null, the parse overload is expected to call throwOnError.
     *
     * @see #parse(ReadableSequentialData, boolean, boolean, int, int) for a description
     */
    protected abstract T parseImpl(
            @NonNull PbjReader input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException;

    /**
     * Parses an object from the {@link PbjReader} and returns it.
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
     * @param input The {@link PbjReader} from which to read the data to construct an object
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
            @NonNull PbjReader input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException {
        T res = parseImpl(input, strictMode, parseUnknownFields, maxDepth, maxSize);
        input.throwOnError();
        return res;
    }

    /**
     * Same as parse, except doesn't throw. Check for error by using input.error() != 0 (or > 0), or input.ok()
     * Return value may be null
     */
    public final T parseNoEx(
            @NonNull PbjReader input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException {
        return parseImpl(input, strictMode, parseUnknownFields, maxDepth, maxSize);
    }

    /**
     * Same as parseStrict, except doesn't throw. Check for error by using input.error() != 0 (or > 0), or input.ok()
     * Return value may be null
     */
    public final T parseNoEx(@NonNull PbjReader input) throws ParseException {
        return parseNoEx(input, true, false, DEFAULT_MAX_DEPTH, DEFAULT_MAX_SIZE);
    }

    /**
     * Parses an object from the {@link PbjReader} and returns it, using the default `Codec.DEFAULT_MAX_SIZE` and
     * `Codec.DEFAULT_MAX_DEPTH` limits, and without strict mode or unknown field collection.
     *
     * @param input The {@link PbjReader} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(@NonNull PbjReader input) throws ParseException {
        return parse(input, false, false, DEFAULT_MAX_DEPTH, DEFAULT_MAX_SIZE);
    }

    /**
     * Writes the true, logical number of bytes consumed by {@code reader} (which may be less than the number of
     * bytes {@code reader} has physically buffered ahead from {@code input}) back onto {@code input}'s position,
     * so that further reads of a {@link BufferedSequentialData}-backed {@code input} continue exactly where the
     * parsed message ended. A plain stream-backed {@code input} (not a {@link BufferedSequentialData}) cannot be
     * rewound, so it is left as-is and must be treated as fully consumed by the caller.
     */
    private static void syncPosition(@NonNull ReadableSequentialData input, @NonNull PbjReader reader) {
        if (input instanceof BufferedSequentialData bsd) {
            bsd.position(reader.position());
        }
    }

    /**
     * Temporary for test compatibility
     *
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
     * <p>
     * This method uses a thread-local {@link PbjReader} to avoid allocating one per call. As a result it is not
     * reentrant: calling this method again on the same thread before an outer call has returned (e.g. from within
     * {@code parseImpl}) throws a {@link RuntimeException} rather than parsing normally.
     *
     * @param input The {@link ReadableSequentialData} from which to read the data to construct an object
     * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
     * @param parseUnknownFields when {@code true} and strictMode is {@code false}, the parser will collect unknown
     *                           fields in the unknownFields list in the model; otherwise they'll be simply skipped.
     * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
     * @param maxSize a ParseException will be thrown if the size of a delimited field exceeds the limit
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     * @throws RuntimeException If this method is called reentrantly on the same thread
     */
    @NonNull
    public final T parse(
            @NonNull ReadableSequentialData input,
            boolean strictMode,
            boolean parseUnknownFields,
            int maxDepth,
            int maxSize)
            throws ParseException {

        ReadCache cache = tlsReader.get();
        if (cache.inUse) {
            throw new RuntimeException("tls reader already in use, avoid recursion");
        }
        cache.inUse = true;
        PbjReader reader = cache.reader;
        try {
            reader.resetWith(input);
            T res = parse(reader, strictMode, parseUnknownFields, maxDepth, maxSize);
            reader.throwOnError();
            return res;
        } finally {
            syncPosition(input, reader);
            cache.inUse = false;
        }
    }

    /**
     * Temporary for test compatibility
     *
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
     * Temporary for test compatibility
     *
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
        return parse(bytes, strictMode, false, maxDepth, DEFAULT_MAX_SIZE);
    }

    /**
     * Temporary for test compatibility
     *
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
     * <p>
     * This default implementation uses the default limit of `Codec.DEFAULT_MAX_SIZE` for `maxSize`
     *
     * @param bytes The {@link Bytes} from which to read the data to construct an object
     * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
     * @param parseUnknownFields when {@code true} and strictMode is {@code false}, the parser will collect unknown
     *                           fields in the unknownFields list in the model; otherwise they'll be simply skipped.
     * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    public final T parse(@NonNull Bytes bytes, boolean strictMode, boolean parseUnknownFields, int maxDepth)
            throws ParseException {
        return parse(bytes, strictMode, parseUnknownFields, maxDepth, DEFAULT_MAX_SIZE);
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
     * <p>
     * The {@code maxSize} specifies a custom value for the default `Codec.DEFAULT_MAX_SIZE` limit. IMPORTANT:
     * specifying a value larger than the default one can put the application at risk because a maliciously-crafted
     * payload can cause the parser to allocate too much memory which can result in OutOfMemory and/or crashes.
     * It's important to carefully estimate the maximum size limit that a particular protobuf model type should support,
     * and then pass that value as a parameter. Note that the estimated limit should apply to the **type** as a whole,
     * rather than to individual instances of the model. In other words, this value should be a constant, or a config
     * value that is controlled by the application, rather than come from the input that the application reads.
     * When in doubt, use the other overloaded versions of this method that use the default `Codec.DEFAULT_MAX_SIZE`.
     * <p>
     * This method uses a thread-local {@link PbjReader} to avoid allocating one per call. As a result it is not
     * reentrant: calling this method again on the same thread before an outer call has returned (e.g. from within
     * {@code parseImpl}) throws a {@link RuntimeException} rather than parsing normally.
     *
     * @param bytes The {@link Bytes} from which to read the data to construct an object
     * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
     * @param parseUnknownFields when {@code true} and strictMode is {@code false}, the parser will collect unknown
     *                           fields in the unknownFields list in the model; otherwise they'll be simply skipped.
     * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
     * @param maxSize a ParseException will be thrown if the size of a delimited field exceeds the limit
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     * @throws RuntimeException If this method is called reentrantly on the same thread
     */
    @NonNull
    public final T parse(
            @NonNull Bytes bytes, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException {

        ReadCache cache = tlsReader.get();
        if (cache.inUse) {
            throw new RuntimeException("tls reader already in use, avoid recursion");
            /*
            PbjReader reader = new PbjReader(input);
            T res = parse(bytes, strictMode, parseUnknownFields, maxDepth, maxSize);
            reader.throwOnError();
            return res; //*/
        }
        cache.inUse = true;
        PbjReader reader = cache.reader;
        try {
            reader.resetWith(bytes);
            T res = parse(reader, strictMode, parseUnknownFields, maxDepth, maxSize);
            reader.throwOnError();
            return res;
        } finally {
            cache.inUse = false;
        }
    }

    /**
     * Temporary for test compatibility
     *
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
        return parseStrict(bytes.toReadableSequentialData());
    }

    /**
     * The actual writing logic for a specific codec, invoked by the {@link #write} methods through a single,
     * consistent entry point. Subclasses implement this method rather than {@code write} directly, since
     * {@code write} may perform additional work before and after delegating to this implementation.
     *
     * @see #write(Object, PbjWriter) for a description
     */
    protected abstract void writeImpl(@NonNull T item, @NonNull PbjWriter output);

    /**
     * Writes an item to the given {@link PbjWriter}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link PbjWriter} to write to.
     */
    public final void write(@NonNull T item, @NonNull PbjWriter output) {
        writeImpl(item, output);
        output.throwOnError();
    }

    /**
     * Writes an item to the given {@link PbjWriter}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link PbjWriter} to write to.
     */
    public final void writeNoEx(@NonNull T item, @NonNull PbjWriter output) {
        writeImpl(item, output);
    }

    /**
     * Temporary for test compatibility
     *
     * Writes an item to the given {@link WritableSequentialData}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link WritableSequentialData} to write to.
     * @throws IOException If the {@link WritableSequentialData} cannot be written to.
     */
    public void write(@NonNull T item, @NonNull WritableSequentialData output) throws IOException {
        WriteCache cache = tlsWriter.get();
        if (cache.inUse) {
            throw new RuntimeException("tls writer already in use, avoid recursion");
        }
        cache.inUse = true;
        try {
            cache.writer.resetWith(output);
            write(item, cache.writer);
            cache.writer.flush();
        } finally {
            cache.inUse = false;
        }
    }

    /**
     * Writes an item to the given byte array, this is a performance focused method. In non-performance centric use
     * cases there are simpler methods such as {@link #toBytes(T)} or writing to a {@link WritableStreamingData}.
     *
     * @param item The item to write. Must not be null.
     * @param output The byte array to write to, this must be large enough to hold the entire item.
     * @param startOffset The offset in the output array to start writing at.
     * @return The number of bytes written to the output array.
     * @throws RuntimeException If there is a problem writing to the output array.
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
     * {@link #writeImpl(Object, PbjWriter)} so that hand-written codecs don't need to provide one.
     *
     * @see #write(Object, byte[], int) for a description
     */
    protected int writeImpl(@NonNull T item, @NonNull byte[] output, final int startOffset) {
        WriteCache cache = tlsWriter.get();
        if (cache.inUse) {
            throw new RuntimeException("tls writer already in use, avoid recursion");
        }
        cache.inUse = true;
        try {
            cache.writer.resetWithNull();
            writeImpl(item, cache.writer);
        } finally {
            cache.writer.flush();
            cache.inUse = false;
        }
        return cache.writer.position() - startOffset;
    }

    /**
     * Reads from this data input the length of the data within the input. The implementation may
     * read all the data, or just some special serialized data, as needed to find out the length of
     * the data.
     *
     * @param input The input to use
     * @return The length of the data item in the input
     * @throws ParseException If parsing fails
     */
    public abstract int measure(@NonNull PbjReader input) throws ParseException;

    /*
    public int measure(@NonNull PbjReader input) throws ParseException {
        final long startPosition = input.position();
        parse(input);
        return (int) (input.position() - startPosition);
    }
    //*/

    /**
     * Temporary for test compatibility
     *
     * Reads from this data input the length of the data within the input. The implementation may
     * read all the data, or just some special serialized data, as needed to find out the length of
     * the data.
     * <p>
     * If {@code input} is a {@link BufferedSequentialData} (e.g. {@code BufferedData}), its position is
     * updated to reflect exactly the bytes consumed by this call. A plain stream-backed input should be
     * treated as fully consumed afterward.
     *
     * @param input The input to use
     * @return The length of the data item in the input
     * @throws ParseException If parsing fails
     */
    public final int measure(@NonNull ReadableSequentialData input) throws ParseException {
        ReadCache cache = tlsReader.get();
        if (cache.inUse) {
            throw new RuntimeException("tls reader already in use, avoid recursion");
        }
        cache.inUse = true;
        PbjReader reader = cache.reader;
        try {
            reader.resetWith(input);
            return measure(reader);
        } finally {
            syncPosition(input, reader);
            cache.inUse = false;
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
     * Compares the given item with the bytes in the input, and returns false if it determines that
     * the bytes in the input could not be equal to the given item. Sometimes we need to compare an
     * item in memory with serialized bytes and don't want to incur the cost of deserializing the
     * entire object, when we could have determined the bytes do not represent the same object very
     * cheaply and quickly.
     *
     * @param item The item to compare. Cannot be null.
     * @param input The input with the bytes to compare
     * @return true if the bytes represent the item, false otherwise.
     * @throws ParseException If parsing fails
     */
    public abstract boolean fastEquals(@NonNull T item, @NonNull PbjReader input) throws ParseException;

    /**
     * Temporary for test compatibility
     *
     * Compares the given item with the bytes in the input, and returns false if it determines that
     * the bytes in the input could not be equal to the given item. Sometimes we need to compare an
     * item in memory with serialized bytes and don't want to incur the cost of deserializing the
     * entire object, when we could have determined the bytes do not represent the same object very
     * cheaply and quickly.
     * <p>
     * If {@code input} is a {@link BufferedSequentialData} (e.g. {@code BufferedData}), its position is
     * updated to reflect exactly the bytes consumed by this call. A plain stream-backed input should be
     * treated as fully consumed afterward.
     *
     * @param item The item to compare. Cannot be null.
     * @param input The input with the bytes to compare
     * @return true if the bytes represent the item, false otherwise.
     * @throws ParseException If parsing fails
     */
    public final boolean fastEquals(@NonNull T item, @NonNull ReadableSequentialData input) throws ParseException {
        ReadCache cache = tlsReader.get();
        if (cache.inUse) {
            throw new RuntimeException("tls reader already in use, avoid recursion");
        }
        cache.inUse = true;
        PbjReader reader = cache.reader;
        try {
            reader.resetWith(input);
            return fastEquals(item, reader);
        } finally {
            syncPosition(input, reader);
            cache.inUse = false;
        }
    }

    /**
     * Converts a Record into a Bytes object
     *
     * @param item The input model data to convert into a Bytes object.
     * @return The new Bytes object.
     * @throws RuntimeException wrapping an IOException If it is impossible
     * to write to the {@link WritableStreamingData}
     */
    public final Bytes toBytes(@NonNull T item) {
        WriteCache cache = tlsWriter.get();
        if (cache.inUse) {
            throw new RuntimeException("tls writer already in use, avoid recursion");
        }
        cache.inUse = true;
        try {
            cache.writer.resetWithNull();
            return toBytes(item, cache.writer);
        } finally {
            cache.inUse = false;
        }
    }

    /**
     * Writes an item using the given {@link PbjWriter} and returns the written bytes.
     * <p>
     * The {@code writer} must be a standalone (non-streaming) writer, since the result is obtained via
     * {@link PbjWriter#toByteArrayWrapped()}; a writer backed by an {@link java.io.OutputStream} or
     * {@link WritableSequentialData} will report a usage error instead of returning the bytes.
     *
     * @param item The item to write. Must not be null.
     * @param writer The {@link PbjWriter} to write the item to.
     * @return The bytes written to {@code writer}, wrapped in a {@link Bytes} instance.
     */
    public final Bytes toBytes(@NonNull T item, PbjWriter writer) {
        write(item, writer);
        return writer.toByteArrayWrapped();
    }

    /**
     * Get the default value for the model class.
     *
     * @return The default value for the model class
     */
    public abstract T getDefaultInstance();
}
