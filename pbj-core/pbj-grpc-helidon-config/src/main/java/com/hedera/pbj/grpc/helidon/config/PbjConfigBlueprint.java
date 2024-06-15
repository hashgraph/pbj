package com.hedera.pbj.grpc.helidon.config;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.ProtocolConfig;

@Prototype.Blueprint
@Prototype.Configured
@Prototype.Provides(ProtocolConfig.class)
interface PbjConfigBlueprint extends ProtocolConfig {
    /**
     * Default maximum number of messages to buffer coming from the client until we start applying back pressure.
     *
     * @see #maxIncomingBufferedMessages()
     */
    int DEFAULT_MAX_INCOMING_BUFFERED_MESSAGES = 10;

    /**
     * Default maximum message size in bytes ({@value}).
     *
     * @see #maxMessageSize()
     */
    int DEFAULT_MAX_MESSAGE_SIZE = 1024*10; // 10KB

    /**
     * The size of the response buffer to make available to the {@link ServiceInterface}.
     *
     * @see #maxResponseBufferSize()
     */
    int DEFAULT_MAX_RESPONSE_BUFFER_SIZE = 1024*10; // 10KB

    /**
     * Maximum size of any message in bytes.
     * Defaults to {@value #DEFAULT_MAX_MESSAGE_SIZE}.
     *
     * @return the maximum number of bytes a single message can be
     */
    @Option.DefaultInt(DEFAULT_MAX_MESSAGE_SIZE)
    @Option.Configured
    int maxMessageSize();

    /**
     * Maximum size of the response buffer in bytes.
     * Defaults to {@value #DEFAULT_MAX_RESPONSE_BUFFER_SIZE}.
     *
     * @return the maximum number of bytes a response can be
     */
    @Option.DefaultInt(DEFAULT_MAX_RESPONSE_BUFFER_SIZE)
    @Option.Configured
    int maxResponseBufferSize();

    /**
     * Protocol configuration type.
     *
     * @return type of this configuration
     */
    default String type() {
        return "pbj";
    }

    /**
     * The maximum number of messages to buffer coming from the client until we start applying back pressure.
     * Defaults to {@value #DEFAULT_MAX_INCOMING_BUFFERED_MESSAGES}.
     */
    int maxIncomingBufferedMessages();
}
