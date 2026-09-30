// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartialApplierTest {
    private static Bytes bytes(String hex) {
        return Bytes.wrap(HexFormat.of().parseHex(hex));
    }

    @Test
    void replacesWholeRepeatedRunsPreservesEmptyPresenceAndDropsClears() {
        final var old = bytes("080112016112001801220178");
        final var patch = bytes("12001201622a00");
        assertEquals(bytes("080112001201622a00"), PartialApplier.splice(old, patch, new int[] {3, 4}));
        assertEquals(old, PartialApplier.splice(old, Bytes.EMPTY, new int[0]));
        assertEquals(Bytes.EMPTY, PartialApplier.splice(old, Bytes.EMPTY, new int[] {1, 2, 3, 4}));
        assertEquals(patch, PartialApplier.splice(Bytes.EMPTY, patch, new int[] {1}));
    }

    @Test
    void preservesFixedWidthsOpaqueNestedPayloadsAndNegativeVarints() {
        final var all = bytes("08ffffffffffffffffff011100000000000000001a02ffff2500000000");
        assertEquals(all, PartialApplier.splice(Bytes.EMPTY, all, new int[0]));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "00",
                "04",
                "08",
                "088000",
                "088080808080808080808002",
                "0b",
                "0c",
                "0e",
                "0f",
                "1100",
                "1a05ff",
                "1affffffffffffffffff01",
                "2500",
                "10010801",
                "808080801001",
                "808080808000"
            })
    void rejectsMalformedFraming(String hex) {
        assertThrows(IllegalArgumentException.class, () -> PartialApplier.splice(bytes(hex), Bytes.EMPTY, new int[0]));
        assertThrows(IllegalArgumentException.class, () -> PartialApplier.splice(Bytes.EMPTY, bytes(hex), new int[0]));
    }

    @Test
    void rejectsAmbiguousClearLists() {
        for (int[] clear : new int[][] {{0}, {-1}, {2, 1}, {1, 1}, {0x20000000}}) {
            assertThrows(IllegalArgumentException.class, () -> PartialApplier.splice(Bytes.EMPTY, Bytes.EMPTY, clear));
        }
        assertThrows(
                IllegalArgumentException.class, () -> PartialApplier.splice(Bytes.EMPTY, bytes("0801"), new int[] {1}));
    }
}
