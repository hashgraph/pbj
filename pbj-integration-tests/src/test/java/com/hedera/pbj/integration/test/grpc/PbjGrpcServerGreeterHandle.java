// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import com.hedera.pbj.grpc.helidon.PbjRouting;
import io.helidon.webserver.WebServer;

/** A Greeter handle for the PBJ GRPC server implementation. */
class PbjGrpcServerGreeterHandle extends GrpcServerGreeterHandle {
    private final int port;

    private WebServer server;

    public PbjGrpcServerGreeterHandle(final int port) {
        this.port = port;
    }

    @Override
    public synchronized void start() {
        if (server != null) {
            throw new IllegalStateException("Server already started");
        }
        server = WebServer.builder()
                .port(port)
                .addRouting(PbjRouting.builder().service(this))
                .maxPayloadSize(10000)
                .build()
                .start();
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
