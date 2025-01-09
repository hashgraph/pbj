package com.hedera.pbj.integration.test;

import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.pbj.integration.AccountDetailsPbj;
import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base set of tests to make sure that JSON is round tripped correctly with ProtoC Generated code
 */
@SuppressWarnings("DataFlowIssue")
public class JsonCodecTest {
    @Test
    public void simpleTimestampTest() throws Exception {
        // build with protoc
        com.hederahashgraph.api.proto.java.Timestamp t = com.hederahashgraph.api.proto.java.Timestamp.newBuilder()
                .setSeconds(1234)
                .setNanos(567)
                .build();
        // write to JSON with protoc
        String protoCJson = JsonFormat.printer().print(t);
        // parse with pbj
        Timestamp tPbj = Timestamp.JSON.parse(BufferedData.wrap(protoCJson.getBytes(StandardCharsets.UTF_8)));
        // check
        assertEquals(t.getSeconds(), tPbj.seconds());
        assertEquals(t.getNanos(), tPbj.nanos());
        // write with pbj
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        WritableStreamingData out = new WritableStreamingData(bout);
        Timestamp.JSON.write(tPbj, out);
        String pbjJson = bout.toString();
        System.out.println("pbjJson = " + pbjJson);
        assertEquals(protoCJson, pbjJson);
    }
    @Test
    public void simpleKeyTest() throws Exception {
        // build with protoc
        com.hederahashgraph.api.proto.java.Key keyProtoC = com.hederahashgraph.api.proto.java.Key.newBuilder()
                .setECDSA384(ByteString.copyFrom(new byte[]{0,1,2,3}))
                .build();
        // write to JSON with protoc
        String protoCJson = JsonFormat.printer().print(keyProtoC);
        System.out.println("protoCJson = " + protoCJson);
        // parse with pbj
        Key tPbj = Key.JSON.parse(BufferedData.wrap(protoCJson.getBytes(StandardCharsets.UTF_8)));
        // check
        assertEquals(HexFormat.of().formatHex(keyProtoC.getECDSA384().toByteArray()), tPbj.ecdsa384().toHex());
        // write with pbj
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        WritableStreamingData out = new WritableStreamingData(bout);
        Key.JSON.write(tPbj, out);
        String pbjJson = bout.toString();
        System.out.println("pbjJson = " + pbjJson);
        assertEquals(protoCJson, pbjJson);
    }

    @Test
    public void accountDetailsTest() throws Exception {
        // get prebuild pbj
        final AccountDetails accountDetailsPbj = AccountDetailsPbj.ACCOUNT_DETAILS;
        // convert to protoc
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        WritableStreamingData out = new WritableStreamingData(bout);
        AccountDetails.PROTOBUF.write(accountDetailsPbj, out);
        GetAccountDetailsResponse.AccountDetails accountDetailsProtoC =
                GetAccountDetailsResponse.AccountDetails.parseFrom(bout.toByteArray());
        // write to JSON with protoC
        String protoCJson = JsonFormat.printer().print(accountDetailsProtoC);
        // parse with pbj
        AccountDetails accountDetailsPbj2 = AccountDetails.JSON.parse(BufferedData.wrap(protoCJson.getBytes(StandardCharsets.UTF_8)));
        // check
        assertEquals(accountDetailsPbj, accountDetailsPbj2);
        // write with pbj
        bout.reset();
        AccountDetails.JSON.write(accountDetailsPbj2, out);
        String pbjJson = bout.toString();
        assertEquals(protoCJson, pbjJson);
    }

    @Test
    public void everythingTest() throws Exception {
        // get prebuild pbj
        final Everything everythingPbj = EverythingTestData.EVERYTHING;
        // convert to protoc
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        WritableStreamingData out = new WritableStreamingData(bout);
        Everything.PROTOBUF.write(everythingPbj, out);
        com.hedera.pbj.test.proto.java.Everything accountDetailsProtoC =
                com.hedera.pbj.test.proto.java.Everything.parseFrom(bout.toByteArray());
        // write to JSON with protoC
        String protoCJson = JsonFormat.printer().print(accountDetailsProtoC);
        System.out.println("protoCJson = " + protoCJson);
        // parse with pbj
        Everything everythingPbj2 = Everything.JSON.parse(BufferedData.wrap(protoCJson.getBytes(StandardCharsets.UTF_8)));
        // check
        assertEquals(everythingPbj, everythingPbj2);
        // write with pbj
        bout.reset();
        Everything.JSON.write(everythingPbj2, out);
        String pbjJson = bout.toString();
        assertEquals(protoCJson, pbjJson);
    }


}
