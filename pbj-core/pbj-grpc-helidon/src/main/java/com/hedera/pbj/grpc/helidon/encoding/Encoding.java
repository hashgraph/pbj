package com.hedera.pbj.grpc.helidon.encoding;

public interface Encoding {
    GzipEncoding GZIP = new GzipEncoding();
    IdentityEncoding IDENTITY = new IdentityEncoding();

    byte[] decode(byte[] data) throws Exception;
}
