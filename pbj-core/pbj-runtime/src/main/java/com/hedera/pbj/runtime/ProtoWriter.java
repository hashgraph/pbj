package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.DataOutput;

import java.io.IOException;

/**
 * Interface for referencing the static write method from generated writer classes.
 *
 * @param <T> The model object that is being written
 */
public interface ProtoWriter<T> {

    /**
     * Write out a {@code T} model to output stream in protobuf format.
     *
     * @param data The input model data to write
     * @param out The output stream to write to
     * @throws IOException If there is a problem writing
     */
    void write(T data, DataOutput out) throws IOException;
}
