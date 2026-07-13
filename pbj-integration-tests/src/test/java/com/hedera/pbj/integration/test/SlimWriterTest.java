// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.SlimWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

public class SlimWriterTest {

    @Test
    void writeBytesToClosedOutputStreamShouldThrow() throws IOException {
        final var baos = new ByteArrayOutputStream();
        final var writer = new SlimWriter(baos);
        baos.close();

        final byte[] bigData = new byte[32 * 1024];
        assertThrows(UncheckedIOException.class, () -> writer.writeBytes(bigData, 0, bigData.length));
    }
}
