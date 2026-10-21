// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.webserver.WebServer;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Real socket backpressure must not retain a publisher beyond the RPC deadline. */
@Timeout(12)
class PbjGrpcWebSlowReaderTest {
    private static final String PATH = "/slowreader.Service/Read";
    private static final String CONTENT_TYPE = "application/grpc-web+proto";
    private static final Bytes LARGE_REPLY = Bytes.wrap(new byte[16 * 1024 * 1024]);

    enum Transport {
        HTTP_1_1,
        HTTP_2
    }

    @Test
    void finiteServerDeadlineBoundsAnIdleCallAfterHttp2Reset() throws Exception {
        final SlowService service = new SlowService(Bytes.wrap("initial reply"));
        try (final TestServer server = new TestServer(service);
                final Socket client = new Socket()) {
            client.setSoTimeout(3_000);
            client.connect(new InetSocketAddress("localhost", server.server.port()));
            // Permit the initial reply, then leave the application subscription idle after resetting its stream.
            sendHttp2Request(client, 65_535, false);
            awaitHttp2ResponseHeaders(client);
            assertThat(service.writeExited.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(service.completedWrites).hasValue(1);
            assertThat(service.writeFailure.get()).isNull();
            writeFrame(client.getOutputStream(), 3, 0, 1, new byte[] {0, 0, 0, 8}); // RST_STREAM, CANCEL
            client.getOutputStream().flush();

            assertThat(service.cleanupDelivered.await(3, TimeUnit.SECONDS))
                    .as("a reset idle stream must be cleaned up within the finite server lifetime")
                    .isTrue();
            assertThat(client.isClosed()).isFalse();
            assertThat(service.errors).hasValue(1);
            assertThat(service.error.get()).isInstanceOf(GrpcException.class);
            assertThat(((GrpcException) service.error.get()).status())
                    .isIn(GrpcStatus.DEADLINE_EXCEEDED, GrpcStatus.CANCELLED);
        }
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    void deadlineInterruptsARealBlockedResponseWithoutTheClientClosingItsSocket(final Transport transport)
            throws Exception {
        final SlowService service = new SlowService();
        try (final TestServer server = new TestServer(service);
                final Socket client = new Socket()) {
            // Keep the HTTP/1 TCP receive window small. The HTTP/2 test additionally advertises a zero stream window.
            client.setReceiveBufferSize(1024);
            client.setSoTimeout(3_000);
            client.connect(new InetSocketAddress("localhost", server.server.port()));
            if (transport == Transport.HTTP_1_1) {
                sendHttp1Request(client);
                assertThat(readHttp1Headers(client.getInputStream())).startsWith("HTTP/1.1 200");
            } else {
                sendHttp2Request(client);
                awaitHttp2ResponseHeaders(client);
            }
            assertThat(service.writeEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(service.writeExited.await(100, TimeUnit.MILLISECONDS))
                    .as("the reply must encounter real socket or HTTP/2 flow-control backpressure")
                    .isFalse();
            assertThat(service.completedWrites).hasValue(0);

            assertThat(service.cleanupDelivered.await(4, TimeUnit.SECONDS)).isTrue();
            assertThat(service.writeExited.await(4, TimeUnit.SECONDS))
                    .as("the server must stop the blocked publisher while the client socket remains open")
                    .isTrue();
            assertThat(client.isClosed()).isFalse();
            assertThat(service.writeFailure.get()).isNotNull();
            assertThat(service.completedWrites).hasValue(0);
            assertThat(service.errors).hasValue(1);
            assertThat(service.error.get()).isInstanceOf(GrpcException.class);
            assertThat(((GrpcException) service.error.get()).status()).isEqualTo(GrpcStatus.DEADLINE_EXCEEDED);
        }
    }

    private static void sendHttp1Request(final Socket socket) throws IOException {
        final OutputStream out = socket.getOutputStream();
        out.write(("POST " + PATH + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + CONTENT_TYPE
                        + "\r\ngrpc-timeout: 1S\r\nContent-Length: 5\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[5]);
        out.flush();
    }

    private static String readHttp1Headers(final InputStream in) throws IOException {
        final ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int suffix = 0;
        while (headers.size() < 16 * 1024) {
            final int next = in.read();
            if (next == -1) {
                throw new EOFException("Server closed before response headers");
            }
            headers.write(next);
            suffix = (suffix << 8) | next;
            if (suffix == 0x0d0a0d0a) {
                return headers.toString(StandardCharsets.US_ASCII);
            }
        }
        throw new IOException("Response headers exceeded the test limit");
    }

    private static void sendHttp2Request(final Socket socket) throws IOException {
        sendHttp2Request(socket, 0, true);
    }

    private static void sendHttp2Request(final Socket socket, final int initialWindow, final boolean clientTimeout)
            throws IOException {
        final OutputStream out = socket.getOutputStream();
        out.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        // The slow-reader case uses a zero stream window and never grants more DATA credit with WINDOW_UPDATE.
        writeFrame(out, 4, 0, 0, new byte[] {
            0,
            4,
            (byte) (initialWindow >>> 24),
            (byte) (initialWindow >>> 16),
            (byte) (initialWindow >>> 8),
            (byte) initialWindow
        });
        final ByteArrayOutputStream headers = new ByteArrayOutputStream();
        headers.write(0x83); // Static table index 3: :method POST.
        headers.write(0x86); // Static table index 6: :scheme http.
        headers.write(1); // Literal value with static indexed name 1: :authority.
        writeString(headers, "localhost");
        headers.write(4); // Literal value with static indexed name 4: :path.
        writeString(headers, PATH);
        writeHeader(headers, "content-type", CONTENT_TYPE);
        if (clientTimeout) {
            writeHeader(headers, "grpc-timeout", "1S");
        }
        writeFrame(out, 1, 4, 1, headers.toByteArray()); // HEADERS, END_HEADERS, stream 1
        writeFrame(out, 0, 1, 1, new byte[5]); // DATA, END_STREAM, one empty gRPC message
        out.flush();
    }

    /** HPACK literal fields without indexing or Huffman coding keep the raw client's behavior explicit. */
    private static void writeHeader(final ByteArrayOutputStream out, final String name, final String value) {
        out.write(0);
        writeString(out, name);
        writeString(out, value);
    }

    private static void writeString(final ByteArrayOutputStream out, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        assertThat(bytes.length).isLessThan(127);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }

    private static void awaitHttp2ResponseHeaders(final Socket socket) throws IOException {
        final InputStream in = socket.getInputStream();
        final OutputStream out = socket.getOutputStream();
        while (true) {
            final byte[] header = in.readNBytes(9);
            if (header.length != 9) {
                throw new EOFException("Server closed before HTTP/2 response headers");
            }
            final int length = ((header[0] & 0xff) << 16) | ((header[1] & 0xff) << 8) | (header[2] & 0xff);
            final int type = header[3] & 0xff;
            final int flags = header[4] & 0xff;
            final int stream = ((header[5] & 0x7f) << 24)
                    | ((header[6] & 0xff) << 16)
                    | ((header[7] & 0xff) << 8)
                    | (header[8] & 0xff);
            final byte[] payload = in.readNBytes(length);
            if (payload.length != length) {
                throw new EOFException("Incomplete HTTP/2 frame");
            }
            if (type == 4 && (flags & 1) == 0) {
                writeFrame(out, 4, 1, 0, new byte[0]); // ACK the server SETTINGS without granting DATA credit.
                out.flush();
            } else if (type == 1 && stream == 1) {
                return;
            } else if (type == 3 || type == 7) {
                throw new IOException("Unexpected HTTP/2 reset/GOAWAY before response headers, type=" + type);
            }
        }
    }

    private static void writeFrame(
            final OutputStream out, final int type, final int flags, final int stream, final byte[] payload)
            throws IOException {
        out.write(new byte[] {
            (byte) (payload.length >>> 16),
            (byte) (payload.length >>> 8),
            (byte) payload.length,
            (byte) type,
            (byte) flags,
            (byte) (stream >>> 24),
            (byte) (stream >>> 16),
            (byte) (stream >>> 8),
            (byte) stream
        });
        out.write(payload);
    }

    private static final class SlowService implements ServiceInterface {
        private final Bytes reply;
        private final CountDownLatch writeEntered = new CountDownLatch(1);
        private final CountDownLatch writeExited = new CountDownLatch(1);
        private final CountDownLatch cleanupDelivered = new CountDownLatch(1);
        private final AtomicInteger completedWrites = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicReference<Throwable> writeFailure = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        private SlowService() {
            this(LARGE_REPLY);
        }

        private SlowService(final Bytes reply) {
            this.reply = reply;
        }

        @Override
        public String serviceName() {
            return "Service";
        }

        @Override
        public String fullName() {
            return "slowreader.Service";
        }

        @Override
        public List<Method> methods() {
            return List.of(new Method() {
                @Override
                public String name() {
                    return "Read";
                }

                @Override
                public MethodType type() {
                    return MethodType.SERVER_STREAMING;
                }
            });
        }

        @Override
        public Pipeline<? super Bytes> open(
                final Method method, final RequestOptions options, final Pipeline<? super Bytes> replies) {
            return new Pipeline<Bytes>() {
                @Override
                public void onSubscribe(final Flow.Subscription subscription) {}

                @Override
                public void onNext(final Bytes item) {
                    Thread.ofVirtual().name("slow-reader-publisher").start(() -> {
                        writeEntered.countDown();
                        try {
                            replies.onNext(reply);
                            completedWrites.incrementAndGet();
                        } catch (final RuntimeException e) {
                            writeFailure.set(e);
                        } finally {
                            writeExited.countDown();
                        }
                    });
                }

                @Override
                public void onError(final Throwable throwable) {
                    error.set(throwable);
                    errors.incrementAndGet();
                    cleanupDelivered.countDown();
                }

                @Override
                public void onComplete() {}
            };
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final WebServer server;

        private TestServer(final ServiceInterface service) {
            server = WebServer.builder()
                    .port(0)
                    .routing(routing -> routing.register(PbjGrpcWebService.builder(service)
                            .defaultTimeout(Duration.ofSeconds(1))
                            .maxTimeout(Duration.ofSeconds(1))
                            .build()))
                    .build()
                    .start();
        }

        @Override
        public void close() {
            server.stop();
        }
    }
}
