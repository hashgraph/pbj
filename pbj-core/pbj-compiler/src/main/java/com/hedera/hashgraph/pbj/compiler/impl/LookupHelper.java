package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.StreamSupport;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.removingLeadingDot;
import static com.hedera.hashgraph.pbj.compiler.impl.FileAndPackageNamesConfig.*;

/**
 * Class that manages packages and enum names that are used more than one place in code generation
 */
@SuppressWarnings({"unused", "DuplicatedCode"})
public final class LookupHelper {
	/** The option name for PBJ package at file level */
	private static final String PBJ_PACKAGE_OPTION_NAME = "(pbj.java_package)";
	/** The option name for PBJ package at msgDef level */
	private static final String PBJ_MESSAGE_PACKAGE_OPTION_NAME = "(pbj.message_java_package)";
	/** The option name for PBJ package at msgDef level */
	private static final String PBJ_ENUM_PACKAGE_OPTION_NAME = "(pbj.enum_java_package)";
	/** The option name for protoc java package at file level */
	private static final String PROTOC_JAVA_PACKAGE_OPTION_NAME = "java_package";

	/** Map from fully qualified msgDef name to fully qualified pbj java package, not including java class */
	private final Map<String,String> pbjPackageMap = new HashMap<>();

	/** Map from fully qualified msgDef name to fully qualified protoc java package, not including java class */
	private final Map<String,String> protocPackageMap = new HashMap<>();

	/** Map from proto file path to list of other proto files it imports */
	private final Map<String,Set<String>> protoFileImports = new HashMap<>();

	/**
	 * Map from file path to map of message/enum name to fully qualified message/enum name. This allows us to lookup for
	 * a given protobuf file an unqualified message name and get back the qualified message name.
	 */
	private final Map<String,Map<String,String>> msgAndEnumByFile = new HashMap<>();

	/** Set of all fully qualified message names that are enums, so we can check if a message is an enum or not */
	private final Set<String> enumNames = new HashSet<>();

	/**
	 * Build a new lookup helper, root directory of protobuf files. This scans directory reading protobuf files
	 * extracting what is needed.
	 *
	 * @param allSrcFiles collection of all proto src files
	 */
	public LookupHelper(Iterable<File> allSrcFiles) {
		build(allSrcFiles);
	}

	/**
	 * Get the unqualified proto name for a message, enum or a message type. For example
	 * "proto.GetAccountDetailsResponse.AccountDetails" would return "AccountDetails".
	 *
	 * @param context The parser context for a message, enum or a message type.
	 * @return unqualified proto name
	 */
	public String getUnqualifiedProtoName(final ParserRuleContext context) {
		if (context instanceof final MessageDefContext msgDef) {
			return msgDef.messageName().getText();
		} else if (context instanceof final EnumDefContext enumDef) {
			return enumDef.enumName().getText();
		} else if (context instanceof final MessageTypeContext msgTypeContext) {
			final String messageType = msgTypeContext.getText();
			return messageType.contains(".") ? messageType.substring(messageType.lastIndexOf('.')+1) : messageType;
		} else {
			throw new UnsupportedOperationException("getUnqualifiedProtoNameForMsgOrEnum only supports MessageDefContext or EnumDefContext");
		}
	}

	/**
	 * Get the fully qualified proto name for a message, enum or a message type. For example
	 * "proto.GetAccountDetailsResponse.AccountDetails" would return "proto.GetAccountDetailsResponse.AccountDetails".
	 *
	 * @param protoSrcFile the proto source file that the message or enum is in
	 * @param context The parser context for a message, enum or a message type.
	 * @return fully qualified proto name
	 */
	public String getFullyQualifiedProtoName(final File protoSrcFile, final ParserRuleContext context) {
		if (context instanceof final MessageTypeContext msgTypeContext) {
			final String messageType = msgTypeContext.getText();
			// check if fully qualified
			if (messageType.contains(".")) {
				return messageType;
			}
			// check local file message types
			final var messageMapLocal = msgAndEnumByFile.get(protoSrcFile.getAbsolutePath());
			if (messageMapLocal == null) {
				throw new PbjCompilerException("Failed to find messageMapLocal for proto file [" + protoSrcFile + "]");
			}
			final String nameFoundInLocalFile = messageMapLocal.get(messageType);
			if (nameFoundInLocalFile != null) {
				return nameFoundInLocalFile;
			}
			// message type is not from local file so check imported files
			for (var importedProtoFilePath : protoFileImports.get(protoSrcFile.getAbsolutePath())) {
				final var messageMap = msgAndEnumByFile.get(importedProtoFilePath);
				if (messageMap == null) {
					throw new PbjCompilerException("Failed to find messageMap for proto file [" + importedProtoFilePath + "]");
				}
				final var found = messageMap.get(messageType);
				if (found != null) {
					return found;
				}
			}
			// we failed to find
			throw new PbjCompilerException("Failed to find fully qualified message type for [" + messageType +
					"] in file [" + protoSrcFile + "]");
		} else if (context instanceof MessageDefContext || context instanceof EnumDefContext ) {
			Map<String, String> fileMap = msgAndEnumByFile.get(protoSrcFile.getAbsolutePath());
			if (fileMap == null) {
				throw new PbjCompilerException("Failed to find messageMapLocal for proto file [" + protoSrcFile + "]");
			}
			return fileMap.get(getUnqualifiedProtoName(context));
		} else {
			throw new UnsupportedOperationException("getPackageForMsgOrEnum only supports MessageDefContext or EnumDefContext");
		}
	}

	/**
	 * Get the unqualified Java class name for given message or enum.
	 *
	 * @param protoSrcFile the proto source file that the message or enum is in
	 * @param fileType The type of file we want the package for
	 * @param context Parser Context, a message or enum
	 * @return java package to put model class in
	 */
	String getUnqualifiedClass(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
		final String name;
		final boolean isEnum;
		if (context instanceof final MessageTypeContext msgType) {
			final var unqualifiedName = getUnqualifiedProtoName(msgType);
			final var qualifiedName = getFullyQualifiedProtoName(protoSrcFile, msgType);
			isEnum = isEnum(qualifiedName);
			name = unqualifiedName;
		} else if (context instanceof final MessageDefContext msgDef) {
			name = msgDef.messageName().getText();
			isEnum = false;
		} else if (context instanceof final EnumDefContext enumDef) {
			name = enumDef.enumName().getText();
			isEnum = true;
		} else {
			throw new UnsupportedOperationException("getPackageForMsgOrEnum only supports MessageDefContext or EnumDefContext");
		}
		if (isEnum) {
			return name;
		} else {
			return  switch (fileType) {
				case MODEL, PROTOC -> name;
				case SCHEMA -> name + SCHEMA_JAVA_FILE_SUFFIX;
				case PARSER -> name + PARSER_JAVA_FILE_SUFFIX;
				case WRITER -> name + WRITER_JAVA_FILE_SUFFIX;
				case TEST -> name + TEST_JAVA_FILE_SUFFIX;
			};
		}
	}

	/**
	 * Get the Java package a class should be generated into for a given msgDef and file type.
	 *
	 * @param protoSrcFile the proto source file that the message or enum is in
	 * @param fileType The type of file we want the package for
	 * @param context Parser Context, a message or enum
	 * @return java package to put model class in
	 */
	String getPackage(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
		if (context instanceof MessageDefContext || context instanceof EnumDefContext || context instanceof MessageTypeContext) {
			final String qualifiedProtoName = getFullyQualifiedProtoName(protoSrcFile, context);
			if (qualifiedProtoName.startsWith("google.protobuf")) {
				return null;
			} else if (fileType == FileType.PROTOC) {
				String protocPackage = protocPackageMap.get(qualifiedProtoName);
				if (protocPackage == null) {
					throw new PbjCompilerException("Not found protoc package for message or enum ["+qualifiedProtoName+"] in file ["+protoSrcFile+"]");
				}
				return protocPackage;
			} else {
				String basePackage = pbjPackageMap.get(qualifiedProtoName);
				if (basePackage == null) {
					throw new PbjCompilerException("Not found pbj package for message or enum ["+qualifiedProtoName+"] in file ["+protoSrcFile+"]");
				}
				return switch (fileType) {
					//noinspection ConstantConditions
					case MODEL, PROTOC -> basePackage;
					case SCHEMA -> basePackage + '.' + SCHEMAS_SUBPACKAGE;
					case PARSER -> basePackage + '.' + PARSERS_SUBPACKAGE;
					case WRITER -> basePackage + '.' + WRITERS_SUBPACKAGE;
					case TEST -> basePackage + '.' + TESTS_SUBPACKAGE;
				};
			}

		} else {
			throw new UnsupportedOperationException("getPackageForMsgOrEnum only supports MessageDefContext, " +
					"EnumDefContext or MessageTypeContext not ["+context.getClass().getName()+"]");
		}
	}

	/**
	 * Get the fully qualified Java class name for given message/enum and file type.
	 *
	 * @param protoSrcFile the proto source file that the message or enum is in
	 * @param fileType The type of file we want the class name for
	 * @param context Parser Context, a message or enum
	 * @return fully qualified Java class name
	 */
	String getFullyQualifiedClass(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
		if (context instanceof MessageDefContext || context instanceof EnumDefContext || context instanceof MessageTypeContext) {
			final String packageName = getPackage(protoSrcFile, fileType, context);
			final String messageName = getUnqualifiedClass(protoSrcFile, fileType, context);
			// protoc supports nested classes so need parent classes/messages
			final String parentClasses;
			if (fileType == FileType.PROTOC && context.getParent() instanceof MessageElementContext) {
				StringBuilder sb = new StringBuilder();
				ParserRuleContext parent = context.getParent();
				while(!(parent instanceof TopLevelDefContext)) {
					if (parent instanceof MessageDefContext) {
						sb.insert(0,'.');
						sb.insert(0,((MessageDefContext) parent).messageName().getText());
					}
					parent = parent.getParent();
				}
				parentClasses = sb.toString();
			} else {
				parentClasses = "";
			}
			return packageName + '.' + parentClasses + messageName;
		} else {
			throw new UnsupportedOperationException("getPackageForMsgOrEnum only supports MessageDefContext or EnumDefContext");
		}
	}

	/**
	 * Check if the given fullyQualifiedMessageOrEnumName is a known enum
	 *
	 * @param fullyQualifiedMessageOrEnumName to check if enum
	 * @return true if known as an enum, recorded by addEnum()
	 */
	private boolean isEnum(String fullyQualifiedMessageOrEnumName) {
		return enumNames.contains(fullyQualifiedMessageOrEnumName);
	}

	/**
	 * Check if the given fullyQualifiedMessageOrEnumName is a known enum
	 *
	 * @param messageType field message type to check if enum
	 * @return true if known as an enum, recorded by addEnum()
	 */
	boolean isEnum(File protoSrcFile, MessageTypeContext messageType) {
		return isEnum(getFullyQualifiedProtoName(protoSrcFile, messageType));
	}

	// =================================================================================================================
	// BUILD METHODS to construct lookup tables

	/**
	 * Builds onto map from protobuf msgDef to java package. This is used to produce imports for messages in other
	 * packages. It parses every src file an extra time but it is so fast it doesn't really matter.
	 *
	 * @param allSrcFiles collection of all proto src files
	 */
	private void build(Iterable<File> allSrcFiles) {
		for (File file: allSrcFiles) {
			final Path filePath = file.toPath();
			final String fullQualifiedFile = file.getAbsolutePath();
			if (file.exists() && file.isFile() && file.getName().endsWith(".proto")) {
				try (var input = new FileInputStream(file)) {
					// parse file
					final var lexer = new Protobuf3Lexer(CharStreams.fromStream(input));
					final var parser = new Protobuf3Parser(new CommonTokenStream(lexer));
					Protobuf3Parser.ProtoContext parsedDoc = parser.proto();
					// create entry in map for file
					msgAndEnumByFile.computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>());
					// look for PBJ package option
					String pbjJavaPackage = null;
					String protocJavaPackage = null;
					for(var option: parsedDoc.optionStatement()) {
						switch (option.optionName().getText()) {
							case PBJ_PACKAGE_OPTION_NAME -> pbjJavaPackage = option.constant().getText().replaceAll("\"","");
							case PROTOC_JAVA_PACKAGE_OPTION_NAME -> protocJavaPackage = option.constant().getText().replaceAll("\"","");
						}
					}
					if (file.getName().endsWith("pbj_custom_options.proto")) {
						// ignore pbj_custom_options.proto file
						continue;
					} else if (pbjJavaPackage == null && protocJavaPackage == null) {
						throw new PbjCompilerException("Proto file ["+file.getAbsolutePath()+"] does not contain \""+PBJ_PACKAGE_OPTION_NAME+"\" or \""+PROTOC_JAVA_PACKAGE_OPTION_NAME+"\" options.");
					} else if (pbjJavaPackage == null) {
						System.err.println("WARNING, proto file ["+file.getAbsolutePath()+"] does not contain \""+PBJ_PACKAGE_OPTION_NAME+"\" or \""+PROTOC_JAVA_PACKAGE_OPTION_NAME+"\" options.");
					}
					// process imports
					final Set<String> fileImports = protoFileImports.computeIfAbsent(fullQualifiedFile, key -> new HashSet<>());
					for (var importStatement : parsedDoc.importStatement()) {
						final String importedFileName = importStatement.strLit().getText().replaceAll("\"","");
						// ignore standard google protobuf imports as we do not need them
						if (importedFileName.startsWith("google/protobuf")) {
							continue;
						}
						// now scan all src files to find import as there can be many src directories
						Optional<File> matchingSrcFile = StreamSupport.stream(allSrcFiles.spliterator(),false)
								.filter(srcFile -> srcFile.getAbsolutePath().endsWith(importedFileName))
								.findFirst();
						if (matchingSrcFile.isPresent()) {
							fileImports.add(matchingSrcFile.get().getAbsolutePath());
						} else {
							throw new PbjCompilerException("Import \""+importedFileName+"\" in proto file \""+file.getAbsolutePath()+"\" can not be found in src files.");
						}
					}
					// process message and enum defs
					final String fileLevelJavaPackage = (pbjJavaPackage != null) ? pbjJavaPackage : protocJavaPackage;
					for (var item : parsedDoc.topLevelDef()) {
						if (item.messageDef() != null) buildMessage(fullQualifiedFile, fileLevelJavaPackage, protocJavaPackage, item.messageDef());
						if (item.enumDef() != null) buildEnum(fullQualifiedFile, fileLevelJavaPackage, protocJavaPackage, item.enumDef());
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

//		printDebug();
	}

	/** Debug dump internal state */
	private void printDebug() {
		System.out.println("== Package Map =================================================================");
		for (var entry: pbjPackageMap.entrySet()) {
			System.out.println("entry = " + entry.getKey()+" = "+entry.getValue());
		}
		System.out.println("== Enum Names =================================================================");
		for(var enumName: enumNames) {
			System.out.println("enumName = " + enumName);
		}
		System.out.println("== Proto File Imports =================================================================");
		for (var entry: protoFileImports.entrySet()) {
			System.out.println("FILE - " + entry.getKey());
			for(var imp: entry.getValue()) {
				System.out.println("    IMPORT - " + imp);
			}
		}
		System.out.println("== Message Imports =================================================================");
		for (var entry: msgAndEnumByFile.entrySet()) {
			System.out.println("FILE - " + entry.getKey());
			for(var entry2: entry.getValue().entrySet()) {
				System.out.println("    "+entry2.getKey()+" -> " + entry2.getValue());
			}
		}
		System.out.println("===================================================================");
	}

	/**
	 * Walk a msgDef def and build packages and enums lists
	 *
	 * @param fullQualifiedFile          the fully qualified path and file name for file containing the message
	 * @param fileLevelPbjJavaPackage    the pbj java package relative to root package that the msgDef should be in
	 * @param fileLevelProtocJavaPackage the protoc java package relative to root package that the msgDef should be in
	 * @param msgDef                     protobuf msgDef def
	 */
	private void buildMessage(final String fullQualifiedFile,
							  final String fileLevelPbjJavaPackage,
							  final String fileLevelProtocJavaPackage,
							  final MessageDefContext msgDef) {
		final var msgName = msgDef.messageName().getText();
		// check for msgDef/enum level pbj package level override option
		String messagePbjPackage = fileLevelPbjJavaPackage;
		for(final var element: msgDef.messageBody().messageElement()) {
			final var option = element.optionStatement();
			if (option != null) {
				if (PBJ_MESSAGE_PACKAGE_OPTION_NAME.equals(option.optionName().getText())) {
					messagePbjPackage = option.constant().getText().replaceAll("\"","");
				}
			}
		}
		final String fullyQualifiedMessage = getFullyQualifiedProtoNameForMsgOrEnum(msgDef);
		// insert into maps
		pbjPackageMap.put(fullyQualifiedMessage, messagePbjPackage);
		protocPackageMap.put(fullyQualifiedMessage, fileLevelProtocJavaPackage);
		msgAndEnumByFile.computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>()).put(msgName,fullyQualifiedMessage);

		// handle child messages and enums
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) {
				buildMessage(fullQualifiedFile, messagePbjPackage, fileLevelProtocJavaPackage, item.messageDef());
			}
			if (item.enumDef() != null) {
				buildEnum(fullQualifiedFile, messagePbjPackage, fileLevelProtocJavaPackage, item.enumDef());
			}
		}
	}

	/**
	 * Walk an enum def and build packages and enums lists
	 *
	 * @param fullQualifiedFile          the fully qualified path and file name for file containing the enum
	 * @param fileLevelPbjJavaPackage    the pbj java package relative to root package that the msgDef should be in
	 * @param fileLevelProtocJavaPackage the protoc java package relative to root package that the msgDef should be in
	 * @param enumDef                    protobuf enum def
	 */
	private void buildEnum(final String fullQualifiedFile,
					   final String fileLevelPbjJavaPackage,
					   final String fileLevelProtocJavaPackage,
					   final EnumDefContext enumDef) {
		final var enumName = enumDef.enumName().getText();
		// check for msgDef/enum level pbj package level override option
		String enumPbjPackage = fileLevelPbjJavaPackage;
		for(final var element: enumDef.enumBody().enumElement()) {
			final var option = element.optionStatement();
			if (option != null) {
				if (PBJ_ENUM_PACKAGE_OPTION_NAME.equals(option.optionName().getText())) {
					enumPbjPackage = option.constant().getText().replaceAll("\"","");
				}
			}
		}
		// insert into maps
		final var fullQualifiedEnumName = getFullyQualifiedProtoNameForMsgOrEnum(enumDef);
		pbjPackageMap.put(fullQualifiedEnumName, enumPbjPackage);
		protocPackageMap.put(fullQualifiedEnumName, fileLevelProtocJavaPackage);
		enumNames.add(fullQualifiedEnumName);
		msgAndEnumByFile.computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>()).put(enumName,fullQualifiedEnumName);
	}

	/**
	 * Get part of the fully qualified protobuf message name for a ParserRuleContext. This walks up parse tree and
	 * computes name. So = <code> proto package + '.' + parent messages + '.' + message/enum name</code>
	 *
	 * @param ruleContext The ParserRuleContext to compute qualified name for, can be MessageDefContext or EnumDefContext
	 * @return part of fully qualified protobuf name
	 */
	private static String getFullyQualifiedProtoNameForMsgOrEnum(final ParserRuleContext ruleContext) {
		String thisName = "";
		if (ruleContext instanceof final Protobuf3Parser.ProtoContext parsedDoc) {
			// get proto package
			final var packageStatement = parsedDoc.packageStatement().stream().findFirst();
			thisName = packageStatement.isEmpty() ? "" : packageStatement.get().fullIdent().getText();
		} else if (ruleContext instanceof final EnumDefContext enumDef) {
			final String parentPart = getFullyQualifiedProtoNameForMsgOrEnum(enumDef.getParent());
			thisName = getFullyQualifiedProtoNameForMsgOrEnum(enumDef.getParent()) + "." + enumDef.enumName().getText();
		} else if (ruleContext instanceof final MessageDefContext msgDef) {
			thisName = getFullyQualifiedProtoNameForMsgOrEnum(msgDef.getParent()) + "." + msgDef.messageName().getText();
		} else if (ruleContext.getParent() != null) {
			thisName = getFullyQualifiedProtoNameForMsgOrEnum(ruleContext.getParent());
		}
		return removingLeadingDot(thisName);
	}
}
