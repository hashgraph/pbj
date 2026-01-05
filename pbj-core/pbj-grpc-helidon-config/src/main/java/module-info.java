// SPDX-License-Identifier: Apache-2.0
import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

@Features.Preview
@Features.Name("PBJConfig")
@Features.Description("WebServer gRPC-PBJ Config")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"WebServer", "PBJ"})
module com.hedera.pbj.grpc.helidon.config {
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.config; // indirectly used on API of generated 'PbjConfig'
    requires transitive io.helidon.common;
    requires transitive io.helidon.config;
    requires io.helidon.webserver;
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata.codegen;

    exports com.hedera.pbj.grpc.helidon.config;
}
