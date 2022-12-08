import com.google.protobuf.CodedOutputStream;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

public class Test {
    public static void main(String[] args) throws MalformedProtobufException {
        Test test = new Test();
        test.buffer.clear();
        for (int i = 0; i < 1; i++) {
            final long read = readVarintJasper(test.buffer);
            System.out.println("read = " + read);
        }
    }
    final ByteBuffer buffer = ByteBuffer.allocate(256*1024);
    public Test() {
        try {
            CodedOutputStream cout = CodedOutputStream.newInstance(buffer);
            cout.writeUInt64NoTag(5);
            cout.flush();
            // copy to direct buffer
            buffer.flip();
        } catch (IOException e){
            e.printStackTrace();
        }
    }


    public static long readVarintJasper(ByteBuffer buf) throws MalformedProtobufException {
        int intValue = buf.get();

        if (intValue >= 0) {
            return intValue;
        }
        throw new MalformedProtobufException("doh");
    }
}
