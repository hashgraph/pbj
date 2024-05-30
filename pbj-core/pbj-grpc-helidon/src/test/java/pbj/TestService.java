package pbj;

import com.hedera.pbj.runtime.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public interface TestService extends ServiceInterface {
    enum CustomMethod implements Method {
        echoUnary,
        echoBidi,
        echoServerStream,
        echoClientStream,
        failUnary,
        failBidi,
        failServerStream,
        failClientStream
    }

    String echo(String message);

    @NonNull
    default String serviceName() {
        return "TestService";
    }

    @NonNull
    default String fullName() {
        return "proto.TestService";
    }

    @NonNull
    default List<Method> methods() {
        return Arrays.asList(CustomMethod.values());
    }

    @Override
    default void open(
            final @NonNull RequestOptions options,
            final @NonNull Method method,
            final @NonNull BlockingQueue<Bytes> messages,
            final @NonNull ResponseCallback callback) {

        final var m = (CustomMethod) method;
        Thread.ofVirtual().start(() -> {
            try {
                switch (m) {
                    case CustomMethod.echoUnary -> {
                        final var message = messages.take();
                        final var ct = options.contentType();
                        if (options.isJson() || options.isProtobuf() || !ct.equals("application/grpc+string")) {
                            throw new IllegalArgumentException("Only 'string' is allowed");
                        }

                        final var response = echo(message.asUtf8String());
                        callback.send(Bytes.wrap(response));
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
