// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * An unchecked wrapper for a ParseException object used in rare cases
 * where existing code shouldn't throw checked exceptions.
 */
public class UncheckedParseException extends RuntimeException {
    public UncheckedParseException(ParseException cause) {
        super(cause);
    }
}
