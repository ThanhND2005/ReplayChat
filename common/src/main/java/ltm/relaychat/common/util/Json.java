package ltm.relaychat.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import ltm.relaychat.common.protocol.Envelope;
import ltm.relaychat.common.protocol.MalformedEnvelopeException;
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
     * Đọc chuỗi JSON nhận từ socket thành {@link Envelope}, kiểm tra đủ các
     * ràng buộc của PROTOCOL.md §3.
     *
     * <p>Mọi lỗi định dạng đều quy về một loại ngoại lệ duy nhất là
     * {@link MalformedEnvelopeException}, nên server chỉ cần bắt một chỗ để trả
     * {@code ERROR 400}; không còn {@code NullPointerException} hay
     * {@code ClassCastException} lọt ra làm sập phiên.
     *
     * <p>Các trường hợp bị từ chối: không phải JSON object; thiếu hoặc
     * {@code null} ở {@code v}, {@code type}, {@code id}, {@code ts}; sai kiểu dữ
     * liệu (ví dụ {@code ts} là chuỗi, {@code v} là số thực); {@code id} rỗng;
     * {@code type} không có trong {@link MessageType}; {@code v} khác
     * {@link Envelope#PROTOCOL_VERSION}; {@code body} có mặt nhưng không phải object.
     * Thiếu {@code body} thì coi như {@code {}}.
     *
     * @param json chuỗi JSON đọc từ socket
     * @return Envelope đã kiểm tra hợp lệ
     * @throws MalformedEnvelopeException nếu payload vi phạm một trong các ràng buộc trên
     */
    public static Envelope envelopeFromJson(String json) {
        if (json == null) {
            throw new MalformedEnvelopeException("Payload rỗng", null, null);
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new MalformedEnvelopeException("Payload không phải JSON hợp lệ", null, e);
        }
        if (!parsed.isJsonObject()) {
            throw new MalformedEnvelopeException("Payload phải là một JSON object", null, null);
        }
        JsonObject root = parsed.getAsJsonObject();

        // Lấy id trước tiên, để dù trường khác sai thì phản hồi ERROR vẫn ghép được với yêu cầu
        String requestId = peekString(root, "id");

        int version = requireInt(root, "v", requestId);
        if (version != Envelope.PROTOCOL_VERSION) {
            throw new MalformedEnvelopeException(
                    "Không hỗ trợ phiên bản giao thức " + version, requestId, null);
        }

        String typeName = requireString(root, "type", requestId);
        MessageType type;
        try {
            type = MessageType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new MalformedEnvelopeException("Loại message không tồn tại: " + typeName, requestId, e);
        }

        String id   = requireString(root, "id", requestId);
        long   ts   = requireLong(root, "ts", requestId);
        JsonObject body = optionalBody(root, requestId);

        return new Envelope(version, type, id, ts, body);
    }

    /**
     * Convenience: parse an Envelope from raw UTF-8 bytes (frame payload).
     *
     * @param bytes raw payload bytes from a {@link ltm.relaychat.common.protocol.Frame}
     * @return parsed Envelope
     * @throws MalformedEnvelopeException nếu payload không phải Envelope hợp lệ
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

    // ── Kiểm tra từng trường của Envelope ────────────────────────────────────

    /** Trả về giá trị chuỗi của {@code field} nếu có và đúng kiểu, ngược lại {@code null}. Không ném lỗi. */
    private static String peekString(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return null;
    }

    /** Lấy {@code field} dưới dạng giá trị nguyên thủy; thiếu, {@code null} hoặc là object/mảng thì báo lỗi. */
    private static JsonPrimitive requirePrimitive(JsonObject root, String field, String requestId) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            throw new MalformedEnvelopeException("Thiếu trường bắt buộc '" + field + "'", requestId, null);
        }
        if (!element.isJsonPrimitive()) {
            throw wrongType(field, requestId, null);
        }
        return element.getAsJsonPrimitive();
    }

    /** Lấy {@code field} là chuỗi khác rỗng. */
    private static String requireString(JsonObject root, String field, String requestId) {
        JsonPrimitive value = requirePrimitive(root, field, requestId);
        if (!value.isString()) {
            throw wrongType(field, requestId, null);
        }
        String text = value.getAsString();
        if (text.isBlank()) {
            throw new MalformedEnvelopeException("Trường '" + field + "' không được rỗng", requestId, null);
        }
        return text;
    }

    /**
     * Lấy {@code field} là số nguyên kiểu {@code long}. Số thực (ví dụ {@code 1.5}),
     * số quá lớn hoặc chuỗi chứa số (ví dụ {@code "123"}) đều bị coi là sai kiểu.
     */
    private static long requireLong(JsonObject root, String field, String requestId) {
        JsonPrimitive value = requirePrimitive(root, field, requestId);
        if (!value.isNumber()) {
            throw wrongType(field, requestId, null);
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw wrongType(field, requestId, e);
        }
    }

    /** Lấy {@code field} là số nguyên kiểu {@code int}. */
    private static int requireInt(JsonObject root, String field, String requestId) {
        long value = requireLong(root, field, requestId);
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException e) {
            throw wrongType(field, requestId, e);
        }
    }

    /** Lấy {@code body}: thiếu hoặc {@code null} thì trả object rỗng, có mặt mà không phải object thì báo lỗi. */
    private static JsonObject optionalBody(JsonObject root, String requestId) {
        JsonElement element = root.get("body");
        if (element == null || element.isJsonNull()) {
            return new JsonObject();
        }
        if (!element.isJsonObject()) {
            throw new MalformedEnvelopeException("Trường 'body' phải là JSON object", requestId, null);
        }
        return element.getAsJsonObject();
    }

    private static MalformedEnvelopeException wrongType(String field, String requestId, Throwable cause) {
        return new MalformedEnvelopeException("Trường '" + field + "' sai kiểu dữ liệu", requestId, cause);
    }
}
