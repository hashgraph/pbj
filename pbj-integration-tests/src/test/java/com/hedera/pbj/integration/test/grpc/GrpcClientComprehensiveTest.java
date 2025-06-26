// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.grpc.client.helidon.PbjGrpcClient;
import com.hedera.pbj.grpc.client.helidon.PbjGrpcClientConfig;
import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.WebClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/**
 * A comprehensive test for the PBJ GRPC client. The testing is performed against multiple GRPC server implementations -
 * currently using the PBJ GRPC server on top of the Helidon HTTP2 server, as well as Google Protobuf GRPC server
 * on top of Netty. More server implementations can be added via the `testClassArguments()` method below.
 * Each test allocates a unique port number and then creates a server and client instances, which allows all the tests
 * to run in parallel.
 */
@ParameterizedClass
@MethodSource("testClassArguments")
public class GrpcClientComprehensiveTest {
    private static final PortsAllocator PORTS = new PortsAllocator(8666, 10666);

    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    private static final Options PROTO_OPTIONS =
            new Options(Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC);

    private final Function<Integer, GrpcServerGreeterHandle> serverFactory;

    static Stream<Arguments> testClassArguments() {
        return Stream.of(
                Arguments.of((Function<Integer, GrpcServerGreeterHandle>) PbjGrpcServerGreeterHandle::new),
                Arguments.of((Function<Integer, GrpcServerGreeterHandle>) GoogleProtobufGrpcServerGreeterHandle::new));
    }

    public GrpcClientComprehensiveTest(Function<Integer, GrpcServerGreeterHandle> serverFactory) {
        this.serverFactory = serverFactory;
    }

    private GrpcClient createGrpcClient(final int port, final ServiceInterface.RequestOptions requestOptions) {
        final Tls tls = Tls.builder().enabled(false).build();
        final WebClient webClient =
                WebClient.builder().baseUri("http://localhost:" + port).tls(tls).build();

        final PbjGrpcClientConfig config = new PbjGrpcClientConfig(
                Duration.ofSeconds(10), tls, requestOptions.authority(), requestOptions.contentType());

        return new PbjGrpcClient(webClient, config);
    }

    @Test
    void testUnaryMethodHappyCase() {
        try (final PortsAllocator.Port port = PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            server.setSayHello(request ->
                    HelloReply.newBuilder().message("Hello " + request.name()).build());

            final GrpcClient grpcClient = createGrpcClient(port.port(), PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(grpcClient, PROTO_OPTIONS);

            final HelloRequest request =
                    HelloRequest.newBuilder().name("test name").build();
            final HelloReply reply = client.sayHello(request);

            assertEquals("Hello test name", reply.message());
        }
    }

    @Test
    void testServerStreamingMethodHappyCase() {
        try (final PortsAllocator.Port port = PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            server.setSayHelloStreamReply(((request, replies) -> {
                replies.onNext(HelloReply.newBuilder()
                        .message("Hello 1 " + request.name())
                        .build());
                replies.onNext(HelloReply.newBuilder()
                        .message("Hello 2 " + request.name())
                        .build());
                replies.onNext(HelloReply.newBuilder()
                        .message("Hello 3 " + request.name())
                        .build());
                replies.onComplete();
            }));

            final GrpcClient grpcClient = createGrpcClient(port.port(), PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(grpcClient, PROTO_OPTIONS);

            final HelloRequest request =
                    HelloRequest.newBuilder().name("test name").build();
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
    }

    void testStreamingMethodsHappyCase(
            final Consumer<GrpcServerGreeterHandle> serverConfigurer,
            final BiFunction<GreeterInterface, Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> method,
            final List<HelloReply> expectedReplies) {
        try (final PortsAllocator.Port port = PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            serverConfigurer.accept(server);

            final GrpcClient grpcClient = createGrpcClient(port.port(), PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client = new GreeterInterface.GreeterClient(grpcClient, PROTO_OPTIONS);

            final HelloRequest request =
                    HelloRequest.newBuilder().name("test name").build();
            final List<HelloReply> replies = new ArrayList<>();
            final List<Throwable> errors = new ArrayList<>();
            final AtomicBoolean completed = new AtomicBoolean(false);
            final Pipeline<? super HelloRequest> requests = method.apply(client, new Pipeline<>() {
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
    }

    @Test
    void testClientStreamingMethodsHappyCase() {
        testStreamingMethodsHappyCase(
                server -> server.setSayHelloStreamRequest(replies -> {
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
                                                        .collect(Collectors.joining(", "))
                                                + "!")
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
                }),
                (client, replies) -> client.sayHelloStreamRequest(replies),
                List.of(HelloReply.newBuilder()
                        .message("Hello test name 1, test name 2, test name 3!")
                        .build()));
    }

    @Test
    void testBidiStreamingMethodsHappyCase() {
        testStreamingMethodsHappyCase(
                server -> server.setSayHelloStreamBidi(replies -> {
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
                }),
                (client, replies) -> client.sayHelloStreamBidi(replies),
                List.of(
                        HelloReply.newBuilder().message("Hello test name 1").build(),
                        HelloReply.newBuilder().message("Hello test name 2").build(),
                        HelloReply.newBuilder().message("Hello test name 3").build()));
    }
}
