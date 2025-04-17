// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;

/**
 * A record representing an unknown field.
 * @param wireType the wire type of the field (e.g. varint, or delimited, etc.)
 * @param bytes a list of the raw bytes of each occurrence of the field (e.g. for repeated fields)
 */
public record UnknownField(ProtoConstants wireType, List<Bytes> bytes) {}
