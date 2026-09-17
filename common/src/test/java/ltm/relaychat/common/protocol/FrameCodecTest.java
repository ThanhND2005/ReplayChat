package ltm.relaychat.common.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FrameCodec} covering all edge cases mentioned in the
 * T1 task specification:
 * <ol>
 *   <li>Frame split in the middle (partial read simulation)</li>
 *   <li>Two frames concatenated ("stuck together")</li>
 *   <li>Frame declared as too large (≥ 1 MiB)</li>
 * </ol>
 */
class FrameCodecTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a raw wire representation of one frame (4-byte header + payload). */
    private static byte[] buildWireFrame(byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(Frame.HEADER_SIZE + payload.length);
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    /** Decodes one frame from an in-memory byte array using FrameCodec. */
    private static Frame decode(byte[] wire) throws IOException {
        ByteArrayInputStream  bais   = new ByteArrayInputStream(wire);
        ByteArrayOutputStream unused = new ByteArrayOutputStream();
        FrameCodec codec = new FrameCodec(bais, unused);
        return codec.read();
    }

    // ── Happy-path tests ──────────────────────────────────────────────────────

    @Test
    void roundTrip_simpleAsciiMessage() throws IOException {
        String json    = "{\"type\":\"PING\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos  = new ByteArrayOutputStream();
        FrameCodec            write = new FrameCodec(new ByteArrayInputStream(new byte[0]), baos);
        write.write(new Frame(payload));

        Frame read = decode(baos.toByteArray());
        assertArrayEquals(payload, read.payload(), "Payload must survive a round-trip unchanged");
    }

    @Test
    void roundTrip_utf8Payload() throws IOException {
        String json    = "{\"text\":\"Xin chào 🌏\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos  = new ByteArrayOutputStream();
        FrameCodec            write = new FrameCodec(new ByteArrayInputStream(new byte[0]), baos);
        write.writeJson(json);

        String result = new FrameCodec(
                new ByteArrayInputStream(baos.toByteArray()),
                new ByteArrayOutputStream()
        ).readJson();

        assertEquals(json, result);
    }

    // ── Edge case 1: frame split in the middle ────────────────────────────────

    /**
     * Verifies that {@code readFully} correctly assembles a frame whose wire
     * bytes arrive in two separate chunks (simulated by a slow input stream).
     */
    @Test
    void read_frameArrivesInTwoParts_reassemblesCorrectly() throws IOException {
        byte[] payload = "hello partial".getBytes(StandardCharsets.UTF_8);
        byte[] wire    = buildWireFrame(payload);

        // Split the wire bytes arbitrarily in the middle of the payload
        int    splitAt   = Frame.HEADER_SIZE + 3;      // 3 bytes into the payload
        byte[] part1     = Arrays.copyOfRange(wire, 0, splitAt);
        byte[] part2     = Arrays.copyOfRange(wire, splitAt, wire.length);

        // SlowInputStream delivers bytes in two bursts
        java.io.InputStream slow = new SlowInputStream(part1, part2);
        Frame read = new FrameCodec(slow, new ByteArrayOutputStream()).read();

        assertArrayEquals(payload, read.payload(),
                "readFully must reassemble a frame that arrives in two parts");
    }

    // ── Edge case 2: two frames concatenated in one read ─────────────────────

    @Test
    void read_twoFramesConcatenated_decodesIndependently() throws IOException {
        byte[] payload1 = "first".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "second".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos  = new ByteArrayOutputStream();
        FrameCodec            write = new FrameCodec(new ByteArrayInputStream(new byte[0]), baos);
        write.write(new Frame(payload1));
        write.write(new Frame(payload2));

        FrameCodec reader = new FrameCodec(
                new ByteArrayInputStream(baos.toByteArray()),
                new ByteArrayOutputStream()
        );

        Frame f1 = reader.read();
        Frame f2 = reader.read();

        assertArrayEquals(payload1, f1.payload(), "First frame payload must match");
        assertArrayEquals(payload2, f2.payload(), "Second frame payload must match");
    }

    // ── Edge case 3: frame too large ─────────────────────────────────────────

    @Test
    void read_payloadExceedsOneMiB_throwsFrameTooLargeException() {
        int oversized = Frame.MAX_PAYLOAD_BYTES + 1;

        // Craft a raw header claiming an oversized payload (don't include actual bytes)
        ByteBuffer header = ByteBuffer.allocate(Frame.HEADER_SIZE);
        header.putInt(oversized);

        ByteArrayInputStream bais = new ByteArrayInputStream(header.array());
        FrameCodec codec = new FrameCodec(bais, new ByteArrayOutputStream());

        assertThrows(FrameCodec.FrameTooLargeException.class, codec::read,
                "Must reject a frame that claims to be larger than 1 MiB");
    }

    @Test
    void frame_constructor_rejectsOversizedPayload() {
        byte[] big = new byte[Frame.MAX_PAYLOAD_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () -> new Frame(big),
                "Frame constructor must reject payloads larger than 1 MiB");
    }

    // ── EOF before full frame ─────────────────────────────────────────────────

    @Test
    void read_eofMidHeader_throwsEOFException() {
        // Only 2 of 4 header bytes available
        byte[] truncatedHeader = new byte[]{0x00, 0x00};
        ByteArrayInputStream bais  = new ByteArrayInputStream(truncatedHeader);
        FrameCodec           codec = new FrameCodec(bais, new ByteArrayOutputStream());

        assertThrows(EOFException.class, codec::read,
                "Must throw EOFException when stream ends before full header");
    }

    @Test
    void read_eofMidPayload_throwsEOFException() {
        // Header says payload = 10 bytes, but only 3 bytes follow
        ByteBuffer buf = ByteBuffer.allocate(Frame.HEADER_SIZE + 3);
        buf.putInt(10);
        buf.put(new byte[]{0x01, 0x02, 0x03});

        FrameCodec codec = new FrameCodec(
                new ByteArrayInputStream(buf.array()),
                new ByteArrayOutputStream()
        );

        assertThrows(EOFException.class, codec::read,
                "Must throw EOFException when stream ends mid-payload");
    }

    // ── Zero-length payload ───────────────────────────────────────────────────

    @Test
    void roundTrip_emptyPayload() throws IOException {
        byte[] empty = new byte[0];
        ByteArrayOutputStream baos  = new ByteArrayOutputStream();
        FrameCodec            write = new FrameCodec(new ByteArrayInputStream(new byte[0]), baos);
        write.write(new Frame(empty));

        Frame read = decode(baos.toByteArray());
        assertArrayEquals(empty, read.payload(), "Empty payload must survive round-trip");
    }

    // ── Helper: slow input stream ─────────────────────────────────────────────

    /**
     * An {@link java.io.InputStream} that returns bytes in two sequential
     * bursts.  Used to simulate a TCP stack delivering a frame in parts.
     */
    private static class SlowInputStream extends java.io.InputStream {
        private final byte[] chunk1;
        private final byte[] chunk2;
        private int pos = 0;

        SlowInputStream(byte[] chunk1, byte[] chunk2) {
            this.chunk1 = chunk1;
            this.chunk2 = chunk2;
        }

        @Override
        public int read() throws IOException {
            if (pos < chunk1.length) return chunk1[pos++] & 0xFF;
            int idx = pos - chunk1.length;
            if (idx < chunk2.length) { pos++; return chunk2[idx] & 0xFF; }
            return -1;
        }
    }
}
