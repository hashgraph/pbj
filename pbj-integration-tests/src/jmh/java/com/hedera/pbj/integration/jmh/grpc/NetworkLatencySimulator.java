// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc;

import com.hedera.pbj.grpc.client.helidon.PbjGrpcCall;
import com.hedera.pbj.grpc.client.helidon.PbjGrpcNetworkBytesInspector;
import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * Simulate network latency based on the actual amount of user data being sent/received over a network.
 * Note: with 1Gbps, we get 8ns per byte. So to test higher speeds, we should switch to floating point math.
 * A very fast network would make any compression look bad because we'll waste CPU time on the compression
 * instead of leveraging the fast network.
 * A slow network would show that compression may be useful sometimes. Specifically, for larger, compressible
 * payloads. Smaller payloads (<8K) never benefit from compression.
 */
public class NetworkLatencySimulator {
    // ms 1e-3, us 1e-6, ns 1e-9:
    private static final long NANOS_IN_MILLI = 1_000_000L;

    /**
     * Install the NetworkLatencySimulator as a PbjGrpcNetworkBytesInspector in PbjGrpcCall.
     * @param networkSpeedMbitPerSecond the speed in Mbps, e.g. 1_000 for 1Gbps network
     * @param printSizes if true, print a few sent/received sizes for debugging/additional information
     */
    public static void simulate(final long networkSpeedMbitPerSecond, final boolean printSizes) {
        // mbit->mbyte = /8:
        final long nanosPerByte = 1_000_000_000L * 8 / (networkSpeedMbitPerSecond * 1_000_000L);
        PbjGrpcCall.setNetworkBytesInspector(new PbjGrpcNetworkBytesInspector() {
            // max number of sizes to print if enabled:
            int counter = 4;

            private void sleep(long bytes) {
                final long nanos = nanosPerByte * bytes;
                try {
                    Thread.sleep(nanos / NANOS_IN_MILLI, (int) (nanos % NANOS_IN_MILLI));
                } catch (InterruptedException ignore) {
                }
            }

            @Override
            public void sent(Bytes bytes) {
                if (printSizes && counter-- >= 0) {
                    System.err.println("sent: " + bytes.length() + " bytes");
                }
                sleep(bytes.length());
            }

            @Override
            public void received(Bytes bytes) {
                if (printSizes && counter-- >= 0) {
                    System.err.println("received: " + bytes.length() + " bytes");
                }
                sleep(bytes.length());
            }
        });
    }
}
