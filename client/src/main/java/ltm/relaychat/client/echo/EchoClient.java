package ltm.relaychat.client.echo;

import ltm.relaychat.common.protocol.Envelope;
import ltm.relaychat.common.protocol.Frame;
import ltm.relaychat.common.protocol.FrameCodec;
import ltm.relaychat.common.protocol.MessageType;
import ltm.relaychat.common.util.Json;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.Socket;

/**
 * Minimal Echo Client using {@link FrameCodec} for end-to-end testing and verification.
 *
 * <p>Connects to an {@link EchoServer} via TCP, sends {@link Frame} or {@link Envelope}
 * messages, and reads back the echoed response.
 */
public class EchoClient implements AutoCloseable {

    private final String host;
    private final int port;
    private Socket socket;
    private FrameCodec codec;

    public EchoClient(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        connect();
    }

    public void connect() throws IOException {
        this.socket = new Socket(host, port);
        this.codec = new FrameCodec(socket.getInputStream(), socket.getOutputStream());
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Sends a raw {@link Frame} and waits for the echoed {@link Frame}.
     *
     * @param frame the frame to send
     * @return the echoed frame received from server
     * @throws IOException on connection or transfer error
     */
    public synchronized Frame sendAndReceive(Frame frame) throws IOException {
        ensureConnected();
        codec.write(frame);
        return codec.read();
    }

    /**
     * Sends an {@link Envelope} and waits for the echoed {@link Envelope}.
     *
     * @param envelope the envelope to send
     * @return the echoed envelope received from server
     * @throws IOException on connection, transfer, or parsing error
     */
    public synchronized Envelope sendAndReceive(Envelope envelope) throws IOException {
        ensureConnected();
        String json = Json.envelopeToJson(envelope);
        codec.writeJson(json);
        String responseJson = codec.readJson();
        return Json.envelopeFromJson(responseJson);
    }

    /**
     * Sends a JSON string as a frame and waits for the echoed JSON string.
     *
     * @param json UTF-8 JSON string
     * @return the echoed JSON string from server
     * @throws IOException on connection or transfer error
     */
    public synchronized String sendAndReceiveJson(String json) throws IOException {
        ensureConnected();
        codec.writeJson(json);
        return codec.readJson();
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Client is not connected to " + host + ":" + port);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 9000;

        if (args.length > 0) {
            for (String arg : args) {
                if (arg.startsWith("--host=")) {
                    host = arg.substring("--host=".length());
                } else if (arg.startsWith("--port=")) {
                    try {
                        port = Integer.parseInt(arg.substring("--port=".length()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        System.out.println("[EchoClient] Connecting to " + host + ":" + port + "...");
        try (EchoClient client = new EchoClient(host, port)) {
            System.out.println("[EchoClient] Connected successfully.");

            // 1. Send PING Envelope
            Envelope ping = Envelope.of(MessageType.PING, "c-1001");
            System.out.println("[EchoClient] Sending: " + Json.envelopeToJson(ping));
            Envelope echoedPing = client.sendAndReceive(ping);
            System.out.println("[EchoClient] Received: " + Json.envelopeToJson(echoedPing));

            // 2. Send MSG_SEND Envelope with Vietnamese text
            JsonObject body = new JsonObject();
            body.addProperty("room", "ltm");
            body.addProperty("text", "Xin chào từ EchoClient! 🚀");
            Envelope msg = Envelope.of(MessageType.MSG_SEND, "c-1002", body);
            System.out.println("[EchoClient] Sending: " + Json.envelopeToJson(msg));
            Envelope echoedMsg = client.sendAndReceive(msg);
            System.out.println("[EchoClient] Received: " + Json.envelopeToJson(echoedMsg));

            System.out.println("[EchoClient] All echo tests completed successfully!");
        } catch (Exception e) {
            System.err.println("[EchoClient] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
