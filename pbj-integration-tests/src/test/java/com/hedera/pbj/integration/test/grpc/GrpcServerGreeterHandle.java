// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import com.hedera.pbj.runtime.grpc.Pipeline;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiConsumer;
import java.util.function.Function;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/**
 * A handle for a GRPC server that implements the Greeter service using a PBJ-generated interface
 * that can be used directly with the PBJ GRPC server. Other server implementations would have to
 * wrap service calls to convert payloads and/or adapt to different APIs.
 *
 * A test must provide implementations for service methods that it will call. The default implementations
 * are either no-ops, or return hard-coded results, or nulls which may kill the low-level server implementation.
 */
abstract class GrpcServerGreeterHandle implements GreeterInterface, AutoCloseable {

    private Function<HelloRequest, HelloReply> sayHello;
    private BiConsumer<HelloRequest, Pipeline<? super HelloReply>> sayHelloStreamReply;
    private Function<Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> sayHelloStreamRequest;
    private Function<Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> sayHelloStreamBidi;

    abstract void start();

    abstract void stop();

    @Override
    public void close() {
        stop();
    }

    @NonNull
    @Override
    public HelloReply sayHello(@NonNull HelloRequest request) {
        if (sayHello != null) {
            return sayHello.apply(request);
        }
        return HelloReply.DEFAULT;
    }

    @Override
    public void sayHelloStreamReply(@NonNull HelloRequest request, @NonNull Pipeline<? super HelloReply> replies) {
        if (sayHelloStreamReply != null) {
            sayHelloStreamReply.accept(request, replies);
        }
        // no-op otherwise
    }

    @NonNull
    @Override
    public Pipeline<? super HelloRequest> sayHelloStreamRequest(@NonNull Pipeline<? super HelloReply> replies) {
        if (sayHelloStreamRequest != null) {
            return sayHelloStreamRequest.apply(replies);
        }
        // this will likely kill the server, but it's not supposed to be invoked unless the test does that,
        // in which case the test must provide an implementation.
        return null;
    }

    @NonNull
    @Override
    public Pipeline<? super HelloRequest> sayHelloStreamBidi(@NonNull Pipeline<? super HelloReply> replies) {
        if (sayHelloStreamBidi != null) {
            return sayHelloStreamBidi.apply(replies);
        }
        // this will likely kill the server, but it's not supposed to be invoked unless the test does that,
        // in which case the test must provide an implementation.
        return null;
    }

    public void setSayHello(Function<HelloRequest, HelloReply> sayHello) {
        this.sayHello = sayHello;
    }

    public void setSayHelloStreamReply(BiConsumer<HelloRequest, Pipeline<? super HelloReply>> sayHelloStreamReply) {
        this.sayHelloStreamReply = sayHelloStreamReply;
    }

    public void setSayHelloStreamRequest(
            Function<Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> sayHelloStreamRequest) {
        this.sayHelloStreamRequest = sayHelloStreamRequest;
    }

    public void setSayHelloStreamBidi(
            Function<Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> sayHelloStreamBidi) {
        this.sayHelloStreamBidi = sayHelloStreamBidi;
    }
}
