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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        static String formatForEach(
                final List<RPC> rpcList,
                final Function<RPC, String> formatter,
                final String delimiter,
                final int indent) {
            return rpcList.stream()
                    .map(formatter)
                    .collect(Collectors.joining(delimiter))
                    .indent(indent);
        }

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
                            .mapRequest(bytes -> parse$simpleRequestType(bytes.toReadableSequentialData(), options))
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

        String formatMethodDescriptorDeclaration() {
            return "private MethodDescriptor<$requestType, $replyType> $methodNameDescriptor;"
                    .replace("$methodName", name)
                    .replace("$requestType", requestType)
                    .replace("$replyType", replyType);
        }

        String formatMethodDescriptorGetter() {
            final String type;
            if (!requestStream && !replyStream) {
                type = "UNARY";
            } else if (requestStream && !replyStream) {
                type = "CLIENT_STREAMING";
            } else if (!requestStream && replyStream) {
                type = "SERVER_STREAMING";
            } else {
                type = "BIDI_STREAMING";
            }

            return """
                    private MethodDescriptor<$requestType, $replyType> $methodNameDescriptor() {
                        if ($methodNameDescriptor != null) {
                            return $methodNameDescriptor;
                        }
                        return $methodNameDescriptor = MethodDescriptor.<$requestType, $replyType>newBuilder()
                               .setType(MethodDescriptor.MethodType.$methodType)
                               .setFullMethodName("/" + FULL_NAME + "/$methodName")
                               .setRequestMarshaller(
                    $requestMarshaller
                               )
                               .setResponseMarshaller(
                    $replyMarshaller
                               )
                               .build();
                    }
                    """
                    .replace("$methodName", name)
                    .replace("$requestType", requestType)
                    .replace("$requestMarshaller", formatMarshaller(requestType).indent(DEFAULT_INDENT * 4))
                    .replace("$replyType", replyType)
                    .replace("$replyMarshaller", formatMarshaller(replyType).indent(DEFAULT_INDENT * 4))
                    .replace("$methodType", type);
        }

        private static String formatMarshaller(final String dataType) {
            return """
                    new MethodDescriptor.Marshaller<>() {
                        @Override
                        public InputStream stream(final $dataType t) {
                            return serialize$dataType(t, options).toInputStream();
                        }
                        @Override
                        public $dataType parse(final InputStream inputStream) {
                            try {
                                return parse$dataType(new ReadableStreamingData(inputStream), options);
                            } catch (ParseException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    """
                    .replace("$dataType", dataType);
        }

        String formatMethodImplementation() {
            final String returnType;
            final String arguments;
            final String refType;
            final String listener;
            final String returnClause;

            // spotless:off
            if (!requestStream && !replyStream) {
                // type = "UNARY";
                returnType = "$replyType";
                arguments = "@NonNull final $requestType request";
                refType = "$replyType";
                listener = """
                        @Override
                        public void onMessage(final $replyType response) {
                            ref.set(response);
                            latch.countDown();
                        }
                        """;
                returnClause = "return ref.get();";
            } else if (!requestStream && replyStream) {
                //type = "SERVER_STREAMING";
                returnType = "void";
                arguments = "@NonNull final $requestType request, @NonNull final Pipeline<? super $replyType> replies";
                refType = "Status";
                listener = """
                        @Override
                        public void onMessage(final $replyType response) {
                            replies.onNext(response);
                            call.request(1);
                        }
                        @Override
                        public void onClose(final Status status, final Metadata trailers) {
                            ref.set(status);
                            latch.countDown();
                        }
                        """;
                returnClause = """
                        if (Status.OK.equals(ref.get())) {
                            replies.onComplete();
                        } else {
                            RuntimeException ex = ref.get().asRuntimeException();
                            replies.onError(ex);
                            throw ex;
                        }
                        """;
            } else {
                //type = "CLIENT_STREAMING";
                //type = "BIDI_STREAMING";
                // These two types use the exact same method signatures and behave the same way.
                // In theory, the client should be restricted to receive no more than one (1) reply
                // in the CLIENT_STREAMING case. However, we don't hard-code this limitation.
                returnType = "Pipeline<? super $requestType>";
                arguments = "@NonNull final Pipeline<? super $replyType> replies";
                refType = null;
                listener = """
                        @Override
                        public void onMessage(final $replyType response) {
                            replies.onNext(response);
                            call.request(1);
                        }
                        @Override
                        public void onClose(final Status status, final Metadata trailers) {
                            if (Status.OK.equals(status)) {
                                replies.onComplete();
                            } else {
                                replies.onError(status.asRuntimeException());
                            }
                        }
                        """;
                returnClause = """
                        call.request(1);
                        return new Pipeline<>() {
                            @Override
                            public void onSubscribe(Flow.Subscription subscription) {
                                subscription.request(Long.MAX_VALUE); // turn off flow control
                            }
                            @Override
                            public void onNext(final $requestType request) {
                                call.sendMessage(request);
                            }
                            @Override
                            public void onError(Throwable throwable) {
                                call.cancel(throwable.getMessage(), throwable);
                                replies.onError(throwable);
                            }
                            @Override
                            public void onComplete() {
                                call.halfClose();
                                replies.onComplete();
                            }
                        };
                        """;
            }

            final String refDeclaration;
            final String sendAndWaitLoop;
            if (refType == null) {
                // The client would be sending multiple requests through the returned Pipeline
                refDeclaration = "";
                sendAndWaitLoop = "";
            } else {
                // The client sends just a single request, so we need to block and wait for replies
                refDeclaration = """
                        final AtomicReference<$refType> ref = new AtomicReference<>();
                        final CountDownLatch latch = new CountDownLatch(1);
                        """
                        .replace("$refType", refType)
                        .indent(DEFAULT_INDENT);
                sendAndWaitLoop = """
                        call.request(1);
                        call.sendMessage(request);
                        call.halfClose();
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        """
                        .indent(DEFAULT_INDENT);
            }

            return """
                    @Override
                    public $returnType $methodName($arguments) {
                        CallOptions callOptions = CallOptions.DEFAULT;
                        if (options.authority().isPresent()) {
                            callOptions = callOptions.withAuthority(options.authority().get());
                        }
                        final ClientCall<$requestType, $replyType> call = channel.newCall($methodNameDescriptor(), callOptions);
                    $refDeclaration
                        final ClientCall.Listener<$replyType> listener = new ClientCall.Listener<>() {
                    $listener
                        };
                        call.start(listener, new Metadata());
                    $sendAndWaitLoop
                    $returnClause
                    }
                    """
                    .replace("$returnType", returnType)
                    .replace("$arguments", arguments)
                    .replace("$refDeclaration", refDeclaration)
                    .replace("$sendAndWaitLoop", sendAndWaitLoop)
                    .replace("$listener", listener.indent(DEFAULT_INDENT * 2))
                    .replace("$returnClause", returnClause.indent(DEFAULT_INDENT))
                    .replace("$methodName", name)
                    .replace("$requestType", requestType)
                    .replace("$replyType", replyType)
                    ;
            // spotless:on
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
        writer.addImport("com.hedera.pbj.runtime.io.ReadableSequentialData");
        writer.addImport("com.hedera.pbj.runtime.io.stream.ReadableStreamingData");
        writer.addImport("com.hedera.pbj.runtime.ParseException");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport("io.grpc.CallOptions");
        writer.addImport("io.grpc.Channel");
        writer.addImport("io.grpc.ClientCall");
        writer.addImport("io.grpc.Metadata");
        writer.addImport("io.grpc.MethodDescriptor");
        writer.addImport("io.grpc.Status");
        writer.addImport("java.io.InputStream");
        writer.addImport("java.util.List");
        writer.addImport("java.util.Arrays");
        writer.addImport("java.util.Objects");
        writer.addImport("java.util.concurrent.Flow");
        writer.addImport("java.util.concurrent.CountDownLatch");
        writer.addImport("java.util.concurrent.atomic.AtomicReference");

        rpcList.forEach(rpc -> {
            writer.addImport(rpc.requestTypePackage + "." + rpc.requestType);
            writer.addImport(rpc.replyTypePackage + "." + rpc.replyType);
        });

        // spotless:off
        writer.append("""
                $javaDocComment
                $deprecated$public interface $serviceName$suffix extends ServiceInterface {
                    /** The simple name of the service. */
                    public static final String SERVICE_NAME = "$serviceName";
                
                    /** The full name of the service. */
                    public static final String FULL_NAME = "$fullyQualifiedProtoServiceName";
                
                    enum $serviceNameMethod implements Method {
                $methodNames
                    }
                
                $methodSignatures
                
                    @NonNull
                    default String serviceName() {
                        return $serviceName$suffix.SERVICE_NAME;
                    }
                
                    @NonNull
                    default String fullName() {
                        return $serviceName$suffix.FULL_NAME;
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

                    /** A client class for $serviceName. */
                    public class $serviceNameClient implements $serviceName$suffix {
                        private final RequestOptions options;
                        private final Channel channel;

                        // NOTE: method descriptors are uniquely defined by methods themselves and the options above,
                        // and re-creating them is idempotent. Therefore, to simplify the code, we don't use
                        // any synchronization for lazy getters below as the values will stabilize eventually.
                $methodDescriptorsDeclarations
                        public $serviceNameClient(final @NonNull RequestOptions options, final @NonNull Channel channel) {
                            this.options = Objects.requireNonNull(options);
                            this.channel = Objects.requireNonNull(channel);
                        }

                $methodDescriptorsGetters

                $methodImplementations
                    }
                }
                """
                .replace("$javaDocComment", javaDocComment)
                .replace("$deprecated$", deprecated)
                .replace("$serviceName", serviceName)
                .replace("$suffix", SUFFIX)
                .replace("$methodNames", RPC.formatForEach(rpcList, RPC::name, ",\n", DEFAULT_INDENT * 2))
                .replace("$methodSignatures", RPC.formatForEach(rpcList, RPC::formatSignature, "\n\n", DEFAULT_INDENT))
                .replace("$fullyQualifiedProtoServiceName", lookupHelper.getLookupHelper().getFullyQualifiedProtoNameForContext(serviceDef))
                .replace("$methodCaseStatements", RPC.formatForEach(rpcList, RPC::formatCaseStatement, "\n", DEFAULT_INDENT * 4))
                .replace("$requestReplySerdeMethods", formatRequestReplySerdeMethods(rpcList).indent(DEFAULT_INDENT))
                .replace("$methodDescriptorsDeclarations", RPC.formatForEach(rpcList, RPC::formatMethodDescriptorDeclaration, "\n", DEFAULT_INDENT * 2))
                .replace("$methodDescriptorsGetters", RPC.formatForEach(rpcList, RPC::formatMethodDescriptorGetter, "\n", DEFAULT_INDENT * 2))
                .replace("$methodImplementations", RPC.formatForEach(rpcList, RPC::formatMethodImplementation, "\n", DEFAULT_INDENT * 2))
        );
        // spotless:on
    }

    private static String formatRequestReplySerdeMethods(final List<RPC> rpcList) {
        return rpcList.stream()
                .flatMap(rpc -> Stream.of(rpc.requestType(), rpc.replyType()))
                .distinct()
                .flatMap(dataType -> Stream.of(formatParseMethod(dataType), formatSerializeMethod(dataType)))
                .collect(Collectors.joining("\n\n"));
    }

    private static String formatParseMethod(final String dataType) {
        // spotless:off
        return """
                @NonNull
                private static $requestType parse$simpleRequestType(@NonNull final ReadableSequentialData data, @NonNull final RequestOptions options) throws ParseException {
                    Objects.requireNonNull(data);
                    Objects.requireNonNull(options);

                    // Default to protobuf, and don't error out if both are set:
                    if (options.isJson() && !options.isProtobuf()) {
                        // not strict, and hard-code maxDepth for now:
                        return $requestType.JSON.parse(data, false, 16);
                    } else {
                        // not strict, and hard-code maxDepth for now:
                        return $requestType.PROTOBUF.parse(data, false, 16);
                    }
                }
                """
                .replace("$requestType", dataType)
                .replace("$simpleRequestType", dataType.replace(".", ""))
                ;
        // spotless:on
    }

    private static String formatSerializeMethod(final String dataType) {
        // spotless:off
        return """
                @NonNull
                private static Bytes serialize$simpleReplyType(@NonNull final $dataType reply, @NonNull final RequestOptions options) {
                    Objects.requireNonNull(reply);
                    Objects.requireNonNull(options);

                    // Default to protobuf, and don't error out if both are set:
                    if (options.isJson() && !options.isProtobuf()) {
                        return Bytes.wrap($dataType.JSON.toJSON(reply));
                    } else {
                        return $dataType.PROTOBUF.toBytes(reply);
                    }
                }
                """
                .replace("$dataType", dataType)
                .replace("$simpleReplyType", dataType.replace(".", ""))
                ;
        // spotless:on
    }
}
