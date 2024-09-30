/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
