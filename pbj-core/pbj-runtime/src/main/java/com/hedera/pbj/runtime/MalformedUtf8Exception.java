// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * Thrown during the UTF-8 encoding process when it is malformed.
 */
public class MalformedUtf8Exception extends RuntimeException {

    /**
     * Construct new MalformedUtf8Exception
     *
     * @param message error message
     */
    public MalformedUtf8Exception(final String message) {
        super(message);
    }

    public MalformedUtf8Exception(final String message, final Throwable cause) {
        super(message, cause);
    }
}
