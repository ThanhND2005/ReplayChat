package ltm.relaychat.common.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kiểm thử {@link FrameCodec} qua socket TCP thật trên localhost.
 *
 * <p>Các test trong {@link FrameCodecTest} chỉ chạy trên luồng byte trong bộ nhớ.
 * Ở đây dữ liệu đi qua tầng TCP của hệ điều hành, nên một khung có thể đến
 * thành nhiều mảnh, hoặc nhiều khung đến dính nhau trong cùng một lần đọc —
 * đúng những gì server sẽ gặp khi chạy thật.
 *
 * <p>Đây là tiêu chí "Xong khi" của T1: gửi 10.000 khung qua localhost,
 * bên nhận khớp từng byte.
 */
class FrameCodecSocketTest {

    /** Số khung trong bài kiểm thử chính (tiêu chí của T1). */
    private static final int FRAME_COUNT = 10_000;

    /** Độ dài tối đa của payload ngẫu nhiên trong bài kiểm thử chính. */
    private static final int MAX_RANDOM_LENGTH = 8 * 1024;

    /**
     * Hạt giống cố định cho bộ sinh ngẫu nhiên. Bên gửi và bên nhận cùng dùng
     * hạt giống này nên sinh ra đúng một dãy payload; khi test hỏng, chạy lại
     * sẽ tái hiện được đúng khung bị lỗi.
     */
    private static final long SEED = 20260914L;

    /** Thời gian chờ tối đa (giây) cho accept, read và kết quả của luồng nhận. */
    private static final int WAIT_SECONDS = 30;

    // ── Các bài kiểm thử ─────────────────────────────────────────────────────

    /**
     * Gửi 10.000 khung liên tiếp qua một kết nối, bên nhận so khớp từng byte.
     *
     * <p>Ba khung đầu là trường hợp biên: payload rỗng, 1 byte và đúng 1 MiB.
     * Bên gửi bọc socket bằng {@link BufferedOutputStream} để header và payload
     * của mỗi khung đi ra trong một lần flush — cách server nên ghi khi chạy thật.
     */
    @Test
    @Timeout(60)
    void sendTenThousandFrames_receiverMatchesEveryByte() throws Exception {
        runExchange(FRAME_COUNT, MAX_RANDOM_LENGTH, (socket, count, rnd) -> {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
            FrameCodec codec = new FrameCodec(socket.getInputStream(), out);
            for (int i = 0; i < count; i++) {
                codec.write(new Frame(payloadFor(i, rnd, MAX_RANDOM_LENGTH)));
            }
        });
    }

    /**
     * Bên gửi cố tình cắt mỗi khung thành nhiều mảnh nhỏ 1–64 byte, flush sau
     * từng mảnh và tắt thuật toán Nagle để các mảnh đi thành những gói TCP riêng.
     * Bên nhận vẫn phải ghép lại đúng từng khung, qua đó kiểm chứng
     * {@code readFully} hoạt động đúng trên mạng thật chứ không chỉ trong bộ nhớ.
     */
    @Test
    @Timeout(60)
    void framesSplitIntoTinyTcpWrites_receiverReassemblesEachFrame() throws Exception {
        int count = 1_000;
        int maxLength = 512;
        runExchange(count, maxLength, (socket, n, rnd) -> {
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            // Dãy ngẫu nhiên riêng cho kích thước mảnh, để không làm lệch dãy payload của bên nhận
            Random pieceSizes = new Random(SEED + 1);
            for (int i = 0; i < n; i++) {
                byte[] payload = payloadFor(i, rnd, maxLength);
                byte[] wire = ByteBuffer.allocate(Frame.HEADER_SIZE + payload.length)
                        .putInt(payload.length)
                        .put(payload)
                        .array();
                int pos = 0;
                while (pos < wire.length) {
                    int size = Math.min(1 + pieceSizes.nextInt(64), wire.length - pos);
                    out.write(wire, pos, size);
                    out.flush();
                    pos += size;
                }
            }
        });
    }

    // ── Hạ tầng dùng chung ───────────────────────────────────────────────────

    /** Cách bên gửi ghi {@code count} khung, với payload sinh từ {@code rnd}, vào socket. */
    @FunctionalInterface
    private interface Sender {
        void send(Socket socket, int count, Random rnd) throws IOException;
    }

    /**
     * Mở một server socket trên cổng ngẫu nhiên của localhost, chạy bên nhận
     * trên luồng riêng rồi để {@code sender} gửi {@code count} khung.
     *
     * <p>Bên nhận tự sinh lại dãy payload mong đợi bằng cùng hạt giống, nên
     * không phải giữ 10.000 mảng byte trong bộ nhớ. Sau khung cuối, bên gửi
     * đóng kết nối và lần đọc tiếp theo phải báo {@link EOFException}, nghĩa là
     * không có byte thừa nào lọt qua.
     */
    private static void runExchange(int count, int maxRandomLength, Sender sender) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            // Không để accept chờ mãi nếu bên gửi hỏng trước khi kết nối được
            server.setSoTimeout(WAIT_SECONDS * 1000);

            Future<Integer> receiver = pool.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(WAIT_SECONDS * 1000);
                    FrameCodec codec = new FrameCodec(socket.getInputStream(), OutputStream.nullOutputStream());
                    Random expected = new Random(SEED);
                    for (int i = 0; i < count; i++) {
                        byte[] want = payloadFor(i, expected, maxRandomLength);
                        byte[] got = codec.read().payload();
                        int index = i;
                        assertArrayEquals(want, got, () -> "Khung thứ " + index + " bị sai nội dung");
                    }
                    assertThrows(EOFException.class, codec::read,
                            "Sau khung cuối cùng không được còn dữ liệu thừa");
                    return count;
                }
            });

            // Nếu bên nhận phát hiện lỗi và đóng kết nối sớm, bên gửi sẽ gặp IOException.
            // Giữ lại lỗi đó và ưu tiên báo lỗi của bên nhận, vì đó mới là nguyên nhân thật.
            IOException sendError = null;
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                sender.send(socket, count, new Random(SEED));
            } catch (IOException e) {
                sendError = e;
            }

            int received = awaitResult(receiver);
            if (sendError != null) {
                throw sendError;
            }
            assertEquals(count, received, "Bên nhận phải đọc đủ số khung đã gửi");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Sinh payload cho khung thứ {@code index}.
     *
     * <p>Khung 0, 1, 2 là trường hợp biên: rỗng, 1 byte và đúng
     * {@link Frame#MAX_PAYLOAD_BYTES}. Các khung còn lại có độ dài ngẫu nhiên từ
     * 0 đến {@code maxRandomLength}. Nội dung là byte bất kỳ chứ không phải JSON,
     * vì {@link FrameCodec} không quan tâm payload chứa gì.
     *
     * <p>Bên gửi và bên nhận phải gọi hàm này theo cùng thứ tự để dãy ngẫu nhiên khớp nhau.
     */
    private static byte[] payloadFor(int index, Random rnd, int maxRandomLength) {
        int length = switch (index) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> Frame.MAX_PAYLOAD_BYTES;
            default -> rnd.nextInt(maxRandomLength + 1);
        };
        byte[] payload = new byte[length];
        rnd.nextBytes(payload);
        return payload;
    }

    /**
     * Chờ kết quả của luồng nhận. Nếu luồng đó ném lỗi, ném lại lỗi gốc để
     * JUnit hiện đúng thông báo so khớp thay vì một {@link ExecutionException} chung chung.
     */
    private static <T> T awaitResult(Future<T> future) throws Exception {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
