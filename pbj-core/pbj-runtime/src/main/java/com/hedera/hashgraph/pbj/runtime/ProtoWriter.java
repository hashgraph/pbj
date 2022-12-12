package com.hedera.hashgraph.pbj.runtime;

import com.hedera.hashgraph.pbj.runtime.io.DataOutput;

import java.io.IOException;

public interface ProtoWriter<T> {
    void write(T obj, DataOutput out) throws IOException;
}
