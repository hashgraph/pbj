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

        private String formatMethodSignature(String methodModifier) {
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

            final String formattedModifier =
                    methodModifier == null || methodModifier.isBlank() ? "" : (methodModifier + " ");
            final StringBuilder sb = new StringBuilder();

            // return type:
            if (!requestStream && !replyStream) {
                sb.append("@NonNull\n");
                sb.append(formattedModifier);
                sb.append(replyType);
            } else if (!requestStream) {
                sb.append(formattedModifier);
                sb.append("void");
            } else {
                sb.append("@NonNull\n");
                sb.append(formattedModifier);
                sb.append("Pipeline<? super ").append(requestType).append('>');
            }

            // name and args:
            sb.append(' ').append(name).append('(');
            if (!requestStream) {
                sb.append("@NonNull final ").append(requestType).append(" request");
                if (replyStream) {
                    sb.append(", ");
                }
            }
            if (requestStream || replyStream) {
                sb.append("@NonNull final Pipeline<? super ").append(replyType).append("> replies");
            }
            sb.append(")");

            return sb.toString();
        }

        String formatMethodDeclaration() {
            final StringBuilder sb = new StringBuilder();
            if (javaDoc.contains("\n")) {
                sb.append("/**\n");
                sb.append(" * ").append(javaDoc.replaceAll("\n\s*", "\n * "));
                sb.append("\n */\n");
            } else {
                sb.append("/** ").append(javaDoc).append(" */\n");
            }

            sb.append(formatMethodSignature(null));
            sb.append(";");

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

        private String formatUnaryMethodImplementation() {
            return """
                        @Override
                        $methodSignature {
                            final AtomicReference<$replyType> replyRef = new AtomicReference<>();
                            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
                            final CountDownLatch latch = new CountDownLatch(1);
                            final Pipeline<$replyType> pipeline = new Pipeline<>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    subscription.request(Long.MAX_VALUE); // turn off flow control
                                }
                                @Override
                                public void onNext(final $replyType reply) {
                                    if (replyRef.get() != null) {
                                        throw new IllegalStateException("$methodName is unary, but received more than one reply. The latest reply is: " + reply);
                                    }
                                    replyRef.set(reply);
                                    latch.countDown();
                                }
                                @Override
                                public void onError(final Throwable throwable) {
                                    errorRef.set(throwable);
                                    latch.countDown();
                                }
                                @Override
                                public void onComplete() {
                                    latch.countDown();
                                }
                            };

                            final GrpcCall<$requestType, $replyType> call = grpcClient.createCall(
                                    FULL_NAME + "/$methodName",
                                    get$simpleRequestTypeCodec(requestOptions),
                                    get$simpleReplyTypeCodec(requestOptions),
                                    pipeline
                                    );
                            call.sendRequest(request, true);
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }

                            if (errorRef.get() != null) {
                                // Make a new exception to capture the stack trace. Simply re-throwing the original
                                // exception may look confusing because the stack trace would be misleading.
                                throw new RuntimeException(errorRef.get());
                            }

                            if (replyRef.get() != null) {
                                return replyRef.get();
                            }

                            throw new RuntimeException("Call to $methodName completed w/o receiving a reply or an error explicitly. The request was: " + request);
                        }
                        """
                    .replace("$methodSignature", formatMethodSignature("public"))
                    .replace("$requestType", requestType)
                    .replace("$simpleRequestType", requestType.replace(".", ""))
                    .replace("$replyType", replyType)
                    .replace("$simpleReplyType", replyType.replace(".", ""))
                    .replace("$methodName", name);
        }

        private String formatClientStreamingMethodImplementation() {
            return """
                        @Override
                        $methodSignature {
                            final AtomicReference<$replyType> replyRef = new AtomicReference<>();
                            final Pipeline<$replyType> pipeline = new Pipeline<>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    replies.onSubscribe(subscription);
                                }
                                @Override
                                public void onNext(final $replyType reply) {
                                    if (replyRef.get() != null) {
                                        throw new IllegalStateException("$methodName is clientStreaming, but received more than one reply. The latest reply is: " + reply);
                                    }
                                    replyRef.set(reply);
                                    replies.onNext(reply);
                                }
                                @Override
                                public void onError(final Throwable throwable) {
                                    replies.onError(throwable);
                                }
                                @Override
                                public void onComplete() {
                                    replies.onComplete();
                                }
                            };

                            final GrpcCall<$requestType, $replyType> call = grpcClient.createCall(
                                    FULL_NAME + "/$methodName",
                                    get$simpleRequestTypeCodec(requestOptions),
                                    get$simpleReplyTypeCodec(requestOptions),
                                    pipeline
                                    );

                            return new Pipeline<$requestType>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    // no-op
                                }
                                @Override
                                public void onNext(final $requestType request) {
                                    call.sendRequest(request, false);
                                }
                                @Override
                                public void onError(final Throwable throwable) {
                                    replies.onError(throwable);
                                }
                                @Override
                                public void onComplete() {
                                    call.completeRequests();
                                }
                            };
                        }
                        """
                    .replace("$methodSignature", formatMethodSignature("public"))
                    .replace("$requestType", requestType)
                    .replace("$simpleRequestType", requestType.replace(".", ""))
                    .replace("$replyType", replyType)
                    .replace("$simpleReplyType", replyType.replace(".", ""))
                    .replace("$methodName", name);
        }

        private String formatServerStreamingMethodImplementation() {
            return """
                        @Override
                        $methodSignature {
                            final CountDownLatch latch = new CountDownLatch(1);
                            final Pipeline<$replyType> pipeline = new Pipeline<>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    replies.onSubscribe(subscription);
                                }
                                @Override
                                public void onNext(final $replyType reply) {
                                    replies.onNext(reply);
                                }
                                @Override
                                public void onError(final Throwable throwable) {
                                    replies.onError(throwable);
                                    latch.countDown();
                                }
                                @Override
                                public void onComplete() {
                                    replies.onComplete();
                                    latch.countDown();
                                }
                            };

                            final GrpcCall<$requestType, $replyType> call = grpcClient.createCall(
                                    FULL_NAME + "/$methodName",
                                    get$simpleRequestTypeCodec(requestOptions),
                                    get$simpleReplyTypeCodec(requestOptions),
                                    pipeline
                                    );
                            call.sendRequest(request, true);
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }

                            // This method will return successfully even if the GRPC call failed. All server exceptions
                            // have been reported through replies.onError() where the client must handle them.
                            // Alternatively, the client could time out if the replies pipeline never saw onComplete().
                        }
                        """
                    .replace("$methodSignature", formatMethodSignature("public"))
                    .replace("$requestType", requestType)
                    .replace("$simpleRequestType", requestType.replace(".", ""))
                    .replace("$replyType", replyType)
                    .replace("$simpleReplyType", replyType.replace(".", ""))
                    .replace("$methodName", name);
        }

        private String formatBidiStreamingMethodImplementation() {
            return """
                        @Override
                        $methodSignature {
                            final Pipeline<$replyType> pipeline = new Pipeline<>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    replies.onSubscribe(subscription);
                                }
                                @Override
                                public void onNext(final $replyType reply) {
                                    replies.onNext(reply);
                                }
                                @Override
                                public void onError(final Throwable throwable) {
                                    replies.onError(throwable);
                                }
                                @Override
                                public void onComplete() {
                                    replies.onComplete();
                                }
                            };

                            final GrpcCall<$requestType, $replyType> call = grpcClient.createCall(
                                    FULL_NAME + "/$methodName",
                                    get$simpleRequestTypeCodec(requestOptions),
                                    get$simpleReplyTypeCodec(requestOptions),
                                    pipeline
                                    );

                            return new Pipeline<$requestType>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    // no-op
                                }
                                @Override
                                public void onNext(final $requestType request) {
                                    call.sendRequest(request, false);
                                }
                                @Override
                                public void onError(final Throwable throwable) {
                                    replies.onError(throwable);
                                }
                                @Override
                                public void onComplete() {
                                    call.completeRequests();
                                    replies.onComplete();
                                }
                            };
                        }
                        """
                    .replace("$methodSignature", formatMethodSignature("public"))
                    .replace("$requestType", requestType)
                    .replace("$simpleRequestType", requestType.replace(".", ""))
                    .replace("$replyType", replyType)
                    .replace("$simpleReplyType", replyType.replace(".", ""))
                    .replace("$methodName", name);
        }

        String formatMethodImplementation() {
            // Different method types implementations share only a very tiny number of common lines of code,
            // and while they look a bit similar, the implementations differ in details a lot. So it doesn't make
            // sense to try and generalize the format. Instead, each implementation has its own dedicated formatter
            // method defined above.
            if (!requestStream && !replyStream) {
                return formatUnaryMethodImplementation();
            } else if (requestStream && !replyStream) {
                return formatClientStreamingMethodImplementation();
            } else if (!requestStream && replyStream) {
                return formatServerStreamingMethodImplementation();
            } else {
                return formatBidiStreamingMethodImplementation();
            }
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
            } else if (item.optionComment() != null || item.emptyStatement_() != null) {
                // Ignore these
            } else {
                System.err.printf("ServiceGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }

        writer.addImport("com.hedera.pbj.runtime.grpc.GrpcCall");
        writer.addImport("com.hedera.pbj.runtime.grpc.GrpcClient");
        writer.addImport("com.hedera.pbj.runtime.grpc.Pipeline");
        writer.addImport("com.hedera.pbj.runtime.grpc.Pipelines");
        writer.addImport("com.hedera.pbj.runtime.grpc.ServiceInterface");
        writer.addImport("com.hedera.pbj.runtime.Codec");
        writer.addImport("com.hedera.pbj.runtime.ParseException");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.Bytes");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport("java.util.List");
        writer.addImport("java.util.Arrays");
        writer.addImport("java.util.Objects");
        writer.addImport("java.util.concurrent.CountDownLatch");
        writer.addImport("java.util.concurrent.Flow");
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
                
                $getCodecMethods
                $requestReplySerdeMethods
                
                    /** A client class for $serviceName. */
                    public class $serviceNameClient implements $serviceName$suffix {
                        private final GrpcClient grpcClient;
                        private final RequestOptions requestOptions;

                        public $serviceNameClient(@NonNull final GrpcClient grpcClient, @NonNull final RequestOptions requestOptions) {
                            this.grpcClient = Objects.requireNonNull(grpcClient);
                            this.requestOptions = Objects.requireNonNull(requestOptions);
                        }
                
                        @Override
                        public void close() {
                            grpcClient.close();
                        }

                $methodImplementations
                    }
                }
                """
                .replace("$javaDocComment", javaDocComment)
                .replace("$deprecated$", deprecated)
                .replace("$serviceName", serviceName)
                .replace("$suffix", SUFFIX)
                .replace("$methodNames", RPC.formatForEach(rpcList, RPC::name, ",\n", DEFAULT_INDENT * 2))
                .replace("$methodSignatures", RPC.formatForEach(rpcList, RPC::formatMethodDeclaration, "\n\n", DEFAULT_INDENT))
                .replace("$fullyQualifiedProtoServiceName", lookupHelper.getLookupHelper().getFullyQualifiedProtoNameForContext(serviceDef))
                .replace("$methodCaseStatements", RPC.formatForEach(rpcList, RPC::formatCaseStatement, "\n", DEFAULT_INDENT * 4))
                .replace("$getCodecMethods", formatGetCodecMethods(rpcList).indent(DEFAULT_INDENT))
                .replace("$requestReplySerdeMethods", formatRequestReplySerdeMethods(rpcList).indent(DEFAULT_INDENT))
                .replace("$methodImplementations", RPC.formatForEach(rpcList, RPC::formatMethodImplementation, "\n\n", DEFAULT_INDENT * 2))
        );
        // spotless:on
    }

    private static String formatGetCodecMethods(final List<RPC> rpcList) {
        return rpcList.stream()
                .flatMap(rpc -> Stream.of(rpc.requestType(), rpc.replyType()))
                .distinct()
                .map(ServiceGenerator::formatGetCodecMethod)
                .collect(Collectors.joining("\n\n"));
    }

    private static String formatGetCodecMethod(final String dataType) {
        // spotless:off
        return """
                @NonNull
                private static Codec<$dataType> get$simpleDataTypeCodec(@NonNull final RequestOptions options) {
                    Objects.requireNonNull(options);

                    // Default to protobuf, and don't error out if both are set:
                    if (options.isJson() && !options.isProtobuf()) {
                        return $dataType.JSON;
                    } else {
                        return $dataType.PROTOBUF;
                    }
                }
                """
                .replace("$dataType", dataType)
                .replace("$simpleDataType", dataType.replace(".", ""))
                ;
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
                private static $requestType parse$simpleRequestType(@NonNull final Bytes message, @NonNull final RequestOptions options) throws ParseException {
                    Objects.requireNonNull(message);
                    Objects.requireNonNull(options);

                    // not strict, no unknown fields, hard-code maxDepth for now, and use custom maxSize:
                    return get$simpleRequestTypeCodec(options).parse(message.toReadableSequentialData(), false, false, 16, options.maxMessageSizeBytes());
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
                private static Bytes serialize$simpleReplyType(@NonNull final $replyType reply, @NonNull final RequestOptions options) {
                    Objects.requireNonNull(reply);
                    Objects.requireNonNull(options);

                    return get$simpleReplyTypeCodec(options).toBytes(reply);
                }
                """
                .replace("$replyType", replyType)
                .replace("$simpleReplyType", replyType.replace(".", ""))
                ;
        // spotless:on
    }
}
