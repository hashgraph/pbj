package com.hedera.pbj.runtime.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.CharBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharBufferToWritableSequentialDataTest {
    private static final int CHAR_BUFFER_SIZE = 16 * 1024 * 1024;
    private static final ThreadLocal<CharBuffer> THREAD_LOCAL_CHAR_BUFFERS =
            ThreadLocal.withInitial(() -> CharBuffer.allocate(CHAR_BUFFER_SIZE));

    public static CharBuffer getThreadLocalCharBuffer() {
        final var local = THREAD_LOCAL_CHAR_BUFFERS.get();
        local.clear();
        return local;
    }

    @Test
    void basicWriting() throws IOException {
        final var charBuffer = getThreadLocalCharBuffer();
        final CharBufferToWritableSequentialData adapter = new CharBufferToWritableSequentialData(charBuffer);
        assertEquals(CHAR_BUFFER_SIZE,adapter.capacity());
        assertEquals(0,adapter.position());
        adapter.writeUTF8("foo");
        assertEquals(CHAR_BUFFER_SIZE,adapter.capacity());
        assertEquals(3,adapter.position());
        adapter.skip(20);
        assertEquals(23,adapter.position());
    }

}
