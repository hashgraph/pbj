package com.hedera.hashgraph.pbj.integration;

import com.hedera.hashgraph.pbj.runtime.io.DataBuffer;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import com.hederahashgraph.api.proto.pbj.test.parser.EverythingProtoParser;
import com.hederahashgraph.api.proto.pbj.test.writer.EverythingWriter;

import java.nio.ByteBuffer;

import static com.hedera.hashgraph.pbj.integration.AccountDetailsPbj.ACCOUNT_DETAILS;
import static com.hedera.hashgraph.pbj.integration.EverythingTestData.EVERYTHING;

@SuppressWarnings("DuplicatedCode")
public class EverythingWriterPerfTest {

    public static void main(String[] args) throws Exception {
        parse();
//        parseProtoC();
//        write();
    }

    private static void write() throws Exception {
        final DataBuffer outDataBuffer = DataBuffer.allocate(1024*1024, true);
        for (int i = 0; i < 10_000_000; i++) {
            outDataBuffer.reset();
            EverythingWriter.write(EVERYTHING, outDataBuffer);
            if (outDataBuffer.getPosition() <= 0) {
                System.out.println("outDataBuffer = " + outDataBuffer);
            }
        }
    }

    private static void parse() throws Exception {
        final DataBuffer inDataBuffer = DataBuffer.allocate(1024*1024, true);
        EverythingWriter.write(EVERYTHING, inDataBuffer);
        inDataBuffer.flip();

        for (int i = 0; i < 10_000_000; i++) {
            inDataBuffer.resetPosition();
            var e = EverythingProtoParser.parse(inDataBuffer);
//            if (!e.booleanField()) {
//                System.out.println("outDataBuffer = " + inDataBuffer);
//            }
        }
    }
    private static void parseProtoC() throws Exception {
        final ByteBuffer inBuffer = ByteBuffer.allocateDirect(1024*1024);
        final DataBuffer inDataBuffer = DataBuffer.wrap(inBuffer);
        EverythingWriter.write(EVERYTHING, inDataBuffer);
        inDataBuffer.flip();
        inBuffer.limit((int)inDataBuffer.getLimit());

        for (int i = 0; i < 10_000_000; i++) {
            inBuffer.position(0);
            com.hederahashgraph.api.proto.java.test.Everything.parseFrom(inBuffer);
        }
    }
    public static void write3() throws Exception {
        // write to temp data buffer and then read into byte array
        DataBuffer tempDataBuffer = DataBuffer.allocate(5 * 1024 * 1024, false);
        EverythingWriter.write(EVERYTHING, tempDataBuffer);
        tempDataBuffer.flip();
        final byte[] protobuf = new byte[(int) tempDataBuffer.getRemaining()];
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
