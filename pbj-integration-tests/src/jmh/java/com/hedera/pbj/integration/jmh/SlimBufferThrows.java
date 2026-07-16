// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.SlimBuffer;

/**
 * A SlimBuffer variant that throws immediately on setError instead of recording an error flag.
 * Used to benchmark the cost of the exception mechanism vs the flag-and-check pattern.
 * On valid input, setError is never called, so the two should perform identically.
 */
public class SlimBufferThrows extends SlimBuffer {

    public SlimBufferThrows(byte[] completeBuffer) {
        super(completeBuffer);
    }

    @Override
    public void setError(int errorKind) {
        throw new RuntimeException(new ParseException("SlimBufferThrows errorKind=" + errorKind));
    }
}
