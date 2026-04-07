// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * Extends {@link Codec} to identify XDR (RFC 4506) codecs in the type system.
 * Allows XDR-specific convenience methods to be added in the future.
 *
 * @param <T> The type of object to serialize and deserialize
 */
public interface XdrCodec<T> extends Codec<T> {}
