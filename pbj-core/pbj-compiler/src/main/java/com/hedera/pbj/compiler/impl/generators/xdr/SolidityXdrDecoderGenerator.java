// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.xdr;

import static com.hedera.pbj.compiler.impl.Common.camelToUpperSnake;
import static com.hedera.pbj.compiler.impl.Common.snakeToCamel;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a Solidity XDR decoder library for a protobuf message where all fields are fixed-size scalars.
 * Messages with variable-length fields (string, bytes, repeated, oneof, map, message) are skipped.
 */
public final class SolidityXdrDecoderGenerator {

    /** XDR presence flag size in bytes (always 4). */
    private static final int PRESENCE_SIZE = 4;

    private SolidityXdrDecoderGenerator() {
        // static utility class
    }

    /**
     * Information about a single fixed-size field for Solidity generation.
     *
     * @param name         proto field name (snake_case)
     * @param fieldType    proto field type
     * @param presenceOffset byte offset of the presence flag from the start of the struct
     * @param valueOffset  byte offset of the field value from the start of the struct
     * @param valueSize    size of the value in bytes (4 or 8)
     */
    private record FieldInfo(
            String name,
            Field.FieldType fieldType,
            int presenceOffset,
            int valueOffset,
            int valueSize) {

        String camelName() {
            return snakeToCamel(name, false);
        }

        String upperSnakeName() {
            return camelToUpperSnake(camelName());
        }

        /** Solidity type for the struct field. */
        String solidityType() {
            return switch (fieldType) {
                case INT32, SINT32, SFIXED32 -> "int32";
                case UINT32, FIXED32 -> "uint32";
                case INT64, SINT64, SFIXED64 -> "int64";
                case UINT64, FIXED64 -> "uint64";
                case FLOAT -> "bytes4";
                case DOUBLE -> "bytes8";
                case BOOL -> "bool";
                case ENUM -> "uint32";
                default -> throw new IllegalStateException("Not a fixed-size type: " + fieldType);
            };
        }

        /** The right-shift amount to extract the value from a calldataload result. */
        int valueShift() {
            return 256 - valueSize * 8;
        }

        /** Internal helper method name for reading this field type. */
        String helperMethod() {
            return switch (fieldType) {
                case INT32, SINT32, SFIXED32 -> "_readInt32";
                case UINT32, FIXED32, ENUM -> "_readUint32";
                case INT64, SINT64, SFIXED64 -> "_readInt64";
                case UINT64, FIXED64 -> "_readUint64";
                case FLOAT -> "_readBytes4";
                case DOUBLE -> "_readBytes8";
                case BOOL -> "_readBool";
                default -> throw new IllegalStateException("Not a fixed-size type: " + fieldType);
            };
        }
    }

    /**
     * Returns true if a field type is fixed-size and can be Solidity-decoded.
     */
    private static boolean isFixedSize(final Field.FieldType type) {
        return switch (type) {
            case INT32, SINT32, SFIXED32,
                    UINT32, FIXED32,
                    INT64, SINT64, SFIXED64,
                    UINT64, FIXED64,
                    FLOAT, DOUBLE, BOOL, ENUM -> true;
            default -> false;
        };
    }

    /**
     * Returns the XDR wire value size (excluding presence flag) for a fixed-size type.
     */
    private static int xdrValueSize(final Field.FieldType type) {
        return switch (type) {
            case INT32, SINT32, SFIXED32, UINT32, FIXED32, FLOAT, BOOL, ENUM -> 4;
            case INT64, SINT64, SFIXED64, UINT64, FIXED64, DOUBLE -> 8;
            default -> throw new IllegalStateException("Not a fixed-size type: " + type);
        };
    }

    /**
     * Generate a Solidity XDR decoder library for the given message and write it to solidityOutputDir.
     * If the message contains non-fixed-size fields, logs a note to System.err and skips those fields.
     *
     * @param msgDef           the ANTLR message definition context
     * @param solidityOutputDir the root output directory for .sol files
     * @param lookupHelper     contextual lookup helper for package/class resolution
     */
    public static void generate(
            final Protobuf3Parser.MessageDefContext msgDef,
            final File solidityOutputDir,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String messageName = msgDef.messageName().getText();
        final String javaPackage = lookupHelper.getPackage(FileType.MODEL, msgDef);

        // Build the list of fixed-size fields with their offsets
        final List<FieldInfo> fields = new ArrayList<>();
        boolean hasSkippedFields = false;
        boolean firstSkipSeen = false;
        boolean hasFixedAfterSkip = false;
        int currentOffset = 0;

        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.field() != null && item.field().fieldName() != null) {
                final SingleField field = new SingleField(item.field(), lookupHelper);
                if (field.repeated() || !isFixedSize(field.type())) {
                    hasSkippedFields = true;
                    firstSkipSeen = true;
                    continue;
                }
                if (firstSkipSeen) {
                    hasFixedAfterSkip = true;
                }
                final int valueSize = xdrValueSize(field.type());
                final int presenceOffset = currentOffset;
                final int valueOffset = currentOffset + PRESENCE_SIZE;
                fields.add(new FieldInfo(field.name(), field.type(), presenceOffset, valueOffset, valueSize));
                currentOffset += PRESENCE_SIZE + valueSize;
            } else if (item.oneof() != null || item.mapField() != null) {
                hasSkippedFields = true;
                firstSkipSeen = true;
            }
        }

        if (hasSkippedFields && hasFixedAfterSkip) {
            System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                    + " has fixed-size fields after variable-length fields; cannot compute static offsets — skipping.");
            return;
        }

        if (hasSkippedFields) {
            System.err.println(
                    "SolidityXdrDecoderGenerator: message " + messageName + " has variable-length fields; skipping them.");
        }

        if (fields.isEmpty()) {
            // Nothing to generate for a message with no fixed-size fields
            return;
        }

        final int encodedSize = currentOffset;
        final String libraryName = messageName + "Xdr";

        // Determine which helper methods are actually needed
        final boolean needsInt32 = fields.stream().anyMatch(f -> "_readInt32".equals(f.helperMethod()));
        final boolean needsUint32 = fields.stream().anyMatch(f -> "_readUint32".equals(f.helperMethod()));
        final boolean needsInt64 = fields.stream().anyMatch(f -> "_readInt64".equals(f.helperMethod()));
        final boolean needsUint64 = fields.stream().anyMatch(f -> "_readUint64".equals(f.helperMethod()));
        final boolean needsBytes4 = fields.stream().anyMatch(f -> "_readBytes4".equals(f.helperMethod()));
        final boolean needsBytes8 = fields.stream().anyMatch(f -> "_readBytes8".equals(f.helperMethod()));
        final boolean needsBool = fields.stream().anyMatch(f -> "_readBool".equals(f.helperMethod()));

        final StringBuilder sb = new StringBuilder();

        // File header
        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("pragma solidity ^0.8.20;\n\n");

        // Library doc
        sb.append("/// @title ").append(libraryName).append("\n");
        sb.append("/// @notice Pure XDR decoder for ").append(messageName).append(" calldata.\n");
        sb.append("///         Generated by PBJ \u2014 do not edit.\n");
        sb.append("library ").append(libraryName).append(" {\n\n");

        // Wire layout comment
        sb.append("    // XDR wire layout (all fields present):\n");
        sb.append("    //   offset  size  field\n");
        for (final FieldInfo f : fields) {
            sb.append(String.format("    //   %6d  %4d  presence flag for %s%n", f.presenceOffset(), PRESENCE_SIZE, f.name()));
            sb.append(String.format("    //   %6d  %4d  %s (%s)%n", f.valueOffset(), f.valueSize(), f.name(), xdrTypeComment(f.fieldType())));
        }
        sb.append(String.format("    // Total: %d bytes%n", encodedSize));
        sb.append("\n");

        // Struct definition
        sb.append("    /// @notice Fully decoded ").append(messageName).append(" struct.\n");
        sb.append("    struct ").append(messageName).append(" {\n");
        for (final FieldInfo f : fields) {
            sb.append("        ").append(f.solidityType()).append(" ").append(f.camelName()).append(";\n");
        }
        sb.append("    }\n\n");

        // Constant offsets
        sb.append("    // --- Constant field offsets (valid when ALL preceding fields are present) ---\n");
        for (final FieldInfo f : fields) {
            sb.append(String.format("    uint256 internal constant OFFSET_%s_PRESENCE = %d;%n",
                    f.upperSnakeName(), f.presenceOffset()));
            sb.append(String.format("    uint256 internal constant OFFSET_%s = %d;%n",
                    f.upperSnakeName(), f.valueOffset()));
        }
        sb.append("\n");

        // ENCODED_SIZE
        sb.append("    /// @notice Total encoded size when all fields are present (bytes).\n");
        sb.append(String.format("    uint256 internal constant ENCODED_SIZE = %d;%n", encodedSize));
        sb.append("\n");

        // decode() function
        sb.append("    /// @notice Decode all fields from calldata at the given offset.\n");
        sb.append("    ///         Reverts if any field is absent (presence flag = 0).\n");
        sb.append("    function decode(bytes calldata data, uint256 offset)\n");
        sb.append("        internal pure returns (").append(messageName).append(" memory result)\n");
        sb.append("    {\n");
        for (final FieldInfo f : fields) {
            sb.append("        result.").append(f.camelName()).append(" = ")
                    .append(f.helperMethod()).append("(data,")
                    .append(" offset + OFFSET_").append(f.upperSnakeName())
                    .append(", offset + OFFSET_").append(f.upperSnakeName()).append("_PRESENCE);\n");
        }
        sb.append("    }\n\n");

        // Individual field accessors
        for (final FieldInfo f : fields) {
            sb.append("    /// @notice Read only the `").append(f.name()).append("` field.\n");
            sb.append("    function ").append(f.camelName()).append("(bytes calldata data, uint256 offset)\n");
            sb.append("        internal pure returns (").append(f.solidityType()).append(" result)\n");
            sb.append("    {\n");
            sb.append("        result = ").append(f.helperMethod()).append("(data,")
                    .append(" offset + OFFSET_").append(f.upperSnakeName())
                    .append(", offset + OFFSET_").append(f.upperSnakeName()).append("_PRESENCE);\n");
            sb.append("    }\n\n");
        }

        // Internal helpers section
        sb.append("    // --- Internal helpers ---\n\n");

        // _readPresence (always needed)
        sb.append("""
                    /// @dev Read a 4-byte XDR presence flag. Reverts if not exactly 0 or 1.
                    function _readPresence(bytes calldata data, uint256 flagOffset)
                        private
                        pure
                        returns (bool present)
                    {
                        uint256 pos = data.offset + flagOffset;
                        uint32 flag;
                        assembly {
                            flag := shr(224, calldataload(pos))
                        }
                        require(flag <= 1, "XDR: invalid presence flag");
                        return flag == 1;
                    }

                """);

        if (needsUint32) {
            sb.append("""
                        /// @dev Read a 4-byte XDR unsigned int (uint32) at valueOffset, with presence check.
                        function _readUint32(bytes calldata data, uint256 valueOffset, uint256 flagOffset)
                            private
                            pure
                            returns (uint32 result)
                        {
                            require(_readPresence(data, flagOffset), "XDR: required field absent");
                            uint256 pos = data.offset + valueOffset;
                            assembly {
                                result := shr(224, calldataload(pos))
                            }
                        }

                    """);
        }

        if (needsInt32) {
            sb.append("""
                        /// @dev Read a 4-byte XDR signed int (int32) at valueOffset, with presence check.
                        function _readInt32(bytes calldata data, uint256 valueOffset, uint256 flagOffset)
                            private
                            pure
                            returns (int32 result)
                        {
                            require(_readPresence(data, flagOffset), "XDR: required field absent");
                            uint256 pos = data.offset + valueOffset;
                            assembly {
                                result := signextend(3, shr(224, calldataload(pos)))
                            }
                        }

                    """);
        }

        if (needsUint64) {
            sb.append("""
                        /// @dev Read an 8-byte XDR unsigned hyper (uint64) at valueOffset, with presence check.
                        function _readUint64(bytes calldata data, uint256 valueOffset, uint256 flagOffset)
                            private
                            pure
                            returns (uint64 result)
                        {
                            require(_readPresence(data, flagOffset), "XDR: required field absent");
                            uint256 pos = data.offset + valueOffset;
                            assembly {
                                result := shr(192, calldataload(pos))
                            }
                        }

                    """);
        }

        if (needsInt64) {
            sb.append("""
                        /// @dev Read an 8-byte XDR signed hyper (int64) at valueOffset, with presence check.
                        function _readInt64(bytes calldata data, uint256 valueOffset, uint256 flagOffset)
                            private
                            pure
                            returns (int64 result)
                        {
                            require(_readPresence(data, flagOffset), "XDR: required field absent");
                            uint256 pos = data.offset + valueOffset;
                            assembly {
                                result := signextend(7, shr(192, calldataload(pos)))
                            }
                        }

                    """);
        }

        if (needsBytes4) {
            sb.append("""
                        /// @dev Read a 4-byte XDR float (as bytes4) at valueOffset, with presence check.
                        function _readBytes4(bytes calldata data, uint256 valueOffset, uint256 flagOffset)
                            private
                            pure
                            returns (bytes4 result)
                        {
                            require(_readPresence(data, flagOffset), "XDR: required field absent");
                            uint256 pos = data.offset + valueOffset;
                            assembly {
                                result := and(calldataload(pos), 0xFFFFFFFF00000000000000000000000000000000000000000000000000000000)
                            }
                        }

                    """);
        }

        if (needsBytes8) {
            sb.append("""
                        /// @dev Read an 8-byte XDR double (as bytes8) at valueOffset, with presence check.
                        function _readBytes8(bytes calldata data, uint256 valueOffset, uint256 flagOffset)
                            private
                            pure
                            returns (bytes8 result)
                        {
                            require(_readPresence(data, flagOffset), "XDR: required field absent");
                            uint256 pos = data.offset + valueOffset;
                            assembly {
                                result := and(calldataload(pos), 0xFFFFFFFFFFFFFFFF000000000000000000000000000000000000000000000000)
                            }
                        }

                    """);
        }

        if (needsBool) {
            sb.append("""
                        /// @dev Read a 4-byte XDR boolean at valueOffset, with presence check.
                        function _readBool(bytes calldata data, uint256 valueOffset, uint256 flagOffset)
                            private
                            pure
                            returns (bool result)
                        {
                            require(_readPresence(data, flagOffset), "XDR: required field absent");
                            uint256 pos = data.offset + valueOffset;
                            uint32 raw;
                            assembly {
                                raw := shr(224, calldataload(pos))
                            }
                            require(raw <= 1, "XDR: invalid boolean value");
                            return raw == 1;
                        }

                    """);
        }

        sb.append("}\n");

        // Write the .sol file to the output directory
        final String packagePath = javaPackage.replace('.', File.separatorChar);
        final File outputDir = new File(solidityOutputDir, packagePath);
        //noinspection ResultOfMethodCallIgnored
        outputDir.mkdirs();
        final File outputFile = new File(outputDir, libraryName + ".sol");
        Files.writeString(outputFile.toPath(), sb.toString());
    }

    private static String xdrTypeComment(final Field.FieldType type) {
        return switch (type) {
            case INT32 -> "int / int32";
            case SINT32 -> "int / sint32";
            case SFIXED32 -> "int / sfixed32";
            case UINT32 -> "unsigned int / uint32";
            case FIXED32 -> "unsigned int / fixed32";
            case INT64 -> "hyper / int64";
            case SINT64 -> "hyper / sint64";
            case SFIXED64 -> "hyper / sfixed64";
            case UINT64 -> "unsigned hyper / uint64";
            case FIXED64 -> "unsigned hyper / fixed64";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case BOOL -> "bool";
            case ENUM -> "int / enum";
            default -> type.name().toLowerCase();
        };
    }
}
