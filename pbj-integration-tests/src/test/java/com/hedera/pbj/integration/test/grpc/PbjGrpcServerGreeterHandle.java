// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import com.hedera.pbj.grpc.helidon.PbjRouting;
import io.helidon.webserver.WebServer;

/** A Greeter handle for the PBJ GRPC server implementation. */
public class PbjGrpcServerGreeterHandle extends GrpcServerGreeterHandle {
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

    @Override
    public void stopNow() {
        // In Helidon, stop() actually closes the server socket on the spot.
        // However, there's no way to abruptly kill open connections, other than by throwing exceptions maybe.
        // We do have tests that throw exceptions on the server thread, so I think we cover those cases.
        // We also have tests that run the server in a separate JVM and kill the process, so we do cover all the cases.
        stop();
    }
}
