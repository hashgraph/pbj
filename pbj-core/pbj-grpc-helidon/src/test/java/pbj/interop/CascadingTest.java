package pbj.interop;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.testing.integration.SimpleRequest;
import io.grpc.testing.integration.SimpleResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Integration test for various forms of cancellation and deadline propagation.
 *
 * <p>Taken from <a href="https://github.com/grpc/grpc-java/blob/master/interop-testing/src/test/java/io/grpc/testing/integration/CascadingTest.java">grpc-java CascadingTest</a>
 */
public class CascadingTest {
    /**
     * Test cancellation propagates from the first node in the call chain all the way
     * to the last.
     */
    @Test
    void testCascadingCancellationViaOuterContextCancellation() throws Exception {
    }

    /**
     * Test that cancellation via call cancellation propagates down the call.
     */
    @Test
    void testCascadingCancellationViaRpcCancel() throws Exception {

    }

    /**
     * Test that when RPC cancellation propagates up a call chain, the cancellation of the parent
     * RPC triggers cancellation of all of its children.
     */
    @Test
    void testCascadingCancellationViaLeafFailure() throws Exception {

    }

    @Test
    void testDeadlinePropagation() throws Exception {

    }
}
