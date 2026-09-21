package ltm.relaychat.common.protocol;

import com.google.gson.JsonObject;

/**
 * Represents one RelayChat protocol message — the parsed form of a {@link Frame}.
 *
 * <h2>Wire example</h2>
 * <pre>
 * {
 *   "v":    1,
 *   "type": "MSG_SEND",
 *   "id":   "c-1042",
 *   "ts":   1757812345123,
 *   "body": { "room": "ltm", "text": "chào cả nhóm" }
 * }
 * </pre>
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code v}    – protocol version, always {@code 1} for this release.</li>
 *   <li>{@code type} – one of the {@link MessageType} constants.</li>
 *   <li>{@code id}   – correlation id chosen by the sender.  Client messages
 *       use the prefix {@code "c-"}, server messages use {@code "s-"}.
 *       Responses copy the request id so the sender can match them.</li>
 *   <li>{@code ts}   – Unix epoch in milliseconds (client wall-clock time).</li>
 *   <li>{@code body} – message-type–specific JSON object (may be empty).</li>
 * </ul>
 *
 * <p>Use {@link ltm.relaychat.common.util.Json} to serialise / deserialise.
 */
public record Envelope(
        int         version,
        MessageType type,
        String      id,
        long        ts,
        JsonObject  body
) {
    /** Current protocol version. */
    public static final int PROTOCOL_VERSION = 1;

    public Envelope {
        java.util.Objects.requireNonNull(type, "type must not be null");
        java.util.Objects.requireNonNull(id, "id must not be null");
        if (body == null) {
            body = new JsonObject();
        }
    }

    /**
     * Compact factory using the current timestamp.
     *
     * @param type message type
     * @param id   correlation id
     * @param body type-specific payload
     */
    public static Envelope of(MessageType type, String id, JsonObject body) {
        return new Envelope(PROTOCOL_VERSION, type, id, System.currentTimeMillis(), body);
    }

    /**
     * Factory for messages with an empty body.
     *
     * @param type message type
     * @param id   correlation id
     */
    public static Envelope of(MessageType type, String id) {
        return of(type, id, new JsonObject());
    }
}
