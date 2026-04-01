// SPDX-License-Identifier: Apache-2.0
/** PBJ gRPC client implementation. */
module com.hedera.pbj.grpc.common {
    requires transitive com.hedera.pbj.runtime;
    requires com.github.luben.zstd_jni;

    exports com.hedera.pbj.grpc.common.compression;
}
