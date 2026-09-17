package ltm.relaychat.loadtest;

/**
 * Load test entry point.
 *
 * <h2>Command-line usage</h2>
 * <pre>
 *   java -jar relaychat-loadtest-fat.jar [options]
 *
 *   --host=&lt;host&gt;        server host         (default: localhost)
 *   --port=&lt;n&gt;           server port         (default: 9000)
 *   --clients=&lt;n&gt;        virtual clients     (default: 100)
 *   --rate=&lt;n&gt;           messages/sec/client (default: 2)
 *   --duration=&lt;s&gt;       test duration (sec) (default: 60)
 *   --out=&lt;file&gt;         output CSV path     (default: results.csv)
 * </pre>
 *
 * <p><b>Note:</b> This class is a stub for M0.  Full implementation in T5.
 */
public class LoadTest {

    public static void main(String[] args) {
        System.out.println("RelayChat LoadTest – M0 stub");
        System.out.println("Run with: --host=localhost --port=9000 --clients=500 --rate=2 --duration=60");
        // TODO T5: spin up VirtualClient threads, record latency, export CSV
    }
}
