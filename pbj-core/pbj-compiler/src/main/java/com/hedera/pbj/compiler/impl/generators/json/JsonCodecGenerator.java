// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.json;

import com.hedera.pbj.compiler.impl.*;
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
public final class JsonCodecGenerator implements Generator {

	/**
	 * {@inheritDoc}
	 */
	public void generate(Protobuf3Parser.MessageDefContext msgDef, final File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
		final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		final String codecClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.JSON_CODEC, msgDef);
		final String codecPackage = lookupHelper.getPackageForMessage(FileType.JSON_CODEC, msgDef);
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
				System.err.println("WriterGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}
		final String writeMethod = JsonCodecWriteMethodGenerator.generateWriteMethod(modelClassName, fields);

		try (FileWriter javaWriter = new FileWriter(javaFile)) {
			javaWriter.write("""
					package $package;
					
					import com.hedera.pbj.runtime.*;
					import com.hedera.pbj.runtime.io.*;
					import com.hedera.pbj.runtime.io.buffer.*;
					import java.io.IOException;
					import java.nio.*;
					import java.nio.charset.*;
					import java.util.*;
					import edu.umd.cs.findbugs.annotations.NonNull;
					import edu.umd.cs.findbugs.annotations.Nullable;
					
					import $qualifiedModelClass;
					$imports
					import com.hedera.pbj.runtime.jsonparser.*;
					import static $schemaClass.*;
					import static com.hedera.pbj.runtime.JsonTools.*;
					
					/**
					 * JSON Codec for $modelClass model object. Generated based on protobuf schema.
					 */
					public final class $codecClass implements JsonCodec<$modelClass> {
					
						/**
						 * Empty constructor
						 */
						 public $codecClass() {
						 	// no-op
						 }
					
					    $unsetOneOfConstants
					    $parseObject
					    $writeMethod
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
					.replace("$unsetOneOfConstants", JsonCodecParseMethodGenerator.generateUnsetOneOfConstants(fields))
					.replace("$writeMethod", writeMethod)
					.replace("$parseObject", JsonCodecParseMethodGenerator.generateParseObjectMethod(modelClassName, fields))
			);
		}
	}

	/**
	 * Converts a field name to a JSON field name.
	 *
	 * @param fieldName the field name
	 * @return the JSON field name
	 */
	static String toJsonFieldName(String fieldName) {
		// based directly on protoc so output matches
		final int length = fieldName.length();
		StringBuilder result = new StringBuilder(length);
		boolean isNextUpperCase = false;
		for (int i = 0; i < length; i++) {
			char ch = fieldName.charAt(i);
			if (ch == '_') {
				isNextUpperCase = true;
			} else if (isNextUpperCase) {
				// This closely matches the logic for ASCII characters in:
				// http://google3/google/protobuf/descriptor.cc?l=249-251&rcl=228891689
				if ('a' <= ch && ch <= 'z') {
					ch = (char) (ch - 'a' + 'A');
				}
				result.append(ch);
				isNextUpperCase = false;
			} else {
				result.append(ch);
			}
		}
		return result.toString();
	}
}
