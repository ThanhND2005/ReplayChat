package ltm.relaychat.common.protocol;

import com.google.gson.JsonObject;
import ltm.relaychat.common.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử phần kiểm tra hợp lệ của {@link Json#envelopeFromJson}.
 *
 * <p>Mục tiêu: mọi payload sai định dạng đều ra đúng một loại lỗi là
 * {@link MalformedEnvelopeException} (để server trả {@code ERROR 400}), và lỗi
 * mang theo {@code id} của yêu cầu mỗi khi đọc được, để client ghép được phản hồi.
 */
class EnvelopeValidationTest {

    /** Một Envelope hợp lệ, các test bên dưới lần lượt làm hỏng từng phần. */
    private static JsonObject validEnvelope() {
        JsonObject body = new JsonObject();
        body.addProperty("room", "ltm");

        JsonObject root = new JsonObject();
        root.addProperty("v", 1);
        root.addProperty("type", "ROOM_JOIN");
        root.addProperty("id", "c-7");
        root.addProperty("ts", 1_757_812_345_123L);
        root.add("body", body);
        return root;
    }

    private static MalformedEnvelopeException rejects(String json) {
        return assertThrows(MalformedEnvelopeException.class, () -> Json.envelopeFromJson(json),
                () -> "Payload phải bị từ chối: " + json);
    }

    // ── Payload không phải JSON object ───────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json at all", "{\"v\":1,", "[]", "123", "\"MSG_SEND\"", "null"})
    void payloadIsNotJsonObject_rejectedWithoutRequestId(String json) {
        MalformedEnvelopeException e = rejects(json);
        assertNull(e.requestId(), "Không đọc được id thì requestId phải là null");
    }

    @Test
    void nullString_rejected() {
        assertThrows(MalformedEnvelopeException.class, () -> Json.envelopeFromJson(null));
    }

    // ── Thiếu trường bắt buộc ────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"v", "type", "ts"})
    void missingRequiredField_rejectedAndKeepsRequestId(String field) {
        JsonObject root = validEnvelope();
        root.remove(field);

        MalformedEnvelopeException e = rejects(root.toString());
        assertTrue(e.getMessage().contains(field), "Thông báo lỗi phải nêu tên trường bị thiếu");
        assertEquals("c-7", e.requestId(), "Vẫn phải giữ id để client ghép được phản hồi ERROR");
    }

    @Test
    void missingId_rejectedWithoutRequestId() {
        JsonObject root = validEnvelope();
        root.remove("id");

        MalformedEnvelopeException e = rejects(root.toString());
        assertNull(e.requestId());
    }

    @Test
    void nullField_treatedAsMissing() {
        MalformedEnvelopeException e = rejects(
                "{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":null,\"body\":{}}");
        assertTrue(e.getMessage().contains("ts"));
    }

    // ── Sai kiểu dữ liệu ─────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":\"abc\",\"body\":{}}",       // ts là chuỗi
            "{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":\"123\",\"body\":{}}",       // ts là chuỗi chứa số
            "{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":1.5,\"body\":{}}",           // ts là số thực
            "{\"v\":1.5,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":0,\"body\":{}}",           // v là số thực
            "{\"v\":99999999999,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":0,\"body\":{}}",   // v tràn int
            "{\"v\":1,\"type\":5,\"id\":\"c-1\",\"ts\":0,\"body\":{}}",                    // type là số
            "{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":0,\"body\":5}",              // body không phải object
            "{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":0,\"body\":[]}",             // body là mảng
            "{\"v\":1,\"type\":{},\"id\":\"c-1\",\"ts\":0,\"body\":{}}"                    // type là object
    })
    void wrongFieldType_rejectedAndKeepsRequestId(String json) {
        MalformedEnvelopeException e = rejects(json);
        assertEquals("c-1", e.requestId());
    }

    @Test
    void idIsNotString_rejectedWithoutRequestId() {
        MalformedEnvelopeException e = rejects("{\"v\":1,\"type\":\"PING\",\"id\":42,\"ts\":0,\"body\":{}}");
        assertNull(e.requestId());
    }

    @Test
    void blankId_rejected() {
        rejects("{\"v\":1,\"type\":\"PING\",\"id\":\"  \",\"ts\":0,\"body\":{}}");
    }

    // ── Giá trị không hợp lệ ─────────────────────────────────────────────────

    @Test
    void unknownType_rejectedAndKeepsRequestId() {
        JsonObject root = validEnvelope();
        root.addProperty("type", "HACK_SERVER");

        MalformedEnvelopeException e = rejects(root.toString());
        assertEquals("c-7", e.requestId());
        assertInstanceOf(IllegalArgumentException.class, e,
                "Vẫn là IllegalArgumentException để code cũ bắt lỗi này chạy đúng");
    }

    @Test
    void unsupportedVersion_rejected() {
        JsonObject root = validEnvelope();
        root.addProperty("v", 2);

        MalformedEnvelopeException e = rejects(root.toString());
        assertEquals("c-7", e.requestId());
    }

    @Test
    void invalidJsonSyntax_keepsOriginalCause() {
        MalformedEnvelopeException e = rejects("{\"v\":1,");
        assertNotNull(e.getCause(), "Nên giữ lỗi cú pháp gốc để dễ gỡ lỗi");
    }

    // ── Trường hợp vẫn được chấp nhận ────────────────────────────────────────

    @Test
    void missingBody_treatedAsEmptyObject() {
        Envelope env = Json.envelopeFromJson("{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"ts\":0}");
        assertNotNull(env.body());
        assertEquals(0, env.body().size());
    }

    @Test
    void validEnvelope_parsedCorrectly() {
        Envelope env = Json.envelopeFromJson(validEnvelope().toString());
        assertEquals(MessageType.ROOM_JOIN, env.type());
        assertEquals("c-7", env.id());
        assertEquals(1_757_812_345_123L, env.ts());
        assertEquals("ltm", env.body().get("room").getAsString());
    }
}
