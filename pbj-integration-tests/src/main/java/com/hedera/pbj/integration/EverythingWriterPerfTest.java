// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;

@SuppressWarnings("DuplicatedCode")
public class EverythingWriterPerfTest {

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
                System.out.println("writtenData = " + writtenData);
            }
        }
    }
}
