/**
 * Runtime module of code needed by PBJ generated code at runtime.
 */
module com.hedera.pbj.runtime {
    requires static com.github.spotbugs.annotations;
    requires org.antlr.antlr4.runtime;
    requires jdk.unsupported;
    exports com.hedera.pbj.runtime;
    exports com.hedera.pbj.runtime.test;
    exports com.hedera.pbj.runtime.io;
    exports com.hedera.pbj.runtime.io.stream;
    exports com.hedera.pbj.runtime.io.buffer;
    exports com.hedera.pbj.runtime.jsonparser;
}