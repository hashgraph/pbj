package com.hedera.hashgraph.pbj.runtime;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hedera.hashgraph.pbj.runtime.ProtoConstants.*;

/**
 * This class is full of parse helper methods, they depend on a ByteBuffer as input with position and limit set
 * correctly and the endian order set to {@code buf.order(ByteOrder.LITTLE_ENDIAN)}
 *
 * Methods that IDE things are unused are used in generated code by PBJ compiler.
 */
@SuppressWarnings({"DuplicatedCode", "unused"})
public final class ProtoParserTools {
    /**
     * The number of lower order bits from the "tag" byte that should be rotated out
     * to reveal the field number
     */
    public static final int TAG_FIELD_OFFSET = 3;
    /**
     * Mask used to extract the wire type from the "tag" byte
     */
    public static final int TAG_WRITE_TYPE_MASK = 0b0000_0111;

    private ProtoParserTools() {}

    /**
     * Add an item to a list returning a new list with the item or the same list with the item added. If the list is
     * Collections.EMPTY_LIST then a new list is created and returned with the item added.
     *
     * @param list The list to add item to or Collections.EMPTY_LIST
     * @param newItem The item to add
     * @return The list passed in if mutable or new list
     * @param <T> The type of items to store in list
     */
    public static <T> List<T> addToList(List<T> list, T newItem) {
        if (list == Collections.EMPTY_LIST) {
            list = new ArrayList<>();
        }
        list.add(newItem);
        return list;
    }

    public static int readInt32(final ByteBuffer buf) throws MalformedProtobufException {
        return readRawVarint32(buf);
    }

    public static long readInt64(final ByteBuffer buf) throws MalformedProtobufException {
        return readRawVarint64(buf);
    }

    public static int readUint32(final ByteBuffer buf) throws MalformedProtobufException {
        return readRawVarint32(buf);
    }

    public static long readUint64(final ByteBuffer buf) throws MalformedProtobufException {
        return readRawVarint64(buf);
    }

    public static boolean readBool(final ByteBuffer buf) throws MalformedProtobufException {
        final var i = readRawVarint32(buf);
        if (i != 1 && i != 0) {
            throw new MalformedProtobufException("Bad protobuf encoding. Boolean was not 0 or 1");
        }
        return i == 1;
    }

    public static int readEnum(final ByteBuffer buf) throws MalformedProtobufException {
        return readRawVarint32(buf);
    }

    public static int readSignedInt32(final ByteBuffer buf) throws MalformedProtobufException {
        return decodeZigZag32(readRawVarint32(buf));
    }

    public static long readSignedInt64(final ByteBuffer buf) throws MalformedProtobufException {
        return decodeZigZag64(readRawVarint64(buf));
    }

    public static int readSignedFixed32(final ByteBuffer buf) throws MalformedProtobufException {
        return buf.getInt();
    }

    public static int readFixed32(final ByteBuffer buf) throws MalformedProtobufException {
        return buf.getInt();
    }

    public static float readFloat(final ByteBuffer buf) throws MalformedProtobufException {
        return buf.getFloat();
    }

    public static long readSignedFixed64(final ByteBuffer buf) throws MalformedProtobufException {
        return buf.getLong();
    }

    public static long readFixed64(final ByteBuffer buf) throws MalformedProtobufException {
        return buf.getLong();
    }

    public static double readDouble(final ByteBuffer buf) throws MalformedProtobufException {
        return buf.getDouble();
    }

    public static String readString(final ByteBuffer buf) throws MalformedProtobufException {
        // Todo micro benchmark if there is a faster way
        return StandardCharsets.UTF_8.decode(readBytes(buf)).toString();
    }

    public static ByteBuffer readBytes(final ByteBuffer buf) throws MalformedProtobufException {
        final long length = readRawVarint32(buf);
        if (length > buf.remaining()) {
            throw new MalformedProtobufException("Truncated protobuf, field missing at least " + (length - buf.remaining()) + " bytes");
        }
        final int start = buf.position();
        // skip over bytes
        buf.position(start + (int)length);
        return buf.slice(start, (int)length);
    }

    public static void skipField(final ByteBuffer buf, final int wireType) throws MalformedProtobufException {
        switch (wireType) {
            case WIRE_TYPE_FIXED_64_BIT -> buf.position(buf.position()+8);
            case WIRE_TYPE_FIXED_32_BIT -> buf.position(buf.position()+4);
            // The value for "zigZag" when calling varint doesn't matter because we are just reading past
            // the varint, we don't care how to interpret it (zigzag is only used for interpretation of
            // the bytes, not how many of them there are)
            case WIRE_TYPE_VARINT_OR_ZIGZAG -> readRawVarint64(buf);
            case WIRE_TYPE_DELIMITED -> {
                final long length = readRawVarint32(buf);
                buf.position(buf.position()+(int)length);
            }
            case WIRE_TYPE_GROUP_START -> throw new MalformedProtobufException(
                    "Wire type 'Group Start' is unsupported");
            case WIRE_TYPE_GROUP_END -> throw new MalformedProtobufException(
                    "Wire type 'Group End' is unsupported");
            default -> throw new MalformedProtobufException(
                    "Unhandled wire type while trying to skip a field " + wireType);
        }
    }

    // =================================================================================================================
    // Optimized VarInt parsing implementation, derived from the google implementation
    // https://github.com/protocolbuffers/protobuf

    public static int readRawVarint32(final ByteBuffer buf) throws MalformedProtobufException {
        // See implementation notes for readRawVarint64
        fastpath:
        {
            int tempPos = buf.position();

            if (buf.remaining() == 0) {
                break fastpath;
            }
            int x;
            if ((x = buf.get(tempPos++)) >= 0) {
                buf.position(tempPos);
                return x;
            } else if (buf.remaining() - tempPos < 9) {
                break fastpath;
            } else if ((x ^= (buf.get(tempPos++) << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (buf.get(tempPos++) << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (buf.get(tempPos++) << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = buf.get(tempPos++);
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && buf.get(tempPos++) < 0
                        && buf.get(tempPos++) < 0
                        && buf.get(tempPos++) < 0
                        && buf.get(tempPos++) < 0
                        && buf.get(tempPos++) < 0) {
                    break fastpath; // Will throw malformedVarint()
                }
            }
            buf.position(tempPos);
            return x;
        }
        return (int) readRawVarint64SlowPath(buf);
    }

    public static long readRawVarint64(final ByteBuffer buf) throws MalformedProtobufException {
        // Implementation notes:
        //
        // Optimized for one-byte values, expected to be common.
        // The particular code below was selected from various candidates
        // empirically, by winning VarintBenchmark.
        //
        // Sign extension of (signed) Java bytes is usually a nuisance, but
        // we exploit it here to more easily obtain the sign of bytes read.
        // Instead of cleaning up the sign extension bits by masking eagerly,
        // we delay until we find the final (positive) byte, when we clear all
        // accumulated bits with one xor.  We depend on javac to constant fold.
        fastpath:
        {
            int tempPos = buf.position();

            if (buf.remaining() == 0) {
                break fastpath;
            }

            long x;
            int y;
            if ((y = buf.get(tempPos++)) >= 0) {
                buf.position(tempPos);
                return y;
            } else if (buf.remaining() - tempPos < 9) {
                break fastpath;
            } else if ((y ^= (buf.get(tempPos++) << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (buf.get(tempPos++) << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (buf.get(tempPos++) << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) buf.get(tempPos++) << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) buf.get(tempPos++) << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) buf.get(tempPos++) << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) buf.get(tempPos++) << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) buf.get(tempPos++) << 56);
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49)
                                ^ (~0L << 56);
                if (x < 0L) {
                    if (buf.get(tempPos++) < 0L) {
                        break fastpath; // Will throw malformedVarint()
                    }
                }
            }
            buf.position(tempPos);
            return x;
        }
        return readRawVarint64SlowPath(buf);
    }
    
    private static long readRawVarint64SlowPath(ByteBuffer buf) throws MalformedProtobufException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = buf.get();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new MalformedProtobufException("Malformed varInt");
    }

    /**
     * Decode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
     * to be varint encoded, thus always taking 10 bytes on the wire.)
     *
     * @param n An unsigned 32-bit integer, stored in a signed int because Java has no explicit
     *     unsigned support.
     * @return A signed 32-bit integer.
     */
    private static int decodeZigZag32(final int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * Decode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
     * to be varint encoded, thus always taking 10 bytes on the wire.)
     *
     * @param n An unsigned 64-bit integer, stored in a signed int because Java has no explicit
     *     unsigned support.
     * @return A signed 64-bit integer.
     */
    private static long decodeZigZag64(final long n) {
        return (n >>> 1) ^ -(n & 1);
    }
}
