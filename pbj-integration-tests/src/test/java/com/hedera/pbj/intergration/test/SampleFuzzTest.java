package com.hedera.pbj.intergration.test;

import com.hedera.hapi.node.base.tests.AccountIDTest;
import com.hedera.hapi.node.base.tests.ContractIDTest;
import com.hedera.pbj.integration.fuzz.FuzzTest;
import com.hedera.pbj.integration.fuzz.FuzzTestResult;
import com.hedera.pbj.test.proto.pbj.tests.EverythingTest;
import com.hedera.pbj.test.proto.pbj.tests.HashevalTest;
import com.hedera.pbj.test.proto.pbj.tests.InnerEverythingTest;
import com.hedera.pbj.test.proto.pbj.tests.MessageWithStringTest;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTest2Test;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestSeconds2Test;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestSecondsTest;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * This is a sample fuzz test just to demonstrate the usage of the FuzzTest class.
 * It will be replaced with a more elaborate fuzz testing framework in the future.
 * See javadoc for FuzzTest for more details.
 */
public class SampleFuzzTest {
    public static final String FUZZ_TEST = "FUZZ_TEST";

    // A percentage threshold for the DESERIALIZATION_FAILED outcomes.
    // Note that we still encounter runs that result in less than 60%
    // of the desirable outcome. Interestingly, this occurs with small
    // objects mostly, for example an object with a single field
    // of type string/byte array. When the string is too long, many a time
    // the random data gets written into the string bytes w/o affecting
    // the validity of the object. This may be addressed by increasing
    // the number of bytes that we modify for a single test run.
    // However, this may decrease the number of subtle modifications
    // which we also want to test.
    // For now, we keep the threshold at 60%.
    // This will be refactored to gather the statistics across all the
    // test cases and pass or fail the overall test based on the statistics.
    private static final double THRESHOLD = 0.6;

    private static final List<List<?>> MODEL_TEST_OBJECTS = List.of(
            AccountIDTest.ARGUMENTS,
            ContractIDTest.ARGUMENTS,
            EverythingTest.ARGUMENTS,
            HashevalTest.ARGUMENTS,
            InnerEverythingTest.ARGUMENTS,
            MessageWithStringTest.ARGUMENTS,
            TimestampTest2Test.ARGUMENTS,
            TimestampTestSeconds2Test.ARGUMENTS,
            TimestampTestSecondsTest.ARGUMENTS,
            TimestampTestTest.ARGUMENTS
    );

    private static Stream<?> objectTestCases() {
        return MODEL_TEST_OBJECTS.stream()
                .flatMap(List::stream);
    }

    @ParameterizedTest
    @MethodSource("objectTestCases")
    @Tag(SampleFuzzTest.FUZZ_TEST)
    void testMethod(Object object) throws Exception {
        assumeFalse(
                this.getClass().desiredAssertionStatus(),
                "Fuzz tests run with assertions disabled only. Use the fuzzTest Gradle target."
        );

        FuzzTestResult<?> fuzzTestResult = FuzzTest.fuzzTest(object, THRESHOLD);
        String resultDescription = fuzzTestResult.format();
        System.out.println(resultDescription);
        assertTrue(fuzzTestResult.passed(), resultDescription);
    }
}
