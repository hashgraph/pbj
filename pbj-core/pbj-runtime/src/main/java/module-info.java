/**
 * Runtime module of code needed by PBJ generated code at runtime.
 */
module com.hedera.hashgraph.pbj.runtime {
    requires jdk.unsupported;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    exports com.hedera.hashgraph.pbj.runtime;
    exports com.hedera.hashgraph.pbj.runtime.test;
    exports com.hedera.hashgraph.pbj.runtime.io;
}