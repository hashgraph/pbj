// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GrpcCompressionTest {
    @Test
    void gzipAcceptsTheExactDecodedLimitAndRejectsOneByteMore() {
        final Bytes payload = Bytes.wrap(new byte[16_384]);
        final Bytes compressed = GrpcCompression.getCompressor("gzip").compress(payload);
        final GrpcCompression.Decompressor decompressor = GrpcCompression.getDecompressor("gzip");
        assertEquals(payload, GrpcCompression.decompress(decompressor, compressed, 16_384));
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.decompress(decompressor, compressed, 16_383));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
    }

    @Test
    void gzipRejectsHighlyCompressedMessages() {
        final Bytes compressed = GrpcCompression.getCompressor("gzip").compress(Bytes.wrap(new byte[8 * 1024 * 1024]));
        final GrpcException failure = assertThrows(
                GrpcException.class,
                () -> GrpcCompression.decompress(GrpcCompression.getDecompressor("gzip"), compressed, 10 * 1024));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
    }

    @Test
    void gzipHandlesAnEmptyMessageWithZeroLimit() {
        final Bytes compressed = GrpcCompression.getCompressor("gzip").compress(Bytes.EMPTY);
        assertEquals(Bytes.EMPTY, GrpcCompression.decompress(GrpcCompression.getDecompressor("gzip"), compressed, 0));
    }

    @Test
    void readsAtMostOneByteBeyondTheDecodedLimit() throws Exception {
        final AtomicInteger bytesRead = new AtomicInteger();
        final InputStream decoded = new ByteArrayInputStream(new byte[1024 * 1024]) {
            @Override
            public synchronized int read(final byte[] bytes, final int offset, final int length) {
                final int read = super.read(bytes, offset, length);
                if (read > 0) {
                    bytesRead.addAndGet(read);
                }
                return read;
            }
        };
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.readDecompressed(decoded, 10_000));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
        assertEquals(10_001, bytesRead.get());
    }

    @Test
    void rejectsLegacyCustomDecompressorsWithoutCallingTheUnboundedMethod() {
        final AtomicBoolean called = new AtomicBoolean();
        final GrpcCompression.Decompressor legacy = bytes -> {
            called.set(true);
            return Bytes.wrap(new byte[1024 * 1024]);
        };
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.decompress(legacy, Bytes.EMPTY, 100));
        assertEquals(GrpcStatus.UNIMPLEMENTED, failure.status());
        assertFalse(called.get());
    }

    @Test
    void rejectsCustomDecompressorsThatReturnMoreThanTheRequestedBound() {
        final GrpcCompression.Decompressor invalid = new GrpcCompression.Decompressor() {
            @Override
            public Bytes decompress(final Bytes bytes) {
                throw new AssertionError("Unbounded method must not be called");
            }

            @Override
            public Bytes decompress(final Bytes bytes, final int maxOutputSize) {
                return Bytes.wrap(new byte[maxOutputSize + 1]);
            }
        };
        final GrpcException failure =
                assertThrows(GrpcException.class, () -> GrpcCompression.decompress(invalid, Bytes.EMPTY, 100));
        assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
    }

    @Test
    void identityDoesNotAcceptAMessageMarkedCompressed() {
        final GrpcException failure = assertThrows(
                GrpcException.class,
                () -> GrpcCompression.decompress(
                        GrpcCompression.getDecompressor(GrpcCompression.IDENTITY), Bytes.EMPTY, 100));
        assertEquals(GrpcStatus.INTERNAL, failure.status());
    }

    @Test
    void registerIfAbsentKeepsTheExistingRegistration() {
        final String name = "if-absent-" + System.nanoTime();

        final GrpcCompression.Compressor compressor = bytes -> bytes;
        assertTrue(GrpcCompression.registerCompressorIfAbsent(name, compressor));
        assertFalse(GrpcCompression.registerCompressorIfAbsent(name, bytes -> Bytes.EMPTY));
        assertSame(compressor, GrpcCompression.getCompressor(name));

        final GrpcCompression.Decompressor decompressor = bytes -> bytes;
        assertTrue(GrpcCompression.registerDecompressorIfAbsent(name, decompressor));
        assertFalse(GrpcCompression.registerDecompressorIfAbsent(name, bytes -> Bytes.EMPTY));
        assertSame(decompressor, GrpcCompression.getDecompressor(name));
    }

    @Test
    void concurrentDefaultsInstallExactlyOneImplementationPerEncoding() throws Exception {
        final String name = "concurrent-default-" + System.nanoTime();
        final TestTransformer first = new TestTransformer();
        final TestTransformer second = new TestTransformer();
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<RegistrationResult> firstResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(2, TimeUnit.SECONDS));
                return new RegistrationResult(
                        GrpcCompression.registerCompressorIfAbsent(name, first),
                        GrpcCompression.registerDecompressorIfAbsent(name, first));
            });
            final Future<RegistrationResult> secondResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(2, TimeUnit.SECONDS));
                return new RegistrationResult(
                        GrpcCompression.registerCompressorIfAbsent(name, second),
                        GrpcCompression.registerDecompressorIfAbsent(name, second));
            });
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            final RegistrationResult firstRegistration = firstResult.get(2, TimeUnit.SECONDS);
            final RegistrationResult secondRegistration = secondResult.get(2, TimeUnit.SECONDS);
            assertTrue(firstRegistration.compressorInstalled() ^ secondRegistration.compressorInstalled());
            assertTrue(firstRegistration.decompressorInstalled() ^ secondRegistration.decompressorInstalled());
            assertSame(firstRegistration.compressorInstalled() ? first : second, GrpcCompression.getCompressor(name));
            assertSame(
                    firstRegistration.decompressorInstalled() ? first : second, GrpcCompression.getDecompressor(name));
        }
    }

    @Test
    void concurrentDefaultInstallationNeverReplacesAnExplicitApplicationRegistration() throws Exception {
        try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int iteration = 0; iteration < 16; iteration++) {
                final String name = "concurrent-application-" + System.nanoTime() + "-" + iteration;
                final TestTransformer application = new TestTransformer();
                final TestTransformer defaults = new TestTransformer();
                final CountDownLatch ready = new CountDownLatch(2);
                final CountDownLatch start = new CountDownLatch(1);
                final Future<?> applicationResult = executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    GrpcCompression.registerCompressor(name, application);
                    GrpcCompression.registerDecompressor(name, application);
                    return null;
                });
                final Future<?> defaultResult = executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    GrpcCompression.registerCompressorIfAbsent(name, defaults);
                    GrpcCompression.registerDecompressorIfAbsent(name, defaults);
                    return null;
                });
                assertTrue(ready.await(2, TimeUnit.SECONDS));
                start.countDown();
                applicationResult.get(2, TimeUnit.SECONDS);
                defaultResult.get(2, TimeUnit.SECONDS);
                assertSame(application, GrpcCompression.getCompressor(name));
                assertSame(application, GrpcCompression.getDecompressor(name));
            }
        }
    }

    private record RegistrationResult(boolean compressorInstalled, boolean decompressorInstalled) {}

    private static final class TestTransformer implements GrpcCompression.GrpcTransformer {
        @Override
        public Bytes compress(final Bytes bytes) {
            return bytes;
        }

        @Override
        public Bytes decompress(final Bytes bytes) {
            return bytes;
        }
    }
}
