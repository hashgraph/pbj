// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.grpc;

import java.util.HashSet;
import java.util.Set;

/**
 * A utility to allocate AutoCloseable port numbers so that tests that start TCP servers can run in parallel.
 */
public class PortsAllocator {
    /** An AutoCloseable port number. */
    public record Port(int port, PortsAllocator portsAllocator) implements AutoCloseable {
        @Override
        public void close() {
            portsAllocator.release(port);
        }
    }

    private final int min;
    private final int max;

    private final Set<Integer> busyPorts = new HashSet<>();

    public PortsAllocator(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public Port acquire() {
        synchronized (busyPorts) {
            if (busyPorts.size() >= max - min + 1) {
                // If this becomes too limiting, implement a more complex blocking behavior to wait until a port is free
                throw new IllegalStateException("Too many busy ports. Reduce concurrency or increase the ports range.");
            }
            int port;
            do {
                port = (int) (Math.random() * (max - min + 1) + min);
            } while (busyPorts.contains(port));
            busyPorts.add(port);
            return new Port(port, this);
        }
    }

    private void release(final int port) {
        synchronized (busyPorts) {
            busyPorts.remove(port);
        }
    }
}
