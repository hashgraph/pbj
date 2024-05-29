package http;

import io.helidon.webclient.api.WebClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

public class HttpTest {
    @Test
    void simpleHttpCall() throws InterruptedException {
        final var pool = Executors.newFixedThreadPool(100);
        final var latch = new CountDownLatch(1000);
        for (int i=0; i<1000; i++) {
            pool.submit(() -> {
                final var client = WebClient.builder().baseUri("http://localhost:8080").build();
                System.out.println(client.get().path("/greet").request().as(String.class));
                latch.countDown();
            });
        }

        latch.await();
    }
}
