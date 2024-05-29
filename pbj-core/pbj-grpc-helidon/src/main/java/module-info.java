/** Runtime module of code needed by PBJ generated code at runtime. */
module com.hedera.pbj.grpc.helidon {
    requires static com.github.spotbugs.annotations;
    requires com.hedera.pbj.runtime;
    requires io.helidon.webserver;
    requires io.helidon.webserver.http2;

    exports com.hedera.pbj.grpc.helidon;

    provides io.helidon.webserver.http2.spi.Http2SubProtocolProvider with
            com.hedera.pbj.grpc.helidon.PbjProtocolProvider;
    provides io.helidon.webserver.spi.ProtocolConfigProvider with
            com.hedera.pbj.grpc.helidon.PbjProtocolConfigProvider;
}
