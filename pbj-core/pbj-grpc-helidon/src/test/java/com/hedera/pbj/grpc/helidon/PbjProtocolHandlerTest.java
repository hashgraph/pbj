/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.grpc.helidon;

import static com.hedera.pbj.runtime.grpc.GrpcStatus.DEADLINE_EXCEEDED;
import static com.hedera.pbj.runtime.grpc.GrpcStatus.INVALID_ARGUMENT;
import static com.hedera.pbj.runtime.grpc.GrpcStatus.OK;
import static io.helidon.http.Status.OK_200;
import static io.helidon.http.http2.Http2StreamState.CLOSED;
import static io.helidon.http.http2.Http2StreamState.HALF_CLOSED_LOCAL;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.pbj.grpc.helidon.config.PbjConfig;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import greeter.HelloReply;
import greeter.HelloRequest;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriEncoding;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.metrics.api.Metrics;
import io.netty.handler.codec.http2.Http2Flags;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The primary set of test cases for gRPC handling in Helidon with PBJ.
 *
 * <p>The tests in this class were created by reviewing the
 * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2 specification</a>, and by
 * checking on the behavior of the go gRPC server implementation. The specification and primary implementation were not
 * always in agreement. Where there was a discrepancy, it was noted in these tests.
 *
 * <p>These tests assume Helidon handles HTTP2 correctly. However, the {@link PbjProtocolHandler} is responsible for
 * setting some HTTP2 values, such as advancing the flow control window correctly, and handling the stream status
 * correctly, so those things are checked.
 *
 * <p>To understand these tests, you need to be familiar with the gRPC over HTTP2 specification, and with the HTTP/2
 * specification. At a high level, the HTTP/2 specification defines "streams", "frames", and "states". Over a single
 * physical connection a client and server can multiplex many streams. Data sent or received for each stream is "framed"
 * and transmitted over the connection. The connection "state" indicates the lifecycle phase of the stream. In these
 * tests, it is critical to verify the state of the server during various scenarios, and to verify that the server is
 * sending the correct frames back to the client. We are not worried with basic HTTP/2 framing and rules, since Helidon
 * handles that. We only need to worry about the header and data frames that make up the main part of the lifecycle of
 * the stream, since this is the part that {@link PbjProtocolHandler} impacts.
 */
class PbjProtocolHandlerTest {

    @BeforeEach
    void setUp() {
        // The Helidon metrics are global, so we need to reset them before each test, because we want to verify
        // in EVERY TEST the impact of that test on the metrics, to make sure the metrics are useful and correct.
        Metrics.globalRegistry().close();
    }

    /**
     * Tests the behavior of the {@link PbjProtocolHandler} when handling the headers of a gRPC request.
     *
     * <p>NOTE: The pseudo-headers :method, :scheme, and :path are required, but they are not handled by the
     * PbjProtocolHandler. They are handled by the PbjProtocolSelector. So we don't test for them here.
     *
     * <p>NOTE: The "TE" header, although specified in the gRPC over HTTP2 spec, is not required. The HTTP2
     * specification says: "the TE header field, which MAY be present in an HTTP/2 request [...] MUST NOT
     * contain any value other than 'trailers'". So we don't need to worry about handling it, the HTTP/2 server
     * implementation should. We'll just ignore it.
     *
     * <p>NOTE: Another pseudo-header, :authority, is optional. It doesn't matter to use. It could be used for logging,
     * but we leave that to any reverse proxies, this server implementation doesn't care about the Authority header.
     *
     * <p>NOTE: The gRPC specification mentions the optional "grpc-message-type" header. This implementation does
     * not use that field at all. It is ignored.
     *
     * <p>NOTE: While the gRPC specification defines the "user-agent" header, we don't use it for anything, so we
     * ignore it.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc9113#section-8.2.2-2">gRPC over HTTP2 spec</a>
     */
    @Nested
    final class HeaderTests {
        /**
         * The "grpc-timeout" header is optional. If not specified, the timeout is considered to be infinite. If it is
         * specified, then the server will sever the connection with the client if the timeout is exceeded.
         */
        @Nested
        final class GrpcTimeoutTests {
            private static Stream<Arguments> provideGoodTimeouts() {
                return Stream.of(
                        Arguments.of("2H", NANOSECONDS.convert(ofHours(2))),
                        Arguments.of("2M", NANOSECONDS.convert(ofMinutes(2))),
                        Arguments.of("2S", NANOSECONDS.convert(ofSeconds(2))),
                        Arguments.of("2m", NANOSECONDS.convert(ofMillis(2))),
                        Arguments.of("2u", NANOSECONDS.convert(ofNanos(2 * 1000))),
                        Arguments.of("2n", NANOSECONDS.convert(ofNanos(2))),
                        // "positive integer as ASCII string of at most 8 digits"
                        Arguments.of("12345678H", NANOSECONDS.convert(ofHours(12345678))),
                        Arguments.of("12345678M", NANOSECONDS.convert(ofMinutes(12345678))),
                        Arguments.of("12345678S", NANOSECONDS.convert(ofSeconds(12345678))),
                        Arguments.of("12345678m", NANOSECONDS.convert(ofMillis(12345678))),
                        Arguments.of("12345678u", NANOSECONDS.convert(ofNanos(1000 * 12345678L))),
                        Arguments.of("12345678n", NANOSECONDS.convert(ofNanos(12345678))));
            }

            @MethodSource("provideGoodTimeouts")
            @ParameterizedTest
            void validTimeouts(String timeout, long expectedNanos) {
                // Given a valid grpc-timeout
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-timeout", timeout)
                        .build();

                // When we make a request
                conn.request("Alice");

                // The deadline that was set matches the grpc-timeout. This proves it was parsed correctly.
                // And since we never advanced the clock in our fake deadline detector, the deadline should
                // have not been exceeded.
                assertThat(conn.deadlineDetector.futures).hasSize(1);
                assertThat(conn.deadlineDetector.futures.getFirst().delay).isEqualTo(expectedNanos);
                conn.assertSuccessfulUnaryResponse();
            }

            @ValueSource(strings = {
                    // Missing the timeout unit
                    "2",
                    // Unknown timeout unit
                    "2X",
                    // Random nonsense characters
                    "-", "_", "*", "!", "@", "#", "$", "%", "^", "&", "(", ")", "+", "=", "[", "]", "{", "}", ";", ":",
                    "'", "\"", ",", "<", ">", ".", "?", "/", "\\", "|", "`", "~", " ", "\t", "\n", "\r",
                    // Cannot be missing the timeout value
                    "H", "M", "S", "m", "u", "n",
                    "-H", "-M", "-S", "-m", "-u", "-n",
                    // Must only use digits for the timeout value
                    "abcH", "abcM", "abcS", "abcm", "abcu", "abcn",
                    "1bcH", "a2cM", "ab3S",
                    // Fractional timeout values
                    "2.5", "2.5X", "2.5H", "2.5M", "2.5S", "2.5m", "2.5u", "2.5n",
                    // Very large timeout values (cannot be more than 8 ascii digits)
                    "123456789", "123456789X", "123456789H", "123456789M", "123456789S", "123456789m", "123456789u",
                    "123456789n",
                    // Cannot have negative timeout values
                    "-2", "-2X", "-2H", "-2M", "-2S", "-2m", "-2u", "-2n",
                    // Or really large negative values
                    "-123456789", "-123456789X", "-123456789H", "-123456789M", "-123456789S", "-123456789m",
                    "-123456789u", "-123456789n",
                    // And whitespace shouldn't be allowed
                    " 2H", "2H ", "\r2H", "2H\r", "\n2H", "2H\n", "\t2H", "2H\t"
                    })
            @ParameterizedTest
            void invalidTimeouts(String timeout) {
                // Given an INVALID grpc-timeout
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-timeout", timeout)
                        .build();

                // When the connection is opened
                conn.open();

                // It will fail because the timeout is invalid
                // Even though the request failed, it was made, and should be counted
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);

                // It passed HTTP/2, but failed during GRPC processing
                assertThat(conn.route.failedGrpcRequestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();

                // There is only a single response header because the stream is closed immediately upon initialization,
                // and therefore no data is sent.
                assertThat(conn.streamWriter.responseHeaderFrames).hasSize(1);
                assertThat(conn.streamWriter.responseDataFrames).isEmpty();

                // The go GRPC server returns HTTP status 400, and grpc-status of 13 (INTERNAL error)! But, according
                // to the specification, the HTTP response code should always be 200 if the request was valid from an
                // HTTP/2 perspective, with the gRPC related error in the grpc-status header:
                //
                // "Implementations should expect broken deployments to send non-200 HTTP status codes in responses as
                // well as a variety of non-GRPC Content-Types". Emphasis on the word "broken"!
                //
                // In addition, the go GRPC server sends status code 13, INTERNAL error, as the grpc status! I think
                // the correct code would be INVALID_ARGUMENT.
                //
                // So we will return a 200 OK response, with a grpc-status of 3 (INVALID_ARGUMENT).
                conn.assertHttpStatusEquals(OK_200);
                conn.assertGrpcStatusEquals(INVALID_ARGUMENT);
                conn.assertResponseHeaderEquals("Content-Type", "application/grpc");

                // The stream should be HALF_CLOSED_LOCAL because we sent END_STREAM, but not RST_STREAM
                conn.assertStreamStateEquals(HALF_CLOSED_LOCAL);
            }

            @Test
            void timeoutExceededBeforeDataSent() {
                // Given a VALID grpc-timeout
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-timeout", "1n")
                        .build();

                // When the connection is opened and then the deadline is exceeded before the request is made
                conn.open();
                conn.deadlineDetector.advanceTime(ofNanos(2));

                // The request will fail because the deadline was exceeded
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedGrpcRequestCounter().count()).isZero();
                assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();
                assertThat(conn.route.deadlineExceededCounter().count()).isEqualTo(1);

                // The HTTP status is OK but the grpc status is DEADLINE_EXCEEDED
                conn.assertHttpStatusEquals(OK_200);
                conn.assertGrpcStatusEquals(DEADLINE_EXCEEDED);
                conn.assertResponseHeaderEquals("Content-Type", "application/grpc+proto");
                conn.assertResponseHeaderEquals("grpc-encoding", "identity");
                conn.assertResponseHeaderEquals("grpc-accept-encoding", "identity");

                // The stream should be HALF_CLOSED_LOCAL because the client didn't terminate the stream, the server did
                conn.assertStreamStateEquals(HALF_CLOSED_LOCAL);
            }

            @Test
            void timeoutExceededAfterResponseSent() {
                // Given a VALID grpc-timeout
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-timeout", "1S")
                        .build();

                // When a request is made and completes BEFORE the expiration
                conn.request("Alice");
                conn.deadlineDetector.advanceTime(ofSeconds(2));

                // Then the request will succeed  (it is too late to fail!!)
                conn.assertSuccessfulUnaryResponse();
            }

            @Test
            void timeoutExceededAfterSomeDataReceivedButNotAll() {
                // Given a VALID grpc-timeout
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-timeout", "1n")
                        .build();

                // When the connection is opened and then the deadline is exceeded before all data is received
                final var proto = createRequestData("Alice");
                conn.open();
                conn.sendIncompleteData(proto.slice(0, 3), (int) proto.length());
                conn.deadlineDetector.advanceTime(ofNanos(2));

                // The request will fail because the deadline was exceeded
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedGrpcRequestCounter().count()).isZero();
                assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();
                assertThat(conn.route.deadlineExceededCounter().count()).isEqualTo(1);

                // The HTTP status is OK but the grpc status is DEADLINE_EXCEEDED
                conn.assertHttpStatusEquals(OK_200);
                conn.assertGrpcStatusEquals(DEADLINE_EXCEEDED);
                conn.assertResponseHeaderEquals("Content-Type", "application/grpc+proto");
                conn.assertResponseHeaderEquals("grpc-encoding", "identity");
                conn.assertResponseHeaderEquals("grpc-accept-encoding", "identity");

                // The stream should be HALF_CLOSED_LOCAL because the client didn't terminate the stream, the server did
                conn.assertStreamStateEquals(HALF_CLOSED_LOCAL);
            }

            @Test
            void timeoutExceededWhenReceivingStreamFromServer() throws InvalidProtocolBufferException {
                // Given a VALID grpc-timeout and a server-streaming request
                final AtomicReference<Runnable> clockAdvancer = new AtomicReference<>();
                final var svc = new ServiceInterfaceStub() {
                    @Override
                    public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
                        // Send three responses, and then wait for the timeout to expire
                        for (int i = 1; i < 4; i++) {
                            replies.onNext(HelloReply.newBuilder().setMessage("Hello!").build());
                        }

                        // Advance the clock to force the timeout to happen BEFORE this method terminated.
                        clockAdvancer.get().run();
                    }
                };

                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-timeout", "1n")
                        .withService(svc)
                        .withServiceMethod(GreeterService.GreeterMethod.sayHelloStreamReply)
                        .build();

                clockAdvancer.set(() -> {
                    // The server was created to send responses for the first three but not the fourth. So now we can
                    // advance the time beyond what the deadline detector will allow, so it should terminate.
                    conn.deadlineDetector.advanceTime(ofNanos(2));
                });

                // When the request is made and the deadline is exceeded before we've received all responses
                conn.open();
                conn.sendAllData(createRequestData("Alice"));

                // We should see three responses, but not the fourth.
                assertThat(conn.streamWriter.responseDataFrames).hasSize(3);
                for (int i = 0; i < 3; i++) {
                    final var frameBytes = Bytes.wrap(conn.streamWriter.responseDataFrames.get(i).data().readBytes());
                    final var len = frameBytes.length() - 5; // 5 bytes for the "compress" and length prefix
                    assertThat(frameBytes.getByte(0)).isZero(); // The compression flag
                    assertThat(frameBytes.getInt(1)).isEqualTo(len); // The length of the message
                    final var proto = frameBytes.slice(5, len);
                    final var response = HelloReply.parseFrom(proto.toByteArray());
                    assertThat(response.getMessage()).isEqualTo("Hello!");
                }

                // Then the request will fail because the deadline was exceeded
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedGrpcRequestCounter().count()).isZero();
                assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();
                assertThat(conn.route.deadlineExceededCounter().count()).isEqualTo(1);

                // The HTTP status is OK but the grpc status is DEADLINE_EXCEEDED
                conn.assertHttpStatusEquals(OK_200);
                conn.assertGrpcStatusEquals(DEADLINE_EXCEEDED);
                conn.assertResponseHeaderEquals("Content-Type", "application/grpc+proto");
                conn.assertResponseHeaderEquals("grpc-encoding", "identity");
                conn.assertResponseHeaderEquals("grpc-accept-encoding", "identity");

                // The stream should be CLOSED because the client sent END_STREAM before the timeout happened
                conn.assertStreamStateEquals(CLOSED);
            }

            @Test
            void timeoutExceededWithBidiStreaming() throws InvalidProtocolBufferException {
                // Given a VALID grpc-timeout and a bidi-streaming request
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-timeout", "1n")
                        .withServiceMethod(GreeterService.GreeterMethod.sayHelloStreamBidi)
                        .build();

                // When the request is made and some requests have been made, but we are not yet done,
                // and the deadline is exceeded
                conn.open();
                conn.sendBytes(createRequestData("Alice"));
                conn.sendBytes(createRequestData("Bob"));
                conn.sendBytes(createRequestData("Carol"));
                conn.deadlineDetector.advanceTime(ofNanos(2));
                conn.sendBytes(createRequestData("Dave")); // This should NEVER BE HANDLED

                // We should see three responses, but not the fourth.
                assertThat(conn.streamWriter.responseDataFrames).hasSize(3);
                for (int i = 0; i < 3; i++) {
                    final var frameBytes = Bytes.wrap(conn.streamWriter.responseDataFrames.get(i).data().readBytes());
                    final var len = frameBytes.length() - 5; // 5 bytes for the "compress" and length prefix
                    assertThat(frameBytes.getByte(0)).isZero(); // The compression flag
                    assertThat(frameBytes.getInt(1)).isEqualTo(len); // The length of the message
                    final var proto = frameBytes.slice(5, len);
                    final var response = HelloReply.parseFrom(proto.toByteArray());
                    assertThat(response.getMessage()).startsWith("Hello");
                }

                // Then the request will fail because the deadline was exceeded
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedGrpcRequestCounter().count()).isZero();
                assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();
                assertThat(conn.route.deadlineExceededCounter().count()).isEqualTo(1);

                // The HTTP status is OK but the grpc status is DEADLINE_EXCEEDED
                conn.assertHttpStatusEquals(OK_200);
                conn.assertGrpcStatusEquals(DEADLINE_EXCEEDED);
                conn.assertResponseHeaderEquals("Content-Type", "application/grpc+proto");
                conn.assertResponseHeaderEquals("grpc-encoding", "identity");
                conn.assertResponseHeaderEquals("grpc-accept-encoding", "identity");

                // The stream should be HALF_CLOSED_LOCAL because the client never sent END_STREAM before the timeout
                // happened, so the server was the one that closed the connection.
                conn.assertStreamStateEquals(HALF_CLOSED_LOCAL);
            }
        }

        @Nested
        final class ContentTypeTests {
            /**
             * If the Content-Type is missing, or does not start with "application/grpc", the server should
             * respond with a 415 Unsupported Media Type and the stream state should end up CLOSED. See
             * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md"/>
             */
            @ValueSource(strings = {"", "text/plain", "application/json"})
            @ParameterizedTest
            void unsupportedContentType(String contentType) {
                // Given a handler with a content type that is not supported
                final var b = new ConnectionBuilder().withoutHeader("Content-Type");
                if (!contentType.isBlank()) b.withContentType(contentType);
                final var conn = b.build();

                // When the handler is initialized it will fail.
                conn.open();

                // Even though the request failed, it was made, and should be counted
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);

                // It failed at the HTTP/2 level, it didn't even get to the GRPC level!
                assertThat(conn.route.failedGrpcRequestCounter().count()).isZero();
                assertThat(conn.route.failedHttpRequestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();

                // There is only a single response header because the stream is closed immediately upon initialization,
                // and therefore no data is sent.
                assertThat(conn.streamWriter.responseHeaderFrames).hasSize(1);
                assertThat(conn.streamWriter.responseDataFrames).isEmpty();

                // I verified with the go GRPC server its behavior in this scenario. The following headers should be
                // available in the response
                // Content-Type: application/grpc
                // Grpc-Message: invalid gRPC request Content-Type ""
                // Grpc-Status: 3
                final var responseHeaderFrame = conn.streamWriter.responseHeaderFrames.getFirst();
                assertThat(responseHeaderFrame.status()).isEqualTo(Status.UNSUPPORTED_MEDIA_TYPE_415);
                final var responseHeaders = responseHeaderFrame.httpHeaders().stream()
                        .collect(Collectors.toMap(Header::name, Header::values));
                assertThat(responseHeaders).contains(
                        entry("grpc-status", "" + INVALID_ARGUMENT.ordinal()),
                        entry("grpc-message", UriEncoding.encodeUri("invalid gRPC request content-type \"" + contentType + "\"")),
                        entry("Content-Type", "application/grpc"),
                        entry("grpc-accept-encoding", "identity"));

                // The stream should be HALF_CLOSED_LOCAL because we sent END_STREAM, but not RST_STREAM
                assertThat(conn.serverStreamState()).isEqualTo(HALF_CLOSED_LOCAL);
            }

            /**
             * If the content type is "application/grpc", then it is treated as "application/grpc+proto".
             */
            @Test
            void contentTypeIsNormalized() {
                // Given a request with a default content type
                final var conn = new ConnectionBuilder()
                        .withContentType("application/grpc")
                        .build();

                // When the request is made
                conn.open();
                final var data = createRequestData("Alice");
                conn.sendAllData(data);

                // Then the request will succeed and the "opts" passed to the service specifies proto
                conn.assertSuccessfulUnaryResponse();
                assertThat(conn.service.opts.contentType()).isEqualTo("application/grpc+proto");
            }
        }

        @Nested
        final class GrpcEncodingTests {
            /**
             * These are perfectly valid encodings, but none of them are supported at this time.
             *
             * @param encoding the encoding to test with.
             */
            @ValueSource(strings = {"gzip", "compress", "deflate", "br", "zstd", "gzip, deflate;q=0.5"})
            @ParameterizedTest
            void unsupportedGrpcEncodings(String encoding) {
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-encoding", encoding)
                        .build();

                // Initializing the handler will throw an error because the content types are unsupported
                conn.open();

                // Even though the request failed, it was made, and should be counted
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);
                // And since it failed the failed counter should be incremented
                assertThat(conn.route.failedGrpcRequestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();

                // The HTTP2 response itself was successful, but the GRPC response was not
                assertThat(conn.streamWriter.responseHeaderFrames).hasSize(1);
                assertThat(conn.streamWriter.responseDataFrames).isEmpty();
                final var responseHeaderFrame = conn.streamWriter.responseHeaderFrames.getFirst();
                assertThat(responseHeaderFrame.status()).isEqualTo(OK_200);

                // I verified with the go GRPC server its behavior in this scenario. The following headers should be
                // available in the response
                // Content-Type: application/grpc
                // Grpc-Message: grpc: Decompressor is not installed for grpc-encoding "[bad encoding here]"
                // Grpc-Status: 12
                final var responseHeaders = responseHeaderFrame.httpHeaders().stream()
                        .collect(Collectors.toMap(Header::name, Header::values));
                assertThat(responseHeaders).contains(
                        entry("grpc-status", "" + GrpcStatus.UNIMPLEMENTED.ordinal()),
                        entry("grpc-message", UriEncoding.encodeUri("Decompressor is not installed for grpc-encoding \"" + encoding + "\"")),
                        entry("Content-Type", "application/grpc"),
                        entry("grpc-accept-encoding", "identity"));

                // The stream should be HALF_CLOSED_LOCAL because we sent END_STREAM, but not RST_STREAM
                assertThat(conn.serverStreamState()).isEqualTo(HALF_CLOSED_LOCAL);
            }

            /**
             * These are encodings we support. They all contain "identity".
             *
             * <p>See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding"/> for information
             * on the encoding header syntax.
             *
             * @param encoding
             */
            @ValueSource(strings = {
                    // Simple identity strings with qualifiers
                    "identity", "identity;q=0.5", "identity;", "identity;nonsense",
                    // an identity with and without a qualifier in a list of encodings
                    "gzip, deflate;q=0.5, identity;q=0.1",
                    "gzip, deflate;q=0.5, identity",
                    "gzip, identity;q=0.1, deflate;q=0.5",
                    "gzip, identity, deflate;q=0.5",
                    "identity;q=.9, deflate;q=0.5, gzip;q=0.1, br;q=0.1",
                    "identity, deflate;q=0.5, gzip;q=0.1, br;q=0.1"})
            @ParameterizedTest
            void supportedComplexEncodingsWithIdentity(String encoding) {
                // Given a valid encoding
                final var conn = new ConnectionBuilder()
                        .withHeader("grpc-encoding", encoding)
                        .build();

                // When the request is mode
                conn.open();
                conn.sendAllData(createRequestData("Alice"));

                // Then the request succeeds
                conn.assertSuccessfulUnaryResponse();
            }
        }

        /**
         * This is an optional header. If specified, it MUST contain one of "application/grpc",
         * "application/grpc+proto", or "application/grpc+json". If it does not, the server should respond with a
         * "415 Unsupported Media Type". However, the Go GRPC server seems to totally ignore invalid values for this
         * header, or might ignore it entirely. The gRPC specification doesn't describe the correct behavior for this
         * header.
         */
        @Nested
        final class GrpcAcceptEncodingTests {

        }

        /**
         * gRPC supports custom headers, referred to as "Custom-Metadata". The header, if suffixed with "-bin", is
         * considered to be binary data and is base64 encoded. Otherwise, it is a normal textual data header.
         * All such headers should be passed to the application handling code so it can handle those fields as
         * needed (for example, the "authentication" header can be passed this way).
         */
        @Nested
        final class MetadataTests {

        }
    }

    @Nested
    final class Http2Tests {
        /**
         * When sending multiple messages to the server, the HTTP/2 client may break the stream of messages over
         * multiple data frames. Each data frame will have some of the bytes. Multiple messages are sent such
         * that each message is a length-delimited set of Length-Prefixed-Message, where each has a "compressed"
         * byte followed by a 4-byte length prefix, followed by the message bytes.
         *
         * <p>Now, it may be that the prefix of the message (those 5 bytes) end up on a data frame boundary, so that
         * some bytes are in frame N and the rest in frame N+1. It is critical that when the
         * {@link PbjProtocolHandler} handles a frame, if it has some subset of those 5 bytes, that it waits for the
         * next frame before it proceeds with accumulating the remaining bytes of the message.
         *
         * <p>This test simulates this scenario.
         */
        @Nested
        final class FramingTests {
            private static Stream<Arguments> provideSplitFrames() {
                // I'm going to construct a single byte array that is the concatenation of three protobuf messages.
                // I'm then going to run a series of tests, where each test sends a subset of the bytes to the server.
                // Initially each subset will be of length 1, then of length 2, then of length 3, and so on until
                // eventually we send the entire three protobuf messages to the server in a single frame.
                //
                // For each iteration, after all frames are sent, there should be valid responses that indicate all
                // three protobuf messages were received.
                final var args = new ArrayList<Arguments>();
                final var allBytes =
                        createLengthPrefixedMessage(createRequestData("Alice")).append(
                                createLengthPrefixedMessage(createRequestData("Bob")).append(
                                        createLengthPrefixedMessage(createRequestData("Carol"))));

                for (int i = 0; i < allBytes.length(); i++) {
                    final var frames = new ArrayList<Http2FrameHeader>();
                    final var frameBytes = new ArrayList<Bytes>();
                    final int numBytesPerFrame = i + 1;
                    for (int j = 0; j < allBytes.length(); j += numBytesPerFrame) {
                        final var len = Math.min(numBytesPerFrame, allBytes.length() - j);
                        final var bytes = allBytes.slice(j, len);
                        frames.add(createDataFrameHeader((int) bytes.length(), (j + len) == allBytes.length(), 1));
                        frameBytes.add(bytes);
                    }
                    args.add(Arguments.of(frames, frameBytes));
                }

                return args.stream();
            }

            @MethodSource("provideSplitFrames")
            @ParameterizedTest
            void protobufIsSpreadAcrossMultipleFrames(List<Http2FrameHeader> frameHeaders, List<Bytes> frameBytes) throws InvalidProtocolBufferException {
                final var conn = new ConnectionBuilder()
                        .withServiceMethod(GreeterService.GreeterMethod.sayHelloStreamBidi)
                        .build();

                conn.open();

                for (int i = 0; i < frameHeaders.size(); i++) {
                    conn.handler.data(frameHeaders.get(i), BufferData.create(frameBytes.get(i).toByteArray()));
                }

                // Now we can assert that we have successfully handled 3 different protobuf messages
                // by checking the responses
                assertThat(conn.streamWriter.responseDataFrames).hasSize(3);
                for (final var frame : conn.streamWriter.responseDataFrames) {
                    final var bytes = Bytes.wrap(frame.data().readBytes());
                    final var len = bytes.length() - 5; // 5 bytes for the "compress" and length prefix
                    assertThat(bytes.getByte(0)).isZero(); // The compression flag
                    assertThat(bytes.getInt(1)).isEqualTo(len); // The length of the message
                    final var proto = bytes.slice(5, len);
                    final var response = HelloReply.parseFrom(proto.toByteArray());
                    assertThat(response.getMessage()).startsWith("Hello");
                }

                // Then the request will fail because the deadline was exceeded
                assertThat(conn.route.requestCounter().count()).isEqualTo(1);
                assertThat(conn.route.failedGrpcRequestCounter().count()).isZero();
                assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
                assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();
                assertThat(conn.route.deadlineExceededCounter().count()).isZero();

                // The HTTP status is OK but the grpc status is DEADLINE_EXCEEDED
                conn.assertHttpStatusEquals(OK_200);
                conn.assertGrpcStatusEquals(OK);
                conn.assertResponseHeaderEquals("Content-Type", "application/grpc+proto");
                conn.assertResponseHeaderEquals("grpc-encoding", "identity");
                conn.assertResponseHeaderEquals("grpc-accept-encoding", "identity");

                // The stream should be CLOSED because the client sent END_OF_STREAM before the server closed
                conn.assertStreamStateEquals(CLOSED);
            }
        }

        /**
         * The {@link PbjProtocolHandler} is responsible for advancing the stream state correctly. Periodically
         * the Helidon code will look up the state and use it for interacting with the client. So we need to make
         * sure it is updated correctly as per
         * <a href="https://datatracker.ietf.org/doc/html/rfc9113#section-5.1-2.1.1">the spec</a>.
         */
        @Nested
        final class StateTests {
            // Test that for a unary method invocation, the server ends in CLOSED state. The client must have received
            // an END_STREAM flag on the response.

            // Test that for a client-streaming method invocation, when the client sends an END_STREAM flag, the server
            // will transition to HALF_CLOSED_REMOTE, then it will respond with data, and then send an END_STREAM
            // and transition to CLOSED.

            // Test that for a server-streaming method invocation, the server will eventually send END_STREAM when it
            // is done sending data, and that it then ends up in CLOSED.

            // Test that for a bidi-streaming method invocation, the server will transition to HALF_CLOSED_REMOTE if
            // it receives an END_STREAM flag from the client, and it will then notify the application so it can
            // finish streaming to the client, and then send END_STREAM.

            // Test that for a bidi-streaming method invocation, the server will transaction to HALF_CLOSED_LOCAL if
            // it is done and send END_STREAM, and then when the client finally sends an END_STREAM, the server will
            // transition to CLOSED.

            // Test that for a unary method, if the client sends an RST_STREAM before all data has been received, then
            // the server will transition to CLOSED and NOT respond.

            // Test that for a client-streaming method, if the client sends RST_STREAM then the server will transition
            // to CLOSED and not respond.

            // Test that for server-streaming, if the client sends RST_STREAM before all data was originally sent, then
            // the connection is CLOSED and there is no response.

            // Test that for bidi-streaming, if the client sends RST_STREAM then the server closes and terminates the
            // connection and ends up in CLOSED state.

            // Test that for bidi-streaming, if the server sends RST_STREAM, then it also stops receiving data from
            // the client (this should be handled by Helidon).
        }
    }

    @Nested
    final class StreamClosedTests {
        @Test
        void errorThrownForOnNextWhenStreamIsClosed() {
            // Use a custom streamWriter that will throw an exception when "streamClosed" is set to true, and it is
            // asked to write something. This can be used to simulate what happens when the network connection fails.
            final var streamClosed = new AtomicBoolean(false);
            final var streamWriter = new StreamWriterStub() {
                @Override
                public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                    if (streamClosed.get()) {
                        throw new IllegalStateException("Stream is closed");
                    }
                }
            };

            // Within this test, the replyRef will be set once when the setup is complete, and then
            // will be available for the test code to use to call onNext, onError, etc. as required.
            final var replyRef = new AtomicReference<Pipeline<? super HelloReply>>();
            final var service = new ServiceInterfaceStub() {
                @Override
                public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
                    replyRef.set(replies);
                }
            };

            final var conn = new ConnectionBuilder()
                    .withStreamWriter(streamWriter)
                    .withService(service)
                    .withServiceMethod(GreeterService.GreeterMethod.sayHelloStreamReply)
                    .build();


            conn.open();
            conn.sendBytes(createRequestData("Alice"));

            final var replies = replyRef.get();
            assertThat(replies).isNotNull();

            replies.onNext(HelloReply.newBuilder().setMessage("Good").build());
            streamClosed.set(true);

            final var failingReply = HelloReply.newBuilder().setMessage("Bad").build();
            assertThatThrownBy(() -> replies.onNext(failingReply))
                    .isInstanceOf(Exception.class);

            assertThat(conn.route.requestCounter().count()).isEqualTo(1);
            assertThat(conn.route.failedGrpcRequestCounter().count()).isZero();
            assertThat(conn.route.failedHttpRequestCounter().count()).isZero();
            assertThat(conn.route.failedUnknownRequestCounter().count()).isZero();
            assertThat(conn.route.failedResponseCounter().count()).isEqualTo(1);
        }
    }

    private static Bytes createRequestData(String name) {
        return createData(HelloRequest.newBuilder().setName(name).build());
    }

    private static Bytes createData(HelloRequest request) {
        return Bytes.wrap(request.toByteArray());
    }

    private static Bytes createLengthPrefixedMessage(Bytes data) {
        return createLengthPrefixedMessage(data, (int) data.length());
    }

    private static Bytes createLengthPrefixedMessage(Bytes data, int protoLen) {
        final var buf = BufferedData.allocate((int) data.length() + 5);
        buf.writeByte((byte) 0);
        buf.writeInt(protoLen);
        buf.writeBytes(data);
        return buf.getBytes(0, buf.length());
    }

    private static Http2FrameHeader createDataFrameHeader(int length, int streamId) {
        return createDataFrameHeader(length, true, streamId);
    }

    private static Http2FrameHeader createDataFrameHeader(int length, boolean eos, int streamId) {
        return Http2FrameHeader.create(length + 5, Http2FrameTypes.DATA, eos ? Http2Flag.DataFlags.create(Http2Flags.END_STREAM) : Http2Flag.DataFlags.create(0), streamId);
    }

    private static final class Connection {
        private final StreamWriterStub streamWriter;
        private final int streamId;
        private final OutboundFlowControlStub flowControl;
        private final Http2StreamState currentStreamState;
        private final PbjConfigStub config;
        private final PbjMethodRoute route;
        private final DeadlineDetectorStub deadlineDetector;
        private final ServiceInterfaceStub service;
        private final PbjProtocolHandler handler;
        private Bytes sentBytes = Bytes.EMPTY;

        public Connection(Http2Headers headers, StreamWriterStub streamWriter, int streamId, OutboundFlowControlStub flowControl, Http2StreamState currentStreamState, PbjConfigStub config, PbjMethodRoute route, DeadlineDetectorStub deadlineDetector, ServiceInterfaceStub service) {
            this.streamWriter = streamWriter;
            this.streamId = streamId;
            this.flowControl = flowControl;
            this.currentStreamState = currentStreamState;
            this.config = config;
            this.route = route;
            this.deadlineDetector = deadlineDetector;
            this.service = service;

            this.handler = new PbjProtocolHandler(
                    headers,
                    streamWriter,
                    streamId,
                    flowControl,
                    currentStreamState,
                    config,
                    route,
                    deadlineDetector);
        }

        public void open() {
            handler.init();
        }

        public HelloReply request(String name) {
            open();
            sendAllData(createRequestData(name));

            var responseBytes = Bytes.EMPTY;
            for (final var data : service.receivedBytes) {
                responseBytes = responseBytes.append(data);
            }

            try {
                return HelloReply.parseFrom(responseBytes.toByteArray());
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }

        public void sendIncompleteData(Bytes bytes, int protoLen) {
            sentBytes = sentBytes.append(bytes);
            final var frameHeader = createDataFrameHeader((int) bytes.length(), false, 1);
            final var buf = createLengthPrefixedMessage(bytes, protoLen);
            handler.data(frameHeader, BufferData.create(buf.toByteArray()));
        }

        public void sendBytes(Bytes bytes) {
            sentBytes = sentBytes.append(bytes);
            final var frameHeader = createDataFrameHeader((int) bytes.length(), false, 1);
            final var buf = createLengthPrefixedMessage(bytes, (int) bytes.length());
            handler.data(frameHeader, BufferData.create(buf.toByteArray()));
        }

        public void terminateForcefully() {

        }

        public void close() {

        }

        public Http2StreamState serverStreamState() {
            return handler.streamState();
        }

        private void sendAllData(Bytes bytes) {
            sentBytes = bytes;
            final var protoLen = (int) bytes.length();
            final var frameHeader = createDataFrameHeader(protoLen, 1);
            final var buf = createLengthPrefixedMessage(bytes, protoLen);
            handler.data(frameHeader, BufferData.create(buf.toByteArray()));
        }

        public void assertSuccessfulUnaryResponse() {
            // Request was made
            assertThat(route.requestCounter().count()).isEqualTo(1);

            // It succeeded!
            assertThat(route.failedGrpcRequestCounter().count()).isZero();
            assertThat(route.failedHttpRequestCounter().count()).isZero();
            assertThat(route.failedUnknownRequestCounter().count()).isZero();
            assertThat(route.deadlineExceededCounter().count()).isZero();

            // The method was called
            assertThat(service.calledMethod).isSameAs(route.method());
            assertThat(service.receivedBytes).contains(sentBytes);

            // There are two response headers -- the one at the start of the request, and the final trailer
            assertThat(streamWriter.responseHeaderFrames).hasSize(2);
            assertThat(streamWriter.responseDataFrames).hasSize(1); // Not necessarily true

            // The first response header gives the HTTP/2 OK status, the second one gives the gRPC OK status code
            assertHttpStatusEquals(OK_200);
            assertGrpcStatusEquals(GrpcStatus.OK);
            assertResponseHeaderEquals("Content-Type", "application/grpc+proto");
            assertResponseHeaderEquals("grpc-encoding", "identity");
            assertResponseHeaderEquals("grpc-accept-encoding", "identity");

            // The stream should be CLOSED because the client sent an END_STREAM which transitioned us to
            // HALF_CLOSE_REMOTE after accepting all the bytes, and then we needed to transition to CLOSED
            // when we were done with the response.
            assertStreamStateEquals(CLOSED);
        }

        void assertHttpStatusEquals(Status expectedStatus) {
            assertThat(streamWriter.responseHeaderFrames).hasSizeGreaterThanOrEqualTo(1);
            final var responseHeaderFrame = streamWriter.responseHeaderFrames.getFirst();
            assertThat(responseHeaderFrame.status()).isEqualTo(expectedStatus);
        }

        void assertGrpcStatusEquals(GrpcStatus expectedStatus) {
            assertThat(streamWriter.responseHeaderFrames).hasSizeGreaterThanOrEqualTo(1);
            final var responseHeaderFrame = streamWriter.responseHeaderFrames.getLast();
            final var responseHeaders = responseHeaderFrame.httpHeaders().stream()
                    .collect(Collectors.toMap(Header::name, Header::values));
            assertThat(responseHeaders).contains(
                    entry("grpc-status", "" + expectedStatus.ordinal()));
        }

        void assertResponseHeaderEquals(String headerName, String expectedValue) {
            assertThat(streamWriter.responseHeaderFrames).hasSizeGreaterThanOrEqualTo(1);
            for (final var responseHeaderFrame : streamWriter.responseHeaderFrames) {
                final var responseHeaders = responseHeaderFrame.httpHeaders().stream()
                        .collect(Collectors.toMap(Header::name, Header::values));
                if (responseHeaders.containsKey(headerName)) {
                    assertThat(responseHeaders).contains(
                            entry(headerName, expectedValue));
                    return;
                }
            }
            fail("No response header with name " + headerName + " found");
        }

        void assertStreamStateEquals(Http2StreamState expectedState) {
            assertThat(serverStreamState()).isEqualTo(expectedState);
        }

        public int receivedBytesLength() {
            final var headerBytes = streamWriter.responseHeaderFrames.size() * StreamWriterStub.FAKE_ENCODED_HEADER_SIZE;
            final var dataBytes = streamWriter.responseDataFrames.stream().map(Http2FrameData::data)
                    .map(buf -> Bytes.wrap(buf.readBytes()))
                    .reduce(Bytes::append)
                    .orElse(Bytes.EMPTY);
            return (int) dataBytes.length() + headerBytes;
        }
    }

    /**
     * A convenient builder for {@link PbjProtocolHandler} for testing purposes. By default, a fully valid call
     * is assembled for the call to the handler. You can override different headers, etc. to see how the handler
     * behaves.
     */
    private static final class ConnectionBuilder {
        private WritableHeaders headers;
        private ServiceInterface.Method method = GreeterService.GreeterMethod.sayHello;
        private ServiceInterfaceStub service = new ServiceInterfaceStub();
        private StreamWriterStub streamWriter = new StreamWriterStub();

        private ConnectionBuilder() {
            // :method:
            // :path:
            // :authority:
            // :scheme:
            // Content-Type
            // user-agent
            // te
            headers = WritableHeaders.create()
                    .set(HeaderNames.CONTENT_TYPE, "application/grpc+proto")
                    .set(HeaderNames.create("user-agent"), "java-test/1.0")
                    .set(HeaderNames.create("te"), "trailers");
        }

        ConnectionBuilder withHeader(String name, String value) {
            headers.set(HeaderNames.create(name), value);
            return this;
        }

        ConnectionBuilder withUserAgent(String userAgent) {
            return withHeader("user-agent", userAgent);
        }

        ConnectionBuilder withContentType(String contentType) {
            headers.set(HeaderNames.CONTENT_TYPE, contentType);
            return this;
        }

        ConnectionBuilder withoutHeader(String name) {
            headers.remove(HeaderNames.create(name));
            return this;
        }

        ConnectionBuilder withService(ServiceInterfaceStub service) {
            this.service = service;
            return this;
        }

        ConnectionBuilder withServiceMethod(ServiceInterface.Method method) {
            this.method = method;
            return this;
        }

        Connection build() {
            return new Connection(
                    Http2Headers.create(headers),
                    streamWriter,
                    1,
                    new OutboundFlowControlStub(),
                    Http2StreamState.OPEN,
                    new PbjConfigStub(),
                    new PbjMethodRoute(service, method),
                    new DeadlineDetectorStub(),
                    service);
        }

        public ConnectionBuilder withStreamWriter(StreamWriterStub streamWriter) {
            this.streamWriter = streamWriter;
            return this;
        }
    }

    private static final class OutboundFlowControlStub implements FlowControl.Outbound {
        public static final int INITIAL_WINDOW_SIZE = 1000;
        private int windowSize = INITIAL_WINDOW_SIZE;

        @Override
        public long incrementStreamWindowSize(int increment) {
            return windowSize + increment;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void blockTillUpdate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int maxFrameSize() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void decrementWindowSize(int decrement) {
            windowSize -= decrement;
        }

        @Override
        public void resetStreamWindowSize(int size) {
            windowSize = size;
        }

        @Override
        public int getRemainingWindowSize() {
            return windowSize;
        }
    }

    private static class StreamWriterStub implements Http2StreamWriter {
        public static final int FAKE_ENCODED_HEADER_SIZE = 100;
        private final List<Http2FrameData> responseDataFrames = new ArrayList<>();
        private final List<Http2Headers> responseHeaderFrames = new ArrayList<>();


        @Override
        public void write(Http2FrameData frame) {
            responseDataFrames.add(frame);
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            responseDataFrames.add(frame);
            flowControl.decrementWindowSize(frame.data().available());
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl) {
            // OK, I need to update the flow control. But I don't know how big the encoded headers will be on the wire.
            // I can try to make this real, or I can cheat. I'm going to cheat unless there is some reason I cannot.
            // I'm going to pretend that headers are 100 bytes long as far as flow control is concerned!!
            responseHeaderFrames.add(headers);
            flowControl.decrementWindowSize(FAKE_ENCODED_HEADER_SIZE);
            return FAKE_ENCODED_HEADER_SIZE;
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, Http2FrameData dataFrame, FlowControl.Outbound flowControl) {
            throw new UnsupportedOperationException();
//            responseHeaderFrames.add(headers);
//            responseDataFrames.add(dataFrame);
//            return 0;
        }
    }

    private static final class PbjConfigStub implements PbjConfig {

        @Override
        public int maxMessageSizeBytes() {
            return 1000;
        }

        @Override
        public String name() {
            return "";
        }
    }

    private static final class DeadlineDetectorStub implements DeadlineDetector {
        private long currentTime = 0;
        private final List<ScheduledFutureStub> futures = new ArrayList<>();

        @NonNull
        @Override
        public ScheduledFuture<?> scheduleDeadline(long deadlineNanos, @NonNull Runnable onDeadlineExceeded) {
            final var future = new ScheduledFutureStub(deadlineNanos, onDeadlineExceeded);
            this.futures.add(future);
            return future;
        }

        public void advanceTime(Duration duration) {
            currentTime += duration.toNanos();

            // Find all futures that have expired and complete them
            final var completed = futures.stream()
                    .filter(f -> f.getDelay(NANOSECONDS) <= currentTime)
                    .toList();

            for (var f : completed) {
                f.complete(null);
            }

            // And remove them so we don't fire them off again
            futures.removeAll(completed);
        }
    }

    private static final class ScheduledFutureStub extends CompletableFuture<Void> implements ScheduledFuture<Void> {
        private final long delay;
        private final Runnable onDeadlineExceeded;

        public ScheduledFutureStub(long delay, @NonNull Runnable onDeadlineExceeded) {
            this.delay = delay;
            this.onDeadlineExceeded = onDeadlineExceeded;
        }

        @Override
        public long getDelay(@NonNull TimeUnit unit) {
            return unit.convert(delay, NANOSECONDS);
        }

        @Override
        public int compareTo(@NonNull Delayed o) {
            return Long.compare(delay, o.getDelay(NANOSECONDS));
        }

        @Override
        public boolean complete(Void value) {
            boolean b = super.complete(value);
            if (b) {
                onDeadlineExceeded.run();
            }
            return b;
        }
    }

    private static class ServiceInterfaceStub implements GreeterService {
        private Method calledMethod;
        private RequestOptions opts;
        private List<Bytes> receivedBytes = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        @NonNull
        public Pipeline<? super Bytes> open(@NonNull Method method, @NonNull RequestOptions options, @NonNull Pipeline<? super Bytes> replies) {
            this.calledMethod = method;
            this.opts = options;
            return GreeterService.super.open(method, options, replies);
        }

        @Override
        public HelloReply sayHello(HelloRequest request) {
            this.receivedBytes.add(Bytes.wrap(request.toByteArray()));
            return HelloReply.newBuilder().build();
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamRequest(Pipeline<? super HelloReply> replies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sayHelloStreamReply(HelloRequest request, Pipeline<? super HelloReply> replies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Pipeline<? super HelloRequest> sayHelloStreamBidi(Pipeline<? super HelloReply> replies) {
            // Here we receive info from the client. In this case, it is a stream of requests with
            // names. We will respond with a stream of replies.
            return new Pipeline<>() {
                @Override
                public void clientEndStreamReceived() {
                    onComplete();
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    replies.onNext(
                            HelloReply.newBuilder().setMessage("Hello " + item.getName()).build());
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    replies.onComplete();
                }
            };
        }
    }
}
