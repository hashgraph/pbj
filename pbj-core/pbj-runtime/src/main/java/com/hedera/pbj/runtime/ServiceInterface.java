package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Defines a common interface for all implementations of a gRPC {@code service}. PBJ will generate a subinterface
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
    interface Method {
        String name();
    }

    interface RequestOptions {
        String APPLICATION_GRPC = "application/grpc";
        String APPLICATION_GRPC_PROTO = "application/grpc+proto";
        String APPLICATION_GRPC_JSON = "application/grpc+json";

        boolean isProtobuf();
        boolean isJson();
        String contentType();
    }

    /**
     * Through this interface the {@link ServiceInterface} implementation will send responses back to the client.
     * The {@link #close()} method is called after all responses have been sent.
     *
     * <p>It is not common for an application to implement or use this interface. It is typically implemented by
     * a webserver to integrate PBJ into that server.
     */
    interface ResponseCallback {
        /**
         * Called to send a single response message to the client. For unary methods, this will be called once. For
         * server-side streaming or bidi-streaming, this may be called many times.
         *
         * @param response A response message to send to the client.
         */
        void send(@NonNull Bytes response);

        /**
         * Called to close the connection with the client, signaling that no more responses will be sent.
         */
        void close();
    }

    /** Gets the simple name of the service. For example, "HelloService". */
    @NonNull String serviceName();
    /** Gets the full name of the service. For example, "example.HelloService". */
    @NonNull String fullName();
    /** Gets a list of each method in the service. This list may be empty but should never be null. */
    @NonNull List<Method> methods();

    /**
     * Called by the webserver to open a new connection between the client and the service. This method may be called
     * many times concurrently, once per connection. The implementation must therefore be thread-safe. A default
     * implementation is provided by the generated PBJ code, which will handle the dispatching of messages to the
     * appropriate methods in the correct way (unary, server-side streaming, etc.).
     *
     * @param opts Any options from the request, such as the content type.
     * @param method The method that was called by the client.
     * @param messages A blocking queue of messages sent by the client.
     * @param callback A callback to send responses back to the client.
     */
    void open(
            @NonNull RequestOptions opts,
            @NonNull Method method,
            @NonNull BlockingQueue<Bytes> messages,
            @NonNull ResponseCallback callback);
}
