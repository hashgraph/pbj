// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;

/**
 * Defines a common interface for all implementations of a gRPC {@code service}. PBJ will generate a sub-interface
 * for each {@code service} in the protobuf schema definition files, with default implementations of each of the
 * given methods in this interface.
 *
 * <p>For example, suppose I have the following protobuf file:
 * <pre>
 * {@code
 * package example;
 *
 * service HelloService {
 *   rpc SayHello (HelloRequest) returns (HelloResponse);
 * }
 *
 * message HelloRequest {
 *   string greeting = 1;
 * }
 *
 * message HelloResponse {
 *   string reply = 1;
 * }
 * }
 * </pre>
 *
 * <p>From this file, PBJ will generate a {@code HelloService} interface that extends {@code ServiceInterface}:
 * <pre>
 * {@code
 * public interface HelloService extends ServiceInterface {
 *    // ...
 *
 *    @NonNull
 *    HelloResponse sayHello(final @NonNull HelloRequest request);
 *
 *    default String serviceName() { return "HelloService"; }
 *    default String fullName() { return "example.HelloService"; }
 *
 *    // ...
 * }
 * }
 * </pre>
 *
 * In the application code, you will simply create a new class implementing the {@code HelloService} interface, and
 * register it with your webserver in whatever way is appropriate for your webserver.
 */
public interface ServiceInterface {
    /** Represents the metadata of a method in a gRPC service. */
    interface Method {
        String name();
    }

    /** The options that are passed to the service when a new connection is opened. */
    interface RequestOptions {
        /** A constant for the gRPC content type "application/grpc". */
        String APPLICATION_GRPC = "application/grpc";
        /** A constant for the gRPC content type "application/grpc+proto". */
        String APPLICATION_GRPC_PROTO = "application/grpc+proto";
        /** A constant for the gRPC content type "application/grpc+json". */
        String APPLICATION_GRPC_JSON = "application/grpc+json";

        /**
         * The authority of the client that is connecting to the service. This is the value of the ":authority" header
         * in the HTTP/2 request. This value is used by the service to determine the client's identity. It may be that
         * no authority is provided, in which case this method will return an empty optional.
         *
         * @return the authority of the client
         */
        @NonNull
        Optional<String> authority();

        /**
         * Gets whether the content type describes a protobuf message. This will be true if the {@link #contentType()}
         * is equal to {@link #APPLICATION_GRPC_PROTO} or {@link #APPLICATION_GRPC}.
         */
        boolean isProtobuf();

        /**
         * Gets whether the content type describes a JSON message. This will be true if the {@link #contentType()}
         * is equal to {@link #APPLICATION_GRPC_JSON}.
         */
        boolean isJson();

        /**
         * Gets the content type of the request. This is the value of the "content-type" header in the HTTP/2 request.
         * This value is used by the service to determine how to parse the request. Since gRPC supports custom content
         * types, it is possible that the content type will be something other than the constants defined in this
         * interface.
         *
         * @return the content type of the request
         */
        @NonNull
        String contentType();
    }

    /** Gets the simple name of the service. For example, "HelloService". */
    @NonNull
    String serviceName();
    /** Gets the full name of the service. For example, "example.HelloService". */
    @NonNull
    String fullName();
    /** Gets a list of each method in the service. This list may be empty but should never be null. */
    @NonNull
    List<Method> methods();

    /**
     * Called by the webserver to open a new connection between the client and the service. This method may be called
     * many times concurrently, once per connection. The implementation must therefore be thread-safe. A default
     * implementation is provided by the generated PBJ code, which will handle the dispatching of messages to the
     * appropriate methods in the correct way (unary, server-side streaming, etc.).
     *
     * @param method The method that was called by the client.
     * @param opts Any options from the request, such as the content type.
     * @param responses The subscriber used by the service to push responses back to the client.
     */
    @NonNull
    Pipeline<? super Bytes> open(
            @NonNull Method method, @NonNull RequestOptions opts, @NonNull Pipeline<? super Bytes> responses)
            throws GrpcException;
}
