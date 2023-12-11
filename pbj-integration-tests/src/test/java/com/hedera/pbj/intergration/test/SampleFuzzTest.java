package com.hedera.pbj.intergration.test;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.pbj.integration.fuzz.FuzzTest;
import com.hedera.pbj.integration.fuzz.FuzzTestResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a sample fuzz test just to demonstrate the usage of the FuzzTest class.
 * It will be replaced with a more elaborate fuzz testing framework in the future.
 * See javadoc for FuzzTest for more details.
 */
public class SampleFuzzTest {

    @Test
    void testMethod() {
        AccountID accountID = AccountID.newBuilder().accountNum(1).realmNum(2).shardNum(3).build();

        // A percentage threshold for the DESERIALIZATION_FAILED outcomes.
        final double THRESHOLD = 0.5;

        FuzzTestResult<AccountID> fuzzTestResult = FuzzTest.fuzzTest(accountID, AccountID.PROTOBUF, THRESHOLD);

        System.out.println(fuzzTestResult.format());

        assertTrue(fuzzTestResult.passed());
    }

}
