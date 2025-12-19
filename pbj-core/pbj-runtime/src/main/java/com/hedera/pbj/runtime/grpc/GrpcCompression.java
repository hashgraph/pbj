// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.grpc;

import static java.lang.System.Logger.Level.DEBUG;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A registry of GRPC compressors and decompressors (commonly - transformers) supported by PBJ GRPC server and client.
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
            byte[] buffer = new byte[1024];
            try (GZIPInputStream gis = new GZIPInputStream(bytes.toInputStream());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                int len;
                while ((len = gis.read(buffer, 0, buffer.length)) > 0) {
                    baos.write(buffer, 0, len);
                }
                return Bytes.wrap(baos.toByteArray());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final Map<String, Compressor> COMPRESSOR_MAP = Map.of(
            IdentityGrpcTransformer.NAME, IdentityGrpcTransformer.INSTANCE,
            GzipGrpcTransformer.NAME, GzipGrpcTransformer.INSTANCE);
    private static final Map<String, Decompressor> DECOMPRESSOR_MAP = Map.of(
            IdentityGrpcTransformer.NAME, IdentityGrpcTransformer.INSTANCE,
            GzipGrpcTransformer.NAME, GzipGrpcTransformer.INSTANCE);

    /** Return names of all known compressors. */
    public static Set<String> getCompressorNames() {
        return COMPRESSOR_MAP.keySet();
    }

    /** Return a known Compressor by its name, or null if unknown. */
    public static Compressor getCompressor(String name) {
        return COMPRESSOR_MAP.get(name);
    }

    /** Return names of all known decompressors. */
    public static Set<String> getDecompressorNames() {
        return DECOMPRESSOR_MAP.keySet();
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
                                + GrpcCompression.getDecompressorNames());
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
                        + "' while only the following are supported: " + GrpcCompression.getDecompressorNames());
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
                .filter(ae -> GrpcCompression.getCompressorNames().stream()
                        .anyMatch(sae -> ae.equals(sae) || ae.startsWith(sae + ";")))
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
