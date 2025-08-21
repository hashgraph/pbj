// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class PbjMapTest {
    @Test
    void basic() {
        var map = PbjMap.of(Map.of("key1", "value1", "key2", "value2"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key1"));
        assertTrue(map.containsValue("value2"));
        assertTrue(map.equals(map));
        assertEquals(map.hashCode(), map.hashCode());
        assertEquals("value1", map.get("key1"));
        assertFalse(map.isEmpty());
        assertEquals(Set.of("key1", "key2"), map.keySet());
        assertEquals(2, map.values().size());
        assertEquals(2, map.entrySet().size());
    }

    @Test
    void immutability() {
        var map = PbjMap.of(Map.of());
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> map.clear());
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(Map.of("key1", "value1", "key2", "value2")));
        assertThrows(UnsupportedOperationException.class, () -> map.put("key3", "value3"));
        assertThrows(UnsupportedOperationException.class, () -> map.remove("key3"));
        assertEquals("{} with sortedKeys: []", map.toString());
    }

    @Test
    void sorted() {
        var map = PbjMap.of(Map.of("key2", "value2", "key1", "value1", "key3", "value3"));
        assertEquals(3, map.size());
        assertEquals(3, map.getSortedKeys().size());
        assertEquals(List.of("key1", "key2", "key3"), map.getSortedKeys());

        var map2 = PbjMap.of(Map.of("key1", "value1", "key2", "value2", "key3", "value3"));
        assertEquals(map, map2);
    }
}
