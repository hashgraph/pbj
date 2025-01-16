// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GrpcExceptionTest {
    @ParameterizedTest
    @EnumSource(GrpcStatus.class)
    void testStatus(final GrpcStatus expected) {
        // If it is OK, then it will actually fail the test, so do not run the test in that case.
        Assumptions.assumeThat(expected).isNotEqualTo(GrpcStatus.OK);
        // A GrpcException that is given any status should return that status from the status() method.
        GrpcException grpcException = new GrpcException(expected);
        assertThat(grpcException.status()).isEqualTo(expected);
    }

    @Test
    void testOkStatusThrows() {
        // If the status is OK, then the constructor should throw an IllegalArgumentException.
        //noinspection ThrowableNotThrown
        assertThatThrownBy(() -> new GrpcException(GrpcStatus.OK))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("status cannot be OK");
    }

    @Test
    void messageIsNullByDefault() {
        // If the message is not specified, then the getMessage() method should return null.
        GrpcException grpcException = new GrpcException(GrpcStatus.UNKNOWN);
        assertThat(grpcException.getMessage()).isNull();
    }

    @Test
    void messageIsSet() {
        // If the message is specified, then the getMessage() method should return that message.
        GrpcException grpcException = new GrpcException(GrpcStatus.UNKNOWN, "hello");
        assertThat(grpcException.getMessage()).isEqualTo("hello");

        grpcException = new GrpcException(GrpcStatus.UNKNOWN, "world", null);
        assertThat(grpcException.getMessage()).isEqualTo("world");
    }

    @Test
    void causeIsNullByDefault() {
        // If the cause is not specified, then the getCause() method should return null.
        GrpcException grpcException = new GrpcException(GrpcStatus.UNKNOWN);
        assertThat(grpcException.getCause()).isNull();
    }

    @Test
    void causeIsSet() {
        // If the cause is specified, then the getCause() method should return that cause.
        Throwable cause = new Throwable();
        GrpcException grpcException = new GrpcException(GrpcStatus.UNKNOWN, cause);
        assertThat(grpcException.getCause()).isEqualTo(cause);

        grpcException = new GrpcException(GrpcStatus.UNKNOWN, null, cause);
        assertThat(grpcException.getCause()).isEqualTo(cause);
    }
}
