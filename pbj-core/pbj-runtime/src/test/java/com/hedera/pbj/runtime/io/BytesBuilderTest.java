package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.BytesBuilder;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

final class BytesBuilderTest {
    @Test
    @DisplayName("Appends two Bytes objects")
    void appendBytes() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes b2 = Bytes.wrap(new byte[]{4, 5, 6});
        Bytes appended = BytesBuilder.appendBytes(b1, b2);
        byte[] res = new byte[7];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6}, res);
    }

    @Test
    @DisplayName("Appends two Bytes objects, one empty")
    void appendEmptyBytes() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendBytes(b1, Bytes.EMPTY);
        byte[] res = new byte[4];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3}, res);
    }

    @Test
    @DisplayName("Appends RandomAccessData")
    void appendRandomAccessData() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        RandomAccessData rad = BufferedData.wrap(new byte[]{4, 5, 6});
        Bytes appended = BytesBuilder.appendBytes(b1, rad);
        byte[] res = new byte[7];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6}, res);
    }

    @Test
    @DisplayName("Appends byte")
    void appendByte() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendByte(b1,(byte)9);
        byte[] res = new byte[5];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 9}, res);
    }

    @Test
    @DisplayName("Appending ints")
    void appendInt() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendInt(b1,-8);
        byte[] res = new byte[8];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1, -1, -1, -8}, res);
        Bytes appended1 = BytesBuilder.appendInt(appended, Integer.MAX_VALUE);
        res = new byte[12];
        appended1.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1, -1, -1, -8, 127, -1, -1, -1}, res);
        Bytes appended2 = BytesBuilder.appendInt(appended1, Integer.MAX_VALUE, ByteOrder.LITTLE_ENDIAN);
        res = new byte[16];
        appended2.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1, -1, -1, -8, 127, -1, -1, -1, -1, -1, -1, 127}, res);
    }

    @Test
    @DisplayName("Appending unsigned ints")
    void appendUnsignedInt() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendUnsignedInt(b1,-8);
        byte[] res = new byte[8];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1, -1, -1, -8}, res);
        Bytes appended1 = BytesBuilder.appendUnsignedInt(appended, Integer.MIN_VALUE);
        res = new byte[12];
        appended1.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1, -1, -1, -8, -128, 0, 0, 0}, res);
        Bytes appended2 = BytesBuilder.appendUnsignedInt(appended1, Integer.MIN_VALUE, ByteOrder.LITTLE_ENDIAN);
        res = new byte[16];
        appended2.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1, -1, -1, -8, -128, 0, 0, 0, 0, 0, 0, -128}, res);
    }

    @Test
    @DisplayName("Appending longs")
    void appendLong() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendLong(b1,101L);
        byte[] res = new byte[12];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 101}, res);
        Bytes appended1 = BytesBuilder.appendLong(appended, (Long.MIN_VALUE + 1));
        res = new byte[20];
        appended1.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 101, -128, 0, 0, 0, 0, 0, 0, 1}, res);
        Bytes appended2 = BytesBuilder.appendLong(appended1, (Long.MIN_VALUE + 1), ByteOrder.LITTLE_ENDIAN);
        res = new byte[28];
        appended2.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 101,
                -128, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, -128}, res);
    }

    @Test
    @DisplayName("Appending floats")
    void appendFloat() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendFloat(b1,1.01f);
        byte[] res = new byte[8];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 63, -127, 71, -82}, res);
        Bytes appended1 = BytesBuilder.appendFloat(appended, (Float.MIN_VALUE + 2.02f));
        res = new byte[12];
        appended1.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 63, -127, 71, -82, 64, 1, 71, -82}, res);
        Bytes appended2 = BytesBuilder.appendFloat(appended1, (Float.MIN_VALUE + 2.02f), ByteOrder.LITTLE_ENDIAN);
        res = new byte[16];
        appended2.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 63, -127, 71, -82, 64, 1, 71, -82, -82, 71, 1, 64}, res);
    }

    @Test
    @DisplayName("Appending doubles")
    void appendDouble() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendDouble(b1,1.01d);
        byte[] res = new byte[12];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 63, -16, 40, -11, -62, -113, 92, 41}, res);
        Bytes appended1 = BytesBuilder.appendDouble(appended, (Double.MAX_VALUE - 2.02d));
        res = new byte[20];
        appended1.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 63, -16, 40, -11, -62, -113, 92, 41, 127, -17, -1, -1, -1, -1, -1, -1}, res);
        Bytes appended2 = BytesBuilder.appendDouble(appended1, (Double.MAX_VALUE - 2.02f), ByteOrder.LITTLE_ENDIAN);
        res = new byte[28];
        appended2.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 63, -16, 40, -11, -62, -113, 92, 41,
                127, -17, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -17, 127}, res);
    }

    @Test
    @DisplayName("Appending VarInt")
    void appendVarInt() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendVarInt(b1,1, false);
        byte[] res = new byte[5];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1}, res);
        Bytes appended1 = BytesBuilder.appendVarInt(appended, 0x7F, false);
        res = new byte[6];
        appended1.getBytes(0, res);
//        ByteArrayOutputStream bout = new ByteArrayOutputStream();
//        WritableStreamingData dout = new WritableStreamingData(bout);
//        dout.writeVarInt(0x7F, false);
//        byte[] writtenData = bout.toByteArray();
//        System.out.println("Lubo1: " + Arrays.toString(res) + ":" + Arrays.toString(writtenData));
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1, 127}, res);
        Bytes appended2 = BytesBuilder.appendVarInt(appended1, 0x7FFF, false);
        res = new byte[9];
        appended2.getBytes(0, res);
//        ByteArrayOutputStream bout = new ByteArrayOutputStream();
//        WritableStreamingData dout = new WritableStreamingData(bout);
//        dout.writeVarInt(0x7FFF, false);
//        byte[] writtenData = bout.toByteArray();
//        System.out.println("Lubo1: " + Arrays.toString(res) + ":" + Arrays.toString(writtenData));
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1, 127, -1, -1, 1}, res);

        Bytes appended3 = BytesBuilder.appendVarInt(appended1, 0xFFFFFFFF, false);
        res = new byte[16];
        appended3.getBytes(0, res);
//        ByteArrayOutputStream bout = new ByteArrayOutputStream();
//        WritableStreamingData dout = new WritableStreamingData(bout);
//        dout.writeVarInt(0xFFFFFFFF, false);
//        byte[] writtenData = bout.toByteArray();
//        System.out.println("Lubo1: " + Arrays.toString(res) + ":" + Arrays.toString(writtenData));
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1, 127, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1}, res);

        Bytes b2 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended4 = BytesBuilder.appendVarInt(b2,1, true);
        res = new byte[5];
        appended4.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2}, res);

        Bytes appended5 = BytesBuilder.appendVarInt(appended4, 0x7F, true);
        res = new byte[7];
        appended5.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1}, res);
        Bytes appended6 = BytesBuilder.appendVarInt(appended5, 0x7FFF, true);
        res = new byte[10];
        appended6.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3}, res);

        Bytes appended7 = BytesBuilder.appendVarInt(appended6, 0xFFFFFFFF, true);
        res = new byte[11];
        appended7.getBytes(0, res);
//        ByteArrayOutputStream bout = new ByteArrayOutputStream();
//        WritableStreamingData dout = new WritableStreamingData(bout);
//        dout.writeVarInt(0xFFFFFFFF, true);
//        byte[] writtenData = bout.toByteArray();
//        System.out.println("Lubo1: " + Arrays.toString(res) + ":" + Arrays.toString(writtenData));
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3, 1}, res);

    }

    @Test
    @DisplayName("Appending VarLong")
    void appendVarLong() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = BytesBuilder.appendVarLong(b1,1L, false);
        byte[] res = new byte[5];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1}, res);

        Bytes appended1 = BytesBuilder.appendVarLong(appended, 0x7FL, false);
        res = new byte[6];
        appended1.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1, 127}, res);

        Bytes appended2 = BytesBuilder.appendVarLong(appended1, 0x7FFFL, false);
        res = new byte[9];
        appended2.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1, 127, -1, -1, 1}, res);

        Bytes appended3 = BytesBuilder.appendVarLong(appended1, 0xFFFFFFFFL, false);
        res = new byte[11];
        appended3.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 1, 127, -1, -1, -1, -1, 15}, res);

        Bytes b2 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended4 = BytesBuilder.appendVarLong(b2, 1L, true);
        res = new byte[5];
        appended4.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2}, res);

        Bytes appended5 = BytesBuilder.appendVarLong(appended4, 0x7FL, true);
        res = new byte[7];
        appended5.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1}, res);

        Bytes appended6 = BytesBuilder.appendVarLong(appended5, 0x7FFFL, true);
        res = new byte[10];
        appended6.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3}, res);

        Bytes appended7 = BytesBuilder.appendVarLong(appended6, 0xFFFFFFFFL, true);
        res = new byte[15];
        appended7.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3, -2, -1, -1, -1, 31}, res);

        Bytes appended8 = BytesBuilder.appendVarLong(appended7, 0xFFFFFFFFFFFFFFFFL, false);
        res = new byte[25];
        appended8.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3, -2, -1, -1, -1, 31,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 1}, res);

        Bytes appended9 = BytesBuilder.appendVarLong(appended8, 0xFFFFFFFFFFFFFFFFL, true);
        res = new byte[26];
        appended9.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3, -2, -1, -1, -1, 31,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, 1}, res);

        Bytes appended10 = BytesBuilder.appendVarLong(appended9, 0x7FFFFFFFFFFFFFFFL, false);
        res = new byte[35];
        appended10.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3, -2, -1, -1, -1, 31,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, 1, -1, -1, -1, -1, -1, -1, -1, -1, 127}, res);

        Bytes appended11 = BytesBuilder.appendVarLong(appended10, 0x7FFFFFFFFFFFFFFFL, true);
        res = new byte[45];
        appended11.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 2, -2, 1, -2, -1, 3, -2, -1, -1, -1, 31,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, 1, -1, -1, -1, -1, -1, -1, -1, -1, 127,
                -2, -1, -1, -1, -1, -1, -1, -1, -1, 1}, res);

    }
}