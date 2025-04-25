// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * An interface implemented by a PBJ-generated Java message object.
 * This interface provides easy access to a few static fields declared in every message, allowing
 * the code to reference them directly, w/o knowing the concrete Java class of the message
 * at compile time and w/o a need to use Java reflection for this purpose.
 *
 * @param <T> a concrete type of PBJ-generated Java message
 */
public interface PbjMessage<T> {
    /** Get the Protobuf codec for this message, also accessible via the T.PROTOBUF static field. */
    Codec<T> getProtobufCodec();

    /** Get the JSON codec for this message, also accessible via the T.JSON static field. */
    JsonCodec<T> getJsonCodec();

    /**
     * Get the default instance with all fields set to default values, also accessible via the T.DEFAULT static field.
     */
    T getDefaultMessage();
}
