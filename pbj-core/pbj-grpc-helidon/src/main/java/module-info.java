import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/** Runtime module of code needed by PBJ generated code at runtime. */
@Preview
@Feature(value = "PBJ",
        description = "WebServer gRPC-PBJ Support",
        in = HelidonFlavor.SE,
        path = {"WebServer", "PBJ"}
)
@SuppressWarnings({ "requires-automatic" })
module com.hedera.pbj.grpc.helidon {
    requires static io.helidon.common.features.api;
    requires static com.github.spotbugs.annotations;
    requires static io.helidon.common.features.processor;
    requires static io.helidon.codegen.apt;
    requires static io.helidon.builder.codegen;
    requires com.hedera.pbj.grpc.helidon.config;
    requires com.hedera.pbj.runtime;
    requires io.helidon.webserver;
    requires io.helidon.webserver.http2;
    requires io.helidon.metrics.api;
    requires java.net.http;

    exports com.hedera.pbj.grpc.helidon;

    provides io.helidon.webserver.http2.spi.Http2SubProtocolProvider with
            com.hedera.pbj.grpc.helidon.PbjProtocolProvider;
    provides io.helidon.webserver.spi.ProtocolConfigProvider with
            com.hedera.pbj.grpc.helidon.PbjProtocolConfigProvider;
}
