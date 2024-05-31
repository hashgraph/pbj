package pbj;

import com.google.protobuf.util.JsonFormat;
import com.hedera.pbj.runtime.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.HelloReply;
import greeter.HelloReplyOuterClass;
import greeter.HelloRequest;
import greeter.HelloRequestOuterClass;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * This service doesn't rely on any PBJ objects, because the build right now doesn't have a good way to use the
 * compiler. This will be fixed in a future release. So for now, we use Google's generated protobuf objects.
 */
public interface GreeterService extends ServiceInterface {
    enum GreeterMethod implements Method {
        sayHello,
        sayHelloStreamReply,
        sayHelloStreamRequest,
        sayHelloStreamBidi
    }

    HelloReply sayHello(HelloRequest request);

    @NonNull
    default String serviceName() {
        return "GreeterService";
    }

    @NonNull
    default String fullName() {
        return "greeter.GreeterService";
    }

    @NonNull
    default List<Method> methods() {
        return Arrays.asList(GreeterMethod.values());
    }

    @Override
    default void open(
            final @NonNull RequestOptions options,
            final @NonNull Method method,
            final @NonNull BlockingQueue<Bytes> messages,
            final @NonNull ResponseCallback callback) {

        final var m = (GreeterMethod) method;
        Thread.ofVirtual().start(() -> {
            try {
                switch (m) {
                    case GreeterMethod.sayHello -> {
                        // Block waiting for the next message
                        final var message = messages.take();
                        // Parse the message into a HelloRequest
                        HelloRequest request;
                        if (options.isProtobuf()) {
                            request = HelloRequest.parseFrom(message.toByteArray());
                        } else if (options.isJson()) {
                            final var builder = HelloRequest.newBuilder();
                            JsonFormat.parser().merge(message.asUtf8String(), builder);
                            request = builder.build();
                        } else {
                            request = HelloRequest.newBuilder().setName(message.asUtf8String()).build();
                        }
                        // Call the service method
                        final var reply = sayHello(request);
                        // Convert the reply back into the appropriate format
                        Bytes replyBytes;
                        if (options.isProtobuf()) {
                            replyBytes = Bytes.wrap(reply.toByteArray());
                        } else if (options.isJson()) {
                            replyBytes = Bytes.wrap(JsonFormat.printer().print(reply));
                        } else {
                            replyBytes = Bytes.wrap(reply.getMessage().getBytes());
                        }
                        // Send back the reply and close the stream (unary).
                        callback.send(replyBytes);
                        callback.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                callback.close();
            }
        });
    }
}
