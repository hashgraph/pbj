// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

/**
 * An interface for a GRPC call.
 * <p>
 * It's capable of sending requests to a GRPC service via the sendRequest() method declared here.
 * <p>
 * An implementation of this interface is supposed to maintain a reference to a {@code Pipeline<ReplyT>}
 * through which the call will send replies from the GRPC service to the application code.
 *
 * @param <RequestT> request type
 * @param <ReplyT> reply type
 */
public interface GrpcCall<RequestT, ReplyT> {
    /**
     * Send a request to the service.
     * @param request a request object
     * @param endOfStream a flag indicating if this is the last request, useful for unary or server-streaming methods
     */
    void sendRequest(final RequestT request, final boolean endOfStream);

    /**
     * Half-closes the call to indicate that it's complete from the request sender perspective.
     * When using HTTP2 as a transport protocol, this is usually represented as sending an empty buffer
     * of data with the endOfStream flag set to true.
     * <p>
     * Note that an application can indicate this state using the `sendRequest` method above as well,
     * but it requires a real, non-empty request to be sent prior to half-closing the call, so that method
     * is most applicable to unary calls where the method implementation knows that there's just a single request
     * to be sent.
     * <p>
     * For other method types, such as client-streaming or bidi for example, this `halfClose()` method may be
     * more convenient instead.
     * <p>
     * Note that sending more requests after the call has been half-closed will result in an exception.
     */
    void halfClose();
}
