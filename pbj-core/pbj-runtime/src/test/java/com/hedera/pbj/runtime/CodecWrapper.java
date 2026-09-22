// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.PbjReader;
import com.hedera.pbj.runtime.io.buffer.PbjWriter;
import edu.umd.cs.findbugs.annotations.NonNull;
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
            @NonNull PbjReader input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void writeImpl(@NonNull T item, @NonNull PbjWriter output) {
        writer.write(item, output);
    }

    @Override
    public int measure(@NonNull PbjReader input) throws ParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int measureRecord(T item) {
        return sizeOf.applyAsInt(item);
    }

    @Override
    public boolean fastEquals(@NonNull T item, @NonNull PbjReader input) throws ParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public T getDefaultInstance() {
        throw new UnsupportedOperationException();
    }
}
