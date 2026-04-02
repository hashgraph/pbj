// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

// import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
// import com.hedera.pbj.test.proto.pbj.Everything;

public class Java25Test {
    public static void main(String[] args) { // throws ParseException {
        //        Everything obj = Everything.DEFAULT;
        //
        //        final Bytes bytes = Everything.PROTOBUF.toBytes(obj);
        //
        //        final Everything parsed = Everything.PROTOBUF.parse(bytes);

        final Bytes bytes = Bytes.wrap("5424726\0");
        System.err.println(bytes.getVarInt(0, false));
    }
}
