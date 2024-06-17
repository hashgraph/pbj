package pbj.interop;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipelines;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import grpc.testing.Empty;
import java.util.List;
import java.util.concurrent.Flow;

public interface TestService extends ServiceInterface {
    enum TestMethod implements Method {
        EmptyCall,
        UnaryCall,
        CacheableUnaryCall,
        StreamingOutputCall,
        StreamingInputCall,
        FullDuplexCall,
        HalfDuplexCall,
        UnimplementedCall
    }

    Empty EmptyCall (Empty request);

    @NonNull
    default String serviceName() { return "Test"; }

    @NonNull
    default String fullName() { return "grpc.testing.TestService"; }

    @NonNull
    default List<Method> methods() {
        return List.of(TestMethod.values());
    }

    @NonNull
    @Override
    default Flow.Subscriber<? super Bytes> open(
            @NonNull Method method,
            @NonNull RequestOptions opts,
            @NonNull Flow.Subscriber<? super Bytes> responses) {

        final var m = (TestMethod) method;
        try {
            switch (m) {
                case EmptyCall -> {
                    return Pipelines.<Empty, Empty>unary()
                            .mapRequest(bytes -> parseEmptyRequest(bytes, opts))
                            .method(this::EmptyCall)
                            .mapResponse(reply -> createEmptyReply(reply, opts))
                            .respondTo(responses)
                            .build();
                }
                case UnaryCall -> {
                    return null;
                }
                case CacheableUnaryCall -> {
                    return null;
                }
                case StreamingOutputCall -> {
                    return null;
                }
                case StreamingInputCall -> {
                    return null;
                }
                case FullDuplexCall -> {
                    return null;
                }
                case HalfDuplexCall -> {
                    return null;
                }
                case UnimplementedCall -> {
                    return null;
                }
            }
        } catch (Exception e) {
            responses.onError(e);
        }

        return null;
    }

    private Empty parseEmptyRequest(Bytes message, RequestOptions options) throws InvalidProtocolBufferException {
        Empty request;
        if (options.isProtobuf()) {
            request = Empty.parseFrom(message.toByteArray());
        } else {
            throw new GrpcException(GrpcStatus.UNIMPLEMENTED);
        }
        return request;
    }

    private Bytes createEmptyReply(Empty reply, RequestOptions options) throws InvalidProtocolBufferException {
        if (options.isProtobuf()) {
            return Bytes.wrap(reply.toByteArray());
        } else {
            throw new GrpcException(GrpcStatus.UNIMPLEMENTED);
        }
    }

}
