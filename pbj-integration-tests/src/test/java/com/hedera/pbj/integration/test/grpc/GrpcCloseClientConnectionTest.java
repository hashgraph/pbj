// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.grpc.client.helidon.PbjGrpcClient;
import com.hedera.pbj.grpc.client.helidon.PbjGrpcClientConfig;
import com.hedera.pbj.grpc.helidon.PbjRouting;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/** Test the Pipeline.closeConnection(). */
public class GrpcCloseClientConnectionTest {

    private static final int port = 18666;

    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    private static final Options OPTIONS =
            new Options(Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC);

    // First, implement the service and start it up
    private static class GreeterServiceImpl implements GreeterInterface {

        @Override
        public HelloReply sayHello(HelloRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamRequest(Pipeline<? super HelloReply> replies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamBidi(Pipeline<? super HelloReply> replies) {
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    if ("kill me".equals(item.name())) {
                        replies.closeConnection();
                        return;
                    }
                    final HelloReply reply = HelloReply.newBuilder()
                            .message("Hello " + item.name())
                            .build();
                    replies.onNext(reply);
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    replies.onComplete();
                }
            };
        }
    }

    private static final GreeterServiceImpl SERVICE = new GreeterServiceImpl();
    private static WebServer SERVER;
    private static GrpcClient GRPC_CLIENT;

    @BeforeAll
    static void setup() {
        SERVER = WebServer.builder()
                .port(port)
                .addRouting(PbjRouting.builder().service(SERVICE))
                .maxPayloadSize(10000)
                .build()
                .start();

        final Tls tls = Tls.builder().enabled(false).build();
        final WebClient webClient =
                WebClient.builder().baseUri("http://localhost:" + port).tls(tls).build();

        final PbjGrpcClientConfig config =
                new PbjGrpcClientConfig(Duration.ofSeconds(10), tls, OPTIONS.authority(), OPTIONS.contentType());

        GRPC_CLIENT = new PbjGrpcClient(webClient, config);
    }

    @AfterAll
    static void teardown() {
        SERVER.stop();
    }

    @Test
    public void testCloseClientConnection() {
        final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, OPTIONS);

        final List<HelloReply> replies = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final Pipeline<? super HelloRequest> requests = client.sayHelloStreamBidi(new Pipeline<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {
                errors.add(throwable);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }

            @Override
            public void onNext(HelloReply item) throws RuntimeException {
                replies.add(item);
            }
        });

        requests.onNext(HelloRequest.newBuilder().name("test name 1").build());
        requests.onNext(HelloRequest.newBuilder().name("test name 2").build());
        requests.onNext(HelloRequest.newBuilder().name("kill me").build());
        requests.onNext(HelloRequest.newBuilder().name("test name 3").build());
        requests.onComplete();

        // NOTE: the test method isn't blocking (because it returns a requests pipeline.)
        // So we have to wait a tad. Both server and client are running on the current host here
        // (in the same JVM, in fact.) So 1 second should be sufficient, unless the computer is really-really slow.
        // If we find this not working, then we'll come up with a longer timeout and will be watching the completed
        // flag in a loop instead.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        // Server and client run asynchronously. Depending on how fast the closing of the connection happens,
        // the client may not even receive the very first two replies. So we cannot check the list for equality.
        // Instead, we do this:
        assertTrue(replies.size() <= 2);
        if (!replies.isEmpty()) {
            assertFalse(replies.stream()
                    .anyMatch(hr -> hr.equals(
                            HelloReply.newBuilder().message("Hello kill me").build())));
            assertFalse(replies.stream()
                    .anyMatch(hr -> hr.equals(
                            HelloReply.newBuilder().message("Hello test name 3").build())));
        }

        // Log all errors (if any) and assert there's none
        errors.forEach(System.err::println);
        // Again, depending on how brutal the connection closing happened, there may in fact be errors.
        // So we don't check them, nor do we check the completed flag for the same reason.

        // Ensure the GRPCClient connection is actually closed:
        assertThrows(
                UncheckedIOException.class,
                () -> client.sayHelloStreamBidi(new Pipeline<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {}

                    @Override
                    public void onError(Throwable throwable) {}

                    @Override
                    public void onComplete() {}

                    @Override
                    public void onNext(HelloReply item) throws RuntimeException {}
                }));

        // Also ensure that the server is still running and can accept a new connection via a new GrpcClient instance:
        {
            final Tls tls = Tls.builder().enabled(false).build();
            final WebClient webClient = WebClient.builder()
                    .baseUri("http://localhost:" + port)
                    .tls(tls)
                    .build();

            final PbjGrpcClientConfig config =
                    new PbjGrpcClientConfig(Duration.ofSeconds(10), tls, OPTIONS.authority(), OPTIONS.contentType());

            final PbjGrpcClient grpcClient = new PbjGrpcClient(webClient, config);

            final GreeterInterface.GreeterClient client2 = new GreeterInterface.GreeterClient(grpcClient, OPTIONS);

            final List<HelloReply> replies2 = new ArrayList<>();
            final List<Throwable> errors2 = new ArrayList<>();
            final AtomicBoolean completed2 = new AtomicBoolean(false);
            final Pipeline<? super HelloRequest> requests2 = client2.sayHelloStreamBidi(new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // no-op
                }

                @Override
                public void onError(Throwable throwable) {
                    errors2.add(throwable);
                }

                @Override
                public void onComplete() {
                    completed2.set(true);
                }

                @Override
                public void onNext(HelloReply item) throws RuntimeException {
                    replies2.add(item);
                }
            });

            requests2.onNext(HelloRequest.newBuilder().name("test name 1").build());
            requests2.onNext(HelloRequest.newBuilder().name("test name 2").build());
            requests2.onNext(HelloRequest.newBuilder().name("test name 3").build());
            requests2.onComplete();

            // NOTE: the test method isn't blocking (because it returns a requests pipeline.)
            // So we have to wait a tad. Both server and client are running on the current host here
            // (in the same JVM, in fact.) So 1 second should be sufficient, unless the computer is really-really slow.
            // If we find this not working, then we'll come up with a longer timeout and will be watching the completed
            // flag in a loop instead.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }

            assertEquals(
                    List.of(
                            HelloReply.newBuilder().message("Hello test name 1").build(),
                            HelloReply.newBuilder().message("Hello test name 2").build(),
                            HelloReply.newBuilder().message("Hello test name 3").build()),
                    replies2);

            // Log all errors (if any) and assert there's none
            errors2.forEach(System.err::println);
            assertTrue(errors2.isEmpty());

            assertTrue(completed2.get());
        }
    }
}
