// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static com.hedera.pbj.compiler.impl.LookupHelper.extractComparableFields;
import static com.hedera.pbj.compiler.impl.LookupHelper.normalizeFileName;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageBodyContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageElementContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LookupHelperTest {
    @Mock
    MessageDefContext defContext;

    @Mock
    Protobuf3Parser.OptionCommentContext optionComment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testNormalizeFileName_withQuotes() {
        normalizeAndVerify("\"state/common.proto\"");
    }

    @Test
    void testNormalizeFileName_noQuotes() {
        normalizeAndVerify("state/common.proto");
    }

    @Test
    void testNormalizeFileName_alreadyNormalized() {
        String fileName = "common.proto";
        assertEquals(fileName, normalizeFileName(fileName));
    }

    private static void normalizeAndVerify(String fileName) {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            String expected = "state\\common.proto";
            String actual = normalizeFileName(fileName);
            assertEquals(expected, actual);
        } else {
            String expected = "state/common.proto";
            String actual = normalizeFileName(fileName);
            assertEquals(expected, actual);
        }
    }

    @Test
    void testExtractComparableFields_nullComment() {
        assertTrue(extractComparableFields(defContext).isEmpty(), "Should return empty list");
    }

    @Test
    void testExtractComparableFields_emptyComment() {
        when(defContext.optionComment()).thenReturn(optionComment);
        assertTrue(extractComparableFields(defContext).isEmpty(), "Should return empty list");
    }

    @Test
    void testExtractComparableFields_malformedComment() {
        when(optionComment.getText()).thenReturn("B1sr9i4TZp");
        when(defContext.optionComment()).thenReturn(optionComment);
        assertTrue(extractComparableFields(defContext).isEmpty(), "Should return empty list");
    }

    @Test
    void testExtractComparableFields_notApplicableComment() {
        when(optionComment.getText()).thenReturn("// <<<pbj.java_package = \"com.hedera.pbj.test.proto.pbj\">>>");
        when(defContext.optionComment()).thenReturn(optionComment);
        assertTrue(extractComparableFields(defContext).isEmpty(), "Should return empty list");
    }

    @Test
    void testExtractComparableFields_commentWithUnkownField() {
        when(optionComment.getText())
                .thenReturn("// <<<pbj.comparable = \"int32Number, int64Number, unknown, text\" >>>");
        when(defContext.optionComment()).thenReturn(optionComment);
        final var messageBody = mock(MessageBodyContext.class);
        final var int32Number = createMessageElement("int32Number");
        final var int64Number = createMessageElement("int64Number");
        final var text = createMessageElement("text");
        when(messageBody.messageElement()).thenReturn(asList(int32Number, int64Number, text));
        when(defContext.messageBody()).thenReturn(messageBody);
        assertThrows(
                IllegalArgumentException.class,
                () -> extractComparableFields(defContext),
                "Should throw IllegalArgumentException");
    }

    @Test
    void testExtractComparableFields_validComment() {
        when(optionComment.getText()).thenReturn("// <<<pbj.comparable = \"int32Number, int64Number, text\" >>>");
        when(defContext.optionComment()).thenReturn(optionComment);
        final var messageBody = mock(MessageBodyContext.class);
        final var int32Number = createMessageElement("int32Number");
        final var int64Number = createMessageElement("int64Number");
        final var text = createMessageElement("text");
        when(messageBody.messageElement()).thenReturn(asList(int32Number, int64Number, text));
        when(defContext.messageBody()).thenReturn(messageBody);
        List<String> comparableFields = extractComparableFields(defContext);
        assertEquals(3, comparableFields.size(), "Should return 3 fields");
        assertEquals("int32Number", comparableFields.get(0), "Should return int32Number");
        assertEquals("int64Number", comparableFields.get(1), "Should return int64Number");
        assertEquals("text", comparableFields.get(2), "Should return text");
    }

    private static MessageElementContext createMessageElement(final String fieldNameStr) {
        final var messageElement = mock(MessageElementContext.class);
        final var field = mock(Protobuf3Parser.FieldContext.class);
        final var fieldName = mock(Protobuf3Parser.FieldNameContext.class);
        when(fieldName.getText()).thenReturn(fieldNameStr);
        when(field.fieldName()).thenReturn(fieldName);
        when(messageElement.field()).thenReturn(field);

        return messageElement;
    }
}
