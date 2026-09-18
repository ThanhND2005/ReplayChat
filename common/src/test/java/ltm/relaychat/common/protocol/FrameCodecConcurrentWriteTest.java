package ltm.relaychat.common.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử ghi đồng thời: nhiều luồng dùng chung một {@link FrameCodec} để ghi
 * vào cùng một kết nối.
 *
 * <p>Đây là tình huống server gặp từ T3: khi broadcast, nhiều phiên khác nhau
 * cùng gửi {@code MSG_DELIVER} tới một client, trong lúc chính phiên của client
 * đó đang trả {@code MSG_ACK}. Nếu {@code write} không được đồng bộ, header của
 * khung này có thể chen vào giữa payload của khung khác và cả kết nối bị hỏng.
 */
class FrameCodecConcurrentWriteTest {

    /** Số luồng cùng ghi. */
    private static final int THREADS = 8;

    /** Số khung mỗi luồng ghi. */
    private static final int FRAMES_PER_THREAD = 2_000;

    /** Kích thước payload tối thiểu: 4 byte số hiệu luồng + 4 byte số thứ tự. */
    private static final int ID_BYTES = 8;

    @Test
    @Timeout(60)
    void manyThreadsWriteSameCodec_everyFrameStaysIntact() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        FrameCodec codec = new FrameCodec(InputStream.nullInputStream(), sink);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            // Cổng xuất phát: mọi luồng chờ ở đây rồi cùng bắt đầu, để tăng khả năng chen ngang nhau
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> writers = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                writers.add(pool.submit(() -> {
                    start.await();
                    for (int seq = 0; seq < FRAMES_PER_THREAD; seq++) {
                        codec.write(new Frame(payload(thread, seq)));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> writer : writers) {
                writer.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        verifyStream(sink.toByteArray());
    }

    /**
     * Đọc lại toàn bộ luồng byte đã ghi và kiểm tra từng khung còn nguyên vẹn.
     *
     * <p>Thứ tự giữa các luồng có thể xen kẽ tùy ý, nhưng khung của cùng một
     * luồng phải giữ đúng thứ tự đã ghi, và tổng số khung phải đủ. Nếu luồng byte
     * bị trộn, việc đọc sẽ ném lỗi (độ dài khai báo vô lý, hết luồng giữa chừng)
     * hoặc nội dung khung sẽ sai ở các phép so khớp bên dưới.
     */
    private static void verifyStream(byte[] wire) throws Exception {
        FrameCodec reader = new FrameCodec(new ByteArrayInputStream(wire), OutputStream.nullOutputStream());
        int[] nextSeq = new int[THREADS];

        for (int i = 0; i < THREADS * FRAMES_PER_THREAD; i++) {
            byte[] payload = reader.read().payload();
            assertTrue(payload.length >= ID_BYTES, "Khung thứ " + i + " quá ngắn, luồng byte đã bị trộn");

            ByteBuffer buf = ByteBuffer.wrap(payload);
            int thread = buf.getInt();
            int seq = buf.getInt();
            assertTrue(thread >= 0 && thread < THREADS, "Khung thứ " + i + " có số hiệu luồng vô lý: " + thread);
            assertEquals(nextSeq[thread], seq, "Khung của luồng " + thread + " bị mất hoặc sai thứ tự");

            byte[] expected = payload(thread, seq);
            assertTrue(java.util.Arrays.equals(expected, payload),
                    "Nội dung khung " + thread + "/" + seq + " bị hỏng");
            nextSeq[thread]++;
        }

        assertThrows(EOFException.class, reader::read, "Không được còn byte thừa sau khung cuối");
    }

    /**
     * Payload của khung thứ {@code seq} do luồng {@code thread} ghi: 4 byte số hiệu
     * luồng, 4 byte số thứ tự, rồi một đoạn byte lặp có độ dài và giá trị suy ra
     * từ hai số đó. Nhờ vậy bên đọc tự tính lại được nội dung mong đợi.
     */
    private static byte[] payload(int thread, int seq) {
        int fillerLength = (thread * 131 + seq * 17) % 1500;
        byte fillerByte = (byte) (thread * 31 + seq);
        ByteBuffer buf = ByteBuffer.allocate(ID_BYTES + fillerLength);
        buf.putInt(thread).putInt(seq);
        while (buf.hasRemaining()) {
            buf.put(fillerByte);
        }
        return buf.array();
    }
}
