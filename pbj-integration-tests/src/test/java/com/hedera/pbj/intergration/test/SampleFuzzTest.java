package com.hedera.pbj.intergration.test;

import com.hedera.hapi.node.base.tests.AccountIDTest;
import com.hedera.hapi.node.base.tests.ContractIDTest;
import com.hedera.pbj.integration.fuzz.FuzzTest;
import com.hedera.pbj.integration.fuzz.FuzzTestResult;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a sample fuzz test just to demonstrate the usage of the FuzzTest class.
 * It will be replaced with a more elaborate fuzz testing framework in the future.
 * See javadoc for FuzzTest for more details.
 */
public class SampleFuzzTest {
    // A percentage threshold for the DESERIALIZATION_FAILED outcomes.
    private static final double THRESHOLD = 0.5;

    // This is to be extended to all model classes in the future.
    private static final List<List<?>> MODEL_TEST_OBJECTS = List.of(
            AccountIDTest.ARGUMENTS,
            ContractIDTest.ARGUMENTS
    );

    private static Stream<?> objectTestCases() {
        return MODEL_TEST_OBJECTS.stream()
                .flatMap(List::stream);
    }

    // If this parametrized test proves to be taking too long time to complete,
    // we may try and convert it to a regular test, and instead run it
    // in a parallelStream() on the objectTestCases.
    // However, each individual fuzz test already uses a parallel stream
    // for its repeated runs. So using an extra, outer parallel stream here
    // is unlikely to yield too much of a performance boost.
    @ParameterizedTest
    @MethodSource("objectTestCases")
    void testMethod(Object object) {
        FuzzTestResult<?> fuzzTestResult = FuzzTest.fuzzTest(object, THRESHOLD);
        String resultDescription = fuzzTestResult.format();
        System.out.println(resultDescription);
        assertTrue(fuzzTestResult.passed(), resultDescription);
    }
}
