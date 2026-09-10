// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.PbjWriter;

/**
 * Interface for referencing the static write method from generated writer classes
 *
 * @param <T> The model object that is being written
 */
public interface ProtoWriter<T> {

    /**
     * Write out a {@code T} model to a {@link PbjWriter} in protobuf format.
     *
     * @param data The input model data to write
     * @param writer The writer to write to
     */
    void write(T data, PbjWriter writer);
}
