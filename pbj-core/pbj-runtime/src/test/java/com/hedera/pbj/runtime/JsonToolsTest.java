package com.hedera.pbj.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonToolsTest {
    @Test
    void testToJsonFieldName() {
        assertEquals("foo",JsonTools.toJsonFieldName("foo"));
        assertEquals("fooBar",JsonTools.toJsonFieldName("foo_Bar"));
        assertEquals("fooBar",JsonTools.toJsonFieldName("foo_bar"));
        assertEquals("fooBar",JsonTools.toJsonFieldName("fooBar"));
        assertEquals("foobar",JsonTools.toJsonFieldName("foobar"));
    }
    @Test
    void testUnescape() {
        assertEquals("foo", JsonTools.unescape("foo"));
        assertEquals("fo\no", JsonTools.unescape("fo\no"));
        assertEquals("fo\no", JsonTools.unescape("fo\\no"));
        assertEquals("fo\ro", JsonTools.unescape("fo\ro"));
        assertEquals("fo\ro", JsonTools.unescape("fo\\ro"));
    }
    @Test
    void testEscape() {
        assertEquals("f\\noo", JsonTools.escape("f\noo"));
        assertEquals("f\\roo", JsonTools.escape("f\roo"));
        assertEquals("f\\n\\roo", JsonTools.escape("f\n\roo"));
    }
//    @Test
//    void testParseJson() {
//        CharBuffer buf = CharBuffer.allocate(13);
//        buf.put("{}");
//        var out = JsonTools.parseJson(buf);
//        System.out.print("output is " + out);
////        assertEquals(JsonTools.parseJson(charBuffer));
//    }

    @Test
    void testOutputStrings(){
        // bytes
        assertEquals("\"foo\": 5",JsonTools.field("foo",(byte)5));
        assertEquals("\"foo\": 5",JsonTools.field("foo",(Byte)(byte)5));
        // bytes become base64 encoded
        assertEquals("\"foo\": \"YWJj\"",JsonTools.field("foo","abc".getBytes(StandardCharsets.UTF_8)));
//        assertEquals("\"foo\": \"YWJj\"",JsonTools.field("foo",(Bytes)("abc".getBytes(StandardCharsets.UTF_8))));

        // ints and longs
        assertEquals("\"foo\": 5",JsonTools.field("foo",5));
        assertEquals("\"foo\": 5",JsonTools.field("foo",(Integer) 5));
        assertEquals("\"foo\": "+Integer.MIN_VALUE,JsonTools.field("foo",Integer.MIN_VALUE));
        assertEquals("\"foo\": 0",JsonTools.field("foo",0));
        assertEquals("\"foo\": "+Integer.MAX_VALUE,JsonTools.field("foo",Integer.MAX_VALUE));
        assertEquals("\"foo\": null",JsonTools.field("foo",(Integer) null));

        assertEquals("\"foo\": \"5\"",JsonTools.field("foo",5L));
        assertEquals("\"foo\": \"5\"",JsonTools.field("foo",(Long)5L));
        assertEquals("\"foo\": \""+Long.MIN_VALUE+"\"",JsonTools.field("foo",Long.MIN_VALUE));
        assertEquals("\"foo\": \""+Long.MAX_VALUE+"\"",JsonTools.field("foo",Long.MAX_VALUE));
        assertEquals("\"foo\": \"5\"",JsonTools.field("foo",(Long)5L,true));
        assertEquals("\"foo\": 5",JsonTools.field("foo",(Long)5L,false));
//        assertEquals("\"foo\": null",JsonTools.field("foo",(Long) null));

        // floats and doubles
        assertEquals("\"foo\": 5.5",JsonTools.field("foo",5.5f));
        assertEquals("\"foo\": 5.5",JsonTools.field("foo",(Float)5.5f));
        assertEquals("\"foo\": \"-Infinity\"",JsonTools.field("foo",Float.NEGATIVE_INFINITY));
        assertEquals("\"foo\": 1.4E-45",JsonTools.field("foo",Float.MIN_VALUE));
        assertEquals("\"foo\": \"NaN\"",JsonTools.field("foo",Float.NaN));
        assertEquals("\"foo\": 3.4028235E38",JsonTools.field("foo",Float.MAX_VALUE));
        assertEquals("\"foo\": \"Infinity\"",JsonTools.field("foo",Float.POSITIVE_INFINITY));
        assertEquals("\"foo\": null",JsonTools.field("foo",(Float)null));

        assertEquals("\"foo\": 5.5",JsonTools.field("foo",5.5d));
        assertEquals("\"foo\": 5.5",JsonTools.field("foo",(Double)5.5d));
        assertEquals("\"foo\": \"-Infinity\"",JsonTools.field("foo",Double.NEGATIVE_INFINITY));
        assertEquals("\"foo\": 4.9E-324",JsonTools.field("foo",Double.MIN_VALUE));
        assertEquals("\"foo\": \"NaN\"",JsonTools.field("foo",Double.NaN));
        assertEquals("\"foo\": 1.7976931348623157E308",JsonTools.field("foo",Double.MAX_VALUE));
        assertEquals("\"foo\": \"Infinity\"",JsonTools.field("foo",Double.POSITIVE_INFINITY));
        assertEquals("\"foo\": null",JsonTools.field("foo",(Double) null));

        // booleans
        assertEquals("\"foo\": true",JsonTools.field("foo",true));
        assertEquals("\"foo\": false",JsonTools.field("foo",false));
        assertEquals("\"foo\": true",JsonTools.field("foo",Boolean.TRUE));
        assertEquals("\"foo\": false",JsonTools.field("foo",Boolean.FALSE));
        assertEquals("\"foo\": null",JsonTools.field("foo",(Boolean)null));

        // strings
        assertEquals("\"foo\": \"bar\"",JsonTools.field("foo","bar"));
        // TODO: is this correct? Shouldn't a double quote be escaped?
        assertEquals("\"foo\": \"b\"ar\"",JsonTools.field("foo","b\"ar"));
        assertEquals("\"foo\": \"b'ar\"",JsonTools.field("foo","b'ar"));
    }

}
