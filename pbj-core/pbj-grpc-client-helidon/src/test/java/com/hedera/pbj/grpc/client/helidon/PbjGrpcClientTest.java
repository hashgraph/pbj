// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.pbj.runtime.grpc.ServiceInterface;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientConnection;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PbjGrpcClientTest {
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

    private record Options(Optional<String> authority, String contentType) implements ServiceInterface.RequestOptions {}

    private static final Options OPTIONS =
            new Options(Optional.empty(), ServiceInterface.RequestOptions.APPLICATION_GRPC);

    @Mock
    private Tls tls;

    @Mock
    private WebClient webClient;

    @Mock
    private Http2Client http2Client;

    @Test
    void testConstructorAndGetters() {
        final PbjGrpcClientConfig config =
                new PbjGrpcClientConfig(READ_TIMEOUT, tls, OPTIONS.authority(), OPTIONS.contentType());
        doReturn(http2Client).when(webClient).client(Http2Client.PROTOCOL);

        final PbjGrpcClient client = new PbjGrpcClient(webClient, config);

        assertEquals(webClient, client.getWebClient());
        assertEquals(http2Client, client.getHttp2Client());
        assertEquals(config, client.getConfig());
    }

    @Test
    void testCreatePbjGrpcClientStream() {
        final PbjGrpcClientConfig config =
                new PbjGrpcClientConfig(READ_TIMEOUT, tls, OPTIONS.authority(), OPTIONS.contentType());
        final PbjGrpcClient client = new PbjGrpcClient(webClient, config);

        final Http2ClientConnection connection = mock(Http2ClientConnection.class);
        final ClientConnection clientConnection = mock(ClientConnection.class);

        final PbjGrpcClientStream stream = client.createPbjGrpcClientStream(connection, clientConnection);

        // Check if it talks to the connection and clientConnection objects:

        assertNotNull(stream);
        verify(clientConnection, times(1)).helidonSocket();

        stream.close();
        // streamId hasn't been initialized yet, so it's zero:
        verify(connection, times(1)).removeStream(0);
    }
}
