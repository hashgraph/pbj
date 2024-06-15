import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

@Preview
@Feature(value = "PBJConfig",
        description = "WebServer gRPC-PBJ Config",
        in = HelidonFlavor.SE,
        path = {"WebServer", "PBJ"}
)
@SuppressWarnings({ "requires-automatic" })
module com.hedera.pbj.grpc.helidon.config {
    requires static io.helidon.common.features.api;
    requires static io.helidon.common.features.processor;
    requires static io.helidon.config.metadata.processor;
    requires com.hedera.pbj.runtime;
    requires io.helidon.webserver;
    requires io.helidon.webserver.http2;
    exports com.hedera.pbj.grpc.helidon.config;
}
