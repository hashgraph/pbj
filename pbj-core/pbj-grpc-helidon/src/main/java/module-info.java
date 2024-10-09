import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/** Runtime module of code needed by PBJ generated code at runtime. */
@Preview
@Feature(
        value = "PBJ",
        description = "WebServer gRPC-PBJ Support",
        in = HelidonFlavor.SE,
        path = {"WebServer", "PBJ"})
module com.hedera.pbj.grpc.helidon {
    requires transitive com.hedera.pbj.grpc.helidon.config;
    requires transitive com.hedera.pbj.runtime;
    requires transitive io.helidon.common.buffers;
    requires transitive io.helidon.common.config;
    requires transitive io.helidon.common;
    requires transitive io.helidon.http.http2;
    requires transitive io.helidon.webserver.http2;
    requires transitive io.helidon.webserver;
    requires io.helidon.common.media.type;
    requires io.helidon.common.uri;
    requires io.helidon.http;
    requires io.helidon.metrics.api;
    requires java.net.http;
    requires static transitive com.github.spotbugs.annotations;
    requires static io.helidon.builder.codegen;
    requires static io.helidon.codegen.apt;
    requires static io.helidon.common.features.api;
    requires static io.helidon.common.features.processor;

    exports com.hedera.pbj.grpc.helidon;

    provides io.helidon.webserver.http2.spi.Http2SubProtocolProvider with
            com.hedera.pbj.grpc.helidon.PbjProtocolProvider;
    provides io.helidon.webserver.spi.ProtocolConfigProvider with
            com.hedera.pbj.grpc.helidon.PbjProtocolConfigProvider;
}
