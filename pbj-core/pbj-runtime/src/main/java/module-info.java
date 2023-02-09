/**
 * Runtime module of code needed by PBJ generated code at runtime.
 */
module com.hedera.pbj.runtime {
    requires jdk.unsupported;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
	exports com.hedera.pbj.runtime;
    exports com.hedera.pbj.runtime.test;
    exports com.hedera.pbj.runtime.io;
}