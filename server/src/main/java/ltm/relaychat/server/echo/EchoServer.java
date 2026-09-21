package ltm.relaychat.server.echo;

import ltm.relaychat.common.protocol.Envelope;
import ltm.relaychat.common.protocol.Frame;
import ltm.relaychat.common.protocol.FrameCodec;
import ltm.relaychat.common.util.Json;

import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal Echo Server using {@link FrameCodec} for end-to-end testing and verification.
 *
 * <p>Listens on a TCP port. For each incoming connection, it reads {@link Frame}s
 * using {@link FrameCodec#read()} and echoes the exact same frame back using
 * {@link FrameCodec#write(Frame)}.
 *
 * <p>Supports graceful shutdown via {@link #stop()} or {@link #close()}.
 */
public class EchoServer implements AutoCloseable {

    private final int requestedPort;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final Set<Socket> activeClientSockets = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EchoServer() {
        this(9000);
    }

    public EchoServer(int port) {
        this.requestedPort = port;
    }

    /**
     * Starts the server. If port was 0, an ephemeral port is assigned by the OS.
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        serverSocket = new ServerSocket(requestedPort);
        running.set(true);

        acceptThread = new Thread(this::acceptLoop, "echo-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        if (serverSocket == null || !serverSocket.isBound()) {
            return requestedPort;
        }
        return serverSocket.getLocalPort();
    }

    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[EchoServer] Client connected: " + clientSocket.getRemoteSocketAddress());
                activeClientSockets.add(clientSocket);
                clientPool.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                // ServerSocket closed during stop()
                break;
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("[EchoServer] Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket) {
            FrameCodec codec = new FrameCodec(socket.getInputStream(), socket.getOutputStream());
            while (running.get()) {
                Frame frame = codec.read();
                String payloadStr = new String(frame.payload(), java.nio.charset.StandardCharsets.UTF_8);
                try {
                    Envelope env = Json.envelopeFromJson(payloadStr);
                    System.out.printf("[EchoServer] Received from %s: type=%s, id=%s, body=%s (%d bytes)%n",
                            socket.getRemoteSocketAddress(), env.type(), env.id(), env.body(), frame.payload().length);
                } catch (Exception e) {
                    String preview = payloadStr.length() > 80 ? payloadStr.substring(0, 80) + "..." : payloadStr;
                    System.out.printf("[EchoServer] Received frame from %s: \"%s\" (%d bytes)%n",
                            socket.getRemoteSocketAddress(), preview, frame.payload().length);
                }

                // Echo the frame back to the client
                codec.write(frame);
                System.out.printf("[EchoServer] Echoed back %d bytes to %s%n",
                        frame.payload().length, socket.getRemoteSocketAddress());
            }
        } catch (EOFException e) {
            System.out.println("[EchoServer] Client disconnected: " + socket.getRemoteSocketAddress());
        } catch (FrameCodec.FrameTooLargeException e) {
            // Protocol violation: oversized frame (> 1 MiB), close connection immediately per PROTOCOL.md §2
            System.err.println("[EchoServer] Rejected oversized frame from " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        } catch (IOException e) {
            if (running.get()) {
                System.out.println("[EchoServer] Client connection ended: " + socket.getRemoteSocketAddress());
            }
        } finally {
            activeClientSockets.remove(socket);
        }
    }

    @Override
    public synchronized void close() {
        stop();
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        for (Socket socket : activeClientSockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        activeClientSockets.clear();

        clientPool.shutdownNow();
        try {
            clientPool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        if (acceptThread != null) {
            try {
                acceptThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 9000;
        if (args.length > 0) {
            for (String arg : args) {
                if (arg.startsWith("--port=")) {
                    try {
                        port = Integer.parseInt(arg.substring("--port=".length()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        EchoServer server = new EchoServer(port);
        server.start();
        System.out.println("[EchoServer] Started on port " + server.getPort() + ". Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[EchoServer] Stopping...");
            server.stop();
        }));

        while (server.isRunning()) {
            Thread.sleep(1000);
        }
    }
}
