// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon.config;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.ProtocolConfig;

@Prototype.Blueprint
@Prototype.Configured("pbj")
@Prototype.Provides(ProtocolConfig.class)
interface PbjConfigBlueprint extends ProtocolConfig {
    /**
     * Default maximum message size in bytes ({@value}).
     *
     * @see #maxMessageSizeBytes()
     */
    int DEFAULT_MAX_MESSAGE_SIZE_BYTES = 1024 * 10; // 10KB

    /**
     * Maximum size of any message in bytes. Defaults to {@value #DEFAULT_MAX_MESSAGE_SIZE_BYTES}.
     *
     * @return the maximum number of bytes a single message can be
     */
    @Option.DefaultInt(DEFAULT_MAX_MESSAGE_SIZE_BYTES)
    @Option.Configured
    int maxMessageSizeBytes();

    /**
     * Protocol configuration type.
     *
     * @return type of this configuration
     */
    default String type() {
        return "pbj";
    }
}
