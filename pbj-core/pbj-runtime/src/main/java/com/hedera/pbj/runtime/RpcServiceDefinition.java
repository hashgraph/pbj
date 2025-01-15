// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Defines a single RPC Service. The protobuf schema can define messages, or services. A Service is a collection of
 * grpc endpoints, or methods. This class simply contains the definition of the service endpoint.
 */
public interface RpcServiceDefinition {
    /**
     * The base path of the service. This is the path that will be used to register the service with the grpc server.
     * For example, "proto.ConsensusService".
     *
     * @return The base path of the service
     */
    @NonNull String basePath();

    /**
     * The set of methods that are defined for this service.
     *
     * @return The set of methods
     */
    @SuppressWarnings("java:S1452")
    @NonNull Set<RpcMethodDefinition<? extends Record, ? extends Record>> methods();
}
