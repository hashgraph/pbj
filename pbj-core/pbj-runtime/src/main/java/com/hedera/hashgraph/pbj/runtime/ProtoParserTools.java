package com.hedera.hashgraph.pbj.runtime;

import com.hedera.hashgraph.pbj.runtime.io.DataBuffer;
import com.hedera.hashgraph.pbj.runtime.io.ReadOnlyDataBuffer;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hedera.hashgraph.pbj.runtime.ProtoConstants.*;

/**
 * This class is full of parse helper methods, they depend on a DataBuffer as input with position and limit set
 * correctly.
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

    /** Instance should never be created */
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

    public static int readInt32(final DataBuffer buf) throws IOException {
        return buf.readVarInt(false);
    }

    public static long readInt64(final DataBuffer buf) throws IOException {
        return buf.readVarLong(false);
    }

    public static int readUint32(final DataBuffer buf) throws IOException {
        return buf.readVarInt(false);
    }

    public static long readUint64(final DataBuffer buf) throws IOException {
        return buf.readVarLong(false);
    }

    public static boolean readBool(final DataBuffer buf) throws IOException {
        final var i = buf.readVarInt(false);
        if (i != 1 && i != 0) {
            throw new IOException("Bad protobuf encoding. Boolean was not 0 or 1");
        }
        return i == 1;
    }

    public static int readEnum(final DataBuffer buf) throws IOException {
        return buf.readVarInt(false);
    }

    public static int readSignedInt32(final DataBuffer buf) throws IOException {
        return buf.readVarInt(true);
    }

    public static long readSignedInt64(final DataBuffer buf) throws IOException {
        return buf.readVarLong(true);
    }

    public static int readSignedFixed32(final DataBuffer buf) throws IOException {
        return buf.readInt(ByteOrder.LITTLE_ENDIAN);
    }

    public static int readFixed32(final DataBuffer buf) throws IOException {
        return buf.readInt(ByteOrder.LITTLE_ENDIAN);
    }

    public static float readFloat(final DataBuffer buf) throws IOException {
        return buf.readFloat(ByteOrder.LITTLE_ENDIAN);
    }

    public static long readSignedFixed64(final DataBuffer buf) throws IOException {
        return buf.readLong(ByteOrder.LITTLE_ENDIAN);
    }

    public static long readFixed64(final DataBuffer buf) throws IOException {
        return buf.readLong(ByteOrder.LITTLE_ENDIAN);
    }

    public static double readDouble(final DataBuffer buf) throws IOException {
        return buf.readDouble(ByteOrder.LITTLE_ENDIAN);
    }

    public static String readString(final DataBuffer buf) throws IOException {
        final int length = buf.readVarInt(false);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
    }

    public static ReadOnlyDataBuffer readBytes(final DataBuffer buf) throws IOException {
        final int length = buf.readVarInt(false);
        return buf.readDataBuffer(length);
    }

    public static void skipField(final DataBuffer buf, final int wireType) throws IOException {
        switch (wireType) {
            case WIRE_TYPE_FIXED_64_BIT -> buf.skip(8);
            case WIRE_TYPE_FIXED_32_BIT -> buf.skip(4);
            // The value for "zigZag" when calling varint doesn't matter because we are just reading past
            // the varint, we don't care how to interpret it (zigzag is only used for interpretation of
            // the bytes, not how many of them there are)
            case WIRE_TYPE_VARINT_OR_ZIGZAG -> buf.readVarLong(false);
            case WIRE_TYPE_DELIMITED -> {
                final int length = buf.readVarInt(false);
                buf.skip(length);
            }
            case WIRE_TYPE_GROUP_START -> throw new IOException(
                    "Wire type 'Group Start' is unsupported");
            case WIRE_TYPE_GROUP_END -> throw new IOException(
                    "Wire type 'Group End' is unsupported");
            default -> throw new IOException(
                    "Unhandled wire type while trying to skip a field " + wireType);
        }
    }

//    // =================================================================================================================
//    // Optimized VarInt parsing implementation, derived from the google implementation
//    // https://github.com/protocolbuffers/protobuf
//
//    public static int readRawVarint32(final DataBuffer buf) throws IOException {
//        // See implementation notes for readRawVarint64
//        fastpath:
//        {
//            int tempPos = (int)buf.getPosition();
//
//            if (!buf.hasRemaining()) {
//                break fastpath;
//            }
//            int x;
//            if ((x = buf.readByte(tempPos++)) >= 0) {
//                buf.position(tempPos);
//                return x;
//            } else if (buf.remaining() - tempPos < 9) {
//                break fastpath;
//            } else if ((x ^= (buf.get(tempPos++) << 7)) < 0) {
//                x ^= (~0 << 7);
//            } else if ((x ^= (buf.get(tempPos++) << 14)) >= 0) {
//                x ^= (~0 << 7) ^ (~0 << 14);
//            } else if ((x ^= (buf.get(tempPos++) << 21)) < 0) {
//                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
//            } else {
//                int y = buf.read(tempPos++);
//                x ^= y << 28;
//                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
//                if (y < 0
//                        && buf.read(tempPos++) < 0
//                        && buf.read(tempPos++) < 0
//                        && buf.read(tempPos++) < 0
//                        && buf.read(tempPos++) < 0
//                        && buf.read(tempPos++) < 0) {
//                    break fastpath; // Will throw malformedVarint()
//                }
//            }
//            buf.position(tempPos);
//            return x;
//        }
//        return (int) readRawVarint64SlowPath(buf);
//    }
//
//    public static long readRawVarint64(final DataBuffer buf) throws IOException {
//        // Implementation notes:
//        //
//        // Optimized for one-byte values, expected to be common.
//        // The particular code below was selected from various candidates
//        // empirically, by winning VarintBenchmark.
//        //
//        // Sign extension of (signed) Java bytes is usually a nuisance, but
//        // we exploit it here to more easily obtain the sign of bytes read.
//        // Instead of cleaning up the sign extension bits by masking eagerly,
//        // we delay until we find the final (positive) byte, when we clear all
//        // accumulated bits with one xor.  We depend on javac to constant fold.
//        fastpath:
//        {
//            int tempPos = buf.position();
//
//            if (buf.remaining() == 0) {
//                break fastpath;
//            }
//
//            long x;
//            int y;
//            if ((y = buf.read(tempPos++)) >= 0) {
//                buf.position(tempPos);
//                return y;
//            } else if (buf.remaining() - tempPos < 9) {
//                break fastpath;
//            } else if ((y ^= (buf.get(tempPos++) << 7)) < 0) {
//                x = y ^ (~0 << 7);
//            } else if ((y ^= (buf.get(tempPos++) << 14)) >= 0) {
//                x = y ^ ((~0 << 7) ^ (~0 << 14));
//            } else if ((y ^= (buf.get(tempPos++) << 21)) < 0) {
//                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
//            } else if ((x = y ^ ((long) buf.read(tempPos++) << 28)) >= 0L) {
//                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
//            } else if ((x ^= ((long) buf.read(tempPos++) << 35)) < 0L) {
//                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
//            } else if ((x ^= ((long) buf.read(tempPos++) << 42)) >= 0L) {
//                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
//            } else if ((x ^= ((long) buf.read(tempPos++) << 49)) < 0L) {
//                x ^=
//                        (~0L << 7)
//                                ^ (~0L << 14)
//                                ^ (~0L << 21)
//                                ^ (~0L << 28)
//                                ^ (~0L << 35)
//                                ^ (~0L << 42)
//                                ^ (~0L << 49);
//            } else {
//                x ^= ((long) buf.read(tempPos++) << 56);
//                x ^=
//                        (~0L << 7)
//                                ^ (~0L << 14)
//                                ^ (~0L << 21)
//                                ^ (~0L << 28)
//                                ^ (~0L << 35)
//                                ^ (~0L << 42)
//                                ^ (~0L << 49)
//                                ^ (~0L << 56);
//                if (x < 0L) {
//                    if (buf.get(tempPos++) < 0L) {
//                        break fastpath; // Will throw malformedVarint()
//                    }
//                }
//            }
//            buf.position(tempPos);
//            return x;
//        }
//        return readRawVarint64SlowPath(buf);
//    }
//    
//    private static long readRawVarint64SlowPath(DataBuffer buf) throws IOException {
//        long result = 0;
//        for (int shift = 0; shift < 64; shift += 7) {
//            final byte b = buf.read();
//            result |= (long) (b & 0x7F) << shift;
//            if ((b & 0x80) == 0) {
//                return result;
//            }
//        }
//        throw new IOException("Malformed varInt");
//    }
//
//    /**
//     * Decode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
//     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
//     * to be varint encoded, thus always taking 10 bytes on the wire.)
//     *
//     * @param n An unsigned 32-bit integer, stored in a signed int because Java has no explicit
//     *     unsigned support.
//     * @return A signed 32-bit integer.
//     */
//    private static int decodeZigZag32(final int n) {
//        return (n >>> 1) ^ -(n & 1);
//    }
//
//    /**
//     * Decode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
//     * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
//     * to be varint encoded, thus always taking 10 bytes on the wire.)
//     *
//     * @param n An unsigned 64-bit integer, stored in a signed int because Java has no explicit
//     *     unsigned support.
//     * @return A signed 64-bit integer.
//     */
//    private static long decodeZigZag64(final long n) {
//        return (n >>> 1) ^ -(n & 1);
//    }
}
