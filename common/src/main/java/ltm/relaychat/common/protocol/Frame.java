package ltm.relaychat.common.protocol;

/**
 * Wire frame for the RelayChat control channel.
 *
 * <pre>
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │  0         4                                                 │
 *  │  ┌─────────┬─────────────────────────────────────────────── │
 *  │  │ length  │  payload                                        │
 *  │  │ uint32  │  JSON UTF-8, max 1 MB                           │
 *  │  │ big-end │                                                 │
 *  │  └─────────┴─────────────────────────────────────────────── │
 *  └──────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * The {@code length} field encodes the byte-length of the UTF-8 JSON payload.
 * The header is exactly 4 bytes (big-endian uint32).
 * Maximum payload size is {@link #MAX_PAYLOAD_BYTES} (1 MiB).
 */
public record Frame(byte[] payload) {

    /** Header size in bytes: 4-byte big-endian uint32 length field. */
    public static final int HEADER_SIZE = 4;

    /** Maximum allowed payload size: 1 MiB. */
    public static final int MAX_PAYLOAD_BYTES = 1 << 20; // 1 048 576

    /**
     * Creates a Frame, enforcing the payload size limit.
     *
     * @param payload raw UTF-8 JSON bytes
     * @throws IllegalArgumentException if payload exceeds 1 MiB or is null
     */
    public Frame {
        if (payload == null) throw new IllegalArgumentException("payload must not be null");
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "Payload too large: " + payload.length + " bytes (max " + MAX_PAYLOAD_BYTES + ")");
        }
    }

    /** Returns the total wire size (header + payload). */
    public int wireSize() {
        return HEADER_SIZE + payload.length;
    }
}
