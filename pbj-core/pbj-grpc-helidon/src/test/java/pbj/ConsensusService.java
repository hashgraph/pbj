package pbj;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.pbj.runtime.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public interface ConsensusService extends ServiceInterface {
    enum ConsensusMethod implements Method {
        createTopic,
        updateTopic,
        deleteTopic,
        submitMessage,
        getTopicInfo;
    }

    TransactionResponse createTopic(Transaction tx);
    TransactionResponse updateTopic(Transaction tx);
    TransactionResponse deleteTopic(Transaction tx);
    TransactionResponse submitMessage(Transaction tx);
    Response getTopicInfo(Query q);

    @NonNull
    default String serviceName() {
        return "ConsensusService";
    }

    @NonNull
    default String fullName() {
        return "proto.ConsensusService";
    }

    @NonNull
    default List<Method> methods() {
        return List.of(
                ConsensusMethod.createTopic,
                ConsensusMethod.updateTopic,
                ConsensusMethod.deleteTopic,
                ConsensusMethod.submitMessage,
                ConsensusMethod.getTopicInfo);
    }

    @Override
    default void open(
            final @NonNull RequestOptions options,
            final @NonNull Method method,
            final @NonNull BlockingQueue<Bytes> messages,
            final @NonNull ResponseCallback callback) {

        final var m = (ConsensusMethod) method;
        Thread.ofVirtual().start(() -> {
            try {
                switch (m) {
                    case ConsensusMethod.createTopic -> {
                        // Unary method
                        final var message = messages.take();
                        final var messageBytes = options.isProtobuf() // What if it isn't JSON or PROTOBUF?
                                ? Transaction.PROTOBUF.parse(message)
                                : Transaction.JSON.parse(message);
                        final var response = createTopic(messageBytes);
                        final var responseBytes = TransactionResponse.PROTOBUF.toBytes(response);
                        callback.send(responseBytes);
                        callback.close();
                    }
                    case ConsensusMethod.updateTopic -> {
                        // Unary method
                        final var message = messages.take();
                        final var messageBytes = options.isProtobuf()
                                ? Transaction.PROTOBUF.parse(message)
                                : Transaction.JSON.parse(message);
                        final var response = updateTopic(messageBytes);
                        final var responseBytes = TransactionResponse.PROTOBUF.toBytes(response);
                        callback.send(responseBytes);
                        callback.close();
                    }
                    case ConsensusMethod.deleteTopic -> {
                        // Unary method
                        final var message = messages.take();
                        final var messageBytes = options.isProtobuf()
                                ? Transaction.PROTOBUF.parse(message)
                                : Transaction.JSON.parse(message);
                        final var response = deleteTopic(messageBytes);
                        final var responseBytes = TransactionResponse.PROTOBUF.toBytes(response);
                        callback.send(responseBytes);
                        callback.close();
                    }
                    case ConsensusMethod.submitMessage -> {
                        // Unary method.
                        final var message = messages.take();
                        final var messageBytes = options.isProtobuf()
                                ? Transaction.PROTOBUF.parse(message)
                                : Transaction.JSON.parse(message);
                        final var response = submitMessage(messageBytes);
                        final var responseBytes = TransactionResponse.PROTOBUF.toBytes(response);
                        callback.send(responseBytes);
                        callback.close();
                    }
                    case ConsensusMethod.getTopicInfo -> {
                        // Unary method
                        final var message = messages.take();
                        final var messageBytes = options.isProtobuf()
                                ? Query.PROTOBUF.parse(message)
                                : Query.JSON.parse(message);
                        final var response = getTopicInfo(messageBytes);
                        final var responseBytes = Response.PROTOBUF.toBytes(response);
                        callback.send(responseBytes);
                        callback.close();
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
                callback.close();
            }
        });
    }
}
