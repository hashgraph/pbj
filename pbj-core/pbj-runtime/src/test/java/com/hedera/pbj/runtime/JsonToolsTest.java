package com.hedera.pbj.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonToolsTest {
    @Test
    void testToJsonFieldName() {
        assertEquals("foo",JsonTools.toJsonFieldName("foo"));
        assertEquals("fooBar",JsonTools.toJsonFieldName("foo_Bar"));
        assertEquals("fooBar",JsonTools.toJsonFieldName("foo_bar"));
        assertEquals("fooBar",JsonTools.toJsonFieldName("fooBar"));
        assertEquals("foobar",JsonTools.toJsonFieldName("foobar"));
    }
}
