/**
 * An implementation of gRPC for the Helidon webserver based on PBJ.
 *
 * <p>The main entrypoint for this module, when handling a request, is the
 * {@link com.hedera.pbj.grpc.helidon.PbjProtocolSelector} class. A single instance of this class is created by the
 * {@link com.hedera.pbj.grpc.helidon.PbjProtocolProvider}, which is registered with Helidon via the
 * {@link java.util.ServiceLoader} mechanism. A new selector will be created when needed -- typically when config
 * changes. Generally, it is safe to think of this class as being long-lived.
 *
 * <p>The selector is then the initial entrypoint for all gRPC requests. It performs some basic sanity checks on the
 * request (was the method POST, does the route match the expected gRPC route, etc.) and then creates a new instance
 * of {@link com.hedera.pbj.grpc.helidon.PbjProtocolHandler} to handle the request. Almost all the real work in
 * handling the request is done by the protocol handler.
 *
 * <p>Config is currently not used.
 */
package com.hedera.pbj.grpc.helidon;