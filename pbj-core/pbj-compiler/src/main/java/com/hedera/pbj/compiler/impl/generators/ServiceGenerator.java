// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.Common.cleanDocStr;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service code generator.
 */
@SuppressWarnings({"EscapedSpace"})
public final class ServiceGenerator {

    /**
     * Suffix for the generated interface name that follows the service name.
     *
     * Ideally we'd add the "Service" suffix.
     * However, our protobufs add this suffix to their service names already.
     * This would result in e.g. "TokenServiceService.java" which looks odd.
     * So we add "Interface" which makes perfect sense because we generate a Java interface here.
     */
    public static final String SUFFIX = "Interface";

    /** Record for an RPC method. */
    private record RPC(
            String name,
            boolean deprecated,
            String javaDoc,
            boolean requestStream,
            String requestType,
            String requestTypePackage,
            boolean replyStream,
            String replyType,
            String replyTypePackage) {

        String formatSignature() {
            // Examples:
            //    // Unary, a single request/response call.
            //    HelloReply sayHello(HelloRequest request);
            //
            //    // A stream of messages coming from the client, with a single response from the server.
            //    Pipeline<? super HelloRequest> sayHelloStreamRequest(Pipeline<? super HelloReply> replies);
            //
            //    // A single request from the client, with a stream of responses from the server.
            //    void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies);
            //
            //    // A bidirectional stream of requests and responses between the client and the server.
            //    Pipeline<? super HelloRequest> sayHelloStreamBidi(Pipeline<? super HelloReply> replies);

            final StringBuilder sb = new StringBuilder();
            if (javaDoc.contains("\n")) {
                sb.append("/**\n");
                sb.append(" * ").append(javaDoc.replaceAll("\n\s*", "\n * "));
                sb.append("\n */\n");
            } else {
                sb.append("/** ").append(javaDoc).append(" */\n");
            }

            // return type:
            if (!requestStream && !replyStream) {
                sb.append(replyType);
            } else if (!requestStream) {
                sb.append("void");
            } else {
                sb.append("Pipeline<? super ").append(requestType).append('>');
            }

            // name and args:
            sb.append(' ').append(name).append('(');
            if (!requestStream) {
                sb.append(requestType).append(" request");
                if (replyStream) {
                    sb.append(", ");
                }
            }
            if (requestStream || replyStream) {
                sb.append("Pipeline<? super ").append(replyType).append("> replies");
            }
            sb.append(");");

            return sb.toString();
        }

        String formatCaseStatement() {
            final String kind;
            if (!requestStream && !replyStream) {
                kind = "unary";
            } else if (requestStream && !replyStream) {
                kind = "clientStreaming";
            } else if (!requestStream && replyStream) {
                kind = "serverStreaming";
            } else {
                kind = "bidiStreaming";
            }

            return """
                    case $methodName -> Pipelines.<$requestType, $replyType>$kind()
                            .mapRequest(bytes -> parse$simpleRequestType(bytes, options))
                            .method(this::$methodName)
                            .mapResponse(reply -> serialize$simpleReplyType(reply, options))
                            .respondTo(replies)
                            .build();
                    """
                    .replace("$methodName", name)
                    .replace("$requestType", requestType)
                    .replace("$simpleRequestType", requestType.replace(".", ""))
                    .replace("$replyType", replyType)
                    .replace("$simpleReplyType", replyType.replace(".", ""))
                    .replace("$kind", kind);
        }
    }

    /**
     * Generate a Java service interface from protobuf service
     *
     * @param serviceDef the parsed service def
     * @param writer the writer to append the generated service to
     * @param lookupHelper Lookup helper for package information
     * @throws IOException if there was a problem writing generated code
     */
    public static void generateService(
            final Protobuf3Parser.ServiceDefContext serviceDef,
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String serviceName = serviceDef.serviceName().getText();
        final String javaDocComment = (serviceDef.docComment() == null)
                ? ""
                : cleanDocStr(serviceDef.docComment().getText().replaceAll("\n \\*\s*\n", "\n * <br>\n"));

        String deprecated = "";

        List<RPC> rpcList = new ArrayList<>();

        for (var item : serviceDef.serviceElement()) {
            if (item.rpc() != null) {
                final String name = item.rpc().rpcName().ident().getText();

                final String javaDoc = cleanDocStr(
                        (item.rpc().docComment() == null
                                        || item.rpc().docComment().getText().isBlank())
                                ? name
                                : item.rpc()
                                        .docComment()
                                        .getText()
                                        .replaceAll("[\t ]*/\\*\\*([\n\t ]+\\*\s+)?", "") // remove doc start indenting
                                        .replaceAll("/\\*\\*", "") //  remove doc start
                                        .replaceAll("[\n\t ]+\\*/", "") //  remove doc end
                                        .replaceAll("\n[\t\s]+\\*\\*?", "\n") // remove doc indenting
                                        .replaceAll("/n\s*/n", "/n") //  remove empty lines
                        );

                boolean deprecatedServiceValue = false;
                if (item.rpc().optionStatement() != null) {
                    for (var option : item.rpc().optionStatement()) {
                        if ("deprecated".equals(option.optionName().getText())) {
                            deprecatedServiceValue = true;
                        } else {
                            System.err.printf("Unhandled Option: %s%n", option.getText());
                        }
                    }
                }

                final Protobuf3Parser.MessageTypeContext requestTypeMTC =
                        item.rpc().rpcRequestType().rpcType().messageType();
                final Protobuf3Parser.MessageTypeContext replyTypeMTC =
                        item.rpc().rpcReplyType().rpcType().messageType();

                rpcList.add(new RPC(
                        name,
                        deprecatedServiceValue,
                        javaDoc,
                        item.rpc().rpcRequestType().rpcType().STREAM() != null,
                        lookupHelper.getCompleteClass(requestTypeMTC),
                        lookupHelper.getPackage(FileType.MODEL, requestTypeMTC),
                        item.rpc().rpcReplyType().rpcType().STREAM() != null,
                        lookupHelper.getCompleteClass(replyTypeMTC),
                        lookupHelper.getPackage(FileType.MODEL, replyTypeMTC)));
            } else if (item.optionStatement() != null) {
                if ("deprecated".equals(item.optionStatement().optionName().getText())) {
                    deprecated = "@Deprecated ";
                } else {
                    System.err.printf(
                            "Unhandled Option: %s%n", item.optionStatement().getText());
                }
            } else {
                System.err.printf("ServiceGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }

        writer.addImport("com.hedera.pbj.runtime.grpc.Pipeline");
        writer.addImport("com.hedera.pbj.runtime.grpc.Pipelines");
        writer.addImport("com.hedera.pbj.runtime.grpc.ServiceInterface");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.Bytes");
        writer.addImport("com.hedera.pbj.runtime.ParseException");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport("java.util.List");
        writer.addImport("java.util.Arrays");
        writer.addImport("java.util.Objects");

        rpcList.forEach(rpc -> {
            writer.addImport(rpc.requestTypePackage + "." + rpc.requestType);
            writer.addImport(rpc.replyTypePackage + "." + rpc.replyType);
        });

        // spotless:off
        writer.append("""
                $javaDocComment
                $deprecated$public interface $serviceName$suffix extends ServiceInterface {
                    enum $serviceNameMethod implements Method {
                $methodNames
                    }
                
                $methodSignatures
                
                    @NonNull
                    default String serviceName() {
                        return "$serviceName";
                    }
                
                    @NonNull
                    default String fullName() {
                        return "$fullyQualifiedProtoServiceName";
                    }
                
                    @NonNull
                    default List<Method> methods() {
                        return Arrays.asList($serviceNameMethod.values());
                    }
                
                    @Override
                    @NonNull
                    default Pipeline<? super Bytes> open(
                            final @NonNull Method method,
                            final @NonNull RequestOptions options,
                            final @NonNull Pipeline<? super Bytes> replies) {
                        final var m = ($serviceNameMethod) method;
                        try {
                            return switch (m) {
                $methodCaseStatements
                            };
                        } catch (Exception e) {
                            replies.onError(e);
                            return Pipelines.noop();
                        }
                    }
                
                $requestReplySerdeMethods
                }
                """
                .replace("$javaDocComment", javaDocComment)
                .replace("$deprecated$", deprecated)
                .replace("$serviceName", serviceName)
                .replace("$suffix", SUFFIX)
                .replace("$methodNames", rpcList.stream().map(RPC::name).collect(Collectors.joining(",\n")).indent(DEFAULT_INDENT * 2))
                .replace("$methodSignatures", rpcList.stream().map(RPC::formatSignature).collect(Collectors.joining("\n\n")).indent(DEFAULT_INDENT))
                .replace("$fullyQualifiedProtoServiceName", lookupHelper.getLookupHelper().getFullyQualifiedProtoNameForContext(serviceDef))
                .replace("$methodCaseStatements", rpcList.stream().map(RPC::formatCaseStatement).collect(Collectors.joining("\n")).indent(DEFAULT_INDENT * 4))
                .replace("$requestReplySerdeMethods", formatRequestReplySerdeMethods(rpcList).indent(DEFAULT_INDENT))
        );
        // spotless:on
    }

    private static String formatRequestReplySerdeMethods(final List<RPC> rpcList) {
        return rpcList.stream()
                        .map(RPC::requestType)
                        .distinct()
                        .map(ServiceGenerator::formatParseRequestMethod)
                        .collect(Collectors.joining("\n\n"))
                + "\n"
                + rpcList.stream()
                        .map(RPC::replyType)
                        .distinct()
                        .map(ServiceGenerator::formatSerializeReplyMethod)
                        .collect(Collectors.joining("\n\n"));
    }

    private static String formatParseRequestMethod(final String requestType) {
        // spotless:off
        return """
                @NonNull
                private $requestType parse$simpleRequestType(@NonNull final Bytes message, @NonNull final RequestOptions options) throws ParseException {
                    Objects.requireNonNull(message);
                    Objects.requireNonNull(options);

                    // Default to protobuf, and don't error out if both are set:
                    if (options.isJson() && !options.isProtobuf()) {
                        // not strict, and hard-code maxDepth for now:
                        return $requestType.JSON.parse(message.toReadableSequentialData(), false, 16);
                    } else {
                        // not strict, and hard-code maxDepth for now:
                        return $requestType.PROTOBUF.parse(message.toReadableSequentialData(), false, 16);
                    }
                }
                """
                .replace("$requestType", requestType)
                .replace("$simpleRequestType", requestType.replace(".", ""))
                ;
        // spotless:on
    }

    private static String formatSerializeReplyMethod(final String replyType) {
        // spotless:off
        return """
                @NonNull
                private Bytes serialize$simpleReplyType(@NonNull final $replyType reply, @NonNull final RequestOptions options) {
                    Objects.requireNonNull(reply);
                    Objects.requireNonNull(options);

                    // Default to protobuf, and don't error out if both are set:
                    if (options.isJson() && !options.isProtobuf()) {
                        return Bytes.wrap($replyType.JSON.toJSON(reply));
                    } else {
                        return $replyType.PROTOBUF.toBytes(reply);
                    }
                }
                """
                .replace("$replyType", replyType)
                .replace("$simpleReplyType", replyType.replace(".", ""))
                ;
        // spotless:on
    }
}
