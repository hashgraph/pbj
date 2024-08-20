package com.hedera.pbj.runtime.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GrpcStatusTest {
    /**
     * The specific ordinal values must match exactly the expectations as set forth in the specification. This test
     * "fixes" them in place. Any changes to the order in which fields are placed in GrpcStatus will break this test,
     * as the ordinal values will change. The test MUST NOT BE adapted to match.
     */
    @Test
    void statusCodesAreSpecific() {
        assertThat(GrpcStatus.OK.ordinal()).isZero();
        assertThat(GrpcStatus.CANCELLED.ordinal()).isEqualTo(1);
        assertThat(GrpcStatus.UNKNOWN.ordinal()).isEqualTo(2);
        assertThat(GrpcStatus.INVALID_ARGUMENT.ordinal()).isEqualTo(3);
        assertThat(GrpcStatus.DEADLINE_EXCEEDED.ordinal()).isEqualTo(4);
        assertThat(GrpcStatus.NOT_FOUND.ordinal()).isEqualTo(5);
        assertThat(GrpcStatus.ALREADY_EXISTS.ordinal()).isEqualTo(6);
        assertThat(GrpcStatus.PERMISSION_DENIED.ordinal()).isEqualTo(7);
        assertThat(GrpcStatus.RESOURCE_EXHAUSTED.ordinal()).isEqualTo(8);
        assertThat(GrpcStatus.FAILED_PRECONDITION.ordinal()).isEqualTo(9);
        assertThat(GrpcStatus.ABORTED.ordinal()).isEqualTo(10);
        assertThat(GrpcStatus.OUT_OF_RANGE.ordinal()).isEqualTo(11);
        assertThat(GrpcStatus.UNIMPLEMENTED.ordinal()).isEqualTo(12);
        assertThat(GrpcStatus.INTERNAL.ordinal()).isEqualTo(13);
        assertThat(GrpcStatus.UNIMPLEMENTED.ordinal()).isEqualTo(14);
        assertThat(GrpcStatus.DATA_LOSS.ordinal()).isEqualTo(15);
        assertThat(GrpcStatus.UNAUTHENTICATED.ordinal()).isEqualTo(16);
    }
}
