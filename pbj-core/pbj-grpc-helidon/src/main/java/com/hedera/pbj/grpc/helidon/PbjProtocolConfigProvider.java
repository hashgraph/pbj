// SPDX-License-Identifier: Apache-2.0
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
