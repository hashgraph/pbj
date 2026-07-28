// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.PbjReader;
import com.hedera.pbj.runtime.io.PbjWriter;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates Serialization, Deserialization and other IO operations.
 *
 * @param <T> The type of object to serialize and deserialize
 */
public interface Codec<T> {

    static final boolean logNonPbjReads = false,
            logNonPbjWrites = false,
            logGoodPath = false,
            disallowNonPbjReader = false,
            disallowNonPbjWriter = false;

    class WriteCache {
        PbjWriter writer = new PbjWriter();
        boolean inUse = false;
    }

    class ReadCache {
        PbjReader reader = new PbjReader(Bytes.EMPTY);
        boolean inUse = false;
    }

    ThreadLocal<WriteCache> tlsWriter = ThreadLocal.withInitial(WriteCache::new);
    ThreadLocal<ReadCache> tlsReader = ThreadLocal.withInitial(ReadCache::new);

    Set<String> seenStacks = ConcurrentHashMap.newKeySet();
    PrintStream strackTraceLogger = openTraceFile();

    private static PrintStream openTraceFile() {
        try {
            var stream = new PrintStream(new FileOutputStream("/tmp/ldintr.txt", false), true);
            stream.println("Runing------ %b %b".formatted(logNonPbjReads, logNonPbjWrites));
            return stream;
        } catch (IOException e) {
            return System.err;
        }
    }

    private static void logStack(boolean enabled, String header) {
        if (!enabled) return;
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (seenStacks.add(Arrays.toString(stack))) {
            StringBuilder sb = new StringBuilder(header).append('\n');
            for (StackTraceElement e : stack) sb.append("\tat ").append(e).append('\n');
            strackTraceLogger.println(sb);
        }
    }

    default void logGood() {
        logStack(logGoodPath, "Pbj path:");
    }

    default void logRead() {
        logStack(logNonPbjReads, "parse(ReadableSequentialData) called via non-PbjReader path:");
    }

    default void logWrite() {
        logStack(logNonPbjWrites, "parse(ReadableSequentialData) called via non-PbjReader path:");
    }

    default void dbgLog() {
        logStack(true, "Unexcepected Condition:");
    }

    /**
     * The default maximum size of a repeated or length-encoded field (Bytes, String, Message, etc.).
     * The size should not be increased beyond the current limit because of the safety concerns.
     * An application can override this limit when calling the `Codec.parse()` method for a specific
     * protobuf model type if that model is allowed to contain larger fields.
     */
    int DEFAULT_MAX_SIZE = 2 * 1024 * 1024;

    /**
     * The default maximum depth of nested messages before the `parse()` method would error out.
     * Applications can always override the maxDepth by supplying an argument to the main `Codec.parse()` method.
     * The default depth should not be increased beyond the current limit because of the safety concerns.
     */
    int DEFAULT_MAX_DEPTH = 128;

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
    default @NonNull T parse(
            @NonNull ReadableSequentialData input,
            boolean strictMode,
            boolean parseUnknownFields,
            int maxDepth,
            int maxSize)
            throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        logRead();
        PbjReader reader = new PbjReader(input);
        T res = parse(reader, strictMode, parseUnknownFields, maxDepth, maxSize);
        reader.throwOnError();
        return res;
    }

    @NonNull
    default T parse(@NonNull PbjReader input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException {
        logGood();
        T res = realParse(input, strictMode, parseUnknownFields, maxDepth, maxSize);
        input.throwOnError();
        return res;
    }

    @NonNull
    T realParse(@NonNull PbjReader input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException;

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
    default T parse(@NonNull ReadableSequentialData input, boolean strictMode, boolean parseUnknownFields, int maxDepth)
            throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        logRead();
        PbjReader reader = new PbjReader(input);
        T res = parse(reader, strictMode, parseUnknownFields, maxDepth, DEFAULT_MAX_SIZE);
        reader.throwOnError();
        return res;
    }

    default T parse(@NonNull PbjReader input, boolean strictMode, boolean parseUnknownFields, int maxDepth)
            throws ParseException {
        T res = parse(input, strictMode, parseUnknownFields, maxDepth, DEFAULT_MAX_SIZE);
        input.throwOnError();
        return res;
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
    default T parse(@NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth)
            throws ParseException {
        return parse(input, strictMode, false, maxDepth);
    }

    @NonNull
    default T parse(@NonNull PbjReader input, final boolean strictMode, final int maxDepth) throws ParseException {
        return parse(input, strictMode, false, maxDepth);
    }

    @NonNull
    default T parseAndThrow(@NonNull PbjReader input, final boolean strictMode, final int maxDepth)
            throws ParseException {
        T res = parse(input, strictMode, false, maxDepth);
        input.throwOnError();
        return res;
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
    default T parse(@NonNull Bytes bytes, final boolean strictMode, final int maxDepth) throws ParseException {
        return parse(bytes.toReadableSequentialData(), strictMode, maxDepth);
    }

    /**
     * Parses an object from the {@link PbjReader} and returns it.
     *
     * @param input The {@link PbjReader} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws ParseException If parsing fails
     */
    @NonNull
    default T parse(@NonNull ReadableSequentialData input) throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        logRead();
        PbjReader reader = new PbjReader(input);
        T res = parse(reader);
        reader.throwOnError();
        return res;
    }

    @NonNull
    default T parse(@NonNull PbjReader input) throws ParseException {
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
    default T parse(@NonNull Bytes bytes) throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        // logRead();
        ReadCache cache = tlsReader.get();
        if (cache.inUse) {
            dbgLog();
            PbjReader reader = new PbjReader(bytes);
            T res = parse(reader);
            reader.throwOnError();
            return res;
        }
        cache.inUse = true;
        try {
            PbjReader reader = cache.reader;
            reader.resetWith(bytes);
            T res = parse(reader);
            reader.throwOnError();
            return res;
        } finally {
            cache.inUse = false;
        }
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
    default T parseStrict(@NonNull ReadableSequentialData input) throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        logRead();
        PbjReader reader = new PbjReader(input);
        T res = parse(reader, true, DEFAULT_MAX_DEPTH);
        reader.throwOnError();
        return res;
    }

    @NonNull
    default T parseStrict(@NonNull PbjReader input) throws ParseException {
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
    default T parseStrict(@NonNull Bytes bytes) throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        PbjReader reader = new PbjReader(bytes.toReadableSequentialData());
        T res = parseStrict(reader);
        reader.throwOnError();
        return res;
    }

    /**
     * Writes an item to the given {@link WritableSequentialData}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link WritableSequentialData} to write to.
     * @throws IOException If the {@link WritableSequentialData} cannot be written to.
     */
    void realWrite(@NonNull T item, @NonNull PbjWriter output) throws IOException;

    default void write(@NonNull T item, @NonNull WritableSequentialData output) throws IOException {
        if (disallowNonPbjWriter) throw new RuntimeException("PbjWriter Only");
        logWrite();
        PbjWriter writer = new PbjWriter(output);
        write(item, writer);
        writer.flush();
    }

    default void write(@NonNull T item, @NonNull PbjWriter output) throws IOException {
        logGood();
        realWrite(item, output);
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
    default int write(@NonNull T item, @NonNull byte[] output, final int startOffset) {
        final BufferedData bufferedData = BufferedData.wrap(output, startOffset, output.length - startOffset);
        // logWrite();
        PbjWriter writer = new PbjWriter(bufferedData);
        try {
            write(item, writer);
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return (int) bufferedData.position();
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
    default int measure(@NonNull PbjReader input) throws ParseException {
        final long startPosition = input.position();
        parse(input);
        return (int) (input.position() - startPosition);
    }

    default int measure(@NonNull ReadableSequentialData input) throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        PbjReader reader = new PbjReader(input);
        int res = measure(reader);
        reader.throwOnError();
        return res;
    }
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
     * @throws ParseException If parsing fails
     */
    boolean fastEquals(@NonNull T item, @NonNull PbjReader input) throws ParseException;

    default boolean fastEquals(@NonNull T item, @NonNull ReadableSequentialData input) throws ParseException {
        if (disallowNonPbjReader) throw new RuntimeException("PbjReader Only");
        PbjReader reader = new PbjReader(input);
        boolean res = fastEquals(item, reader);
        reader.throwOnError();
        return res;
    }

    default Bytes toBytes(@NonNull T item) {
        WriteCache cache = tlsWriter.get();
        if (cache.inUse) {
            dbgLog();
            int len = measureRecord(item);
            PbjWriter writer = new PbjWriter(len);
            return toBytes(item, writer);
        }
        cache.inUse = true;
        try {
            cache.writer.reset();
            return toBytes(item, cache.writer);
        } finally {
            cache.inUse = false;
        }
    }

    default Bytes toBytes(@NonNull T item, PbjWriter writer) {
        try {
            write(item, writer);
            writer.flush();
            return writer.wrappedBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Get the default value for the model class.
     *
     * @return The default value for the model class
     */
    T getDefaultInstance();
}
