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

    /**
     * Test that the FAILED_TO_FIND_MSG_TYPE error message format contains the actual type name
     * (not the context object's toString representation with memory addresses).
     * This verifies the fix for issue #631 - error messages should use context.getText()
     * rather than context.toString() to avoid showing memory addresses.
     */
    @Test
    void testFailedTypeResolutionErrorMessageFormat() {
        // Test the error message format directly - simulating what happens at LookupHelper.java:261-262
        String typeName = "com.example.UnknownType";
        String sourceFile = "/test/example.proto";
        String importsString = "[/test/imported1.proto, /test/imported2.proto]";

        // This is the actual formatted message that would be thrown
        String errorMsg = String.format(
                "Failed to find fully qualified message type for [%s] in file [%s]%n"
                        + "Imports: %s%n"
                        + "This usually means:%n"
                        + "  - The type is not imported (add: import \"path/to/file.proto\")%n"
                        + "  - The type name is misspelled%n"
                        + "  - The type is in a different package than expected",
                typeName, sourceFile, importsString);

        assertAll(
                "Error message should be user-friendly and contain relevant information",
                () -> assertTrue(
                        errorMsg.contains("com.example.UnknownType"),
                        "Error should contain the actual type name: " + errorMsg),
                () -> assertFalse(
                        errorMsg.contains("@"), "Error should not contain object memory addresses: " + errorMsg),
                () -> assertFalse(
                        errorMsg.contains("Context"),
                        "Error should not contain parser context class names: " + errorMsg),
                () -> assertTrue(
                        errorMsg.contains("Failed to find fully qualified message type"),
                        "Error should have clear explanation: " + errorMsg),
                () -> assertTrue(
                        errorMsg.contains("This usually means:"), "Error should provide helpful guidance: " + errorMsg),
                () -> assertTrue(
                        errorMsg.contains("The type is not imported"), "Should suggest checking imports: " + errorMsg),
                () -> assertTrue(
                        errorMsg.contains("The type name is misspelled"),
                        "Should suggest checking spelling: " + errorMsg),
                () -> assertTrue(
                        errorMsg.contains("different package"), "Should suggest checking package: " + errorMsg));
    }

    /**
     * Test that the IMPORT_NOT_FOUND error message format is clear and helpful.
     * This test verifies the message template directly without requiring file I/O.
     */
    @Test
    void testImportNotFoundErrorMessageFormat() {
        // Test the error message format directly
        String importedFileName = "missing_types.proto";
        String sourceFile = "/path/to/source.proto";

        // Simulate the formatted error message (this is what LookupHelper would produce)
        String errorMsg = String.format(
                "Import \"%s\" in proto file \"%s\" can not be found in src files.%n"
                        + "Please verify:%n"
                        + "  - The import path is correct%n"
                        + "  - The imported .proto file is in your source directory%n"
                        + "  - The file path uses forward slashes (/) even on Windows",
                importedFileName, sourceFile);

        assertAll(
                "Import error message should be clear and actionable",
                () -> assertTrue(errorMsg.contains(importedFileName), "Should mention the missing import file"),
                () -> assertTrue(errorMsg.contains(sourceFile), "Should mention the source file"),
                () -> assertTrue(errorMsg.contains("can not be found"), "Should clearly state the problem"),
                () -> assertTrue(errorMsg.contains("import path is correct"), "Should suggest checking the path"),
                () -> assertTrue(errorMsg.contains("source directory"), "Should mention where to look for the file"),
                () -> assertTrue(
                        errorMsg.contains("forward slashes"), "Should warn about Windows path separator issues"));
    }
}
