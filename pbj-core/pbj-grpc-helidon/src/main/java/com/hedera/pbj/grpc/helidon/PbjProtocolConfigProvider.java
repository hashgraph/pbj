/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ProtocolConfigProvider;

/**
 * Implementation of a service provider interface so Helidon can create a {@link PbjConfig}
 * instance.
 */
public class PbjProtocolConfigProvider implements ProtocolConfigProvider<PbjConfig> {

    /** Create an instance. */
    public PbjProtocolConfigProvider() {
        // default constructor, used by Helidon to create the instance
    }

    @Override
    @NonNull
    public String configKey() {
        return PbjProtocolProvider.CONFIG_NAME;
    }

    @Override
    public PbjConfig create(@NonNull final Config config, @NonNull final String name) {
        return PbjConfig.builder().config(config).name(name).build();
    }
}
