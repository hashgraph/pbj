package com.hedera.pbj.grpc.helidon.encoding;

import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;

public class GzipEncoding implements Encoding {
    @Override
    public byte[] decode(byte[] data) throws Exception {
        return new GZIPInputStream(new ByteArrayInputStream(data)).readAllBytes();
    }
}
