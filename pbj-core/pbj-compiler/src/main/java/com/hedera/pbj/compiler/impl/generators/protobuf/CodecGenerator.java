// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.generators.Generator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Code generator that parses protobuf files and generates writers for each message type.
 */
@SuppressWarnings("DuplicatedCode")
public final class CodecGenerator implements Generator {

    /**
     * {@inheritDoc}
     */
    public void generate(Protobuf3Parser.MessageDefContext msgDef, final File destinationSrcDir,
                         File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
        final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final String codecClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.CODEC, msgDef);
        final String codecPackage = lookupHelper.getPackageForMessage(FileType.CODEC, msgDef);
        final File javaFile = Common.getJavaFile(destinationSrcDir, codecPackage, codecClassName);

        final List<Field> fields = new ArrayList<>();
        final Set<String> imports = new TreeSet<>();
        imports.add(lookupHelper.getPackageForMessage(FileType.MODEL, msgDef));
        imports.add(lookupHelper.getPackageForMessage(FileType.SCHEMA, msgDef));

        for(var item: msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages
                generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
            } else if (item.oneof() != null) { // process one ofs
                final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
                fields.add(field);
                field.addAllNeededImports(imports, true, true, false);
            } else if (item.mapField() != null) { // process map fields
                final MapField field = new MapField(item.mapField(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(imports, true, true, false);
            } else if (item.field() != null && item.field().fieldName() != null) {
                final var field = new SingleField(item.field(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(imports, true, true, false);
            } else if (item.reserved() == null && item.optionStatement() == null) {
                System.err.printf("WriterGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }
        final String writeMethod = CodecWriteMethodGenerator.generateWriteMethod(modelClassName, fields);

        try (FileWriter javaWriter = new FileWriter(javaFile)) {
            // spotless:off
            javaWriter.write("""
                    package $package;
                    
                    import com.hedera.pbj.runtime.*;
                    import com.hedera.pbj.runtime.io.*;
                    import com.hedera.pbj.runtime.io.buffer.*;
                    import com.hedera.pbj.runtime.io.stream.EOFException;
                    import java.io.IOException;
                    import java.nio.*;
                    import java.nio.charset.*;
                    import java.util.*;
                    import edu.umd.cs.findbugs.annotations.NonNull;
                    
                    import $qualifiedModelClass;
                    $imports
                    import static $schemaClass.*;
                    import static com.hedera.pbj.runtime.ProtoWriterTools.*;
                    import static com.hedera.pbj.runtime.ProtoParserTools.*;
                    import static com.hedera.pbj.runtime.ProtoConstants.*;
                
                    /**
                     * Protobuf Codec for $modelClass model object. Generated based on protobuf schema.
                     */
                    public final class $codecClass implements Codec<$modelClass> {
                    
                        /**
                         * Empty constructor
                         */
                         public $codecClass() {
                             // no-op
                         }
                    
                    $unsetOneOfConstants
                    $parseMethod
                    $writeMethod
                    $measureDataMethod
                    $measureRecordMethod
                    $fastEqualsMethod
                    }
                    """
                    .replace("$package", codecPackage)
                    .replace("$imports", imports.isEmpty() ? "" : imports.stream()
                            .filter(input -> !input.equals(codecPackage))
                            .collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")))
                    .replace("$schemaClass", lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef))
                    .replace("$modelClass", modelClassName)
                    .replace("$qualifiedModelClass", lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef))
                    .replace("$codecClass", codecClassName)
                    .replace("$unsetOneOfConstants", CodecParseMethodGenerator.generateUnsetOneOfConstants(fields))
                    .replace("$parseMethod", CodecParseMethodGenerator.generateParseMethod(modelClassName, fields))
                    .replace("$writeMethod", writeMethod)
                    .replace("$measureDataMethod", CodecMeasureDataMethodGenerator.generateMeasureMethod(modelClassName, fields))
                    .replace("$measureRecordMethod", CodecMeasureRecordMethodGenerator.generateMeasureMethod(modelClassName, fields))
                    .replace("$fastEqualsMethod", CodecFastEqualsMethodGenerator.generateFastEqualsMethod(modelClassName, fields))
            );
        // spotless:on
        }
    }
}
