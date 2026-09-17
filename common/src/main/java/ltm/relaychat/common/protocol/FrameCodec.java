package ltm.relaychat.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Encodes and decodes {@link Frame} objects on a raw TCP stream.
 *
 * <h2>Wire format</h2>
 * <pre>
 *   [4 bytes big-endian uint32: payload length] [N bytes: UTF-8 JSON payload]
 * </pre>
 *
 * <h2>Thread-safety</h2>
 * A {@code FrameCodec} instance is <em>not</em> thread-safe. Each connection
 * should have its own {@code FrameCodec}, used from a single thread (or under
 * external synchronisation).
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li>Uses {@link DataInputStream#readFully} so partial reads from the OS
 *       never result in a truncated frame.</li>
 *   <li>Rejects payloads larger than {@link Frame#MAX_PAYLOAD_BYTES} (1 MiB).</li>
 *   <li>Two frames written back-to-back are read independently.</li>
 * </ul>
 */
public final class FrameCodec {

    private final DataInputStream  in;
    private final DataOutputStream out;

    /**
     * Wraps an existing stream pair.
     *
     * @param in  input stream of the TCP connection
     * @param out output stream of the TCP connection
     */
    public FrameCodec(InputStream in, OutputStream out) {
        this.in  = new DataInputStream(in);
        this.out = new DataOutputStream(out);
    }

    /**
     * Reads exactly one frame from the underlying stream.
     *
     * <p>Blocks until 4 header bytes are available, then blocks again until
     * {@code length} payload bytes are available.  Uses
     * {@code DataInputStream.readFully} so a partial OS read never causes
     * data corruption.
     *
     * @return the next {@link Frame} on the stream
     * @throws FrameTooLargeException if the declared payload length exceeds 1 MiB
     * @throws EOFException           if the stream ends mid-frame
     * @throws IOException            for any other I/O error
     */
    public Frame read() throws IOException {
        // Step 1: read the 4-byte big-endian length header
        int length = in.readInt(); // readInt reads exactly 4 bytes big-endian

        // Step 2: reject oversized frames immediately (before allocating memory)
        if (length < 0 || length > Frame.MAX_PAYLOAD_BYTES) {
            throw new FrameTooLargeException(
                    "Declared payload length " + length + " exceeds limit " + Frame.MAX_PAYLOAD_BYTES);
        }

        // Step 3: read exactly `length` bytes — never fewer
        byte[] payload = new byte[length];
        in.readFully(payload);

        return new Frame(payload);
    }

    /**
     * Writes one frame to the underlying stream and flushes.
     *
     * @param frame the frame to send
     * @throws IOException if writing fails
     */
    public void write(Frame frame) throws IOException {
        out.writeInt(frame.payload().length); // 4-byte big-endian header
        out.write(frame.payload());
        out.flush();
    }

    /**
     * Convenience method: encode a UTF-8 string as a frame and write it.
     *
     * @param json UTF-8 JSON string
     * @throws IOException if the JSON is too large or writing fails
     */
    public void writeJson(String json) throws IOException {
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        write(new Frame(bytes));
    }

    /**
     * Convenience method: read one frame and decode its payload as a UTF-8 string.
     *
     * @return the JSON string
     * @throws IOException on any read error
     */
    public String readJson() throws IOException {
        Frame frame = read();
        return new String(frame.payload(), java.nio.charset.StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Inner exception
    // -------------------------------------------------------------------------

    /** Thrown when the declared frame payload length exceeds {@link Frame#MAX_PAYLOAD_BYTES}. */
    public static final class FrameTooLargeException extends IOException {
        public FrameTooLargeException(String message) {
            super(message);
        }
    }
}
