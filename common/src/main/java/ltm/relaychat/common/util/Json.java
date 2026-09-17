package ltm.relaychat.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ltm.relaychat.common.protocol.Envelope;
import ltm.relaychat.common.protocol.MessageType;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper around <a href="https://github.com/google/gson">Gson</a> for the
 * RelayChat protocol.
 *
 * <p><b>All JSON serialisation must go through this class.</b>  Do not
 * instantiate {@code Gson} elsewhere; centralising it here makes it easy to
 * swap the library or add custom adapters later.
 *
 * <h2>Envelope wire format</h2>
 * <pre>
 * {
 *   "v":    1,
 *   "type": "MSG_SEND",
 *   "id":   "c-1042",
 *   "ts":   1757812345123,
 *   "body": { "room": "ltm", "text": "hello" }
 * }
 * </pre>
 */
public final class Json {

    /** Shared Gson instance — thread-safe for read/write after construction. */
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private Json() { /* utility class */ }

    // ── Generic helpers ──────────────────────────────────────────────────────

    /** Serialise any object to a JSON string. */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /** Deserialise a JSON string to the given class. */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /** Deserialise a JSON string to a generic type (e.g., {@code List<String>}). */
    public static <T> T fromJson(String json, Type type) {
        return GSON.fromJson(json, type);
    }

    /** Parse a JSON string into a {@link JsonObject} for manual field access. */
    public static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // ── Envelope helpers ─────────────────────────────────────────────────────

    /**
     * Serialises an {@link Envelope} to its wire JSON representation.
     *
     * <p>Output format:
     * <pre>{"v":1,"type":"MSG_SEND","id":"c-1042","ts":1757812345123,"body":{...}}</pre>
     *
     * @param envelope the envelope to serialise
     * @return compact JSON string
     */
    public static String envelopeToJson(Envelope envelope) {
        JsonObject root = new JsonObject();
        root.addProperty("v",    envelope.version());
        root.addProperty("type", envelope.type().name());
        root.addProperty("id",   envelope.id());
        root.addProperty("ts",   envelope.ts());
        root.add("body",         envelope.body() != null ? envelope.body() : new JsonObject());
        return GSON.toJson(root);
    }

    /**
     * Deserialises a wire JSON string into an {@link Envelope}.
     *
     * @param json the JSON string read from the socket
     * @return parsed Envelope
     * @throws com.google.gson.JsonSyntaxException if the JSON is malformed
     * @throws IllegalArgumentException            if the {@code type} field is unknown
     */
    public static Envelope envelopeFromJson(String json) {
        JsonObject root = parseObject(json);

        int         version = root.get("v").getAsInt();
        MessageType type    = MessageType.valueOf(root.get("type").getAsString());
        String      id      = root.get("id").getAsString();
        long        ts      = root.get("ts").getAsLong();
        JsonObject  body    = root.has("body") && root.get("body").isJsonObject()
                              ? root.getAsJsonObject("body")
                              : new JsonObject();

        return new Envelope(version, type, id, ts, body);
    }

    /**
     * Convenience: parse an Envelope from raw UTF-8 bytes (frame payload).
     *
     * @param bytes raw payload bytes from a {@link ltm.relaychat.common.protocol.Frame}
     * @return parsed Envelope
     */
    public static Envelope envelopeFromBytes(byte[] bytes) {
        return envelopeFromJson(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Convenience: serialise an Envelope to raw UTF-8 bytes (ready for a Frame).
     *
     * @param envelope the envelope to serialise
     * @return UTF-8 encoded JSON bytes
     */
    public static byte[] envelopeToBytes(Envelope envelope) {
        return envelopeToJson(envelope).getBytes(StandardCharsets.UTF_8);
    }
}
