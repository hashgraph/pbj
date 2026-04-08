// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.xdr;

import static com.hedera.pbj.compiler.impl.Common.camelToUpperSnake;
import static com.hedera.pbj.compiler.impl.Common.snakeToCamel;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a Solidity XDR decoder library for a protobuf message.
 * <p>
 * Fixed-size-only messages (all singular, non-repeated, non-oneof scalars) use static offset
 * constants and individual field accessors — Pattern A.
 * <p>
 * Messages with any variable-length field (bytes, string, repeated, oneof, map) use a
 * runtime pointer-advancing decode function — Pattern B — and import {@code XdrDecoderLib.sol}
 * for shared helpers.
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
            return solidityTypeForFixed(fieldType);
        }

        /** The right-shift amount to extract the value from a calldataload result. */
        int valueShift() {
            return 256 - valueSize * 8;
        }

        /** Internal helper method name for reading this field type. */
        String helperMethod() {
            return helperMethodForFixed(fieldType);
        }
    }

    /**
     * Returns the Solidity type keyword for a fixed-size protobuf field type.
     */
    private static String solidityTypeForFixed(final Field.FieldType type) {
        return switch (type) {
            case INT32, SINT32, SFIXED32 -> "int32";
            case UINT32, FIXED32 -> "uint32";
            case INT64, SINT64, SFIXED64 -> "int64";
            case UINT64, FIXED64 -> "uint64";
            case FLOAT -> "bytes4";
            case DOUBLE -> "bytes8";
            case BOOL -> "bool";
            case ENUM -> "uint32";
            default -> throw new IllegalStateException("Not a fixed-size type: " + type);
        };
    }

    /**
     * Returns the private helper method name for reading a fixed-size protobuf field type.
     */
    private static String helperMethodForFixed(final Field.FieldType type) {
        return switch (type) {
            case INT32, SINT32, SFIXED32 -> "_readInt32";
            case UINT32, FIXED32, ENUM -> "_readUint32";
            case INT64, SINT64, SFIXED64 -> "_readInt64";
            case UINT64, FIXED64 -> "_readUint64";
            case FLOAT -> "_readBytes4";
            case DOUBLE -> "_readBytes8";
            case BOOL -> "_readBool";
            default -> throw new IllegalStateException("Not a fixed-size type: " + type);
        };
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
     * <p>
     * If the message contains any variable-length fields (bytes, string, repeated, oneof, map),
     * uses pointer-advancing mode (Pattern B). Otherwise uses static-offset mode (Pattern A).
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

        // Scan to determine if message has any variable-length fields
        boolean hasVariable = false;
        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.field() != null && item.field().fieldName() != null) {
                final SingleField field = new SingleField(item.field(), lookupHelper);
                if (field.repeated() || !isFixedSize(field.type())) {
                    hasVariable = true;
                    break;
                }
            } else if (item.oneof() != null || item.mapField() != null) {
                hasVariable = true;
                break;
            }
        }

        if (hasVariable) {
            generateVariableMode(msgDef, solidityOutputDir, lookupHelper, messageName, javaPackage);
            return;
        }

        // ---- Fixed-only path (Pattern A) ----

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

    // =========================================================================
    // Pattern B: variable-length / pointer-advancing mode
    // =========================================================================

    /**
     * Generate a pointer-advancing Solidity XDR decoder for a message that has at least one
     * variable-length field (bytes, string, repeated, oneof, or map).
     */
    private static void generateVariableMode(
            final Protobuf3Parser.MessageDefContext msgDef,
            final File solidityOutputDir,
            final ContextualLookupHelper lookupHelper,
            final String messageName,
            final String javaPackage)
            throws IOException {

        final String libraryName = messageName + "Xdr";
        final int packageDepth = javaPackage.split("\\.").length;
        final String importPath = "../".repeat(packageDepth) + "XdrDecoderLib.sol";

        // Accumulate struct field declarations and decode() body separately
        final StringBuilder structFields = new StringBuilder();
        final StringBuilder decodeBody = new StringBuilder();
        decodeBody.append("        uint256 ptr = offset;\n");

        // Track which private scalar helpers are needed (only for non-repeated singular fixed fields)
        boolean needsInt32 = false;
        boolean needsUint32 = false;
        boolean needsInt64 = false;
        boolean needsUint64 = false;
        boolean needsBytes4 = false;
        boolean needsBytes8 = false;
        boolean needsBool = false;

        boolean hasAnyStructField = false;

        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.field() != null && item.field().fieldName() != null) {
                final SingleField field = new SingleField(item.field(), lookupHelper);
                final String camelName = snakeToCamel(field.name(), false);

                if (!field.repeated()) {
                    if (isFixedSize(field.type())) {
                        // Singular fixed-size scalar: use private helper + advance ptr
                        final int valueSize = xdrValueSize(field.type());
                        final String solType = solidityTypeForFixed(field.type());
                        final String helper = helperMethodForFixed(field.type());
                        structFields.append("        ").append(solType).append(" ").append(camelName).append(";\n");
                        decodeBody.append("        result.").append(camelName)
                                .append(" = ").append(helper).append("(data, ptr + 4, ptr);\n");
                        decodeBody.append("        ptr += ").append(PRESENCE_SIZE + valueSize).append(";\n");
                        switch (helper) {
                            case "_readUint32" -> needsUint32 = true;
                            case "_readInt32" -> needsInt32 = true;
                            case "_readUint64" -> needsUint64 = true;
                            case "_readInt64" -> needsInt64 = true;
                            case "_readBytes4" -> needsBytes4 = true;
                            case "_readBytes8" -> needsBytes8 = true;
                            case "_readBool" -> needsBool = true;
                            default -> { /* unreachable */ }
                        }
                        hasAnyStructField = true;
                    } else if (field.type() == Field.FieldType.BYTES
                            || field.type() == Field.FieldType.STRING) {
                        // Singular bytes/string: inline presence + length + variable copy
                        final boolean isStr = field.type() == Field.FieldType.STRING;
                        structFields.append("        ").append(isStr ? "string" : "bytes")
                                .append(" ").append(camelName).append(";\n");
                        decodeBody.append("        {\n");
                        decodeBody.append("            uint256 ").append(camelName)
                                .append("FlagPos = data.offset + ptr;\n");
                        decodeBody.append("            uint32 ").append(camelName).append("Flag;\n");
                        decodeBody.append("            assembly { ").append(camelName)
                                .append("Flag := shr(224, calldataload(").append(camelName).append("FlagPos)) }\n");
                        decodeBody.append("            require(").append(camelName)
                                .append("Flag <= 1, \"XDR: invalid presence flag\");\n");
                        decodeBody.append("            require(").append(camelName)
                                .append("Flag == 1, \"XDR: required field absent\");\n");
                        decodeBody.append("            uint32 ").append(camelName)
                                .append("Len = XdrDecoderLib.readUint32Raw(data, ptr + 4);\n");
                        if (isStr) {
                            decodeBody.append("            result.").append(camelName)
                                    .append(" = string(XdrDecoderLib.readVariableBytes(data, ptr + 8, ")
                                    .append(camelName).append("Len));\n");
                        } else {
                            decodeBody.append("            result.").append(camelName)
                                    .append(" = XdrDecoderLib.readVariableBytes(data, ptr + 8, ")
                                    .append(camelName).append("Len);\n");
                        }
                        decodeBody.append("            uint32 ").append(camelName)
                                .append("Pad = XdrDecoderLib.paddedLen(").append(camelName).append("Len);\n");
                        decodeBody.append("            ptr += 8 + uint256(").append(camelName)
                                .append("Len) + uint256(").append(camelName).append("Pad);\n");
                        decodeBody.append("        }\n");
                        hasAnyStructField = true;
                    } else if (field.type() == Field.FieldType.MESSAGE) {
                        System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                                + " has MESSAGE field '" + field.name()
                                + "'; skipping in variable-mode decoder.");
                    } else {
                        System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                                + " has unsupported field type " + field.type()
                                + " for field '" + field.name() + "'; skipping.");
                    }
                } else {
                    // Repeated field
                    if (isFixedSize(field.type())) {
                        // Repeated fixed-size scalar: count + array allocation + loop
                        final int elemSize = xdrValueSize(field.type());
                        final String solType = solidityTypeForFixed(field.type());
                        structFields.append("        ").append(solType).append("[] ")
                                .append(camelName).append(";\n");
                        decodeBody.append("        {\n");
                        decodeBody.append("            uint32 ").append(camelName)
                                .append("Count = XdrDecoderLib.readUint32Raw(data, ptr);\n");
                        decodeBody.append("            ptr += 4;\n");
                        decodeBody.append("            result.").append(camelName)
                                .append(" = new ").append(solType).append("[](").append(camelName).append("Count);\n");
                        decodeBody.append("            for (uint32 ").append(camelName).append("I = 0; ")
                                .append(camelName).append("I < ").append(camelName).append("Count; ")
                                .append(camelName).append("I++) {\n");
                        decodeBody.append("                uint256 ").append(camelName)
                                .append("Pos = data.offset + ptr;\n");
                        appendFixedElemRead(decodeBody, field.type(), camelName);
                        decodeBody.append("                result.").append(camelName).append("[")
                                .append(camelName).append("I] = ").append(camelName).append("Elem;\n");
                        decodeBody.append("                ptr += ").append(elemSize).append(";\n");
                        decodeBody.append("            }\n");
                        decodeBody.append("        }\n");
                        hasAnyStructField = true;
                    } else {
                        System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                                + " has repeated non-scalar field '" + field.name()
                                + "' of type " + field.type() + "; skipping.");
                    }
                }
            } else if (item.oneof() != null) {
                final OneOfField oneOf = new OneOfField(item.oneof(), messageName, lookupHelper);
                final String oneofCamel = snakeToCamel(oneOf.name(), false);

                // Struct: kind discriminant + one field per arm (skipping MESSAGE arms)
                structFields.append("        uint32 ").append(oneofCamel).append("Kind;\n");
                for (final Field arm : oneOf.fields()) {
                    final String armCamel = snakeToCamel(arm.name(), false);
                    if (arm.type() == Field.FieldType.MESSAGE) {
                        System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                                + " oneof '" + oneOf.name() + "' arm '" + arm.name()
                                + "' is MESSAGE type; skipping arm field in struct.");
                    } else if (arm.type() == Field.FieldType.BYTES
                            || arm.type() == Field.FieldType.STRING) {
                        // bytes and string arms both represented as bytes in Solidity
                        structFields.append("        bytes ").append(armCamel).append(";\n");
                    } else if (isFixedSize(arm.type())) {
                        structFields.append("        ").append(solidityTypeForFixed(arm.type()))
                                .append(" ").append(armCamel).append(";\n");
                    } else {
                        System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                                + " oneof '" + oneOf.name() + "' arm '" + arm.name()
                                + "' has unsupported type " + arm.type() + "; skipping.");
                    }
                }

                // Decode: read 4-byte discriminant, then if-else chain per arm
                decodeBody.append("        {\n");
                decodeBody.append("            uint32 ").append(oneofCamel)
                        .append("Disc = XdrDecoderLib.readUint32Raw(data, ptr);\n");
                decodeBody.append("            ptr += 4;\n");
                decodeBody.append("            result.").append(oneofCamel).append("Kind = ")
                        .append(oneofCamel).append("Disc;\n");
                decodeBody.append("            if (").append(oneofCamel).append("Disc == 0) {\n");
                decodeBody.append("                // unset\n");
                decodeBody.append("            }");
                for (final Field arm : oneOf.fields()) {
                    final String armCamel = snakeToCamel(arm.name(), false);
                    decodeBody.append(" else if (").append(oneofCamel).append("Disc == ")
                            .append(arm.fieldNumber()).append(") {\n");
                    if (arm.type() == Field.FieldType.BYTES
                            || arm.type() == Field.FieldType.STRING) {
                        decodeBody.append("                uint32 ").append(armCamel)
                                .append("ArmLen = XdrDecoderLib.readUint32Raw(data, ptr);\n");
                        decodeBody.append("                result.").append(armCamel)
                                .append(" = XdrDecoderLib.readVariableBytes(data, ptr + 4, ")
                                .append(armCamel).append("ArmLen);\n");
                        decodeBody.append("                uint32 ").append(armCamel)
                                .append("ArmPad = XdrDecoderLib.paddedLen(").append(armCamel).append("ArmLen);\n");
                        decodeBody.append("                ptr += 4 + uint256(").append(armCamel)
                                .append("ArmLen) + uint256(").append(armCamel).append("ArmPad);\n");
                    } else if (isFixedSize(arm.type())) {
                        final int armSize = xdrValueSize(arm.type());
                        decodeBody.append("                uint256 ").append(armCamel)
                                .append("ArmPos = data.offset + ptr;\n");
                        appendFixedElemRead(decodeBody, arm.type(), armCamel + "Arm");
                        decodeBody.append("                result.").append(armCamel).append(" = ")
                                .append(armCamel).append("ArmElem;\n");
                        decodeBody.append("                ptr += ").append(armSize).append(";\n");
                    } else if (arm.type() == Field.FieldType.MESSAGE) {
                        decodeBody.append("                revert(\"XDR: MESSAGE oneof arm not supported in Solidity decoder\");\n");
                    } else {
                        decodeBody.append("                revert(\"XDR: unsupported oneof arm type\");\n");
                    }
                    decodeBody.append("            }");
                }
                decodeBody.append(" else {\n");
                decodeBody.append("                revert(\"XDR: unknown oneof discriminant\");\n");
                decodeBody.append("            }\n");
                decodeBody.append("        }\n");

                hasAnyStructField = true;
            } else if (item.mapField() != null) {
                System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                        + " has MAP field; skipping in variable-mode decoder.");
            }
        }

        if (!hasAnyStructField) {
            System.err.println("SolidityXdrDecoderGenerator: message " + messageName
                    + " has no decodable fields in variable-mode; skipping.");
            return;
        }

        // Assemble the full .sol file
        final StringBuilder sb = new StringBuilder();

        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("pragma solidity ^0.8.20;\n\n");
        sb.append("import \"").append(importPath).append("\";\n\n");

        sb.append("/// @title ").append(libraryName).append("\n");
        sb.append("/// @notice Pure XDR decoder for ").append(messageName)
                .append(" calldata (pointer-advancing mode).\n");
        sb.append("///         Generated by PBJ \u2014 do not edit.\n");
        sb.append("library ").append(libraryName).append(" {\n\n");

        // Struct
        sb.append("    /// @notice Fully decoded ").append(messageName).append(" struct.\n");
        sb.append("    struct ").append(messageName).append(" {\n");
        sb.append(structFields);
        sb.append("    }\n\n");

        // decode()
        sb.append("    /// @notice Decode all fields from calldata at the given offset using pointer-advancing mode.\n");
        sb.append("    ///         Reverts if any required field is absent or an unknown discriminant is encountered.\n");
        sb.append("    function decode(bytes calldata data, uint256 offset)\n");
        sb.append("        internal pure returns (").append(messageName).append(" memory result)\n");
        sb.append("    {\n");
        sb.append(decodeBody);
        sb.append("    }\n");

        // Private helpers (only if any fixed-size singular fields are present)
        final boolean needsAnyHelper = needsInt32 || needsUint32 || needsInt64
                || needsUint64 || needsBytes4 || needsBytes8 || needsBool;
        if (needsAnyHelper) {
            sb.append("\n    // --- Internal helpers ---\n\n");
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
        }

        sb.append("}\n");

        // Write the .sol file
        final String packagePath = javaPackage.replace('.', File.separatorChar);
        final File outputDir = new File(solidityOutputDir, packagePath);
        //noinspection ResultOfMethodCallIgnored
        outputDir.mkdirs();
        final File outputFile = new File(outputDir, libraryName + ".sol");
        Files.writeString(outputFile.toPath(), sb.toString());
    }

    /**
     * Append inline assembly to read a single fixed-size element (no presence flag) from calldata.
     * <p>
     * The caller must have already declared {@code uint256 {prefix}Pos = data.offset + ptr;} at
     * 16-space indentation. This method appends the type declaration and assembly read for
     * {@code {prefix}Elem} at 16-space indentation.
     *
     * @param sb     output builder
     * @param type   fixed-size field type
     * @param prefix variable name prefix (e.g., {@code "myField"} → vars {@code myFieldPos},
     *               {@code myFieldElem})
     */
    private static void appendFixedElemRead(
            final StringBuilder sb, final Field.FieldType type, final String prefix) {
        final String posVar = prefix + "Pos";
        final String elemVar = prefix + "Elem";
        switch (type) {
            case UINT32, FIXED32, ENUM -> {
                sb.append("                uint32 ").append(elemVar).append(";\n");
                sb.append("                assembly { ").append(elemVar)
                        .append(" := shr(224, calldataload(").append(posVar).append(")) }\n");
            }
            case INT32, SINT32, SFIXED32 -> {
                sb.append("                int32 ").append(elemVar).append(";\n");
                sb.append("                assembly { ").append(elemVar)
                        .append(" := signextend(3, shr(224, calldataload(").append(posVar).append("))) }\n");
            }
            case UINT64, FIXED64 -> {
                sb.append("                uint64 ").append(elemVar).append(";\n");
                sb.append("                assembly { ").append(elemVar)
                        .append(" := shr(192, calldataload(").append(posVar).append(")) }\n");
            }
            case INT64, SINT64, SFIXED64 -> {
                sb.append("                int64 ").append(elemVar).append(";\n");
                sb.append("                assembly { ").append(elemVar)
                        .append(" := signextend(7, shr(192, calldataload(").append(posVar).append("))) }\n");
            }
            case FLOAT -> {
                sb.append("                bytes4 ").append(elemVar).append(";\n");
                sb.append("                assembly { ").append(elemVar)
                        .append(" := and(calldataload(").append(posVar)
                        .append("), 0xFFFFFFFF00000000000000000000000000000000000000000000000000000000) }\n");
            }
            case DOUBLE -> {
                sb.append("                bytes8 ").append(elemVar).append(";\n");
                sb.append("                assembly { ").append(elemVar)
                        .append(" := and(calldataload(").append(posVar)
                        .append("), 0xFFFFFFFFFFFFFFFF000000000000000000000000000000000000000000000000) }\n");
            }
            case BOOL -> {
                final String rawVar = prefix + "Raw";
                sb.append("                uint32 ").append(rawVar).append(";\n");
                sb.append("                assembly { ").append(rawVar)
                        .append(" := shr(224, calldataload(").append(posVar).append(")) }\n");
                sb.append("                require(").append(rawVar)
                        .append(" <= 1, \"XDR: invalid boolean value\");\n");
                sb.append("                bool ").append(elemVar).append(" = ").append(rawVar).append(" == 1;\n");
            }
            default -> throw new IllegalStateException("Not a fixed-size type: " + type);
        }
    }

    // =========================================================================
    // Shared library generation
    // =========================================================================

    /**
     * Write {@code XdrDecoderLib.sol} to the root of {@code solidityOutputDir}.
     * <p>
     * This file is imported by all variable-length message decoders. It is idempotent — calling
     * it multiple times for the same output directory always writes the same content.
     *
     * @param solidityOutputDir root output directory for .sol files
     */
    public static void generateSharedLib(final File solidityOutputDir) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        solidityOutputDir.mkdirs();
        final File outputFile = new File(solidityOutputDir, "XdrDecoderLib.sol");
        final String content = """
                // SPDX-License-Identifier: Apache-2.0
                pragma solidity ^0.8.20;

                /// @title XdrDecoderLib
                /// @notice Shared XDR calldata decode helpers used by variable-length message decoders.
                ///         Generated by PBJ \u2014 do not edit.
                library XdrDecoderLib {

                    /// @dev Read a 4-byte big-endian uint32 from calldata without a presence check.
                    ///      Used for array counts, oneof discriminants, and variable-length field lengths.
                    function readUint32Raw(bytes calldata data, uint256 pos)
                        internal pure returns (uint32 value)
                    {
                        uint256 abs = data.offset + pos;
                        assembly {
                            value := shr(224, calldataload(abs))
                        }
                    }

                    /// @dev Return the number of XDR padding bytes needed to reach the next 4-byte boundary.
                    function paddedLen(uint32 len) internal pure returns (uint32) {
                        unchecked { return (4 - (len % 4)) % 4; }
                    }

                    /// @dev Copy variable-length bytes from calldata into a new memory array.
                    function readVariableBytes(bytes calldata data, uint256 pos, uint32 len)
                        internal pure returns (bytes memory result)
                    {
                        result = new bytes(len);
                        assembly {
                            calldatacopy(add(result, 32), add(data.offset, pos), len)
                        }
                    }
                }
                """;
        Files.writeString(outputFile.toPath(), content);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

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
