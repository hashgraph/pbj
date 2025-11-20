// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.Test;
import pbj.integ.test.enumeration.reserved.pbj.integration.tests.MessageWithUnrecognizedEnum1;
import pbj.integ.test.enumeration.reserved.pbj.integration.tests.MessageWithUnrecognizedEnum2;
import pbj.integ.test.enumeration.reserved.pbj.integration.tests.PbjEnumUnrecognized1;
import pbj.integ.test.enumeration.reserved.pbj.integration.tests.PbjEnumUnrecognized2;

public class UnrecognizedEnumTest {
    @Test
    public void unrecognizedEnumTest() throws Exception {
        final MessageWithUnrecognizedEnum2 m2 = MessageWithUnrecognizedEnum2.newBuilder()
                .enumValue(PbjEnumUnrecognized2.D2)
                .build();
        final Bytes bytes2 = MessageWithUnrecognizedEnum2.PROTOBUF.toBytes(m2);

        final MessageWithUnrecognizedEnum1 m1 = MessageWithUnrecognizedEnum1.PROTOBUF.parse(bytes2);

        assertEquals(PbjEnumUnrecognized1.UNRECOGNIZED, m1.enumValue());
        assertEquals(3, m1.enumValueProtoOrdinal());

        // Now try serializing it
        final Bytes bytes1 = MessageWithUnrecognizedEnum1.PROTOBUF.toBytes(m1);
        // and the deserializing back into MessageWithUnrecognizedEnum2
        final MessageWithUnrecognizedEnum2 m22 = MessageWithUnrecognizedEnum2.PROTOBUF.parse(bytes1);

        assertEquals(PbjEnumUnrecognized2.D2, m22.enumValue());
    }

    @Test
    public void unrecognizedEnumListTest() throws Exception {
        final MessageWithUnrecognizedEnum2 m2 = MessageWithUnrecognizedEnum2.newBuilder()
                .enumList(PbjEnumUnrecognized2.A2, PbjEnumUnrecognized2.D2, PbjEnumUnrecognized2.B2)
                .build();
        final Bytes bytes2 = MessageWithUnrecognizedEnum2.PROTOBUF.toBytes(m2);

        final MessageWithUnrecognizedEnum1 m1 = MessageWithUnrecognizedEnum1.PROTOBUF.parse(bytes2);

        assertEquals(
                List.of(PbjEnumUnrecognized1.A1, PbjEnumUnrecognized1.UNRECOGNIZED, PbjEnumUnrecognized1.B1),
                m1.enumList());
        assertEquals(List.of(0, 3, 1), m1.enumListProtoOrdinals());

        // Now try serializing it
        final Bytes bytes1 = MessageWithUnrecognizedEnum1.PROTOBUF.toBytes(m1);
        // and the deserializing back into MessageWithUnrecognizedEnum2
        final MessageWithUnrecognizedEnum2 m22 = MessageWithUnrecognizedEnum2.PROTOBUF.parse(bytes1);

        assertEquals(
                List.of(PbjEnumUnrecognized2.A2, PbjEnumUnrecognized2.D2, PbjEnumUnrecognized2.B2), m22.enumList());
    }
}
