// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NoToStringWrapperTest {
    @Test
    void wrapString() {
        var foo = "foo".intern();
        var wrapper = new NoToStringWrapper<String>(foo);
        // get the wrapped value
        assertEquals("foo", wrapper.getValue());
        assertEquals("NoToStringWrapper{java.lang.String}", wrapper.toString());
        assertTrue(wrapper.equals(wrapper));
        assertTrue(wrapper.equals(new NoToStringWrapper<String>("foo")));
        assertFalse(wrapper.equals(new NoToStringWrapper<String>("bar")));
        assertEquals("foo".hashCode(), "foo".hashCode());
        assertEquals(wrapper.hashCode(), foo.hashCode());
        assertFalse(wrapper.equals(null));
    }

    @Test
    void wrapFloat() {
        var wrapper = new NoToStringWrapper<Float>(8.8f);
        assertEquals(8.8f, wrapper.getValue());
        assertEquals("NoToStringWrapper{java.lang.Float}", wrapper.toString());
        assertTrue(wrapper.equals(wrapper));
        assertTrue(wrapper.equals(new NoToStringWrapper<Float>(8.8f)));
        assertFalse(wrapper.equals(new NoToStringWrapper<Float>(9.9f)));
        assertEquals(Float.valueOf(8.8f).hashCode(), Float.valueOf(8.8f).hashCode());
        assertEquals(wrapper.hashCode(), Float.valueOf(8.8f).hashCode());
        assertFalse(wrapper.equals(new NoToStringWrapper<String>("foo")));
        assertFalse(wrapper.equals("foo"));
    }
}
