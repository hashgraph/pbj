package com.hedera.pbj.runtime.json;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A simple JSON lexer that reads from a {@link ReadableSequentialData} and provides read and consume methods. It is
 * designed to avoid looking ahead in the input stream, and instead reads the next byte when needed. It reads data types
 * based on the protobuf JSON format, including strings, numbers, booleans, and null values. It aims to be as fast as
 * possible and avoid object allocation.
 *
 * <p>This class is not thread-safe and should be used by a single thread.
 * <p><b>Table from Protobuf Docs of JSON Mapping of PB types</b>
 * <table><tbody>
 *     <tr><th>Protobuf</th><th>JSON</th><th>JSON example</th><th>Notes</th></tr>
 *     <tr><td>message</td><td>object</td><td><code>{"fooBar": v, "g": null, ...}</code></td><td>Generates JSON objects.
 *     Message field names are mapped to lowerCamelCase and become JSON object keys. If the <code>json_name</code> field
 *     option is specified, the specified value will be used as the key instead. Parsers accept both the lowerCamelCase
 *     name (or the one specified by the <code>json_name</code> option) and the original proto field name.
 *     <code>null</code> is an accepted value for all field types and treated as the default value of the corresponding
 *     field type. However, <code>null</code> cannot be used for the <code>json_name</code> value. For more on why, see
 *     <a href="/news/2023-04-28#json-name">Stricter validation for json_name</a>.</td></tr>
 *     <tr><td>enum</td><td>string</td><td><code>"FOO_BAR"</code></td><td>The name of the enum value as specified in
 *     proto is used. Parsers accept both enum names and integer values.</td></tr><tr><td>map&lt;K,V&gt;</td>
 *     <td>object</td><td><code>{"k": v, ...}</code></td><td>All keys are converted to strings.</td></tr><tr>
 *     <td>repeated V</td><td>array</td><td><code>[v, ...]</code></td><td><code>null</code> is accepted as the empty
 *     list <code>[]</code>.</td></tr>
 *     <tr><td>bool</td><td>true, false</td><td><code>true, false</code></td><td></td></tr><tr><td>string</td>
 *     <td>string</td><td><code>"Hello World!"</code></td><td></td></tr><tr><td>bytes</td><td>base64 string</td><td>
 *     <code>"YWJjMTIzIT8kKiYoKSctPUB+"</code></td><td>JSON value will be the data encoded as a string using standard
 *     base64 encoding with paddings. Either standard or URL-safe base64 encoding with/without paddings is accepted.
 *     </td></tr>
 *     <tr><td>int32, fixed32, uint32</td><td>number</td><td><code>1, -10, 0</code></td><td>JSON value will be a decimal
 *     number. Either numbers or strings are accepted. Empty strings are invalid.</td></tr>
 *     <tr><td>int64, fixed64, uint64</td><td>string</td><td><code>"1", "-10"</code></td><td>JSON value will be a
 *     decimal string. Either numbers or strings are accepted. Empty strings are invalid.</td></tr>
 *     <tr><td>float, double</td><td>number</td><td><code>1.1, -10.0, 0, "NaN", "Infinity"</code></td><td>JSON value
 *     will be a number or one of the special string values "NaN", "Infinity", and "-Infinity". Either numbers or
 *     strings are accepted. Empty strings are invalid. Exponent notation is also accepted.</td></tr>
 *     <tr><td>Any</td><td><code>object</code></td><td><code>{"@type": "url", "f": v, ... }</code></td><td>If the
 *     <code>Any</code> contains a value that has a special JSON mapping, it will be converted as follows:
 *     <code>{"@type": xxx, "value": yyy}</code>. Otherwise, the value will be converted into a JSON object, and the
 *     <code>"@type"</code> field will be inserted to indicate the actual data type.</td></tr>
 *     <tr><td>Timestamp</td><td>string</td><td><code>"1972-01-01T10:00:20.021Z"</code></td><td>Uses RFC 3339, where
 *     generated output will always be Z-normalized and uses 0, 3, 6 or 9 fractional digits. Offsets other than "Z" are
 *     also accepted.</td></tr>
 *     <tr><td>Duration</td><td>string</td><td><code>"1.000340012s", "1s"</code></td><td>Generated output always
 *     contains 0, 3, 6, or 9 fractional digits, depending on required precision, followed by the suffix "s". Accepted
 *     are any fractional digits (also none) as long as they fit into nanosecond precision and the suffix "s" is
 *     required.</td></tr>
 *     <tr><td>Struct</td><td><code>object</code></td><td><code>{ ... }</code></td><td>Any JSON object. See
 *     <code>struct.proto</code>.</td></tr><tr><td>Wrapper types</td><td>various types</td><td><code>2, "2", "foo",
 *     true, "true", null, 0, ...</code></td><td>Wrappers use the same representation in JSON as the wrapped primitive
 *     type, except that <code>null</code> is allowed and preserved during data conversion and transfer.</td></tr>
 *     <tr><td>FieldMask</td><td>string</td><td><code>"f.fooBar,h"</code></td><td>See <code>field_mask.proto</code>.
 *     </td></tr>
 *     <tr><td>ListValue</td><td>array</td><td><code>[foo, bar, ...]</code></td><td></td></tr>
 *     <tr><td>Value</td><td>value</td><td></td><td>Any JSON value. Check
 *     <a href="/reference/protobuf/google.protobuf#value">google.protobuf.Value</a> for details.</td></tr>
 *     <tr><td>NullValue</td><td>null</td><td></td><td>JSON null</td></tr>
 *     <tr><td>Empty</td><td>object</td><td><code>{}</code></td><td>An empty JSON object</td></tr>
 * </tbody>
 * <caption>Table from Protobuf Docs of JSON Mapping of PB types</caption>
 * </table>
 */
public final class JsonLexer {

    private static final int SPACE = 0x20; // Space
    private static final int HORIZONTAL_TAB = 0x09; // Horizontal tab
    private static final int LINE_FEED = 0x0A; // Line feed or New line
    private static final int CARRIAGE_RETURN = 0x0D; // Carriage return
    private static final int QUOTE = 0x22; // Double quote
    private static final int N = 0x6E; // 'n'

    /** the ReadableSequentialData to read from */
    private final ReadableSequentialData data;
    /** scratch space for raw UTF-8 bytes between escapes */
    private byte[] buf = new byte[64];
    /** number of bytes in the buffer buf */
    private int count;

    private boolean hasNextCharRead = false;
    private int nextCharRead;

    /**
     * Construct a new JsonLexer, this is not thread safe and should be used by a single thread.
     *
     * @param data the ReadableSequentialData to read from
     */
    public JsonLexer(ReadableSequentialData data) {
        this.data = data;
    }

    private int readByte() {
        if (hasNextCharRead) {
            hasNextCharRead = false;
            return nextCharRead;
        }
        return data.readByte();
    }

    private void setNextCharRead(int nextCharRead) {
        this.nextCharRead = nextCharRead;
        hasNextCharRead = true;
    }

    /**
     * Consume JSON whitespace from the input data.
     */
    public void consumeWhiteSpace() {
        int c = readByte(); // we have always used "nextCharRead" if one was set
        while (c == SPACE || c == HORIZONTAL_TAB || c == LINE_FEED || c == CARRIAGE_RETURN) {
            c = data.readByte();
        }
        setNextCharRead(c);
    }

    /**
     * Consume JSON whitespace from the input data at the end of the file checking for end of stream
     */
    public void consumeWhiteSpaceEnd() {
        int c;
        if (hasNextCharRead) {
            hasNextCharRead = false;
            c = nextCharRead;
        } else if (data.hasRemaining()){
            c = data.readByte();
        } else { // end of stream
            return;
        }
        while (c == SPACE || c == HORIZONTAL_TAB || c == LINE_FEED || c == CARRIAGE_RETURN) {
            if (!data.hasRemaining()) {
                return;
            }
            c = data.readByte();
        }
        setNextCharRead(c);
    }

    public void openObject() throws ParseException {
        consumeWhiteSpaceEnd();
        final int c = readByte();
        if (c != '{') {
            throw new ParseException(createParseExceptionMessage(c, '{'));
        }
        consumeWhiteSpace();
    }

    public void openArray() throws ParseException {
        consumeWhiteSpace();
        final int c = readByte();
        if (c != '[') {
            throw new ParseException(createParseExceptionMessage(c, '['));
        }
        consumeWhiteSpace();
    }

    public void closeObject() throws ParseException {
        consumeWhiteSpace();
        final int c = readByte();
        if (c != '}') {
            throw new ParseException(createParseExceptionMessage(c, '}'));
        }
        consumeWhiteSpaceEnd();
    }

    public void consumeColon() throws ParseException {
        consumeWhiteSpace();
        final int c = readByte();
        if (c != ':') {
            throw new ParseException(createParseExceptionMessage(c, ':'));
        }
        consumeWhiteSpace();
    }

    public void consumeComma() throws ParseException {
        consumeWhiteSpace();
        final int c = readByte();
        if (c != ',') {
            throw new ParseException(createParseExceptionMessage(c, ','));
        }
        consumeWhiteSpace();
    }

    /**
     * Creates a parse exception message with the given character and expected characters. With other helpful
     * information to aid debugging.
     *
     * @param c the character that was read
     * @param expected the expected characters
     * @return the parse exception message
     */
    private String createParseExceptionMessage(int c, char ... expected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expected ");
        for (int i = 0; i < expected.length; i++) {
            sb.append("'").append(expected[i]).append("'");
            if (i < expected.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(", got: '");
        switch(c) {
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            case '\b' -> sb.append("\\b");
            case '\f' -> sb.append("\\f");
            default -> sb.append((char) c);
        }
        sb.append("' at position: ").append(data.position()-1);
        sb.append(" remaining: ").append(data.remaining());
        if (data instanceof RandomAccessData randomAccessData) {
            // print the 30 bytes around the current position on a line with a "^" under the current position
            StringBuilder sb2 = new StringBuilder();
            for (int i = -15; i < 15; i++) {
                long pos = data.position() + i;
                if (pos >= 0 && pos < data.limit()) {
                    sb2.append((char) randomAccessData.getByte(pos));
                }
            }
            sb.append("\n    JSON surrounding ▶");
            sb.append(sb2.toString()
                    .replace('\n', '↩')
                    .replace('\r', '↵')
                    .replace('\t', '↦')
                    .replace(' ', '␣'));
            sb.append("\n    Current position ▶");
            sb.append(" ".repeat((int)Math.min(15, Math.max(0,data.position()-15))));
            sb.append(" ^");
        }
        return sb.toString();
    }

    /**
     * Checks if the next character is a closing object or closing array or a comma. If it is a closing object or array,
     * it returns false.
     *
     * @return true if there is a next field or false if the object or array is closed
     * @throws ParseException if the input is not a valid JSON object
     */
    public boolean nextFieldOrClose() throws ParseException {
        consumeWhiteSpace();
        final int c = readByte();
        switch (c) {
            case '}', ']' -> {
                consumeWhiteSpaceEnd();
                return false;
            }
            case ',' -> {
                consumeWhiteSpace();
                return true;
            }
            default -> throw new ParseException(createParseExceptionMessage(c, '}', ']', ','));
        }
    }

    /**
     * Parse a JSON string in a SequentialData. Also handles JSON null.
     *
     * @return Java String or null if the input was null
     * @throws ParseException if the input is not a valid JSON string or null
     */
    public String readString() throws ParseException {
        final int firstChar = readByte();
        switch (firstChar) {
            case '}', ']' -> {
                return null; // we have a closing object so return null
            }
            case N -> {
                data.readByte(); // consume 'u', we have always used "nextCharRead" if one was set
                data.readByte(); // consume 'l'
                data.readByte(); // consume 'l'
                return null; // consume 'l'
            }
            case QUOTE -> {
                return readJsonString();
            }
            default -> throw new ParseException(createParseExceptionMessage(firstChar, '"', 'N'));
        }
    }

    /**
     * Parse a JSON boolean in a SequentialData. It can be <code>true</code>, <code>false</code>, <code>"true"</code>,
     * or <code>"false"</code>.
     *
     * @return Java boolean
     * @throws ParseException if the input is not a valid JSON boolean
     */
    public boolean readBoolean() throws ParseException {
        int firstChar = readByte();
        boolean isString = false;
        if (firstChar == QUOTE) {
            isString = true;
            // we have a number a string so jump over the quote
            firstChar = data.readByte();
        }
        if (firstChar == 't') {
            data.readByte(); // consume 'r', we have always used "nextCharRead" if one was set
            data.readByte(); // consume 'u'
            data.readByte(); // consume 'e'
            if (isString) checkClosingQuote(data.readByte());
            return true;
        } else if (firstChar == 'f') {
            data.readByte(); // consume 'a', we have always used "nextCharRead" if one was set
            data.readByte(); // consume 'l'
            data.readByte(); // consume 's'
            data.readByte(); // consume 'e'
            if (isString) checkClosingQuote(data.readByte());
            return false;
        } else {
            throw new ParseException(createParseExceptionMessage(firstChar, 't', 'f'));
        }
    }

    /**
     * Parse a Base64 encoded bytes as a JSON String in a SequentialData.
     *
     * @return Bytes read
     * @throws ParseException if the input is not a valid base 64 bytes
     */
    public Bytes readBytes() throws ParseException {
        final int firstChar = readByte();
        if (firstChar == QUOTE) {
            return Bytes.fromBase64(readJsonString()); // TODO probably a faster way to do this
        } else {
            throw new ParseException(createParseExceptionMessage(firstChar, '"'));
        }
    }

    /**
     * Reads a signed long integer from the input data. If you want 32bit integer, then you need to cast it to int.
     *
     * @return the signed integer value
     * @throws ParseException if the input is not a valid JSON signed integer
     */
    public long readSignedInteger() throws ParseException {
        int firstChar = readByte();
        boolean isString = false;
        if (firstChar == QUOTE) {
            isString = true;
            // we have a number a string so jump over the quote
            firstChar = data.readByte();
        }
        long negative = 1;
        if (firstChar == '-') {
            negative = -1;
            firstChar = data.readByte(); // read next character
        } else if (firstChar == '0') { // fast path for zero
            if (isString) checkClosingQuote(data.readByte());
            return 0;
        }
        if (firstChar < '1' || firstChar > '9') {
            throw new ParseException(
                    createParseExceptionMessage(firstChar, '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'));
        }
        long result = firstChar - '0'; // first digit
        while (true) {
            final int c = data.readByte();
            if (c < '0' || c > '9') { // check if not a digit
                checkClosingQuote(c);
                break;
            }
            result = result * 10 + (c - '0');
        }
        return negative * result;
    }

    /**
     * Reads a double from the input data. The double can be either a string or a number. It can also be one of the
     * special values "NaN", "Infinity", or "-Infinity".
     *
     * @return the double value
     * @throws ParseException if the input is not a valid JSON double
     */
    public double readDouble() throws ParseException {
        int c = readByte();
        boolean isString = false;
        if (c == QUOTE) {
            isString = true;
            // we have a number a string so jump over the quote
            c = data.readByte();
        }
        boolean isNegative = false;
        switch (c) {
            case '-' -> {
                isNegative = true;
                c = data.readByte();
            }
            case 'N' -> {
                data.readByte(); // consume 'a', we have always used "nextCharRead" if one was set
                data.readByte(); // consume 'N'
                if (isString) checkClosingQuote(data.readByte());
                return Double.NaN;
            }
        }
        // handle "Infinity" and "-Infinity"
        if (c == 'I') {
            data.skip(7); // consume 'n', 'f', 'i', 'n', 'i', 't', 'y' TODO does this need to be checked?
            if (isString) checkClosingQuote(data.readByte());
            return isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }

        // First digit
        if (c < '0' || c > '9') {
            throw new ParseException(createParseExceptionMessage(c, '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'));
        }

        // For very large numbers or ones requiring exact precision (like Double.MAX_VALUE),
        // we need to use a string-based approach
        StringBuilder sb = new StringBuilder(32);
        if (isNegative) {
            sb.append('-');
        }
        sb.append((char) c);  // add first digit

        boolean hasDecimalPoint = false;
        boolean hasExponent = false;

        while (true) {
            c = data.readByte();

            // Handle decimal point
            if (c == '.') {
                if (hasDecimalPoint) {
                    throw new ParseException("Unexpected second decimal point in number");
                }
                hasDecimalPoint = true;
                sb.append('.');
                continue;
            }

            // Handle digits
            if (c >= '0' && c <= '9') {
                sb.append((char) c);
                continue;
            }

            // Handle exponent notation
            if (c == 'e' || c == 'E') {
                if (hasExponent) {
                    throw new ParseException("Unexpected second exponent in number");
                }
                hasExponent = true;
                sb.append('E');

                // Handle exponent sign
                c = data.readByte();
                if (c == '-' || c == '+') {
                    sb.append((char) c);
                    c = data.readByte();
                }

                // First digit of exponent must be a digit
                if (c < '0' || c > '9') {
                    throw new ParseException("Expected digit after exponent, got: " + (char) c);
                }

                sb.append((char) c);
                continue;
            }

            // End of number
            if (isString) {
                checkClosingQuote(c);
            } else {
                setNextCharRead(c);
            }

            // Use Double.parseDouble for precise handling of all values including extreme cases
            try {
                return Double.parseDouble(sb.toString());
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid double value: " + sb);
            }
        }
    }

    /**
     * Checks if the closing quote is present. If not, it sets the next character to be read.
     *
     * @param c the character to check
     */
    private void checkClosingQuote(int c) {
        // check for closing quote
        if (c != QUOTE) {// we don't have a number a string so keep last char
            setNextCharRead(c);
        }
    }

    /**
     * Reads a protobuf enum value from the input data. The enum value can be either a string or an integer.
     *
     * @param enumClass the enum class to read
     * @return the enum value
     * @param <E> the enum type
     * @throws ParseException if the input is not a valid JSON enum value
     */
    public <E extends Enum<E>> E readEnum(Class<E> enumClass) throws ParseException {
        final int firstChar = readByte();
        if (firstChar == QUOTE) {
            final String enumValue = readJsonString().toUpperCase();
            try {
                return Enum.valueOf(enumClass, enumValue);
            } catch (IllegalArgumentException e) {
                throw new ParseException("Invalid enum value: \"" + enumValue + "\" in options: " +
                        Arrays.stream(enumClass.getEnumConstants()).map(en -> en.toString())
                                .collect(Collectors.joining(", ")) + " becasue of\n" + e.getMessage());
            }
        } else {
            setNextCharRead(firstChar);
            long res;
            int firstChar1 = readByte();
            if (firstChar1 == QUOTE) {
                // we have a number a string so jump over the quote
                firstChar1 = data.readByte();
            }
            if (firstChar1 == '0') {
                res = 0;// fast path for zero
            } else {
                if (firstChar1 < '1' || firstChar1 > '9') {
                    throw new ParseException(
                            createParseExceptionMessage(firstChar1, '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'));
                }
                long result = firstChar1 - '0'; // first digit
                while (true) {
                    final int c = data.readByte();
                    if (c < '0' || c > '9') { // check if not a digit
                        checkClosingQuote(c);
                        res = result;
                        break;
                    }
                    result = result * 10 + (c - '0');
                }
            }
            final int ordinal = (int) res;
            final E[] enumConstants = enumClass.getEnumConstants();
            if (ordinal < 0 || ordinal >= enumConstants.length) {
                throw new ParseException("Invalid enum ordinal: " + ordinal);
            }
            return enumConstants[ordinal];
        }
    }

    public <K,V> Map<K,V> readMap(Supplier<K> keySupplier, Supplier<V> valueSupplier) throws ParseException {
        openObject();
        Map<K,V> map = Map.of();
        boolean isFirst = true;
        while (true) {
            if (isFirst) {
                isFirst = false;
            } else if (!nextFieldOrClose()) {
                break;
            }
            // read field name and colon
            final String fieldName = readString();
            if (fieldName == null) break;// there are no fields or no more fields
            consumeColon();
            // read and handle field value
            K key = keySupplier.get();
            V value = valueSupplier.get();
            map.put(key, value);
        }
        return map;
    }

    /* ----- Private Methods ------------------------------------------------------------- */

    /** called by the parser – the opening quote has already been consumed */
    private String readJsonString() throws ParseException {
        StringBuilder out = new StringBuilder(32);     // final result

        while (true) {
            int b = data.readByte();                       // your I/O source
            if (b == '"') {                           // closing quote
                flushUtf8Chunk(out);
                return out.toString();                // done
            }
            if (b != '\\') {                          // fast-path: plain byte
                ensureCapacity(1);
                buf[count++] = (byte) b;
                continue;
            }

            /* ---- slow-path: we hit an escape sequence ---- */
            flushUtf8Chunk(out);                      // decode bytes seen so far
            int esc = data.readByte();
            switch (esc) {
                case '"':  out.append('"');  break;
                case '\\': out.append('\\'); break;
                case '/':  out.append('/');  break;
                case 'b':  out.append('\b'); break;
                case 'f':  out.append('\f'); break;
                case 'n':  out.append('\n'); break;
                case 'r':  out.append('\r'); break;
                case 't':  out.append('\t'); break;
                case 'u':                        // \ uXXXX
                    out.append(readUnicodeEscape());
                    break;
                default:
                    throw new ParseException("Invalid escape: \\" + (char) esc);
            }
        }
    }

    /** decode any buffered UTF-8 bytes and append to the StringBuilder */
    private void flushUtf8Chunk(StringBuilder out) throws ParseException {
        if (count == 0) return;
        out.append(new String(buf, 0, count, StandardCharsets.UTF_8));
        count = 0;
    }

    /** grow the buffer when necessary (amortised O(1)) */
    private void ensureCapacity(int needed) {
        int required = count + needed;
        if (required > buf.length) {
            byte[] bigger = new byte[Math.max(required, buf.length * 2)];
            System.arraycopy(buf, 0, bigger, 0, count);
            buf = bigger;
        }
    }

    /** parse four hex digits after <code>\ u</code> and return the resulting code unit */
    private char readUnicodeEscape() throws ParseException {
        int cp = 0;
        for (int i = 0; i < 4; i++) {
            cp = (cp << 4) | hexValue(data.readByte());
        }
        return (char) cp;
    }

    private static int hexValue(int ch) throws ParseException {
        if      ('0' <= ch && ch <= '9') return ch - '0';
        else if ('a' <= ch && ch <= 'f') return 10 + ch - 'a';
        else if ('A' <= ch && ch <= 'F') return 10 + ch - 'A';
        throw new ParseException("Bad \\u escape digit: " + (char) ch);
    }
}
