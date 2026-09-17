package ltm.relaychat.common.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates monotonically-increasing correlation IDs for protocol messages.
 *
 * <h2>Format</h2>
 * <pre>
 *   {prefix}-{counter}
 * </pre>
 * Examples: {@code c-1}, {@code c-1042}, {@code s-7}, {@code A-57}.
 *
 * <h2>Convention (PROTOCOL.md §2)</h2>
 * <ul>
 *   <li>Client messages → prefix {@code "c"} (e.g. {@code c-1042})</li>
 *   <li>Server messages → prefix {@code "s"} (e.g. {@code s-200})</li>
 *   <li>Sync messages   → prefix matching the server id (e.g. {@code A-57})</li>
 * </ul>
 *
 * <p>Each {@code Ids} instance maintains its own counter, so callers should
 * create one instance per logical sender (client, server, sync channel).
 */
public final class Ids {

    private final String        prefix;
    private final AtomicLong    counter;

    /**
     * Creates a new ID generator with the given prefix and starting from 1.
     *
     * @param prefix short string prepended to every generated id (e.g. "c", "s", "A")
     */
    public Ids(String prefix) {
        this.prefix  = prefix;
        this.counter = new AtomicLong(0L);
    }

    /**
     * Returns the next unique correlation ID.
     *
     * @return id string such as {@code "c-1042"}
     */
    public String next() {
        return prefix + "-" + counter.incrementAndGet();
    }

    /** Returns the last generated id without incrementing the counter. */
    public String last() {
        return prefix + "-" + counter.get();
    }
}
