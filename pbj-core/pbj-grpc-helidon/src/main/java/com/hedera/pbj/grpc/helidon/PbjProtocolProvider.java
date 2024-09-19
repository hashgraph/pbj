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

package com.hedera.pbj.grpc.helidon;

import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.http2.spi.Http2SubProtocolProvider;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

/** {@link java.util.ServiceLoader} provider implementation of pbj sub-protocol of HTTP/2. */
public class PbjProtocolProvider implements Http2SubProtocolProvider<PbjConfig> {
    static final String CONFIG_NAME = "pbj";

    /**
     * Default constructor required by Java {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly outside of testing, this is reserved for Java {@link
     *     java.util.ServiceLoader}
     */
    @Deprecated
    public PbjProtocolProvider() {
        // requires deprecated annotation so as to avoid accidental use
    }

    @Override
    @NonNull
    public String protocolType() {
        return CONFIG_NAME;
    }

    @Override
    @NonNull
    public Class<PbjConfig> protocolConfigType() {
        return PbjConfig.class;
    }

    @Override
    @NonNull
    public Http2SubProtocolSelector create(
            @NonNull final PbjConfig config, @NonNull final ProtocolConfigs configs) {
        return new PbjProtocolSelector(config);
    }
}
