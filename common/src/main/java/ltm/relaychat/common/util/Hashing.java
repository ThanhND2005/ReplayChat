package ltm.relaychat.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Utility methods for hashing and random-token generation.
 *
 * <h2>Password hashing</h2>
 * <p>Passwords are hashed with BCrypt (via the jBCrypt library in the
 * {@code server} module).  This class only provides the SHA-256 helper used
 * for file-integrity checks.
 *
 * <h2>File integrity</h2>
 * <p>Every file upload includes a {@code sha256} field in the
 * {@code FILE_OFFER} body.  The server recomputes the hash after receiving the
 * full file and rejects the upload if they differ.
 */
public final class Hashing {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat    HEX           = HexFormat.of();

    private Hashing() { /* utility class */ }

    /**
     * Computes the SHA-256 digest of {@code data} and returns it as a
     * lower-case hex string.
     *
     * @param data bytes to hash
     * @return 64-character lower-case hex string
     */
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec; this cannot happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Generates a cryptographically-random hex string of {@code byteLength}
     * bytes (resulting string length = {@code byteLength * 2}).
     *
     * <p>Used to create session tokens (32 bytes → 64-char hex) and
     * single-use file upload tokens.
     *
     * @param byteLength number of random bytes to generate
     * @return lower-case hex string
     */
    public static String randomHexToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /** Convenience overload: 32-byte (256-bit) session token. */
    public static String randomSessionToken() {
        return randomHexToken(32);
    }
}
