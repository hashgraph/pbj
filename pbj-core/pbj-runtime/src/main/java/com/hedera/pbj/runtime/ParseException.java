// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * A checked exception thrown by Codec.parse() methods when the parsing operation fails.
 *
 * <p>The `cause` of this exception provides more details on the nature of the failure which can be
 * caused by I/O issues, malformed input data, or any other reason that prevents the parse() method
 * from completing the operation.
 */
public class ParseException extends Exception {
    public ParseException(Throwable cause) {
        super(cause);
    }

    public ParseException(String message) {
        super(message);
    }
}
