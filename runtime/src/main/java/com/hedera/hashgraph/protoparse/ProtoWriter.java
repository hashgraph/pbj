package com.hedera.hashgraph.protoparse;

import java.io.IOException;
import java.io.OutputStream;

public interface ProtoWriter<T> {
    void write(T obj, OutputStream out) throws IOException;
}
