// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.helidon;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.grpc.ServiceInterface;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class PbjNativeDeadlineIntegrationTest {
    @Test
    @Timeout(10)
    void serverSchedulerExpiresAnIdleNativeStreamAndNotifiesItsPipelineOnce() throws Exception {
        final IdleService service = new IdleService();
        final WebServer server = WebServer.builder()
                .port(0)
                .addRouting(PbjRouting.builder().service(service))
                .build()
                .start();
        try {
            final Http2Client client = Http2Client.builder()
                    .baseUri("http://localhost:" + server.port())
                    .protocolConfig(Http2ClientProtocolConfig.builder()
                            .priorKnowledge(true)
                            .build())
                    .build();
            final long started = System.nanoTime();
            // A raw HTTP/2 client sends the timeout as metadata; no gRPC client-side timer can mask a server bug.
            try (final Http2ClientResponse response = client.post()
                    .path("/nativeDeadline.Stream/subscribe")
                    .contentType(HttpMediaType.create("application/grpc+proto"))
                    .header(HeaderNames.create("grpc-timeout"), "100m")
                    .readTimeout(Duration.ofSeconds(3))
                    .submit(new byte[5])) {
                assertThat(response.status().code()).isEqualTo(200);
                assertThat(response.entity().inputStream().readAllBytes()).isEmpty();
                assertThat(response.trailers().first(GrpcHeaders.GRPC_STATUS)).contains("4");
            }
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(100));
            assertThat(service.requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.errorReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.errors).hasValue(1);
            assertThat(service.failure.get())
                    .isInstanceOfSatisfying(GrpcException.class, exception -> assertThat(exception.status())
                            .isEqualTo(GrpcStatus.DEADLINE_EXCEEDED));
        } finally {
            server.stop();
        }
    }

    private enum StreamMethod implements ServiceInterface.Method {
        subscribe;

        @Override
        public ServiceInterface.MethodType type() {
            return ServiceInterface.MethodType.SERVER_STREAMING;
        }
    }

    private static final class IdleService implements ServiceInterface {
        private final CountDownLatch requestReceived = new CountDownLatch(1);
        private final CountDownLatch errorReceived = new CountDownLatch(1);
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public String serviceName() {
            return "Stream";
        }

        @Override
        public String fullName() {
            return "nativeDeadline.Stream";
        }

        @Override
        public List<Method> methods() {
            return List.of(StreamMethod.subscribe);
        }

        @Override
        public Pipeline<? super Bytes> open(
                final Method method, final RequestOptions options, final Pipeline<? super Bytes> replies) {
            return new Pipeline<>() {
                @Override
                public void onSubscribe(final Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(final Bytes request) {
                    requestReceived.countDown();
                    // The subscription deliberately remains idle until the native server enforces its deadline.
                }

                @Override
                public void onError(final Throwable error) {
                    failure.set(error);
                    errors.incrementAndGet();
                    errorReceived.countDown();
                }

                @Override
                public void onComplete() {}
            };
        }
    }
}
