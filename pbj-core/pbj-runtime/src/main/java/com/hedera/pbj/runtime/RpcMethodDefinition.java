// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * Defines a single <b>unary</b> RPC method.
 *
 * @param path The name of the method, e.g. "cryptoTransfer"
 * @param requestType The type of the request message
 * @param responseType The type of the response message
 * @param <T> The type of the request message
 * @param <R> The type of the response message
 */
public record RpcMethodDefinition<T extends Record, R extends Record>(
        String path, Class<T> requestType, Class<R> responseType) {

    /**
     * Create a new builder for a {@link RpcMethodDefinition}.
     *
     * @return A new builder
     * @param <T> The type of the request message
     * @param <R> The type of the response message
     */
    public static <T extends Record, R extends Record> Builder<T, R> newBuilder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link RpcMethodDefinition}.
     *
     * @param <T> The type of the request message
     * @param <R> The type of the response message
     */
    public static final class Builder<T extends Record, R extends Record> {
        private String path;
        private Class<T> requestType;
        private Class<R> responseType;

        /**
         * Set the path of the method.
         *
         * @param path The path
         * @return This builder
         */
        public Builder<T, R> path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Set the request type.
         * @param requestType The request type
         * @return This builder
         */
        public Builder<T, R> requestType(Class<T> requestType) {
            this.requestType = requestType;
            return this;
        }

        /**
         * Set the response type.
         * @param responseType The response type
         * @return This builder
         */
        public Builder<T, R> responseType(Class<R> responseType) {
            this.responseType = responseType;
            return this;
        }

        /**
         * Build the {@link RpcMethodDefinition}.
         * @return The {@link RpcMethodDefinition}
         */
        public RpcMethodDefinition<T, R> build() {
            return new RpcMethodDefinition<>(path, requestType, responseType);
        }
    }
}
