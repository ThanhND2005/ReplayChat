package ltm.relaychat.client.echo;

import com.google.gson.JsonObject;
import ltm.relaychat.common.protocol.Envelope;
import ltm.relaychat.common.protocol.Frame;
import ltm.relaychat.common.protocol.MessageType;
import ltm.relaychat.server.echo.EchoServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests connecting {@link EchoClient} to {@link EchoServer}
 * across real TCP sockets using {@link ltm.relaychat.common.protocol.FrameCodec}.
 */
class EchoEndToEndTest {

    private EchoServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new EchoServer(0); // ephemeral port
        server.start();
        assertTrue(server.isRunning());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void endToEnd_pingEnvelope() throws IOException {
        try (EchoClient client = new EchoClient("localhost", server.getPort())) {
            assertTrue(client.isConnected());

            Envelope request = Envelope.of(MessageType.PING, "c-ping-1");
            Envelope echoed = client.sendAndReceive(request);

            assertEquals(request.version(), echoed.version());
            assertEquals(request.type(), echoed.type());
            assertEquals(request.id(), echoed.id());
            assertEquals(request.ts(), echoed.ts());
            assertNotNull(echoed.body());
        }
    }

    @Test
    void endToEnd_msgSendEnvelope_withVietnameseAndUnicode() throws IOException {
        try (EchoClient client = new EchoClient("localhost", server.getPort())) {
            JsonObject body = new JsonObject();
            body.addProperty("room", "ltm");
            body.addProperty("text", "Xin chào RelayChat! Tiếng Việt có dấu và biểu tượng cảm xúc: 🌏 🚀 🔥");

            Envelope request = Envelope.of(MessageType.MSG_SEND, "c-msg-1042", body);
            Envelope echoed = client.sendAndReceive(request);

            assertEquals(MessageType.MSG_SEND, echoed.type());
            assertEquals("c-msg-1042", echoed.id());
            assertEquals("ltm", echoed.body().get("room").getAsString());
            assertEquals(
                    "Xin chào RelayChat! Tiếng Việt có dấu và biểu tượng cảm xúc: 🌏 🚀 🔥",
                    echoed.body().get("text").getAsString()
            );
        }
    }

    @Test
    void endToEnd_multipleSequentialMessages() throws IOException {
        try (EchoClient client = new EchoClient("localhost", server.getPort())) {
            for (int i = 0; i < 50; i++) {
                JsonObject body = new JsonObject();
                body.addProperty("seq", i);
                body.addProperty("content", "message #" + i);

                Envelope req = Envelope.of(MessageType.MSG_SEND, "c-seq-" + i, body);
                Envelope resp = client.sendAndReceive(req);

                assertEquals(req.id(), resp.id());
                assertEquals(i, resp.body().get("seq").getAsInt());
                assertEquals("message #" + i, resp.body().get("content").getAsString());
            }
        }
    }

    @Test
    void endToEnd_largePayload_nearLimit() throws IOException {
        try (EchoClient client = new EchoClient("localhost", server.getPort())) {
            // 512 KiB payload
            int size = 512 * 1024;
            byte[] largePayload = new byte[size];
            Arrays.fill(largePayload, (byte) 'A');

            Frame sentFrame = new Frame(largePayload);
            Frame receivedFrame = client.sendAndReceive(sentFrame);

            assertArrayEquals(sentFrame.payload(), receivedFrame.payload(),
                    "Large payload (512 KiB) must be echoed without corruption");
        }
    }

    @Test
    void endToEnd_multipleConcurrentClients() throws Exception {
        int clientCount = 5;
        int messagesPerClient = 20;
        ExecutorService pool = Executors.newFixedThreadPool(clientCount);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int c = 0; c < clientCount; c++) {
            final int clientId = c;
            tasks.add(() -> {
                try (EchoClient client = new EchoClient("localhost", server.getPort())) {
                    for (int m = 0; m < messagesPerClient; m++) {
                        String id = "c-" + clientId + "-" + m;
                        JsonObject body = new JsonObject();
                        body.addProperty("clientId", clientId);
                        body.addProperty("msgIdx", m);

                        Envelope req = Envelope.of(MessageType.MSG_SEND, id, body);
                        Envelope resp = client.sendAndReceive(req);

                        if (!id.equals(resp.id()) ||
                                resp.body().get("clientId").getAsInt() != clientId ||
                                resp.body().get("msgIdx").getAsInt() != m) {
                            return false;
                        }
                    }
                    return true;
                }
            });
        }

        List<Future<Boolean>> results = pool.invokeAll(tasks);
        for (Future<Boolean> result : results) {
            assertTrue(result.get(), "Each concurrent client must receive accurate echoed responses");
        }

        pool.shutdown();
    }
}
