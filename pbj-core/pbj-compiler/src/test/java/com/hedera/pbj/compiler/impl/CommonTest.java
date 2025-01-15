// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the common utility methods.
 */
final class CommonTest {

    // ================================================================================================================
    // Verify common comments.
    @Test
    @DisplayName("Test remove double asterisk")
    void doubleAsterisk() {
        String str = "/** This is a test */";
        String result = Common.cleanJavaDocComment(str);
        assertEquals("This is a test", result);
    }

    @Test
    @DisplayName("Test comment with params and return")
    void commentWithParamsAndReturn() {
        String str = "/**\n*   Clean up a java doc style comment removing all the \"*\" etc.\n*\n* @param  fieldComment raw Java doc style comment\n* @return clean multi-line content of the comment\n*/\n";
        String result = Common.cleanJavaDocComment(str);
        String expected = "Clean up a java doc style comment removing all the \"*\" etc.\n*\n* @param  fieldComment raw Java doc style comment\n* @return clean multi-line content of the comment";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test one line comment on lultiple lines")
    void oneLineOnMultipleLines() {
        String str = """
                /**
                     * The capacity of this sequence will be the difference between the <b>initial</b> position and the length of the delegate
                     */
                """;
        String result = Common.cleanJavaDocComment(str);
        String expected = "The capacity of this sequence will be the difference between the <b>initial</b> position and the length of the delegate";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns")
    void oneParamsThrowsAndReturns() {
        String str = """
                /**
                     * Reads the signed byte at current {@link #position()}, and then increments the {@link #position()} by 1.
                     *
                     * @return The signed byte at the current {@link #position()}
                     * @throws BufferUnderflowException If there are no bytes remaining in this sequence
                     * @throws DataAccessException If an I/O error occurs
                     */""";
        String result = Common.cleanJavaDocComment(str);
        String expected = """
                Reads the signed byte at current {@link #position()}, and then increments the {@link #position()} by 1.
                
                @return The signed byte at the current {@link #position()}
                @throws BufferUnderflowException If there are no bytes remaining in this sequence
                @throws DataAccessException If an I/O error occurs""";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns")
    void oneParamsThrowsAndReturnsWithMore() {
        String str = """
             /**
              * Read bytes starting at current {@link #position()} into the {@code dst} array, up to the size of the {@code dst}
              * array. If {@code dst} is larger than the remaining bytes in the sequence, only the remaining bytes are read.
              * The total number of bytes actually read are returned. The bytes will be placed starting at index 0 of the array.
              * The {@link #position()} will be incremented by the number of bytes read. If no bytes are available in the
              * sequence, then 0 is returned.
              * <p>
              * Non-closed P between two paragraphs.
              *
              * <p>P at beginning of paragraph.With lots of text. Lipsum dolor sit amet, consectetur adipiscing elit.
              * Nulla nec purus nec.
              *
              * <p>P at beginning of paragraph 2.With lots of text. Lipsum dolor sit amet, consectetur adipiscing elit.
              * Nulla nec purus nec.
              * <p>
              * <ul>
              *     <li>Item 1 - with loose P before another tag</li>
              *     <li>Item 2</li>
              * </ul>
              *
              * <p>Simple closed paragraph.</p>
              *
              * <p>
              * New line closed paragraph.
              * </p>
              *
              * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
              * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
              * incremented by the number of bytes read prior to the exception.
              *
              * @param dst The destination array. Cannot be null.
              * @throws NullPointerException if {@code dst} is null
              * @throws DataAccessException If an I/O error occurs
              * @return The number of bytes read actually read and placed into {@code dst}
              */
             """;
        String result = Common.cleanJavaDocComment(str);
        String expected = """
                Read bytes starting at current {@link #position()} into the {@code dst} array, up to the size of the {@code dst}
                array. If {@code dst} is larger than the remaining bytes in the sequence, only the remaining bytes are read.
                The total number of bytes actually read are returned. The bytes will be placed starting at index 0 of the array.
                The {@link #position()} will be incremented by the number of bytes read. If no bytes are available in the
                sequence, then 0 is returned.
                <p>
                Non-closed P between two paragraphs.</p>
                
                <p>P at beginning of paragraph.With lots of text. Lipsum dolor sit amet, consectetur adipiscing elit.
                Nulla nec purus nec.</p>
                
                <p>P at beginning of paragraph 2.With lots of text. Lipsum dolor sit amet, consectetur adipiscing elit.
                Nulla nec purus nec.</p>
                
                
                <ul>
                <li>Item 1 - with loose P before another tag</li>
                <li>Item 2</li>
                </ul>
                
                <p>Simple closed paragraph.</p>
                
                <p>
                New line closed paragraph.
                </p>
                
                <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
                sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
                incremented by the number of bytes read prior to the exception.</p>
                
                @param dst The destination array. Cannot be null.
                @throws NullPointerException if {@code dst} is null
                @throws DataAccessException If an I/O error occurs
                @return The number of bytes read actually read and placed into {@code dst}""";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns more")
    void oneParamsThrowsAndReturnsWithMore2() {
        String str = """
                
                    /**
                     * Read bytes starting at the current {@link #position()} into the {@code dst} array, up to {@code maxLength}
                     * number of bytes. If {@code maxLength} is larger than the remaining bytes in the sequence, only the remaining
                     * bytes are read. The total number of bytes actually read are returned. The bytes will be placed starting at index
                     * {@code offset} of the array. The {@link #position()} will be incremented by the number of bytes read. If no
                     * bytes are available in the sequence, then 0 is returned.
                     *
                     * <p>The {@code dst} array may be partially written to at the time that any of the declared exceptions are thrown.
                     *
                     * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
                     * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
                     * incremented by the number of bytes read prior to the exception.
                     *
                     * @param dst The array into which bytes are to be written
                     * @param offset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
                     *                no larger than {@code dst.length - maxLength}.
                     * @param maxLength The maximum number of bytes to be written to the given {@code dst} array; must be non-negative
                     *                and no larger than {@code dst.length - offset}
                     * @throws NullPointerException If {@code dst} is null
                     * @throws IndexOutOfBoundsException If {@code offset} is out of bounds of {@code dst} or if
                     *                                  {@code offset + maxLength} is not less than {@code dst.length}
                     * @throws IllegalArgumentException If {@code maxLength} is negative
                     * @throws DataAccessException If an I/O error occurs
                     * @return The number of bytes read actually read and placed into {@code dst}
                     */
                """;
        String result = Common.cleanJavaDocComment(str);
        String expected = """
                Read bytes starting at the current {@link #position()} into the {@code dst} array, up to {@code maxLength}
                number of bytes. If {@code maxLength} is larger than the remaining bytes in the sequence, only the remaining
                bytes are read. The total number of bytes actually read are returned. The bytes will be placed starting at index
                {@code offset} of the array. The {@link #position()} will be incremented by the number of bytes read. If no
                bytes are available in the sequence, then 0 is returned.
                
                <p>The {@code dst} array may be partially written to at the time that any of the declared exceptions are thrown.</p>
                
                <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
                sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
                incremented by the number of bytes read prior to the exception.</p>
                
                @param dst The array into which bytes are to be written
                @param offset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
                no larger than {@code dst.length - maxLength}.
                @param maxLength The maximum number of bytes to be written to the given {@code dst} array; must be non-negative
                and no larger than {@code dst.length - offset}
                @throws NullPointerException If {@code dst} is null
                @throws IndexOutOfBoundsException If {@code offset} is out of bounds of {@code dst} or if
                {@code offset + maxLength} is not less than {@code dst.length}
                @throws IllegalArgumentException If {@code maxLength} is negative
                @throws DataAccessException If an I/O error occurs
                @return The number of bytes read actually read and placed into {@code dst}""";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns more 2")
    void oneParamsThrowsAndReturnsWithMore3() {
        String str = """
                 /**
                     * Reads the next four bytes at the current {@link #position()}, composing them into an int value according to
                     * specified byte order, and then increments the {@link #position()} by four.
                     *
                     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
                     * @return The int value at the current {@link #position()}
                     * @throws BufferUnderflowException If there are fewer than four bytes remaining
                     * @throws DataAccessException if an I/O error occurs
                     */\
                """;
        String result = Common.cleanJavaDocComment(str);
        String expected = """
                Reads the next four bytes at the current {@link #position()}, composing them into an int value according to
                specified byte order, and then increments the {@link #position()} by four.
                
                @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
                @return The int value at the current {@link #position()}
                @throws BufferUnderflowException If there are fewer than four bytes remaining
                @throws DataAccessException if an I/O error occurs""";
        assertEquals(expected, result);
    }
}
