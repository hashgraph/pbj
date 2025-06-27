// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.grpc.GrpcClient;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.Pipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.AssertionFailedError;
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

    private final Function<Integer, GrpcServerGreeterHandle> serverFactory;

    static Stream<Arguments> testClassArguments() {
        return Stream.of(
                Arguments.of((Function<Integer, GrpcServerGreeterHandle>) PbjGrpcServerGreeterHandle::new),
                Arguments.of((Function<Integer, GrpcServerGreeterHandle>) GoogleProtobufGrpcServerGreeterHandle::new));
    }

    public GrpcClientComprehensiveTest(Function<Integer, GrpcServerGreeterHandle> serverFactory) {
        this.serverFactory = serverFactory;
    }

    private static <T extends Throwable> T assertThrowsCause(Class<T> expectedType, Executable executable) {
        try {
            executable.execute();
        } catch (final Throwable t) {
            for (Throwable cause = t; cause != null; cause = cause.getCause()) {
                if (cause.getClass().equals(expectedType)) {
                    return (T) cause;
                }
            }
            throw new AssertionFailedError(
                    "Expected " + expectedType.getName() + " but got "
                            + t.getClass().getName(),
                    t);
        }
        throw new AssertionFailedError("Expected a cause " + expectedType.getName() + ", but no exception was thrown");
    }

    @Test
    void testUnaryMethodHappyCase() {
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            server.setSayHello(request ->
                    HelloReply.newBuilder().message("Hello " + request.name()).build());

            final GrpcClient grpcClient = GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client =
                    new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

            final HelloRequest request =
                    HelloRequest.newBuilder().name("test name").build();
            final HelloReply reply = client.sayHello(request);

            assertEquals("Hello test name", reply.message());
        }
    }

    @Test
    void testServerStreamingMethodHappyCase() {
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
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

            final GrpcClient grpcClient = GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client =
                    new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

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
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            serverConfigurer.accept(server);

            final GrpcClient grpcClient = GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client =
                    new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

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

            GrpcTestUtils.sleep(grpcClient);

            // Log all errors (if any)
            errors.forEach(Throwable::printStackTrace);

            assertEquals(expectedReplies, replies);
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
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            replies.onError(throwable);
                        }

                        @Override
                        public void onComplete() {
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

    @Test
    void testUnaryMethodThrowsException() {
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            server.setSayHello(request -> {
                throw new RuntimeException("generic failure");
            });

            final GrpcClient grpcClient = GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client =
                    new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

            final HelloRequest request =
                    HelloRequest.newBuilder().name("test name").build();
            assertThrowsCause(GrpcException.class, () -> client.sayHello(request));
            // Note: different GRPC servers may report different grpc-statuses.
            // E.g. Google reports UNKNOWN, while PBJ reports INTERNAL (because our service implementation above
            // doesn't throw a PBJ-specific GrpcException.) So we cannot reliably check the status code.
            // Note that in the Google implementation, this happens regardless of whether we just throw,
            // or report it via their StreamObserver as an error.
            // Also, it never retains the original exception message or its stack trace (again, unless the service
            // implementation reports a special, server-specific exception, like the GrpcException for PBJ.)
        }
    }

    @Test
    void testServerStreamingMethodReportsError() {
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            server.setSayHelloStreamReply(((request, replies) -> {
                replies.onNext(HelloReply.newBuilder()
                        .message("Hello 1 " + request.name())
                        .build());
                replies.onError(new RuntimeException("generic failure for Hello 2"));
                // Note that PBJ GRPC server allows one to send more messages after errors, or call onComplete.
                // Google GRPC server errors out in that case. So we don't do that in this method implementation.
            }));

            final GrpcClient grpcClient = GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client =
                    new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

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
                    List.of(HelloReply.newBuilder().message("Hello 1 test name").build()), replies);

            assertEquals(1, errors.size(), "Expected 1 error, but got: " + errors);
            assertTrue(errors.get(0) instanceof GrpcException);

            assertFalse(completed.get());
        }
    }

    /**
     * Client-streaming method can throw or report errors in onNext and onComplete,
     * producing the same result on the client side. This method helps test all the scenarios.
     * It can also simply die in onNext/onComplete, producing slightly different results.
     */
    private void testClientStreamingMethodErrorsOut(
            final BiFunction<GrpcServerGreeterHandle, Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>>
                    sayHelloStreamRequest) {
        try (final PortsAllocator.Port port = GrpcTestUtils.PORTS.acquire();
                final GrpcServerGreeterHandle server = serverFactory.apply(port.port())) {
            server.start();
            server.setSayHelloStreamRequest(replies -> sayHelloStreamRequest.apply(server, replies));

            final GrpcClient grpcClient = GrpcTestUtils.createGrpcClient(port.port(), GrpcTestUtils.PROTO_OPTIONS);
            final GreeterInterface.GreeterClient client =
                    new GreeterInterface.GreeterClient(grpcClient, GrpcTestUtils.PROTO_OPTIONS);

            final List<HelloReply> replies = new ArrayList<>();
            final List<Throwable> errors = new ArrayList<>();
            final AtomicBoolean completed = new AtomicBoolean(false);
            final Pipeline<? super HelloRequest> requests = client.sayHelloStreamRequest(new Pipeline<>() {
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

            GrpcTestUtils.sleep(grpcClient);

            assertEquals(List.of(), replies);

            assertEquals(1, errors.size(), "Expected 1 error, but got: " + errors);
            assertFalse(completed.get());
        }
    }

    @Test
    void testClientStreamingMethodThrowsExceptionInTheMiddle() {
        testClientStreamingMethodErrorsOut((server, replies) -> {
            final List<HelloRequest> requests = new ArrayList<>();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    requests.add(item);
                    if (requests.size() == 2) {
                        throw new RuntimeException("generic failure");
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    // This should never run in this test-case
                    final HelloReply reply = HelloReply.newBuilder()
                            .message("Hello "
                                    + requests.stream().map(HelloRequest::name).collect(Collectors.joining(", "))
                                    + "!")
                            .build();
                    replies.onNext(reply);
                    replies.onComplete();
                }
            };
        });
    }

    // This is similar to testClientStreamingMethodThrowsExceptionInTheMiddle above, but instead of throwing
    // an exception, the server reports it through replies.onError().
    // From the client perspective, the result should be the same:
    @Test
    void testClientStreamingMethodReportsErrorInTheMiddle() {
        testClientStreamingMethodErrorsOut((server, replies) -> {
            final List<HelloRequest> requests = new ArrayList<>();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    requests.add(item);
                    if (requests.size() == 2) {
                        replies.onError(new RuntimeException("generic failure"));
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    // This should never run in this test-case
                    final HelloReply reply = HelloReply.newBuilder()
                            .message("Hello "
                                    + requests.stream().map(HelloRequest::name).collect(Collectors.joining(", "))
                                    + "!")
                            .build();
                    replies.onNext(reply);
                    replies.onComplete();
                }
            };
        });
    }

    @Test
    void testClientStreamingMethodThrowsExceptionInOnComplete() {
        testClientStreamingMethodErrorsOut((server, replies) -> {
            final List<HelloRequest> requests = new ArrayList<>();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    requests.add(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    throw new RuntimeException("generic failure");
                }
            };
        });
    }

    @Test
    void testClientStreamingMethodReportsErrorInOnComplete() {
        testClientStreamingMethodErrorsOut((server, replies) -> {
            final List<HelloRequest> requests = new ArrayList<>();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    requests.add(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    replies.onError(new RuntimeException("generic failure"));
                }
            };
        });
    }

    @Test
    void testClientStreamingMethodServerDiesInTheMiddle() {
        testClientStreamingMethodErrorsOut((server, replies) -> {
            final List<HelloRequest> requests = new ArrayList<>();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    requests.add(item);
                    if (requests.size() == 2) {
                        // The server just dies completely in the middle of receiving the stream from client:
                        server.stopNow();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    // This should never run in this test-case
                    final HelloReply reply = HelloReply.newBuilder()
                            .message("Hello "
                                    + requests.stream().map(HelloRequest::name).collect(Collectors.joining(", "))
                                    + "!")
                            .build();
                    replies.onNext(reply);
                    replies.onComplete();
                }
            };
        });
    }
}
