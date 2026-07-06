// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.SlimWriter;
import java.io.IOException;

/**
 * Interface for referencing the static write method from generated writer classes, using {@link SlimWriter}.
 *
 * @param <T> The model object that is being written
 */
public interface SlimProtoWriter<T> {

    /**
     * Write out a {@code T} model to output stream in protobuf format.
     *
     * @param data The input model data to write
     * @param out The output stream to write to
     * @throws IOException If there is a problem writing
     */
    void write(T data, SlimWriter out) throws IOException;
}
