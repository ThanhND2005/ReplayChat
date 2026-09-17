package ltm.relaychat.common.model;

/**
 * Represents a registered user.
 *
 * <p>Passwords are <strong>never</strong> stored in plain text — only the
 * BCrypt hash (handled in the {@code server} module's {@code AuthService}).
 *
 * @param id           internal unique user id (auto-increment from SQLite)
 * @param username     unique login name chosen by the user
 * @param passwordHash BCrypt hash of the user's password
 * @param online       whether the user currently has an active session
 */
public record User(long id, String username, String passwordHash, boolean online) {

    /**
     * Factory for creating a new user object before it is persisted (id unknown yet).
     *
     * @param username     login name
     * @param passwordHash BCrypt hash
     */
    public static User newUser(String username, String passwordHash) {
        return new User(0L, username, passwordHash, false);
    }

    /** Returns a copy of this user marked as online. */
    public User withOnline(boolean online) {
        return new User(id, username, passwordHash, online);
    }
}
