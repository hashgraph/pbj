// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * A record representing an unknown field as the field number, its wireType, and the raw bytes.
 * <p>
 * Bytes for `ProtoConstants.WIRE_TYPE_DELIMITED` wireType contain raw protobuf encoding of the field data
 * that includes a varInt prefix with the size of the data. For example, to read the actual bytes stored in the field
 * one could use the `ProtoParserTools.readBytes()` method which will read the length correctly and return the actual
 * bytes of the data w/o the length prefix.
 *
 * @param field the protobuf field number
 * @param wireType the wire type of the field (e.g. varint, or delimited, etc.)
 * @param bytes a list of the raw bytes of each occurrence of the field (e.g. for repeated fields)
 */
public record UnknownField(int field, ProtoConstants wireType, Bytes bytes) {}
