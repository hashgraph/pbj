// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * An interface implemented by a PBJ-generated Java message object.
 * This interface allows one to retrieve a PROTOBUF codec for this message which is normally available via the static
 * T.PROTOBUF member without knowing the actual Java class of the message at compile time and without a need to use
 * Java reflection.
 *
 * @param <T> a concrete type of PBJ-generated Java message
 */
public interface PbjMessage<T> {
    /** Get the Protobuf codec for this message, also accessible via the T.PROTOBUF static field. */
    Codec<T> PROTOBUF();
}
