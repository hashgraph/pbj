// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import com.hedera.pbj.runtime.grpc.Pipeline;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.Flow;
import pbj.integration.tests.GreeterGrpc;
import pbj.integration.tests.HelloReply;
import pbj.integration.tests.HelloRequest;

/** A Greeter handle for the Google Protobuf GRPC server implementation. */
class GoogleProtobufGrpcServerGreeterHandle extends GrpcServerGreeterHandle {
    /** Greeter service implementation for Google GRPC server. */
    private class GreeterGrpcImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            final pbj.integration.tests.pbj.integration.tests.HelloReply reply =
                    GoogleProtobufGrpcServerGreeterHandle.this.sayHello(adaptRequest(request));
            responseObserver.onNext(adaptReply(reply));
            responseObserver.onCompleted();
        }

        @Override
        public void sayHelloStreamReply(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            final Pipeline<pbj.integration.tests.pbj.integration.tests.HelloReply> replyPipeline = new Pipeline<>() {
                @Override
                public void onNext(pbj.integration.tests.pbj.integration.tests.HelloReply item)
                        throws RuntimeException {
                    responseObserver.onNext(adaptReply(item));
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // no-op
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onComplete() {
                    responseObserver.onCompleted();
                }
            };
            GoogleProtobufGrpcServerGreeterHandle.this.sayHelloStreamReply(adaptRequest(request), replyPipeline);
        }

        @Override
        public StreamObserver<HelloRequest> sayHelloStreamRequest(StreamObserver<HelloReply> responseObserver) {
            final Pipeline<pbj.integration.tests.pbj.integration.tests.HelloReply> replyPipeline = new Pipeline<>() {
                @Override
                public void onNext(pbj.integration.tests.pbj.integration.tests.HelloReply item)
                        throws RuntimeException {
                    responseObserver.onNext(adaptReply(item));
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // no-op
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onComplete() {
                    responseObserver.onCompleted();
                }
            };
            final Pipeline<? super pbj.integration.tests.pbj.integration.tests.HelloRequest> requestPipeline =
                    GoogleProtobufGrpcServerGreeterHandle.this.sayHelloStreamRequest(replyPipeline);
            return new StreamObserver<HelloRequest>() {
                public void onNext(HelloRequest value) {
                    requestPipeline.onNext(adaptRequest(value));
                }

                public void onError(Throwable t) {
                    requestPipeline.onError(t);
                }

                public void onCompleted() {
                    requestPipeline.onComplete();
                }
            };
        }

        @Override
        public StreamObserver<HelloRequest> sayHelloStreamBidi(StreamObserver<HelloReply> responseObserver) {
            final Pipeline<pbj.integration.tests.pbj.integration.tests.HelloReply> replyPipeline = new Pipeline<>() {
                @Override
                public void onNext(pbj.integration.tests.pbj.integration.tests.HelloReply item)
                        throws RuntimeException {
                    responseObserver.onNext(adaptReply(item));
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    // no-op
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onComplete() {
                    responseObserver.onCompleted();
                }
            };
            final Pipeline<? super pbj.integration.tests.pbj.integration.tests.HelloRequest> requestPipeline =
                    GoogleProtobufGrpcServerGreeterHandle.this.sayHelloStreamBidi(replyPipeline);
            return new StreamObserver<HelloRequest>() {
                public void onNext(HelloRequest value) {
                    requestPipeline.onNext(adaptRequest(value));
                }

                public void onError(Throwable t) {
                    requestPipeline.onError(t);
                }

                public void onCompleted() {
                    requestPipeline.onComplete();
                }
            };
        }
    }

    private final GreeterGrpcImpl greeterGrpc = new GreeterGrpcImpl();
    private final int port;

    private Server server;

    public GoogleProtobufGrpcServerGreeterHandle(final int port) {
        this.port = port;
    }

    @Override
    public synchronized void start() {
        if (server != null) {
            throw new IllegalStateException("Server already started");
        }
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(greeterGrpc)
                .build();
        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }

    private static pbj.integration.tests.pbj.integration.tests.HelloRequest adaptRequest(HelloRequest request) {
        return pbj.integration.tests.pbj.integration.tests.HelloRequest.newBuilder()
                .name(request.getName())
                .build();
    }

    private static HelloReply adaptReply(pbj.integration.tests.pbj.integration.tests.HelloReply reply) {
        return HelloReply.newBuilder().setMessage(reply.message()).build();
    }
}
