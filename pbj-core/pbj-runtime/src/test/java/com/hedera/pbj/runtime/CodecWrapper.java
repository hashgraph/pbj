// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.SlimBuffer;
import com.hedera.pbj.runtime.io.SlimWriter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.function.ToIntFunction;

/**
 * Now that we use Codecs in ProtoWriterTools and ProtoReaderTools, we need to wrap the old test lambdas ProtoWriter and
 * ToIntFunction into a codec class. Only the two methods are implemented and the rest are left as unsupported.
 *
 * @param <T> The type of the object to be encoded/decoded
 */
class CodecWrapper<T> implements Codec<T> {
    private final SlimProtoWriter<T> writer;
    private final ToIntFunction<T> sizeOf;

    CodecWrapper(SlimProtoWriter<T> writer, ToIntFunction<T> sizeOf) {
        this.writer = writer;
        this.sizeOf = sizeOf;
    }

    @NonNull
    @Override
    public T realParse(
            @NonNull SlimBuffer input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)
            throws ParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void realWrite(@NonNull T item, @NonNull SlimWriter output) throws IOException {
        writer.write(item, output);
    }

    @Override
    public int measure(@NonNull SlimBuffer input) throws ParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int measureRecord(T item) {
        return sizeOf.applyAsInt(item);
    }

    @Override
    public boolean fastEquals(@NonNull T item, @NonNull SlimBuffer input) throws ParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public T getDefaultInstance() {
        throw new UnsupportedOperationException();
    }
}
