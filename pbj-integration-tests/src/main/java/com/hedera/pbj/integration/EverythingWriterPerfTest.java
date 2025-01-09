// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import java.util.HexFormat;

/**
 * Testing main class for profiling parser and writer performance for everything object
 */
@SuppressWarnings("DuplicatedCode")
public class EverythingWriterPerfTest {

    /**
     * Empty constructor
     */
    public EverythingWriterPerfTest() {
        // no-op
    }

    /**
     * Simple test main method that writes everything object to buffer 10 million times
     *
     * @param args Not used
     * @throws Exception Thrown if there was a problem
     */
    public static void main(String[] args) throws Exception {
        final BufferedData outDataBuffer = BufferedData.allocate(1024*1024);

        for (int i = 0; i < 10_000_000; i++) {
            outDataBuffer.reset();
            Everything.PROTOBUF.write(EverythingTestData.EVERYTHING, outDataBuffer);
            if (outDataBuffer.position() <= 0) {
                System.out.println("outDataBuffer = " + outDataBuffer);
            }
        }
    }

    /**
     * Second simple test main method that writes everything object to buffer 10 million times using protoC
     *
     * @param args Not used
     * @throws Exception Thrown if there was a problem
     */
    @SuppressWarnings({"unused", "MethodCanBeVariableArityMethod"})
    public static void main2(String[] args) throws Exception {
        // write to temp data buffer and then read into byte array
        BufferedData tempDataBuffer = BufferedData.allocate(5 * 1024 * 1024);
        Everything.PROTOBUF.write(EverythingTestData.EVERYTHING, tempDataBuffer);
        tempDataBuffer.flip();
        final byte[] protobuf = new byte[(int) tempDataBuffer.remaining()];
        tempDataBuffer.readBytes(protobuf);
        // write out with protoc
        final GetAccountDetailsResponse.AccountDetails accountDetailsProtoC = GetAccountDetailsResponse.AccountDetails.parseFrom(protobuf);
//
//        final ByteBuffer bbout = ByteBuffer.allocate(1024*1024);

        for (int i = 0; i < 10_000_000; i++) {
//            bbout.clear();
            final byte[] writtenData = accountDetailsProtoC.toByteArray();
            if (writtenData.length != protobuf.length) {
                System.out.println("writtenData = " + HexFormat.of().formatHex(writtenData));
            }
        }
    }
}
