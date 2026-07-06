// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

/*
CN expects specific causes in specific situations
*/

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.UnknownFieldException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.nio.BufferUnderflowException;
import org.junit.jupiter.api.Test;

class SlimBufferTest {

    @Test
    void readInt_BEInt() {
        int value = 6;
        BufferedData data = BufferedData.allocate(Integer.BYTES);
        data.writeInt(value);
        data.flip();
        SlimBuffer slim = new SlimBuffer(data.toInputStream());
        assertEquals(value, slim.readInt());
    }

    @Test
    void readInt_BELong() {
        long value = 6L;
        BufferedData data = BufferedData.allocate(Long.BYTES);
        data.writeLong(value);
        data.flip();
        SlimBuffer slim = new SlimBuffer(data.toInputStream());
        assertEquals(value, slim.readLong());
    }

    @Test
    void causeIsBufferUndeflow() {
        // CN expects ex to be parse, cause to be underflow
        BufferedData data = BufferedData.allocate(1);
        data.writeByte((byte) 0);
        data.flip();
        SlimBuffer slim = new SlimBuffer(data.toInputStream());
        slim.setError(SlimBuffer.BufferUnderflow);
        ParseException ex = assertThrows(ParseException.class, slim::throwOnError);
        assertInstanceOf(
                BufferUnderflowException.class,
                ex.getCause(),
                "ParseException for BufferUnderflow must carry a BufferUnderflowException cause");
    }

    @Test
    void causeIsUnknownFieldException() {
        BufferedData data = BufferedData.allocate(1);
        data.writeByte((byte) 0);
        data.flip();
        SlimBuffer slim = new SlimBuffer(data.toInputStream());
        slim.setError(SlimBuffer.UnknownField);
        ParseException ex = assertThrows(ParseException.class, slim::throwOnError);
        assertInstanceOf(
                UnknownFieldException.class,
                ex.getCause(),
                "ParseException for UnknownField must carry an UnknownFieldException cause");
    }

    @Test
    void nullCause() {
        BufferedData data = BufferedData.allocate(1);
        data.writeByte((byte) 0);
        data.flip();
        SlimBuffer slim = new SlimBuffer(data.toInputStream());
        slim.setError(SlimBuffer.Parse);
        ParseException ex = assertThrows(ParseException.class, slim::throwOnError);
        assertNull(ex.getCause(), "ParseException shouldn't produce a cause (CN doesn't expect it)");
    }

    @Test
    void miscCause() {
        BufferedData data = BufferedData.allocate(1);
        data.writeByte((byte) 0);
        data.flip();
        SlimBuffer slim = new SlimBuffer(data.toInputStream());
        slim.setError(SlimBuffer.IOError);
        ParseException ex = assertThrows(ParseException.class, slim::throwOnError);
        assertNotNull(ex.getCause(), "ParseException for IOError must have a non-null cause");
        if (ex.getCause() instanceof BufferUnderflowException) {
            throw new AssertionError("ParseException cause must not be BufferUnderflowException");
        }
        if (ex.getCause() instanceof UnknownFieldException) {
            throw new AssertionError("ParseException cause must not be UnknownFieldException");
        }
    }
}
