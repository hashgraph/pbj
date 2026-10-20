// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceInterfaceTest {
    private static ServiceInterface.RequestOptions options(final String contentType) {
        return new ServiceInterface.RequestOptions() {
            @Override
            public Optional<String> authority() {
                return Optional.empty();
            }

            @Override
            public String contentType() {
                return contentType;
            }
        };
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "application/grpc",
                "application/grpc+proto",
                "application/grpc-web",
                "application/grpc-web+proto",
                "application/grpc-web-text",
                "application/grpc-web-text+proto"
            })
    void protobufContentTypes(final String contentType) {
        assertThat(options(contentType).isProtobuf()).isTrue();
        assertThat(options(contentType).isJson()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/grpc+json", "application/grpc-web+json"})
    void jsonContentTypes(final String contentType) {
        assertThat(options(contentType).isJson()).isTrue();
        assertThat(options(contentType).isProtobuf()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/grpc+string", "application/json", ""})
    void otherContentTypes(final String contentType) {
        assertThat(options(contentType).isProtobuf()).isFalse();
        assertThat(options(contentType).isJson()).isFalse();
    }

    @Test
    void nullContentTypeIsNeitherProtobufNorJson() {
        assertThat(options(null).isProtobuf()).isFalse();
        assertThat(options(null).isJson()).isFalse();
    }

    @Test
    void methodTypeDefaultsToUnknown() {
        final ServiceInterface.Method method = () -> "legacy";
        assertThat(method.type()).isEqualTo(ServiceInterface.MethodType.UNKNOWN);
    }
}
