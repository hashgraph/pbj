// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import com.hedera.pbj.runtime.Codec;

/**
 * An interface for GRPC client.
 * <p>
 * It's capable of creating GrpcCall objects that applications can use to send requests to a GRPC service
 * and receive replies from the service through the supplied Pipeline instance.
 */
public interface GrpcClient {
    /**
     * Create a new GRPC call.
     *
     * @param <RequestT> request type
     * @param <ReplyT> reply type
     * @param fullMethodName a full GRPC method name that includes the fully-qualified service name and the method name
     * @param requestCodec a PBJ codec for requests
     * @param replyCodec a PBJ codec for replies
     * @param pipeline a pipeline for receiving replies
     */
    <RequestT, ReplyT> GrpcCall<RequestT, ReplyT> createCall(
            String fullMethodName, Codec<RequestT> requestCodec, Codec<ReplyT> replyCodec, Pipeline<ReplyT> pipeline);
}
