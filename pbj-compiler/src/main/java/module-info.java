/** Compiler module. Not needed at runtime unless you are programmatically compiling */
module com.hedera.pbj.compiler {
    requires jdk.unsupported;
    requires transitive org.antlr.antlr4.runtime;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.pbj.compiler;
}
