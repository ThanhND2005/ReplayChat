package ltm.relaychat.common.model;

/**
 * Represents a single chat message stored in the database.
 *
 * <p>The {@code globalId} field uses the format {@code <serverId>-<seq>}
 * (e.g. {@code "A-57"}) so that messages from two servers can be merged
 * without collision.  See {@code PROTOCOL.md §5 Sync}.
 *
 * @param id       local SQLite auto-increment id
 * @param globalId globally unique message id used for sync and deduplication
 * @param roomName name of the room this message belongs to
 * @param sender   username of the message author
 * @param text     message body
 * @param ts       Unix epoch in milliseconds (client wall-clock)
 * @param delivered whether the message was delivered to all online room members
 */
public record ChatMessage(
        long    id,
        String  globalId,
        String  roomName,
        String  sender,
        String  text,
        long    ts,
        boolean delivered
) { }
