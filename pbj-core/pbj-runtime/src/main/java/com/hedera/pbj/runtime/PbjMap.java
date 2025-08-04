// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implements an immutable map that exposes a list of keys sorted in their natural order.
 * <p>
 * This Map implementation allows one to iterate the entries in a deterministic order
 * which is useful for serializing, hash computation, etc.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class PbjMap<K, V> implements Map<K, V> {
    /** An empty PbjMap. */
    public static final PbjMap EMPTY = new PbjMap(Collections.EMPTY_MAP);

    private final Map<K, V> map;
    private final List<K> sortedKeys;

    private PbjMap(final Map<K, V> map) {
        this.map = Collections.unmodifiableMap(map);
        this.sortedKeys =
                Collections.unmodifiableList(map.keySet().stream().sorted().toList());
    }

    /**
     * A public factory method for PbjMap objects.
     * It returns the PbjMap.EMPTY if the input map is empty.
     * It returns the map itself if the input map is an instance of PbjMap (because it's immutable anyway.)
     * Otherwise, it returns a new PbjMap instance delegating to the provided input map.
     * NOTE: the caller code is expected to never modify the input map after this factory method is called,
     * otherwise the behavior is undefined.
     * @param map an input map
     * @return a PbjMap instance corresponding to the input map
     * @param <K> key type
     * @param <V> value type
     */
    public static <K, V> PbjMap<K, V> of(final Map<K, V> map) {
        if (map == null || map.isEmpty()) return (PbjMap<K, V>) EMPTY;
        if (map instanceof PbjMap) return (PbjMap<K, V>) map;
        return new PbjMap<>(map);
    }

    /**
     * Return a list of keys sorted in their natural order.
     * @return the sorted keys list
     */
    public List<K> getSortedKeys() {
        return sortedKeys;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException("The map is immutable");
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException("The map is immutable");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("The map is immutable");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("The map is immutable");
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PbjMap<?, ?> pbjMap = (PbjMap<?, ?>) o;
        return Objects.equals(map, pbjMap.map) && Objects.equals(sortedKeys, pbjMap.sortedKeys);
    }

    @Override
    public int hashCode() {
        // This is a convenience hashCode() implementation that delegates to Java hashCode,
        // and it's implemented here solely to support the above equals() method override.
        // Generated protobuf models compute map fields' hash codes differently and deterministically.
        return 255 * map.hashCode() + sortedKeys.hashCode();
    }

    @Override
    public String toString() {
        return map.toString() + " with sortedKeys: " + getSortedKeys();
    }
}
