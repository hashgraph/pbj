package com.hedera.pbj.integration;

import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.pbj.runtime.io.buffer.WritableBufferedData;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;

import java.util.Arrays;

import static com.hedera.pbj.integration.AccountDetailsPbj.ACCOUNT_DETAILS;

/**
 * Testing main class for profiling parser and writer performance
 */
@SuppressWarnings("unused")
public class AccountDetailsWriter {

    /**
     * Testing main method for profiling parser and writer performance
     *
     * @param args command line args
     * @throws Exception if there was a problem
     */
    public static void main(String[] args) throws Exception {
        final WritableBufferedData outDataBuffer = WritableBufferedData.allocate(1024*1024, false);

        for (int i = 0; i < 10_000_000; i++) {
            outDataBuffer.reset();
            AccountDetails.PROTOBUF.write(ACCOUNT_DETAILS, outDataBuffer);
            if (outDataBuffer.position() <= 0) {
                System.out.println("outDataBuffer = " + outDataBuffer);
            }
        }
    }

    /**
     * Testing main method for profiling parser and writer performance
     *
     * @param args command line args
     * @throws Exception if there was a problem
     */
    public static void main2(String[] args) throws Exception {
        // write to temp data buffer and then read into byte array
        WritableBufferedData tempDataBuffer = WritableBufferedData.allocate(5 * 1024 * 1024, false);
        AccountDetails.PROTOBUF.write(ACCOUNT_DETAILS, tempDataBuffer);
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
                System.out.println("writtenData = " + Arrays.toString(writtenData));
            }
        }
    }
}
