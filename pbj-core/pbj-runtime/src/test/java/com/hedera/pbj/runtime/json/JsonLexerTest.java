package com.hedera.pbj.runtime.json;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class JsonLexerTest {

    // Test enum for testing readEnum method
    private enum TestEnum {
        FIRST,
        SECOND,
        THIRD
    }

    @Test
    void simpleTest() throws Exception {
        String json = """
                {
                  "name": "Alice",
                  "age": 30
                }
                """;
        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();
        String fieldName1 = lexer.readString();
        assertEquals("name", fieldName1);
        lexer.consumeColon();
        String fieldValue1 = lexer.readString();
        assertEquals("Alice", fieldValue1);
        lexer.consumeComma();

        String fieldName2 = lexer.readString();
        lexer.consumeColon();
        assertEquals("age", fieldName2);
        long fieldValue2 = lexer.readSignedInteger();
        assertEquals(30, fieldValue2);
        lexer.closeObject();
    }

    @ParameterizedTest
    @ValueSource(longs = {0,1,10,-1, -10, Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE})
    void integerTest(long value) throws Exception {
        String json = """
                {
                  "number": "$value"
                }
                """.replace("$value", String.valueOf(value));
        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();
        String fieldName1 = lexer.readString();
        assertEquals("number", fieldName1);
        lexer.consumeColon();
        long readValue = lexer.readSignedInteger();
        assertEquals(value, readValue);
        lexer.closeObject();
    }

    @Test
    void booleanTest() throws Exception {
        String json = """
                {
                  "trueValue": true,
                  "falseValue": false,
                  "trueValue2": "true"
                }
                """;
        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();

        String fieldName1 = lexer.readString();
        assertEquals("trueValue", fieldName1);
        lexer.consumeColon();
        boolean trueValue = lexer.readBoolean();
        assertTrue(trueValue);
        lexer.consumeComma();

        String fieldName2 = lexer.readString();
        assertEquals("falseValue", fieldName2);
        lexer.consumeColon();
        boolean falseValue = lexer.readBoolean();
        assertFalse(falseValue);
        lexer.consumeComma();

        String fieldName3 = lexer.readString();
        assertEquals("trueValue2", fieldName3);
        lexer.consumeColon();
        boolean trueValue2 = lexer.readBoolean();
        assertTrue(trueValue2);

        lexer.closeObject();
    }

    private static final DecimalFormat doubleFormat = new DecimalFormat("0.###############################");

    @ParameterizedTest
    @ValueSource(doubles = {0,1.123,10.1234,-1.1234, -10.1234, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN})
    void doubleTest(double value) throws Exception {
        String json = """
                {
                  "numberStr": "$value",
                  "number": $value
                }
                """.replace("$value", Double.toString(value));
        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();
        {
            String fieldName = lexer.readString();
            assertEquals("numberStr", fieldName);
            lexer.consumeColon();
            double readValue = lexer.readDouble();
            assertEquals(value, readValue, 0.000001,
                    "Expected: " + Double.toString(value) + ",\n"
                           + "but got : " + Double.toString(readValue) + "\n"
                    + "json:" + json);
        }
        lexer.consumeComma();
        {
            String fieldName = lexer.readString();
            assertEquals("number", fieldName);
            lexer.consumeColon();
            double readValue = lexer.readDouble();
            assertEquals(value, readValue, 0.000001);
        }
        lexer.closeObject();
    }

    @Test
    void doubleSpecialValuesTest() throws Exception {
        String json = """
                {
                  "nan": "NaN",
                  "infinity": "Infinity",
                  "negativeInfinity": "-Infinity"
                }
                """;
        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();

        String fieldName1 = lexer.readString();
        assertEquals("nan", fieldName1);
        lexer.consumeColon();
        double nanValue = lexer.readDouble();
        assertTrue(Double.isNaN(nanValue));
        lexer.consumeComma();

        String fieldName2 = lexer.readString();
        assertEquals("infinity", fieldName2);
        lexer.consumeColon();
        double infinityValue = lexer.readDouble();
        assertTrue(Double.isInfinite(infinityValue) && infinityValue > 0);
        lexer.consumeComma();

        String fieldName3 = lexer.readString();
        assertEquals("negativeInfinity", fieldName3);
        lexer.consumeColon();
        double negInfinityValue = lexer.readDouble();
        assertTrue(Double.isInfinite(negInfinityValue) && negInfinityValue < 0);

        lexer.closeObject();
    }

    @Test
    void bytesTest() throws Exception {
        byte[] testData = "Hello, world!".getBytes(StandardCharsets.UTF_8);
        String base64Data = Base64.getEncoder().encodeToString(testData);

        String json = """
                {
                  "data": "$value"
                }
                """.replace("$value", base64Data);

        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();
        String fieldName = lexer.readString();
        assertEquals("data", fieldName);
        lexer.consumeColon();
        Bytes readValue = lexer.readBytes();

        assertArrayEquals(testData, readValue.toByteArray());

        lexer.closeObject();
    }

    @Test
    void enumTest() throws Exception {
        String json = """
                {
                  "enumByName": "SECOND",
                  "enumByOrdinal": 2,
                  "enumByZero": 0
                }
                """;

        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();

        String fieldName1 = lexer.readString();
        assertEquals("enumByName", fieldName1);
        lexer.consumeColon();
        TestEnum enumValue1 = lexer.readEnum(TestEnum.class);
        assertEquals(TestEnum.SECOND, enumValue1);
        lexer.consumeComma();

        String fieldName2 = lexer.readString();
        assertEquals("enumByOrdinal", fieldName2);
        lexer.consumeColon();
        TestEnum enumValue2 = lexer.readEnum(TestEnum.class);
        assertEquals(TestEnum.THIRD, enumValue2);
        lexer.consumeComma();

        String fieldName3 = lexer.readString();
        assertEquals("enumByZero", fieldName3);
        lexer.consumeColon();
        TestEnum enumValue3 = lexer.readEnum(TestEnum.class);
        assertEquals(TestEnum.FIRST,enumValue3);

        lexer.closeObject();
    }

    @Test
    void arrayTest() throws Exception {
        String json = """
                {
                  "array": [1, 2, 3]
                }
                """;

        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();
        String fieldName = lexer.readString();
        assertEquals("array", fieldName);
        lexer.consumeColon();

        lexer.openArray();

        long value1 = lexer.readSignedInteger();
        assertEquals(1, value1);
        assertTrue(lexer.nextFieldOrClose());

        long value2 = lexer.readSignedInteger();
        assertEquals(2, value2);
        assertTrue(lexer.nextFieldOrClose());

        long value3 = lexer.readSignedInteger();
        assertEquals(3, value3);
        assertFalse(lexer.nextFieldOrClose());

        lexer.closeObject();
    }

    @Test
    void nullTest() throws Exception {
        String json = """
                {
                  "nullValue": null
                }
                """;

        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();
        String fieldName = lexer.readString();
        assertEquals("nullValue", fieldName);
        lexer.consumeColon();

        String nullValue = lexer.readString();
        assertNull(nullValue);

        lexer.closeObject();
    }

    @Test
    void whitespaceTest() throws Exception {
        String json = "  {  \n" +
                      "     \"value\"  :  42  \n" +
                      "  }  \n";

        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();

        String fieldName = lexer.readString();
        assertEquals("value", fieldName);
        lexer.consumeColon();

        long value = lexer.readSignedInteger();
        assertEquals(42, value);

        lexer.closeObject();
    }

    @Test
    void nestedObjectTest() throws Exception {
        String json = """
                {
                  "outer": {
                    "inner": "value"
                  }
                }
                """;

        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();

        String fieldName = lexer.readString();
        assertEquals("outer", fieldName);
        lexer.consumeColon();

        lexer.openObject();
        String innerFieldName = lexer.readString();
        assertEquals("inner", innerFieldName);
        lexer.consumeColon();

        String value = lexer.readString();
        assertEquals("value", value);

        lexer.closeObject();
        lexer.closeObject();
    }

    @Test
    void escapeSequencesTest() throws Exception {
        String json = """
                {
                  "escaped": "\\"\\\\/\\b\\f\\n\\r\\t\\u0041"
                }
                """;

        JsonLexer lexer = new JsonLexer(readableSequentialData(json));
        lexer.openObject();

        String fieldName = lexer.readString();
        assertEquals("escaped", fieldName);
        lexer.consumeColon();

        String value = lexer.readString();
        assertEquals("\"\\/\b\f\n\r\tA", value);

        lexer.closeObject();
    }

    @Test
    void parseExceptionTest() {
        String invalidJson = "{\"field\": invalid}";

        JsonLexer lexer = new JsonLexer(readableSequentialData(invalidJson));

        Exception exception = assertThrows(ParseException.class, () -> {
            lexer.openObject();
            lexer.readString();
            lexer.consumeColon();
            lexer.readString(); // This should throw because "invalid" is not a valid JSON string
        });

        assertTrue(exception.getMessage().contains("Expected"));
    }

    @Test
    void parseExceptionReadBoolean() throws ParseException {
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("bad")).readBoolean());
    }

    @Test
    void parseExceptionReadBytes() throws ParseException {
        // missing the starting quote
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("somebaddata")).readBytes());
    }

    @Test
    void parseExceptionNextFieldOrClose() throws ParseException {
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData(".")).nextFieldOrClose());
    }
    @Test
    void consumeWrongCharacters() throws ParseException {
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData(".")).consumeComma());
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData(".")).consumeColon());
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("[")).openObject());
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("]")).closeObject());
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("{")).openArray());
    }


    @Test
    void parseExceptionEnumErrors() throws ParseException {
        // check for non digit
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("x,")).readEnum(TestEnum.class));
        // 4 is a digit but TestEnum only ahs three ordinals
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("4,")).readEnum(TestEnum.class));
        // FOURTH is not a valid value of TestEnum
        assertThrows(ParseException.class, () -> new JsonLexer(readableSequentialData("\"FOURTH\",")).readEnum(TestEnum.class));
    }

    private ReadableSequentialData readableSequentialData(String json) {
        return BufferedData.wrap(json.getBytes(StandardCharsets.UTF_8));
    }
}
