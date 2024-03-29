package com.hedera.pbj.integration;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.util.JsonFormat;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.SignatureObject;
import com.hedera.services.stream.proto.SignatureType;
import com.hederahashgraph.api.proto.java.AccountID;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Random experimental code
 */
@SuppressWarnings("unused")
public class Test {

    public static void main(String[] args) throws Exception {
        AccountID accountID = AccountID.newBuilder().setAccountNum(1).setRealmNum(2).setShardNum(3).build();
        System.out.println(accountID);

        System.out.println("Json = " + JsonFormat.printer().print(accountID));

        SignatureObject signatureObject = SignatureObject.newBuilder()
                .setType(SignatureType.SHA_384_WITH_RSA)
                .setLength(48)
                .setChecksum(123)
                .setSignature(ByteString.copyFrom(new byte[]{
                                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
                                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
                                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E
                        }))
                .setHashObject(HashObject.newBuilder()
                        .setAlgorithm(HashAlgorithm.SHA_384)
                        .setLength(48)
                        .setHash(ByteString.copyFrom(new byte[]{
                                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
                                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
                                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E
                        })))
                .build();

//        SignatureType type,
//        int length,
//        int checksum,
//        Bytes signature,
//        @Nullable com.hedera.hapi.streams.HashObject hashObject

        System.out.println(signatureObject);
        System.out.println("Json = " + JsonFormat.printer().print(signatureObject));
    }
    
    public static void main2(String[] args) throws Exception {
        NonSynchronizedByteArrayOutputStream out = new NonSynchronizedByteArrayOutputStream();
        WritableStreamingData dout = new WritableStreamingData(out);
        dout.writeVarInt(5, false);
        out.flush();

        byte[] bytes = out.toByteArray();
        System.out.println("bytes = " + Arrays.toString(bytes));

        NonSynchronizedByteArrayInputStream in = new NonSynchronizedByteArrayInputStream(bytes);
        ReadableStreamingData din = new ReadableStreamingData(in);
        int read = din.readVarInt(false);
        System.out.println("read = " + read);



        final TimestampTest modelObj = new TimestampTest(4L,8 );
        // get reusable thread buffers
        final BufferedData dataBuffer = BufferedData.allocate(1024*1024);
        final BufferedData dataBuffer2 = BufferedData.allocate(1024*1024);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(1024*1024);

        // model to bytes with PBJ
        TimestampTest.PROTOBUF.write(modelObj,dataBuffer);

        // clamp limit to bytes written and reset position
        dataBuffer.flip();

        // copy bytes to ByteBuffer
        dataBuffer.readBytes(byteBuffer);
        byteBuffer.flip();

        // read proto bytes with ProtoC to make sure it is readable and no parse exceptions are thrown
        final com.hedera.pbj.test.proto.java.TimestampTest protoCModelObj = com.hedera.pbj.test.proto.java.TimestampTest.parseFrom(byteBuffer);

        // read proto bytes with PBJ parser
        dataBuffer.resetPosition();
        final TimestampTest modelObj2 = TimestampTest.PROTOBUF.parse(dataBuffer);

        // check the read back object is equal to written original one
        //assertEquals(modelObj.toString(), modelObj2.toString());
        System.out.println(modelObj.equals(modelObj2));

        // model to bytes with ProtoC writer
        byteBuffer.clear();
        final CodedOutputStream codedOutput = CodedOutputStream.newInstance(byteBuffer);
        protoCModelObj.writeTo(codedOutput);
        codedOutput.flush();
        byteBuffer.flip();
        // copy to a data buffer
        dataBuffer2.writeBytes(byteBuffer);
        dataBuffer2.flip();

        // compare written bytes
        System.out.println(dataBuffer.equals(dataBuffer2));

        // parse those bytes again with PBJ
        dataBuffer2.resetPosition();
        final TimestampTest modelObj3 = TimestampTest.PROTOBUF.parse(dataBuffer2);
        System.out.println(modelObj.equals(modelObj3));

        // test with input stream
        byteBuffer.position(0);
        byte[] protoBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(protoBytes);
        NonSynchronizedByteArrayInputStream bin = new NonSynchronizedByteArrayInputStream(protoBytes);
        TimestampTest.PROTOBUF.parse(new ReadableStreamingData(bin));
    }
    public record Everything(
            List<String> textList
    ){}
}
