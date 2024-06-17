package pbj.interop;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.pbj.grpc.helidon.GreeterService;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import greeter.GreeterGrpc;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.helidon.webserver.WebServer;
import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyTest {
    private static final int NUM_CONCURRENT = 10;
    private static final int NUM_REQUESTS = 100_000_000;
    private final ConcurrentLinkedQueue<AssertionError> failures = new ConcurrentLinkedQueue<>();
    private final CountDownLatch latch = new CountDownLatch(NUM_REQUESTS);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger nextClientId = new AtomicInteger(0);
    private final BlockingDeque<ManagedChannel> channels = new LinkedBlockingDeque<>();

    void setup() {
        // Create a pool of channels. I want to have many, many, unique concurrent calls, but there is a practical
        // limit to the number of concurrent channels. If the deque is empty, there are no available channels.
        for (int i = 0; i < NUM_CONCURRENT; i++) {
            final var channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                    .usePlaintext()
                    .build();

            channels.offer(channel);
        }
    }

    void teardown() {
        channels.forEach(ManagedChannel::shutdownNow);
        channels.forEach(c -> {
            try {
                c.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    void manyConcurrentUnaryCalls() throws InterruptedException {
        // For each virtual client, execute the query and get the reply. Put the reply here in this map. The key
        // is the unique ID of the client (integer), and the value is the reply.
        for (int i = 0; i < NUM_CONCURRENT; i++) {
            executor.submit(new TestClient(nextClientId.getAndIncrement()));
        }

        // It should take less than 20 seconds
        assertThat(latch.await(10, TimeUnit.MINUTES)).isTrue();

        // If there were any failures, throw them.
        assertThat(failures).isEmpty();
    }

    private final class TestClient implements Runnable {
        private final int clientId;

        TestClient(int clientId) {
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try {
                final var channel = channels.takeFirst();
                try {
                    final var stub = GreeterGrpc.newFutureStub(channel);
                    final var request = HelloRequest.newBuilder().setName("" + clientId).build();
                    final var future = stub.sayHello(request);
                    final var reply = future.get(10, TimeUnit.SECONDS);
                    if (!reply.getMessage().equals("Hello " + clientId)) {
                        failures.offer(new AssertionError("Failed " + clientId));
                        System.out.println("FAILURE!");
                    }

                    final var id = nextClientId.getAndIncrement();
                    if (id < NUM_REQUESTS) {
                        if (id % 1000 == 0) {
                            System.out.println("Starting client " + id);
                        }
                        executor.submit(new TestClient(id));
                    }
                } catch (Exception e) {
                    // If some random exception occurs, just reschedule this task for later execution.
                    executor.submit(this);
                } finally {
                    channels.offer(channel);
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            } finally {
                latch.countDown();
            }
        }
    }

    public static void main(String[] args) {
        final var server = WebServer.builder()
                .port(8080)
                .addRouting(PbjRouting.builder().service(new GreeterService() {
                    @Override
                    public HelloReply sayHello(HelloRequest request) {
                        return HelloReply.newBuilder()
                                .setMessage("Hello " + request.getName())
                                .build();
                    }

                    @Override
                    public Flow.Subscriber<? super HelloRequest> sayHelloStreamRequest(Flow.Subscriber<? super HelloReply> replies) {
                        return null;
                    }

                    @Override
                    public void sayHelloStreamReply(HelloRequest request, Flow.Subscriber<? super HelloReply> replies) {

                    }

                    @Override
                    public Flow.Subscriber<? super HelloRequest> sayHelloStreamBidi(Flow.Subscriber<? super HelloReply> replies) {
                        return null;
                    }
                }))
                .build()
                .start();

        final var test = new ConcurrencyTest();
        test.setup();
        try {
            test.manyConcurrentUnaryCalls();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            test.teardown();
        }

        server.stop();
    }
}
