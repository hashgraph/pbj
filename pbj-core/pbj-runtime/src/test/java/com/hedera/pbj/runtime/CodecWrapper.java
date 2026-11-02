// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.function.ToIntFunction;

/**
 * Now that we use Codecs in ProtoWriterTools and ProtoReaderTools, we need to wrap the old test lambdas ProtoWriter and
 * ToIntFunction into a codec class. Only the two methods are implemented and the rest are left as unsupported.
 *
 * @param <T> The type of the object to be encoded/decoded
 */
class CodecWrapper<T> extends Codec<T> {

    private final ProtoWriter<T> writer;
    private final ToIntFunction<T> sizeOf;

    CodecWrapper(ProtoWriter<T> writer, ToIntFunction<T> sizeOf) {
        this.writer = writer;
        this.sizeOf = sizeOf;
    }

    @NonNull
    @Override
    protected T parseImpl(
            @NonNull ReadableSequentialData input,
            boolean strictMode,
            boolean parseUnknownFields,
            int maxDepth,
            int maxSize)
            throws ParseException {
        throw new UnsupportedOperationException("CodecWrapper.parseImpl not supported");
    }

    @Override
    protected void writeImpl(@NonNull final T item, @NonNull final WritableSequentialData out) throws IOException {
        writer.write(item, out);
    }

    @Override
    protected long writeImpl(@NonNull final T item, @NonNull final RandomAccessData out, final long offset)
            throws IOException {
        throw new UnsupportedOperationException("CodecWrapper.writeImpl not supported");
    }

    @Override
    public int measureRecord(T item) {
        return sizeOf.applyAsInt(item);
    }

    @Override
    public T getDefaultInstance() {
        throw new UnsupportedOperationException();
    }
}
