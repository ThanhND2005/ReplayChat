package ltm.relaychat.common.protocol;

/**
 * Standardised error codes returned in {@code ERROR} messages.
 *
 * <p>Each constant maps to an HTTP-inspired numeric code that is sent in the
 * wire message as:
 * <pre>
 * { "v": 1, "type": "ERROR", "id": "s-...", "ts": ...,
 *   "body": { "code": 401, "message": "Unauthorized" } }
 * </pre>
 */
public enum ErrorCode {

    /** The request is malformed or missing required fields. */
    BAD_REQUEST(400, "Bad Request"),

    /** Authentication required or token expired. */
    UNAUTHORIZED(401, "Unauthorized"),

    /** The authenticated user lacks permission for this operation. */
    FORBIDDEN(403, "Forbidden"),

    /** The requested resource (room, user, file, message) does not exist. */
    NOT_FOUND(404, "Not Found"),

    /**
     * A conflict occurred, e.g. username already taken or room already exists.
     */
    CONFLICT(409, "Conflict"),

    /** The payload exceeds the allowed maximum (1 MiB for messages, 100 MB for files). */
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),

    /**
     * The client is sending messages too fast.
     * Rate limit: 20 messages/second per session (enforced from T6).
     */
    TOO_MANY_REQUESTS(429, "Too Many Requests"),

    /** An unexpected server-side error occurred. */
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    // -------------------------------------------------------------------------

    private final int    code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code           = code;
        this.defaultMessage = defaultMessage;
    }

    /** Numeric error code sent on the wire. */
    public int code() { return code; }

    /** Default human-readable message for this error code. */
    public String defaultMessage() { return defaultMessage; }

    /**
     * Looks up an {@code ErrorCode} by its numeric code.
     *
     * @param code numeric code from the wire
     * @return the matching {@code ErrorCode}
     * @throws IllegalArgumentException if no match is found
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) return ec;
        }
        throw new IllegalArgumentException("Unknown error code: " + code);
    }

    @Override
    public String toString() {
        return code + " " + defaultMessage;
    }
}
