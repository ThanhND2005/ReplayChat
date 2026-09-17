package ltm.relaychat.server;

/**
 * Entry point for the RelayChat server.
 *
 * <h2>Command-line usage</h2>
 * <pre>
 *   java -jar relaychat-server-fat.jar [options]
 *
 *   --mode=iterative|pool|nio   server model      (default: pool)
 *   --port=&lt;n&gt;                  control port      (default: 9000)
 *   --file-port=&lt;n&gt;             file channel port (default: 9001)
 *   --sync-port=&lt;n&gt;             sync channel port (default: 9100)
 *   --tls                       enable TLS 1.3    (default: off)
 *   --id=A|B                    server identity   (required for sync)
 *   --peer=host:port            peer server addr  (optional)
 *   --db=path                   SQLite db path    (default: relaychat.db)
 * </pre>
 *
 * <p><b>Note:</b> This class is a stub for M0.  Full implementation starts
 * in T2 (IterativeServer) and T3 (ThreadPoolServer).
 */
public class ServerMain {

    public static void main(String[] args) {
        System.out.println("RelayChat Server – M0 stub");
        System.out.println("Run with: --mode=iterative|pool|nio --port=9000 --tls --id=A --peer=host:9100");
        // TODO T2: parse args and start the appropriate server model
    }
}
