package com.hedera.pbj.grpc.helidon;

import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.http2.spi.Http2SubProtocolProvider;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

/**
 * {@link java.util.ServiceLoader} provider implementation of pbj sub-protocol of HTTP/2.
 */
public class PbjProtocolProvider implements Http2SubProtocolProvider<PbjConfig> {
    static final String CONFIG_NAME = "pbj";

    /**
     * Default constructor required by Java {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly outside of testing, this is reserved for Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public PbjProtocolProvider() {
    }

    @Override
    public String protocolType() {
        return CONFIG_NAME;
    }

    @Override
    public Class<PbjConfig> protocolConfigType() {
        return PbjConfig.class;
    }

    @Override
    public Http2SubProtocolSelector create(PbjConfig config, ProtocolConfigs configs) {
        return new PbjProtocolSelector();
    }
}