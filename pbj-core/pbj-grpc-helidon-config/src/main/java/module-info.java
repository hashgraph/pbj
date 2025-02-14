// SPDX-License-Identifier: Apache-2.0
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

@Preview
@Feature(
        value = "PBJConfig",
        description = "WebServer gRPC-PBJ Config",
        in = HelidonFlavor.SE,
        path = {"WebServer", "PBJ"})
module com.hedera.pbj.grpc.helidon.config {
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.config;
    requires transitive io.helidon.common;
    requires io.helidon.webserver;
    requires static io.helidon.common.features.api;
    requires static io.helidon.common.features.processor;
    requires static io.helidon.config.metadata.processor;

    exports com.hedera.pbj.grpc.helidon.config;
}
