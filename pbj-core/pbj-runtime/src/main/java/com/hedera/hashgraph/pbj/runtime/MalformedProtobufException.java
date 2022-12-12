package com.hedera.hashgraph.pbj.runtime;

/**
 * Thrown during the parsing of protobuf data when it is malformed.
 */
public class MalformedProtobufException extends Exception {

	/**
	 * Construct new MalformedProtobufException
	 *
	 * @param message error message
	 */
	public MalformedProtobufException(final String message) {
		super(message);
	}
}
