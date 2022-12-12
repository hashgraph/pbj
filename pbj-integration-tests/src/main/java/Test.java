import com.google.protobuf.CodedOutputStream;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Test {
    public static void main(String[] args) throws MalformedProtobufException {
        Everything e1 = new Everything(Collections.emptyList());
        System.out.println("e1 = " + e1);
        Everything e2 = new Everything(List.of(""));
        System.out.println("e2 = " + e2);
        System.out.println("e1.equals(e2) = " + e1.equals(e2));
    }
    public record Everything(
            List<String> textList
    ){}
}
