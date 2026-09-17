package ltm.relaychat.common.model;

/**
 * Represents a chat room.
 *
 * @param id           internal unique room id (auto-increment from SQLite)
 * @param name         unique room name (e.g. "ltm", "general")
 * @param passwordHash BCrypt hash of the room password, or {@code null} for public rooms
 * @param createdBy    username of the room creator
 */
public record Room(long id, String name, String passwordHash, String createdBy) {

    /** Returns {@code true} if this room requires a password to join. */
    public boolean isProtected() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
}
