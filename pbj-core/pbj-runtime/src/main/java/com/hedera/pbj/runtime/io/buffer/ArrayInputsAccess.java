// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.internal.ArrayInputs;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;

/** The {@link ArrayInputs.Access} for array-backed {@link BufferedData} and {@link Bytes} inputs. */
final class ArrayInputsAccess implements ArrayInputs.Access {
    static final ArrayInputsAccess INSTANCE = new ArrayInputsAccess();

    static {
        ArrayInputs.install(INSTANCE);
    }

    /** Make sure the access is installed; called when input classes load. */
    static void install() {
        // installed by the static initializer
    }

    private ArrayInputsAccess() {}

    @Override
    public byte[] array(@NonNull final ReadableSequentialData input) {
        if (input instanceof ByteArrayBufferedData data) {
            return data.backingArray();
        }
        if (input instanceof RandomAccessSequenceAdapter adapter && adapter.delegate() instanceof Bytes bytes) {
            return bytes.backingArray();
        }
        return null;
    }

    @Override
    public int index(@NonNull final ReadableSequentialData input) {
        if (input instanceof ByteArrayBufferedData data) {
            return data.arrayOffset() + Math.toIntExact(data.position());
        }
        final RandomAccessSequenceAdapter adapter = (RandomAccessSequenceAdapter) input;
        return ((Bytes) adapter.delegate()).backingOffset() + Math.toIntExact(adapter.absolutePosition());
    }
}
