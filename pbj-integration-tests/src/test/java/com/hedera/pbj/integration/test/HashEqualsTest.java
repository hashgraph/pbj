// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.test.proto.pbj.MessageWithBoxedString;
import com.hedera.pbj.test.proto.pbj.MessageWithRepeatedString;
import com.hedera.pbj.test.proto.pbj.MessageWithString;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.pbj.test.proto.pbj.TimestampTest2;
import java.util.List;
import org.junit.jupiter.api.Test;

class HashEqualsTest {
    @Test
    void differentObjectsWithDefaulEquals() {
        TimestampTest tst = new TimestampTest(1, 2);
        TimestampTest2 tst2 = new TimestampTest2(1, 2, 0);

        assertFalse(tst.equals(tst2));
    }

    @Test
    void sameObjectsWithNoDefaulEquals() {
        TimestampTest tst = new TimestampTest(3, 4);
        TimestampTest tst1 = new TimestampTest(3, 4);

        assertEquals(tst, tst1);
    }

    @Test
    void sameObjectsWithDefaulNoEquals() {
        TimestampTest tst = new TimestampTest(3, 4);
        TimestampTest tst1 = new TimestampTest(3, 5);

        assertNotEquals(tst, tst1);
    }

    @Test
    void sameObjectsWithDefaulEquals() {
        TimestampTest tst = new TimestampTest(0, 0);
        TimestampTest tst1 = new TimestampTest(0, 0);

        assertEquals(tst, tst1);
    }

    @Test
    void differentObjectsWithDefaulHashCode() {
        TimestampTest tst = new TimestampTest(0, 0);
        TimestampTest2 tst2 = new TimestampTest2(0, 0, 0);

        assertEquals(tst.hashCode(), tst2.hashCode());
    }

    @Test
    void differentObjectsWithNoDefaulHashCode() {
        TimestampTest tst = new TimestampTest(1, 0);
        TimestampTest2 tst2 = new TimestampTest2(1, 0, 0);

        assertEquals(tst.hashCode(), tst2.hashCode());
    }

    @Test
    void differentObjectsWithNoDefaulHashCode1() {
        TimestampTest tst = new TimestampTest(0, 0);
        TimestampTest2 tst2 = new TimestampTest2(0, 0, 3);

        assertNotEquals(tst.hashCode(), tst2.hashCode());
    }

    @Test
    void differentObjectsWithNoDefaulHashCode2() {
        TimestampTest2 tst = new TimestampTest2(0, 0, 0);
        TimestampTest2 tst2 = new TimestampTest2(0, 0, 0);

        assertEquals(tst.hashCode(), tst2.hashCode());
    }

    @Test
    void differentObjectsWithNoDefaulHashCode3() {
        TimestampTest2 tst = new TimestampTest2(1, 2, 3);
        TimestampTest2 tst2 = new TimestampTest2(1, 2, 3);

        assertEquals(tst.hashCode(), tst2.hashCode());
    }

    @Test
    void differentObjectsWithNoDefaulHashCode4() {
        TimestampTest2 tst = new TimestampTest2(1, 4, 3);
        TimestampTest2 tst2 = new TimestampTest2(1, 2, 3);

        assertNotEquals(tst.hashCode(), tst2.hashCode());
    }

    @Test
    void testStrings() {
        // new String() to ensure we actually create a brand-new string instance
        final MessageWithString msg1 = new MessageWithString(new String("test"));
        // Same characters, but a brand-new string instance again
        final MessageWithString msg2 = new MessageWithString(new String("test"));

        assertEquals(msg1.hashCode(), msg2.hashCode());
        assertTrue(msg1.equals(msg2));
    }

    @Test
    void testBoxedStrings() {
        // new String() to ensure we actually create a brand-new string instance
        final MessageWithBoxedString msg1 = new MessageWithBoxedString(new String("test"));
        // Same characters, but a brand-new string instance again
        final MessageWithBoxedString msg2 = new MessageWithBoxedString(new String("test"));

        assertEquals(msg1.hashCode(), msg2.hashCode());
        assertTrue(msg1.equals(msg2));
    }

    @Test
    void testRepeatedStrings() {
        // new String() to ensure we actually create a brand-new string instance
        final MessageWithRepeatedString msg1 =
                new MessageWithRepeatedString(List.of(new String("test1"), new String("test2")));
        // Same characters, but a brand-new string instance again
        final MessageWithRepeatedString msg2 =
                new MessageWithRepeatedString(List.of(new String("test1"), new String("test2")));

        assertEquals(msg1.hashCode(), msg2.hashCode());
        assertTrue(msg1.equals(msg2));
    }
}
