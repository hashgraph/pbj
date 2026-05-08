// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// An object cache for frequently parsed models.
/// @param <K> key, which is likely an Integer representing the model hashCode
/// @param <V> value, which is the model type itself
public class ObjectCache<K, V> extends LinkedHashMap<K, V> {
    private static final String PBJ_CACHE_PROPERTY_PREFIX = "pbj.cache.";

    // With the load factor of 1.f, the map would only ever resize if we try to insert capacity+1 elements.
    // However, removeEldestEntry() ensures we keep the size of the map < capacity, so it should never resize.
    private final int capacity;

    /// Create a new ObjectCache.
    /// @param fullyQualifiedModelName fqn of the model used to look up the pbj.cache.fqn system property
    ///     to allow overriding the default cache size
    /// @param defaultModelCacheCapacity the default cache size as specified in the model's <<<pbj.cacheable = N>>>
    public ObjectCache(String fullyQualifiedModelName, int defaultModelCacheCapacity) {
        int capacity = Optional.ofNullable(System.getProperty(PBJ_CACHE_PROPERTY_PREFIX + fullyQualifiedModelName))
                .map(Integer::parseInt)
                .orElse(defaultModelCacheCapacity);
        this.capacity = capacity;
        super(capacity, 1.f, true);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() >= capacity;
    }

    /// Check if the cache is enabled, that is, if its capacity is greater than zero.
    /// @return true if enabled
    public boolean isEnabled() {
        return capacity > 0;
    }
}
