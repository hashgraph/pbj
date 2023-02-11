package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.Bytes;
import com.hedera.pbj.runtime.io.DataInput;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is full of parse helper methods, they depend on a DataInput as input with position and limit set
 * correctly.
 * <p>
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
//            if (newItem instanceof Integer) {
//                list = new FastList();
//            }
//            list = new ArrayList<>(20);
            list = new FastList<>(20);
        }
        list.add(newItem);
        return list;
    }

    /**
     * Read a protobuf int32 from input
     *
     * @param input The input data to read from
     * @return the read int
     * @throws IOException If a I/O error occurs
     */
    public static int readInt32(final DataInput input) throws IOException {
        return input.readVarInt(false);
    }

    /**
     * Read a protobuf int64(long) from input
     *
     * @param input The input data to read from
     * @return the read long
     * @throws IOException If a I/O error occurs
     */
    public static long readInt64(final DataInput input) throws IOException {
        return input.readVarLong(false);
    }

    /**
     * Read a protobuf uint32 from input
     *
     * @param input The input data to read from
     * @return the read int
     * @throws IOException If a I/O error occurs
     */
    public static int readUint32(final DataInput input) throws IOException {
        return input.readVarInt(false);
    }

    /**
     * Read a protobuf uint64 from input
     *
     * @param input The input data to read from
     * @return the read long
     * @throws IOException If a I/O error occurs
     */
    public static long readUint64(final DataInput input) throws IOException {
        return input.readVarLong(false);
    }

    /**
     * Read a protobuf bool from input
     *
     * @param input The input data to read from
     * @return the read boolean
     * @throws IOException If a I/O error occurs
     */
    public static boolean readBool(final DataInput input) throws IOException {
        final var i = input.readVarInt(false);
        if (i != 1 && i != 0) {
            throw new IOException("Bad protobuf encoding. Boolean was not 0 or 1");
        }
        return i == 1;
    }

    /**
     * Read a protobuf enum from input
     *
     * @param input The input data to read from
     * @return the read enum protoc ordinal
     * @throws IOException If a I/O error occurs
     */
    public static int readEnum(final DataInput input) throws IOException {
        return input.readVarInt(false);
    }

    /**
     * Read a protobuf sint32 from input
     *
     * @param input The input data to read from
     * @return the read int
     * @throws IOException If a I/O error occurs
     */
    public static int readSignedInt32(final DataInput input) throws IOException {
        return input.readVarInt(true);
    }

    /**
     * Read a protobuf uint64(long) from input
     *
     * @param input The input data to read from
     * @return the read long
     * @throws IOException If a I/O error occurs
     */
    public static long readSignedInt64(final DataInput input) throws IOException {
        return input.readVarLong(true);
    }

    /**
     * Read a protobuf sfixed32 from input
     *
     * @param input The input data to read from
     * @return the read int
     * @throws IOException If a I/O error occurs
     */
    public static int readSignedFixed32(final DataInput input) throws IOException {
        return input.readInt(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a protobuf fixed32 from input
     *
     * @param input The input data to read from
     * @return the read int
     * @throws IOException If a I/O error occurs
     */
    public static int readFixed32(final DataInput input) throws IOException {
        return input.readInt(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a protobuf float from input
     *
     * @param input The input data to read from
     * @return the read float
     * @throws IOException If a I/O error occurs
     */
    public static float readFloat(final DataInput input) throws IOException {
        return input.readFloat(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a protobuf sfixed64 from input
     *
     * @param input The input data to read from
     * @return the read long
     * @throws IOException If a I/O error occurs
     */
    public static long readSignedFixed64(final DataInput input) throws IOException {
        return input.readLong(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a fixed 64, which is a fixed size encoded long
     *
     * @param input the input to read from
     * @return read long
     * @throws IOException If there was a problem reading
     */
    public static long readFixed64(final DataInput input) throws IOException {
        return input.readLong(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a double from input data
     *
     * @param input the input to read from
     * @return read double
     * @throws IOException If there was a problem reading
     */
    public static double readDouble(final DataInput input) throws IOException {
        return input.readDouble(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a String field from data input
     *
     * @param input the input to read from
     * @return Read string
     * @throws IOException If there was a problem reading
     */
    public static String readString(final DataInput input) throws IOException {
        final int length = input.readVarInt(false);
        if (length == 0) return "";
        byte[] bytes = new byte[length];
        input.readBytes(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
//        return input.readUtf8String(length);
    }

    /**
     * Read a Bytes field from data input
     *
     * @param input the input to read from
     * @return read Bytes object, this can be a copy or a direct reference to inputs data. So it has same life span
     * of InputData
     * @throws IOException If there was a problem reading
     */
    public static Bytes readBytes(final DataInput input) throws IOException {
        final int length = input.readVarInt(false);
        return input.readBytes(length);
    }

    /**
     * Skip over the bytes in a stream for a given wire type. Assumes you have already read tag.
     * 
     * @param input The input to move ahead
     * @param wireType The wire type of field to skip
     * @throws IOException For unsupported wire types
     */
    public static void skipField(final DataInput input, final int wireType) throws IOException {
        switch (wireType) {
            case ProtoConstants.WIRE_TYPE_FIXED_64_BIT -> input.skip(8);
            case ProtoConstants.WIRE_TYPE_FIXED_32_BIT -> input.skip(4);
            // The value for "zigZag" when calling varint doesn't matter because we are just reading past
            // the varint, we don't care how to interpret it (zigzag is only used for interpretation of
            // the bytes, not how many of them there are)
            case ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG -> input.readVarLong(false);
            case ProtoConstants.WIRE_TYPE_DELIMITED -> {
                final int length = input.readVarInt(false);
                input.skip(length);
            }
            case ProtoConstants.WIRE_TYPE_GROUP_START -> throw new IOException(
                    "Wire type 'Group Start' is unsupported");
            case ProtoConstants.WIRE_TYPE_GROUP_END -> throw new IOException(
                    "Wire type 'Group End' is unsupported");
            default -> throw new IOException(
                    "Unhandled wire type while trying to skip a field " + wireType);
        }
    }
}
