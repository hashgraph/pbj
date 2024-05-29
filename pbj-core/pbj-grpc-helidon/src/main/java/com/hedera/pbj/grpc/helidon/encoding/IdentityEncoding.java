package com.hedera.pbj.grpc.helidon.encoding;

public class IdentityEncoding implements Encoding {
    @Override
    public byte[] decode(byte[] data) {
        return data;
    }
}
