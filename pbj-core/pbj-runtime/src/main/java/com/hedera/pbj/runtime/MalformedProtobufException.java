package com.hedera.pbj.runtime;

import java.io.IOException;

/**
 * Thrown during the parsing of protobuf data when it is malformed.
 */
public class MalformedProtobufException extends IOException {

	/**
	 * Construct new MalformedProtobufException
	 *
	 * @param message error message
	 */
	public MalformedProtobufException(final String message) {
		super(message);
	}
}
