package ltm.relaychat.common.protocol;

/**
 * All message types used in the RelayChat protocol (v1).
 *
 * <p>Every message on the wire must include a {@code "type"} field whose value
 * is one of these constants.  See {@code PROTOCOL.md} for full semantics.
 *
 * <h2>Groups</h2>
 * <ul>
 *   <li><b>Auth</b>   – REGISTER LOGIN RESUME LOGOUT AUTH_OK AUTH_FAIL</li>
 *   <li><b>Room</b>   – ROOM_LIST ROOM_CREATE ROOM_JOIN ROOM_LEAVE ROOM_MEMBERS</li>
 *   <li><b>Message</b>– MSG_SEND MSG_ACK MSG_DELIVER MSG_HISTORY PRESENCE</li>
 *   <li><b>File</b>   – FILE_OFFER FILE_UPLOAD_TOKEN FILE_AVAILABLE</li>
 *   <li><b>Sync</b>   – SYNC_HELLO SYNC_MSG SYNC_PRESENCE SYNC_PING</li>
 *   <li><b>System</b> – PING PONG ERROR</li>
 * </ul>
 */
public enum MessageType {

    // ── Authentication ──────────────────────────────────────────────────────
    /** Client requests a new account. */
    REGISTER,
    /** Client authenticates with username + password. */
    LOGIN,
    /** Client resumes a previous session (after reconnect / server failover). */
    RESUME,
    /** Client ends its session gracefully. */
    LOGOUT,
    /** Server confirms successful authentication, carries sessionToken. */
    AUTH_OK,
    /** Server rejects the authentication attempt. */
    AUTH_FAIL,

    // ── Room management ─────────────────────────────────────────────────────
    /** Client requests the list of available rooms. */
    ROOM_LIST,
    /** Client requests creation of a new room. */
    ROOM_CREATE,
    /** Client joins a room. */
    ROOM_JOIN,
    /** Client leaves a room. */
    ROOM_LEAVE,
    /** Server pushes the current member list of a room. */
    ROOM_MEMBERS,

    // ── Messaging ───────────────────────────────────────────────────────────
    /** Client sends a chat message to a room. */
    MSG_SEND,
    /** Server acknowledges receipt of MSG_SEND from the originating client. */
    MSG_ACK,
    /** Server delivers a chat message to every member of a room. */
    MSG_DELIVER,
    /** Client requests message history for a room (paginated). */
    MSG_HISTORY,
    /** Server broadcasts a user's online/offline status change. */
    PRESENCE,

    // ── File transfer ────────────────────────────────────────────────────────
    /** Client announces an intent to upload a file (name, size, sha256). */
    FILE_OFFER,
    /** Server issues a one-time upload token and TCP port for the file channel. */
    FILE_UPLOAD_TOKEN,
    /** Server notifies room members that a file is ready to download. */
    FILE_AVAILABLE,

    // ── Server-to-server sync ────────────────────────────────────────────────
    /** Handshake between two server nodes (id, version). */
    SYNC_HELLO,
    /** Replicates a chat message to the peer server. */
    SYNC_MSG,
    /** Replicates a PRESENCE event to the peer server. */
    SYNC_PRESENCE,
    /** Keep-alive ping on the sync channel. */
    SYNC_PING,

    // ── System ───────────────────────────────────────────────────────────────
    /** Client or server heartbeat probe. */
    PING,
    /** Response to PING. */
    PONG,
    /**
     * Generic error response.  The body must include:
     * <pre>{ "code": 401, "message": "Unauthorized" }</pre>
     * Valid codes: 400 401 403 404 409 413 429 500.
     */
    ERROR
}
