// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        String str =
                "/**\n"
                        + "*   Clean up a java doc style comment removing all the \"*\" etc.\n"
                        + "*\n"
                        + "* @param  fieldComment raw Java doc style comment\n"
                        + "* @return clean multi-line content of the comment\n"
                        + "*/\n";
        String result = Common.cleanJavaDocComment(str);
        String expected =
                "Clean up a java doc style comment removing all the \"*\" etc.\n"
                        + "*\n"
                        + "* @param  fieldComment raw Java doc style comment\n"
                        + "* @return clean multi-line content of the comment";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test one line comment on lultiple lines")
    void oneLineOnMultipleLines() {
        String str =
                "/**\n"
                        + "     * The capacity of this sequence will be the difference between the"
                        + " <b>initial</b> position and the length of the delegate\n"
                        + "     */\n";
        String result = Common.cleanJavaDocComment(str);
        String expected =
                "The capacity of this sequence will be the difference between the <b>initial</b>"
                        + " position and the length of the delegate";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns")
    void oneParamsThrowsAndReturns() {
        String str =
                "/**\n"
                    + "     * Reads the signed byte at current {@link #position()}, and then"
                    + " increments the {@link #position()} by 1.\n"
                    + "     *\n"
                    + "     * @return The signed byte at the current {@link #position()}\n"
                    + "     * @throws BufferUnderflowException If there are no bytes remaining in"
                    + " this sequence\n"
                    + "     * @throws DataAccessException If an I/O error occurs\n"
                    + "     */";
        String result = Common.cleanJavaDocComment(str);
        String expected =
                "Reads the signed byte at current {@link #position()}, and then increments the"
                        + " {@link #position()} by 1.\n"
                        + "     *\n"
                        + "@return The signed byte at the current {@link #position()}\n"
                        + "@throws BufferUnderflowException If there are no bytes remaining in this"
                        + " sequence\n"
                        + "@throws DataAccessException If an I/O error occurs";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns")
    void oneParamsThrowsAndReturnsWithMore() {
        String str =
                "    /**\n"
                    + "     * Read bytes starting at current {@link #position()} into the {@code"
                    + " dst} array, up to the size of the {@code dst}\n"
                    + "     * array. If {@code dst} is larger than the remaining bytes in the"
                    + " sequence, only the remaining bytes are read.\n"
                    + "     * The total number of bytes actually read are returned. The bytes will"
                    + " be placed starting at index 0 of the array.\n"
                    + "     * The {@link #position()} will be incremented by the number of bytes"
                    + " read. If no bytes are available in the\n"
                    + "     * sequence, then 0 is returned.\n"
                    + "     *\n"
                    + "     * <p>The {@code dst} array may be partially written to at the time that"
                    + " any of the declared exceptions are thrown.\n"
                    + "     *\n"
                    + "     * <p>Bytes are read from the sequence one at a time. If there are not"
                    + " {@code length} bytes remaining in this\n"
                    + "     * sequence, then a {@link BufferUnderflowException} will be thrown. The"
                    + " {@link #position()} will be\n"
                    + "     * incremented by the number of bytes read prior to the exception.\n"
                    + "     *\n"
                    + "     * @param dst The destination array. Cannot be null.\n"
                    + "     * @throws NullPointerException if {@code dst} is null\n"
                    + "     * @throws DataAccessException If an I/O error occurs\n"
                    + "     * @return The number of bytes read actually read and placed into {@code"
                    + " dst}\n"
                    + "     */";
        String result = Common.cleanJavaDocComment(str);
        String expected =
                "Read bytes starting at current {@link #position()} into the {@code dst} array, up"
                    + " to the size of the {@code dst}\n"
                    + "array. If {@code dst} is larger than the remaining bytes in the sequence,"
                    + " only the remaining bytes are read.\n"
                    + "The total number of bytes actually read are returned. The bytes will be"
                    + " placed starting at index 0 of the array.\n"
                    + "The {@link #position()} will be incremented by the number of bytes read. If"
                    + " no bytes are available in the\n"
                    + "sequence, then 0 is returned.\n"
                    + "     *\n"
                    + "<p>The {@code dst} array may be partially written to at the time that any of"
                    + " the declared exceptions are thrown.\n"
                    + "     *\n"
                    + "<p>Bytes are read from the sequence one at a time. If there are not {@code"
                    + " length} bytes remaining in this\n"
                    + "sequence, then a {@link BufferUnderflowException} will be thrown. The {@link"
                    + " #position()} will be\n"
                    + "incremented by the number of bytes read prior to the exception.\n"
                    + "     *\n"
                    + "@param dst The destination array. Cannot be null.\n"
                    + "@throws NullPointerException if {@code dst} is null\n"
                    + "@throws DataAccessException If an I/O error occurs\n"
                    + "@return The number of bytes read actually read and placed into {@code dst}";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns more")
    void oneParamsThrowsAndReturnsWithMore2() {
        String str =
                "\n"
                    + "    /**\n"
                    + "     * Read bytes starting at the current {@link #position()} into the"
                    + " {@code dst} array, up to {@code maxLength}\n"
                    + "     * number of bytes. If {@code maxLength} is larger than the remaining"
                    + " bytes in the sequence, only the remaining\n"
                    + "     * bytes are read. The total number of bytes actually read are returned."
                    + " The bytes will be placed starting at index\n"
                    + "     * {@code offset} of the array. The {@link #position()} will be"
                    + " incremented by the number of bytes read. If no\n"
                    + "     * bytes are available in the sequence, then 0 is returned.\n"
                    + "     *\n"
                    + "     * <p>The {@code dst} array may be partially written to at the time that"
                    + " any of the declared exceptions are thrown.\n"
                    + "     *\n"
                    + "     * <p>Bytes are read from the sequence one at a time. If there are not"
                    + " {@code length} bytes remaining in this\n"
                    + "     * sequence, then a {@link BufferUnderflowException} will be thrown. The"
                    + " {@link #position()} will be\n"
                    + "     * incremented by the number of bytes read prior to the exception.\n"
                    + "     *\n"
                    + "     * @param dst The array into which bytes are to be written\n"
                    + "     * @param offset The offset within the {@code dst} array of the first"
                    + " byte to be written; must be non-negative and\n"
                    + "     *                no larger than {@code dst.length - maxLength}.\n"
                    + "     * @param maxLength The maximum number of bytes to be written to the"
                    + " given {@code dst} array; must be non-negative\n"
                    + "     *                and no larger than {@code dst.length - offset}\n"
                    + "     * @throws NullPointerException If {@code dst} is null\n"
                    + "     * @throws IndexOutOfBoundsException If {@code offset} is out of bounds"
                    + " of {@code dst} or if\n"
                    + "     *                                  {@code offset + maxLength} is not"
                    + " less than {@code dst.length}\n"
                    + "     * @throws IllegalArgumentException If {@code maxLength} is negative\n"
                    + "     * @throws DataAccessException If an I/O error occurs\n"
                    + "     * @return The number of bytes read actually read and placed into {@code"
                    + " dst}\n"
                    + "     */";
        String result = Common.cleanJavaDocComment(str);
        String expected =
                "Read bytes starting at the current {@link #position()} into the {@code dst} array,"
                    + " up to {@code maxLength}\n"
                    + "number of bytes. If {@code maxLength} is larger than the remaining bytes in"
                    + " the sequence, only the remaining\n"
                    + "bytes are read. The total number of bytes actually read are returned. The"
                    + " bytes will be placed starting at index\n"
                    + "{@code offset} of the array. The {@link #position()} will be incremented by"
                    + " the number of bytes read. If no\n"
                    + "bytes are available in the sequence, then 0 is returned.\n"
                    + "     *\n"
                    + "<p>The {@code dst} array may be partially written to at the time that any of"
                    + " the declared exceptions are thrown.\n"
                    + "     *\n"
                    + "<p>Bytes are read from the sequence one at a time. If there are not {@code"
                    + " length} bytes remaining in this\n"
                    + "sequence, then a {@link BufferUnderflowException} will be thrown. The {@link"
                    + " #position()} will be\n"
                    + "incremented by the number of bytes read prior to the exception.\n"
                    + "     *\n"
                    + "@param dst The array into which bytes are to be written\n"
                    + "@param offset The offset within the {@code dst} array of the first byte to"
                    + " be written; must be non-negative and\n"
                    + "no larger than {@code dst.length - maxLength}.\n"
                    + "@param maxLength The maximum number of bytes to be written to the given"
                    + " {@code dst} array; must be non-negative\n"
                    + "and no larger than {@code dst.length - offset}\n"
                    + "@throws NullPointerException If {@code dst} is null\n"
                    + "@throws IndexOutOfBoundsException If {@code offset} is out of bounds of"
                    + " {@code dst} or if\n"
                    + "{@code offset + maxLength} is not less than {@code dst.length}\n"
                    + "@throws IllegalArgumentException If {@code maxLength} is negative\n"
                    + "@throws DataAccessException If an I/O error occurs\n"
                    + "@return The number of bytes read actually read and placed into {@code dst}";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test params, throws and returns more 2")
    void oneParamsThrowsAndReturnsWithMore3() {
        String str =
                " /**\n"
                    + "     * Reads the next four bytes at the current {@link #position()},"
                    + " composing them into an int value according to\n"
                    + "     * specified byte order, and then increments the {@link #position()} by"
                    + " four.\n"
                    + "     *\n"
                    + "     * @param byteOrder the byte order, aka endian to use. Should never be"
                    + " null. If it is null, BIG_ENDIAN is used.\n"
                    + "     * @return The int value at the current {@link #position()}\n"
                    + "     * @throws BufferUnderflowException If there are fewer than four bytes"
                    + " remaining\n"
                    + "     * @throws DataAccessException if an I/O error occurs\n"
                    + "     */";
        String result = Common.cleanJavaDocComment(str);
        String expected =
                "Reads the next four bytes at the current {@link #position()}, composing them into"
                    + " an int value according to\n"
                    + "specified byte order, and then increments the {@link #position()} by four.\n"
                    + "     *\n"
                    + "@param byteOrder the byte order, aka endian to use. Should never be null. If"
                    + " it is null, BIG_ENDIAN is used.\n"
                    + "@return The int value at the current {@link #position()}\n"
                    + "@throws BufferUnderflowException If there are fewer than four bytes"
                    + " remaining\n"
                    + "@throws DataAccessException if an I/O error occurs";
        assertEquals(expected, result);
    }
}
