// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

public class GrpcClientTest {

    private static final int port = 8666;

    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    private static final Options OPTIONS =
            new Options(Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC);

    // First, implement the service and start it up
    private static class GreeterServiceImpl implements GreeterInterface {

        @Override
        public HelloReply sayHello(HelloRequest request) {
            final HelloReply reply =
                    HelloReply.newBuilder().message("Hello " + request.name()).build();
            return reply;
        }

        @Override
        public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
            replies.onNext(
                    HelloReply.newBuilder().message("Hello 1 " + request.name()).build());
            replies.onNext(
                    HelloReply.newBuilder().message("Hello 2 " + request.name()).build());
            replies.onNext(
                    HelloReply.newBuilder().message("Hello 3 " + request.name()).build());
            replies.onComplete();
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamRequest(Pipeline<? super HelloReply> replies) {
            final List<HelloRequest> requests = new ArrayList<>();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    requests.add(item);
                    if (requests.size() == 3) {
                        final HelloReply reply = HelloReply.newBuilder()
                                .message("Hello "
                                        + requests.stream()
                                                .map(HelloRequest::name)
                                                .collect(Collectors.joining(", ")) + "!")
                                .build();
                        replies.onNext(reply);
                        replies.onComplete();
                    }
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

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamBidi(Pipeline<? super HelloReply> replies) {
            // Here we receive info from the client. In this case, it is a stream of requests with
            // names. We will respond with a stream of replies.
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
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
    public void testSayHello() {
        final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, OPTIONS);

        final HelloRequest request = HelloRequest.newBuilder().name("test name").build();
        final HelloReply reply = client.sayHello(request);

        assertEquals("Hello test name", reply.message());
    }

    @Test
    public void testSayHelloStreamReply() {
        final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, OPTIONS);

        final HelloRequest request = HelloRequest.newBuilder().name("test name").build();
        final List<HelloReply> replies = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        final AtomicBoolean completed = new AtomicBoolean(false);
        client.sayHelloStreamReply(request, new Pipeline<>() {
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

        assertEquals(
                List.of(
                        HelloReply.newBuilder().message("Hello 1 test name").build(),
                        HelloReply.newBuilder().message("Hello 2 test name").build(),
                        HelloReply.newBuilder().message("Hello 3 test name").build()),
                replies);

        // Log all errors (if any) and assert there's none
        errors.forEach(System.err::println);
        assertTrue(errors.isEmpty());

        assertTrue(completed.get());
    }

    private void testStreamBidi(
            Function<Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> method,
            final List<HelloReply> expectedReplies) {
        final HelloRequest request = HelloRequest.newBuilder().name("test name").build();
        final List<HelloReply> replies = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final Pipeline<? super HelloRequest> requests = method.apply(new Pipeline<>() {
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

        assertEquals(expectedReplies, replies);

        // Log all errors (if any) and assert there's none
        errors.forEach(System.err::println);
        assertTrue(errors.isEmpty());

        assertTrue(completed.get());
    }

    @Test
    public void testSayHelloStreamBidi() {
        final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, OPTIONS);

        testStreamBidi(
                client::sayHelloStreamBidi,
                List.of(
                        HelloReply.newBuilder().message("Hello test name 1").build(),
                        HelloReply.newBuilder().message("Hello test name 2").build(),
                        HelloReply.newBuilder().message("Hello test name 3").build()));
    }

    @Test
    public void testSayHelloStreamRequest() {
        final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(GRPC_CLIENT, OPTIONS);

        testStreamBidi(
                client::sayHelloStreamRequest,
                List.of(HelloReply.newBuilder()
                        .message("Hello test name 1, test name 2, test name 3!")
                        .build()));
    }
}
