package com.hedera.pbj.compiler.impl;

import static com.hedera.pbj.compiler.impl.LookupHelper.normalizeFileName;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LookupHelperTest {

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
        if(System.getProperty("os.name").toLowerCase().contains("windows")) {
            String expected = "state\\common.proto";
            String actual = normalizeFileName(fileName);
            assertEquals(expected, actual);
        } else {
            String expected = "state/common.proto";
            String actual = normalizeFileName(fileName);
            assertEquals(expected, actual);
        }
    }

}