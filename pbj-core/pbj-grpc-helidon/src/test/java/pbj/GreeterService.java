package pbj;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.hedera.pbj.runtime.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.HelloReply;
import greeter.HelloRequest;
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
                        // Unary request
                        final var message = messages.take();
                        final var request = parseRequest(message, options);
                        final var reply = sayHello(request);
                        final var replyBytes = createReply(reply, options);
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

    private HelloRequest parseRequest(Bytes message, RequestOptions options) throws InvalidProtocolBufferException {
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
        return request;
    }

    private Bytes createReply(HelloReply reply, RequestOptions options) throws InvalidProtocolBufferException {
        if (options.isProtobuf()) {
            return Bytes.wrap(reply.toByteArray());
        } else if (options.isJson()) {
            return Bytes.wrap(JsonFormat.printer().print(reply));
        } else {
            return  Bytes.wrap(reply.getMessage().getBytes());
        }
    }
}
