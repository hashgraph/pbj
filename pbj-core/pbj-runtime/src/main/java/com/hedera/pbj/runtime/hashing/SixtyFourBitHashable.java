package com.hedera.pbj.runtime.hashing;

/**
 * Interface for objects that can be hashed to a 64-bit long value.
 */
public interface SixtyFourBitHashable {
    /**
     * Hash this object to a 64-bit long value.
     *
     * @return the 64-bit hash value
     */
    long hashCode64();
}
