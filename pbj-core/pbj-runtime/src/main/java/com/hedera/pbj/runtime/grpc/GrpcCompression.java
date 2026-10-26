// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import static java.lang.System.Logger.Level.DEBUG;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A registry of GRPC compressors and decompressors (commonly - transformers) supported by PBJ GRPC server and client.
 *
 * <p>Registration operations are serialized. Use {@link #registerCompressorIfAbsent} and
 * {@link #registerDecompressorIfAbsent} to install defaults atomically without replacing an application registration;
 * a separate lookup followed by registration does not provide that guarantee. Callers do not need to synchronize
 * externally when using these operations. Compressor and decompressor registrations are independent operations.
 */
public class GrpcCompression {
    private static final System.Logger LOGGER = System.getLogger(GrpcCompression.class.getName());

    /** The default encoding name that doesn't use compression. */
    public static final String IDENTITY = "identity";

    /** A compressor of Bytes. */
    public interface Compressor {
        /** Compress given Bytes and return the compressed Bytes. */
        Bytes compress(Bytes bytes);
    }

    /** A decompressor of Bytes. */
    public interface Decompressor {
        /** Decompress given Bytes and return the decompressed Bytes. */
        Bytes decompress(Bytes bytes);

        /**
         * Decompress a message marked compressed, rejecting output larger than {@code maxOutputSize} while decoding.
         * Transport implementations use this overload so compressed input cannot cause unbounded output allocation.
         *
         * <p>Custom decompressors must override this method and enforce the limit before allocating or retaining
         * output beyond it. The default deliberately rejects compressed messages instead of falling back to the
         * unbounded overload. Streaming implementations can use {@link GrpcCompression#readDecompressed}.
         *
         * @param bytes the compressed wire payload
         * @param maxOutputSize maximum decompressed message size in bytes
         * @return the decompressed message
         * @throws GrpcException if bounded decompression is unsupported or the output limit is exceeded
         */
        default Bytes decompress(final Bytes bytes, final int maxOutputSize) {
            throw new GrpcException(GrpcStatus.UNIMPLEMENTED, "Decompressor does not support bounded output");
        }
    }

    /** Convenience interface to implement both Compressor and Decompressor at once. */
    public interface GrpcTransformer extends Compressor, Decompressor {}

    private static class IdentityGrpcTransformer implements GrpcTransformer {
        private static final String NAME = IDENTITY;
        private static final GrpcTransformer INSTANCE = new IdentityGrpcTransformer();

        @Override
        public Bytes compress(Bytes bytes) {
            return bytes;
        }

        @Override
        public Bytes decompress(Bytes bytes) {
            return bytes;
        }

        @Override
        public Bytes decompress(final Bytes bytes, final int maxOutputSize) {
            throw new GrpcException(GrpcStatus.INTERNAL, "Compressed message requires a non-identity grpc-encoding");
        }
    }

    private static class GzipGrpcTransformer implements GrpcTransformer {
        private static final String NAME = "gzip";
        private static final GrpcTransformer INSTANCE = new GzipGrpcTransformer();

        @Override
        public Bytes compress(Bytes bytes) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    GZIPOutputStream gos = new GZIPOutputStream(baos)) {
                bytes.writeTo(gos);
                gos.finish();
                return Bytes.wrap(baos.toByteArray());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Bytes decompress(Bytes bytes) {
            return decompress(bytes, Integer.MAX_VALUE);
        }

        @Override
        public Bytes decompress(final Bytes bytes, final int maxOutputSize) {
            try (GZIPInputStream gis = new GZIPInputStream(bytes.toInputStream())) {
                return readDecompressed(gis, maxOutputSize);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Reads a decoded stream without retaining more than {@code maxOutputSize} bytes of message data. At most one
     * additional decoded byte is requested to distinguish an exact-size message from an oversized message. The
     * caller owns the stream and is responsible for closing it.
     *
     * @param input a stream producing decoded bytes
     * @param maxOutputSize the maximum decoded message size
     * @return the complete decoded message
     * @throws IOException if reading fails
     * @throws GrpcException with {@link GrpcStatus#RESOURCE_EXHAUSTED} if the decoded message is too large
     */
    public static Bytes readDecompressed(final InputStream input, final int maxOutputSize) throws IOException {
        if (maxOutputSize < 0) {
            throw new IllegalArgumentException("maxOutputSize must not be negative");
        }
        final byte[] buffer = new byte[(int) Math.min(8192L, (long) maxOutputSize + 1)];
        try (final ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8192, maxOutputSize))) {
            int length;
            while ((length = input.read(
                            buffer, 0, (int) Math.min(buffer.length, (long) maxOutputSize - output.size() + 1)))
                    != -1) {
                if (length > maxOutputSize - output.size()) {
                    throw new GrpcException(
                            GrpcStatus.RESOURCE_EXHAUSTED, "Decompressed message exceeds maximum allowed size");
                }
                output.write(buffer, 0, length);
            }
            return Bytes.wrap(output.toByteArray());
        }
    }

    /**
     * Decodes a compressed wire message using a bounded decompressor and verifies its result. Implementations must
     * still enforce the bound while decoding; this final check detects custom implementations that violate it.
     *
     * @param decompressor the selected decompressor
     * @param bytes the compressed message
     * @param maxOutputSize maximum decoded size
     * @return the decoded message
     */
    public static Bytes decompress(final Decompressor decompressor, final Bytes bytes, final int maxOutputSize) {
        if (maxOutputSize < 0) {
            throw new IllegalArgumentException("maxOutputSize must not be negative");
        }
        final Bytes decoded = decompressor.decompress(bytes, maxOutputSize);
        if (decoded.length() > maxOutputSize) {
            throw new GrpcException(GrpcStatus.RESOURCE_EXHAUSTED, "Decompressed message exceeds maximum allowed size");
        }
        return decoded;
    }

    private static final Map<String, Compressor> COMPRESSOR_MAP = new HashMap<>();
    private static final Map<String, Decompressor> DECOMPRESSOR_MAP = new HashMap<>();

    /** Register a Compressor, potentially overwriting an existing registration for `name`. */
    public static synchronized void registerCompressor(String name, Compressor compressor) {
        COMPRESSOR_MAP.put(name, compressor);
    }

    /** Register a Decompressor, potentially overwriting an existing registration for `name`. */
    public static synchronized void registerDecompressor(String name, Decompressor decompressor) {
        DECOMPRESSOR_MAP.put(name, decompressor);
    }

    /**
     * Atomically registers a compressor unless one is already registered under the same name.
     *
     * @param name the encoding name
     * @param compressor the default compressor to install
     * @return true if this call installed the compressor, or false if an existing registration was retained
     */
    public static synchronized boolean registerCompressorIfAbsent(String name, Compressor compressor) {
        return COMPRESSOR_MAP.putIfAbsent(name, compressor) == null;
    }

    /**
     * Atomically registers a decompressor unless one is already registered under the same name.
     *
     * @param name the encoding name
     * @param decompressor the default decompressor to install
     * @return true if this call installed the decompressor, or false if an existing registration was retained
     */
    public static synchronized boolean registerDecompressorIfAbsent(String name, Decompressor decompressor) {
        return DECOMPRESSOR_MAP.putIfAbsent(name, decompressor) == null;
    }

    static {
        registerCompressor(IdentityGrpcTransformer.NAME, IdentityGrpcTransformer.INSTANCE);
        registerCompressor(GzipGrpcTransformer.NAME, GzipGrpcTransformer.INSTANCE);

        registerDecompressor(IdentityGrpcTransformer.NAME, IdentityGrpcTransformer.INSTANCE);
        registerDecompressor(GzipGrpcTransformer.NAME, GzipGrpcTransformer.INSTANCE);
    }

    /** Return names of all known compressors. */
    public static Set<String> getCompressorNames() {
        return Collections.unmodifiableSet(COMPRESSOR_MAP.keySet());
    }

    /** Return a known Compressor by its name, or null if unknown. */
    public static Compressor getCompressor(String name) {
        return COMPRESSOR_MAP.get(name);
    }

    /** Return names of all known decompressors. */
    public static Set<String> getDecompressorNames() {
        return Collections.unmodifiableSet(DECOMPRESSOR_MAP.keySet());
    }

    /** Return a known Decompressor by its name, or null if unknown. */
    public static Decompressor getDecompressor(String name) {
        return DECOMPRESSOR_MAP.get(name);
    }

    /**
     * A utility method to help process "grpc-encoding" header. Note that it should normally contain just a single
     * value. Multiple values result in an exception. No values default to the "identity" decompressor.
     * @param encodingList all values of the "grpc-encoding" header as received from remote peer
     * @return a Decompressor that should be used to decompress bytes received from the peer
     * @throws IllegalStateException if the Decompressor cannot be determined
     */
    public static Decompressor determineDecompressor(List<String> encodingList) {
        if (encodingList == null || encodingList.isEmpty()) {
            if (GrpcCompression.getDecompressor(GrpcCompression.IDENTITY) != null) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(
                            DEBUG, "GrpcCompression.determineDecompressor: identity (by default) for " + encodingList);
                }
                return GrpcCompression.getDecompressor(GrpcCompression.IDENTITY);
            } else {
                throw new IllegalStateException(
                        "GRPC peer didn't provide grpc-encoding header and 'identity' is unsupported, only the following are supported: "
                                + DECOMPRESSOR_MAP.keySet());
            }
        } else if (encodingList.size() > 1) {
            throw new IllegalStateException("GRPC peer specified multiple encodings at once: " + encodingList);
        } else {
            final String encoding = encodingList.get(0);
            if (GrpcCompression.getDecompressor(encoding) != null) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "GrpcCompression.determineDecompressor: " + encoding + " for " + encodingList);
                }
                return GrpcCompression.getDecompressor(encoding);
            } else {
                throw new IllegalStateException("GRPC peer uses an unsupported encoding: '" + encoding
                        + "' while only the following are supported: " + DECOMPRESSOR_MAP.keySet());
            }
        }
    }

    /**
     * A utility method to help process "grpc-accept-encoding" header and determine the name of a Compressor.
     * @param acceptEncoding "grpc-accept-encoding" header as received from remote peer
     * @param encoding a preferred encoding per the local peer configuration
     * @return the name of a Compressor to use for sending data to the remote peer
     */
    public static String determineCompressorName(List<String> acceptEncoding, String encoding) {
        final List<String> supportedAcceptEncodings = acceptEncoding.stream()
                .filter(ae ->
                        COMPRESSOR_MAP.keySet().stream().anyMatch(sae -> ae.equals(sae) || ae.startsWith(sae + ";")))
                .toList();

        if (supportedAcceptEncodings.isEmpty()) {
            // This seems safer than erroring-out outright because: 1) the header may have been missing in
            // the first place, so a client may have listed only "special" encodings, such as "gzip" here,
            // and 2) "identity" is likely supported. In the worst case we'll catch an error
            // when we send a reply back to the client.
            // Note that it would be unsafe to force using the `encoding` here because if it's not "identity",
            // then it may indeed be unsupported by the remote peer.
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(
                        DEBUG,
                        "GrpcCompression.determineCompressorName: identity (by default) with preferred " + encoding
                                + ", acceptEncoding: " + acceptEncoding + ", and supportedAcceptEncoding: "
                                + supportedAcceptEncodings);
            }
            return GrpcCompression.IDENTITY;
        }

        if (supportedAcceptEncodings.contains(encoding)) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(
                        DEBUG,
                        "GrpcCompression.determineCompressorName: " + encoding + " (as preferred)"
                                + ", acceptEncoding: "
                                + acceptEncoding + ", and supportedAcceptEncoding: "
                                + supportedAcceptEncodings);
            }
            return encoding;
        }

        // FUTURE WORK: might be smarter and choose "the best" one.
        // But currently we don't have a definition for what is "the best".
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(
                    DEBUG,
                    "GrpcCompression.determineCompressorName: " + supportedAcceptEncodings.get(0) + " with preferred "
                            + encoding
                            + ", acceptEncoding: " + acceptEncoding + ", and supportedAcceptEncoding: "
                            + supportedAcceptEncodings);
        }
        return supportedAcceptEncodings.get(0);
    }
}
