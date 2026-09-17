package ltm.relaychat.common.model;

/**
 * Metadata for a file being transferred through RelayChat.
 *
 * <p>Sent inside the {@code FILE_OFFER} message body and echoed back in
 * {@code FILE_AVAILABLE} when the upload completes successfully.
 *
 * @param originalName  original filename as provided by the sender (e.g. "report.pdf")
 * @param sizeBytes     total file size in bytes (must be ≤ 100 MB)
 * @param sha256        lower-case hex SHA-256 digest of the full file content,
 *                      computed by the sender before uploading
 * @param roomName      destination room where the file link will be announced
 * @param uploader      username of the user who initiated the upload
 * @param uploadToken   one-time server-issued token for the file channel (null until granted)
 */
public record FileMeta(
        String originalName,
        long   sizeBytes,
        String sha256,
        String roomName,
        String uploader,
        String uploadToken
) {
    /** Maximum allowed file size: 100 MiB. */
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /**
     * Validates the file size against the protocol limit.
     *
     * @throws IllegalArgumentException if {@code sizeBytes} exceeds 100 MiB
     */
    public FileMeta {
        if (sizeBytes > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File too large: " + sizeBytes + " bytes (max " + MAX_FILE_SIZE + ")");
        }
    }

    /** Returns a copy of this metadata with the upload token filled in. */
    public FileMeta withToken(String token) {
        return new FileMeta(originalName, sizeBytes, sha256, roomName, uploader, token);
    }
}
