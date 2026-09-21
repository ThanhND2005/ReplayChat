package ltm.relaychat.common.protocol;

import com.google.gson.JsonObject;
import ltm.relaychat.common.util.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link Envelope} serialisation / deserialisation via
 * {@link Json#envelopeToJson} and {@link Json#envelopeFromJson}.
 */
class EnvelopeJsonTest {

    @Test
    void roundTrip_msgSend() {
        JsonObject body = new JsonObject();
        body.addProperty("room", "ltm");
        body.addProperty("text", "chào cả nhóm");

        Envelope original = new Envelope(
                Envelope.PROTOCOL_VERSION,
                MessageType.MSG_SEND,
                "c-1042",
                1_757_812_345_123L,
                body
        );

        String json     = Json.envelopeToJson(original);
        Envelope parsed = Json.envelopeFromJson(json);

        assertEquals(original.version(), parsed.version());
        assertEquals(original.type(),    parsed.type());
        assertEquals(original.id(),      parsed.id());
        assertEquals(original.ts(),      parsed.ts());
        assertEquals("ltm",            parsed.body().get("room").getAsString());
        assertEquals("chào cả nhóm",  parsed.body().get("text").getAsString());
    }

    @Test
    void roundTrip_ping_emptyBody() {
        Envelope ping   = Envelope.of(MessageType.PING, "c-1");
        String   json   = Json.envelopeToJson(ping);
        Envelope parsed = Json.envelopeFromJson(json);

        assertEquals(MessageType.PING, parsed.type());
        assertEquals("c-1",           parsed.id());
        assertNotNull(parsed.body());
    }

    @Test
    void roundTrip_authOk_withToken() {
        JsonObject body = new JsonObject();
        body.addProperty("sessionToken", "abcdef1234567890");
        body.addProperty("username",     "alice");

        Envelope env    = Envelope.of(MessageType.AUTH_OK, "s-1", body);
        Envelope parsed = Json.envelopeFromJson(Json.envelopeToJson(env));

        assertEquals(MessageType.AUTH_OK,      parsed.type());
        assertEquals("abcdef1234567890",        parsed.body().get("sessionToken").getAsString());
        assertEquals("alice",                   parsed.body().get("username").getAsString());
    }

    @Test
    void roundTrip_error_withCodeAndMessage() {
        JsonObject body = new JsonObject();
        body.addProperty("code",    401);
        body.addProperty("message", "Unauthorized");

        Envelope env    = Envelope.of(MessageType.ERROR, "s-99", body);
        Envelope parsed = Json.envelopeFromJson(Json.envelopeToJson(env));

        assertEquals(MessageType.ERROR, parsed.type());
        assertEquals(401, parsed.body().get("code").getAsInt());
    }

    @Test
    void fromJson_unknownMessageType_throwsIllegalArgumentException() {
        String bad = "{\"v\":1,\"type\":\"INVALID_TYPE\",\"id\":\"c-1\",\"ts\":0,\"body\":{}}";
        assertThrows(IllegalArgumentException.class, () -> Json.envelopeFromJson(bad));
    }

    @Test
    void fromJson_malformedJson_throwsException() {
        assertThrows(Exception.class, () -> Json.envelopeFromJson("not json at all"));
    }

    @Test
    void toBytes_fromBytes_roundTrip() {
        Envelope env    = Envelope.of(MessageType.PONG, "s-42");
        byte[]   bytes  = Json.envelopeToBytes(env);
        Envelope parsed = Json.envelopeFromBytes(bytes);

        assertEquals(MessageType.PONG, parsed.type());
        assertEquals("s-42",           parsed.id());
    }

    @Test
    void roundTrip_allMessageTypes() {
        for (MessageType type : MessageType.values()) {
            JsonObject body = new JsonObject();
            body.addProperty("testKey", "testVal-" + type.name());
            Envelope original = Envelope.of(type, "c-" + type.name(), body);

            String json = Json.envelopeToJson(original);
            Envelope parsed = Json.envelopeFromJson(json);

            assertEquals(original.version(), parsed.version());
            assertEquals(type, parsed.type());
            assertEquals("c-" + type.name(), parsed.id());
            assertEquals("testVal-" + type.name(), parsed.body().get("testKey").getAsString());
        }
    }

    @Test
    void fromJson_missingRequiredFields_throwsIllegalArgumentException() {
        // Missing 'v'
        assertThrows(IllegalArgumentException.class, () ->
                Json.envelopeFromJson("{\"type\":\"PING\",\"id\":\"c-1\",\"ts\":1000,\"body\":{}}"));

        // Missing 'type'
        assertThrows(IllegalArgumentException.class, () ->
                Json.envelopeFromJson("{\"v\":1,\"id\":\"c-1\",\"ts\":1000,\"body\":{}}"));

        // Missing 'id'
        assertThrows(IllegalArgumentException.class, () ->
                Json.envelopeFromJson("{\"v\":1,\"type\":\"PING\",\"ts\":1000,\"body\":{}}"));

        // Missing 'ts'
        assertThrows(IllegalArgumentException.class, () ->
                Json.envelopeFromJson("{\"v\":1,\"type\":\"PING\",\"id\":\"c-1\",\"body\":{}}"));
    }

    @Test
    void envelope_nullBody_defaultsToEmptyJsonObject() {
        Envelope env = new Envelope(1, MessageType.PING, "c-1", 1000L, null);
        assertNotNull(env.body());
        assertTrue(env.body().entrySet().isEmpty());

        String json = Json.envelopeToJson(env);
        Envelope parsed = Json.envelopeFromJson(json);
        assertNotNull(parsed.body());
    }

    @Test
    void envelope_nullTypeOrId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new Envelope(1, null, "c-1", 1000L, new JsonObject()));
        assertThrows(NullPointerException.class, () ->
                new Envelope(1, MessageType.PING, null, 1000L, new JsonObject()));
    }

    @Test
    void roundTrip_vietnameseAndSpecialCharacters() {
        JsonObject body = new JsonObject();
        body.addProperty("content", "Xin chào RelayChat! Tiếng Việt có dấu: ă, â, đ, ê, ô, ơ, ư 🌏 🔥 🚀");
        Envelope original = Envelope.of(MessageType.MSG_SEND, "c-utf8", body);

        byte[] bytes = Json.envelopeToBytes(original);
        Envelope parsed = Json.envelopeFromBytes(bytes);

        assertEquals(original.type(), parsed.type());
        assertEquals(original.id(), parsed.id());
        assertEquals(
                "Xin chào RelayChat! Tiếng Việt có dấu: ă, â, đ, ê, ô, ơ, ư 🌏 🔥 🚀",
                parsed.body().get("content").getAsString()
        );
    }
}
