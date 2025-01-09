// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Test for default methods on {@link SequentialData}. */
final class SequentialDataTest {

    private static Stream<Arguments> provideArgumentsForRemaining() {
        return Stream.of(
                Arguments.of(0, 0, 0), // Empty buffer / stream
                Arguments.of(0, 1, 1), // Single byte available
                Arguments.of(1, 1, 0), // All bytes read
                Arguments.of(1, 2, 1), // One byte remaining
                Arguments.of(1, 3, 2), // Two bytes remaining
                Arguments.of(-1, -1, 0) // Negatives? (error that we handle)
                );
    }

    @ParameterizedTest(name = "position={0}, limit={1}")
    @MethodSource("provideArgumentsForRemaining")
    void hasRemaining(final long position, final long limit, final long expected) {
        // Given a SequentialData with the specified position and limit
        final var data = new StubSequentialData(position, limit);
        // When we check if there are remaining bytes, we find the expected result
        assertThat(data.hasRemaining()).isEqualTo(expected > 0);
    }

    @ParameterizedTest(name = "position={0}, limit={1}, expected={2}")
    @MethodSource("provideArgumentsForRemaining")
    void remaining(final long position, final long limit, final long expected) {
        // Given a SequentialData with the specified position and limit
        final var data = new StubSequentialData(position, limit);
        // When we check if there are remaining bytes, we find the expected result
        assertThat(data.remaining()).isEqualTo(expected);
    }

    // Simple stub implementation of SequentialData for testing purposes
    private record StubSequentialData(long position, long limit) implements SequentialData {
        @Override
        public void limit(long limit) {
            throw new UnsupportedOperationException("Not implemented in this test");
        }

        @Override
        public void skip(long count) {
            throw new UnsupportedOperationException("Not implemented in this test");
        }

        @Override
        public long capacity() {
            return Long.MAX_VALUE;
        }
    }
}
