package com.hedera.pbj.integration;

import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.hedera.pbj.test.proto.pbj.TimestampTest;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class Test {
    public static void main(String[] args) throws Exception {
        NonSynchronizedByteArrayOutputStream out = new NonSynchronizedByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);
        dout.writeVarInt(5, false);
        out.flush();

        byte[] bytes = out.toByteArray();
        System.out.println("bytes = " + Arrays.toString(bytes));

        NonSynchronizedByteArrayInputStream in = new NonSynchronizedByteArrayInputStream(bytes);
        DataInputStream din = new DataInputStream(in);
        int read = din.readVarInt(false);
        System.out.println("read = " + read);



        final TimestampTest modelObj = new TimestampTest(4L,8 );
        // get reusable thread buffers
        final DataBuffer dataBuffer = DataBuffer.allocate(1024*1024,false);
        final DataBuffer dataBuffer2 = DataBuffer.allocate(1024*1024,false);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(1024*1024);

        // model to bytes with PBJ
        TimestampTest.PROTOBUF.write(modelObj,dataBuffer);

        // clamp limit to bytes written and reset position
        dataBuffer.flip();

        // copy bytes to ByteBuffer
        dataBuffer.readBytes(byteBuffer, 0, (int)dataBuffer.getRemaining());
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
        TimestampTest.PROTOBUF.parse(new DataInputStream(bin));
    }
    public record Everything(
            List<String> textList
    ){}
}
