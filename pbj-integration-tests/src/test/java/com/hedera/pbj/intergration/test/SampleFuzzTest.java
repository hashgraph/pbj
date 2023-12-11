package com.hedera.pbj.intergration.test;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.pbj.integration.fuzz.SingleFuzzTest;
import com.hedera.pbj.integration.fuzz.SingleFuzzTestResult;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This is a sample fuzz test just to demonstrate the usage of the SingleFuzzTest class.
 * It's unable to assert or verify anything because a single fuzz test run may not
 * produce a desired result.
 * It will be replaced with a more elaborate fuzz testing framework in the future.
 * See javadoc for SingleFuzzTest for more details.
 */
public class SampleFuzzTest {

    @Test
    void testMethod() {
        AccountID accountID = AccountID.newBuilder().accountNum(1).realmNum(2).shardNum(3).build();
        SingleFuzzTestResult singleFuzzTestResult = SingleFuzzTest.fuzzTest(
                accountID,
                AccountID.PROTOBUF,
                new Random()
        );
        System.out.println("A fuzz test for " + accountID + " resulted in " + singleFuzzTestResult);

        // It's a no-op by design currently. See javadoc for details.
        assertNotNull(singleFuzzTestResult);
    }

}
