package ltm.relaychat.server.echo;

import ltm.relaychat.common.protocol.Envelope;
import ltm.relaychat.common.protocol.Frame;
import ltm.relaychat.common.protocol.FrameCodec;
import ltm.relaychat.common.protocol.MessageType;
import ltm.relaychat.common.util.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EchoServerTest {

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
    void echo_singleFrame_returnsSamePayload() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            FrameCodec codec = new FrameCodec(socket.getInputStream(), socket.getOutputStream());

            byte[] payload = "Hello RelayChat Echo".getBytes(StandardCharsets.UTF_8);
            codec.write(new Frame(payload));

            Frame response = codec.read();
            assertArrayEquals(payload, response.payload());
        }
    }

    @Test
    void echo_envelopeJson_returnsSameEnvelope() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            FrameCodec codec = new FrameCodec(socket.getInputStream(), socket.getOutputStream());

            Envelope ping = Envelope.of(MessageType.PING, "c-test-1");
            codec.writeJson(Json.envelopeToJson(ping));

            String responseJson = codec.readJson();
            Envelope response = Json.envelopeFromJson(responseJson);

            assertEquals(ping.version(), response.version());
            assertEquals(ping.type(), response.type());
            assertEquals(ping.id(), response.id());
        }
    }

    @Test
    void echo_oversizedFrame_serverClosesConnection() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            // Write length > 1 MiB
            ByteBuffer buf = ByteBuffer.allocate(Frame.HEADER_SIZE);
            buf.putInt(Frame.MAX_PAYLOAD_BYTES + 100);
            socket.getOutputStream().write(buf.array());
            socket.getOutputStream().flush();

            // When server detects oversized frame, it closes connection
            // Next read by client should encounter EOF (-1)
            int read = socket.getInputStream().read();
            assertEquals(-1, read, "Server should immediately close connection on oversized frame");
        }
    }
}
