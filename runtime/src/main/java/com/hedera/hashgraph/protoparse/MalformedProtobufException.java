package com.hedera.hashgraph.protoparse;

/**
 * Thrown during the parsing of protobuf data when it is malformed.
 */
public class MalformedProtobufException extends Exception {
	public MalformedProtobufException(final String message) {
		super(message);
	}
}
