// SPDX-License-Identifier: Apache-2.0
/** Transport-neutral gRPC code shared by the PBJ gRPC client and server implementations. */
module com.hedera.pbj.grpc.common {
    requires transitive com.hedera.pbj.runtime;
    requires com.github.luben.zstd_jni;
    requires static transitive com.github.spotbugs.annotations;

    exports com.hedera.pbj.grpc.common;
    exports com.hedera.pbj.grpc.common.compression;
}
