// SPDX-License-Identifier: Apache-2.0
/** Runtime module of code needed by PBJ generated code at runtime. */
module com.hedera.pbj.runtime {
    requires transitive org.antlr.antlr4.runtime;
    requires jdk.unsupported;
    requires static transitive com.github.spotbugs.annotations;

    exports com.hedera.pbj.runtime;
    exports com.hedera.pbj.runtime.test;
    exports com.hedera.pbj.runtime.io;
    exports com.hedera.pbj.runtime.io.stream;
    exports com.hedera.pbj.runtime.io.buffer;
    exports com.hedera.pbj.runtime.jsonparser;
    exports com.hedera.pbj.runtime.json;
    exports com.hedera.pbj.runtime.grpc;
}
