/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.grpc.helidon;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;
import io.helidon.common.Generated;
import io.helidon.common.config.Config;
import java.util.Objects;
import java.util.Optional;

// NOTE: This file was originally generated from a Maven build patterned after that found in the Helidon project.
// But I am not sure how to integrate that into the build process, so for now, I have just copy/pasted it here.

/**
 * Interface generated from definition. Please add javadoc to the definition interface.
 *
 * @see #builder()
 * @see #create()
 */
@Generated(value = "io.helidon.builder.codegen.BuilderCodegen", trigger = "com.hedera.hashgraph.pbj.PbjConfigBlueprint")
public interface PbjConfig extends PbjConfigBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(PbjConfig instance) {
        return PbjConfig.builder().from(instance);
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config used to configure the new instance
     * @return a new instance configured from configuration
     */
    static PbjConfig create(Config config) {
        return PbjConfig.builder().config(config).buildPrototype();
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static PbjConfig create() {
        return PbjConfig.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link PbjConfig}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends PbjConfig> implements Prototype.ConfiguredBuilder<BUILDER, PROTOTYPE> {

        private Config config;
        private int maxMessageSize = 10240;
        private int maxResponseBufferSize = 10240;
        private String name;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance. This method disables automatic service discovery.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(PbjConfig prototype) {
            maxMessageSize(prototype.maxMessageSize());
            maxResponseBufferSize(prototype.maxResponseBufferSize());
            name(prototype.name());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            maxMessageSize(builder.maxMessageSize());
            maxResponseBufferSize(builder.maxResponseBufferSize());
            builder.name().ifPresent(this::name);
            return self();
        }

        /**
         * Update builder from configuration (node of this type).
         * If a value is present in configuration, it would override currently configured values.
         *
         * @param config configuration instance used to obtain values to update this builder
         * @return updated builder instance
         */
        @Override
        public BUILDER config(Config config) {
            Objects.requireNonNull(config);
            this.config = config;
            config.get("max-message-size").as(Integer.class).ifPresent(this::maxMessageSize);
            config.get("max-response-buffer-size").as(Integer.class).ifPresent(this::maxResponseBufferSize);
            return self();
        }

        /**
         * Maximum size of any message in bytes.
         * Defaults to {@value #DEFAULT_MAX_MESSAGE_SIZE}.
         *
         * @param maxMessageSize the maximum number of bytes a single message can be
         * @return updated builder instance
         * @see #maxMessageSize()
         */
        public BUILDER maxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return self();
        }

        /**
         * Maximum size of the response buffer in bytes.
         * Defaults to {@value #DEFAULT_MAX_RESPONSE_BUFFER_SIZE}.
         *
         * @param maxResponseBufferSize the maximum number of bytes a response can be
         * @return updated builder instance
         * @see #maxResponseBufferSize()
         */
        public BUILDER maxResponseBufferSize(int maxResponseBufferSize) {
            this.maxResponseBufferSize = maxResponseBufferSize;
            return self();
        }

        /**
         *
         *
         * @param name
         * @return updated builder instance
         * @see #name()
         */
        public BUILDER name(String name) {
            Objects.requireNonNull(name);
            this.name = name;
            return self();
        }

        /**
         * Maximum size of any message in bytes.
         * Defaults to {@value #DEFAULT_MAX_MESSAGE_SIZE}.
         *
         * @return the max message size
         */
        public int maxMessageSize() {
            return maxMessageSize;
        }

        /**
         * Maximum size of the response buffer in bytes.
         * Defaults to {@value #DEFAULT_MAX_RESPONSE_BUFFER_SIZE}.
         *
         * @return the max response buffer size
         */
        public int maxResponseBufferSize() {
            return maxResponseBufferSize;
        }

        /**
         *
         *
         * @return the name
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * If this instance was configured, this would be the config instance used.
         *
         * @return config node used to configure this builder, or empty if not configured
         */
        public Optional<Config> config() {
            return Optional.ofNullable(config);
        }

        @Override
        public String toString() {
            return "PbjConfigBuilder{"
                    + "maxMessageSize=" + maxMessageSize + ","
                    + "maxResponseBufferSize=" + maxResponseBufferSize + ","
                    + "name=" + name
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (name == null) {
                collector.fatal(getClass(), "Property \"name\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class PbjConfigImpl implements PbjConfig {

            private final int maxMessageSize;
            private final int maxResponseBufferSize;
            private final String name;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected PbjConfigImpl(BuilderBase<?, ?> builder) {
                this.maxMessageSize = builder.maxMessageSize();
                this.maxResponseBufferSize = builder.maxResponseBufferSize();
                this.name = builder.name().get();
            }

            @Override
            public int maxMessageSize() {
                return maxMessageSize;
            }

            @Override
            public int maxResponseBufferSize() {
                return maxResponseBufferSize;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String toString() {
                return "PbjConfig{"
                        + "maxMessageSize=" + maxMessageSize + ","
                        + "maxResponseBufferSize=" + maxResponseBufferSize + ","
                        + "name=" + name
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof PbjConfig other)) {
                    return false;
                }
                return maxMessageSize == other.maxMessageSize()
                    && maxResponseBufferSize == other.maxResponseBufferSize()
                    && Objects.equals(name, other.name());
            }

            @Override
            public int hashCode() {
                return Objects.hash(maxMessageSize, maxResponseBufferSize, name);
            }

        }

    }

    /**
     * Fluent API builder for {@link PbjConfig}.
     */
    class Builder extends BuilderBase<Builder, PbjConfig> implements io.helidon.common.Builder<Builder, PbjConfig> {

        private Builder() {
        }

        @Override
        public PbjConfig buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new PbjConfigImpl(this);
        }

        @Override
        public PbjConfig build() {
            return buildPrototype();
        }

    }

}
