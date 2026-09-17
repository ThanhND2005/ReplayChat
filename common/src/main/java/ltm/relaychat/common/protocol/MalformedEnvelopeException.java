package ltm.relaychat.common.protocol;

/**
 * Ném ra khi payload của một khung không phải Envelope hợp lệ theo PROTOCOL.md §3.2:
 * không phải JSON object, thiếu trường bắt buộc, sai kiểu dữ liệu, {@code type}
 * không tồn tại hoặc {@code v} khác phiên bản đang hỗ trợ.
 *
 * <p>Bên nhận (thường là server) nên bắt ngoại lệ này và trả {@code ERROR 400}
 * <b>mà không đóng kết nối</b>: lỗi chỉ nằm ở một thông điệp, luồng byte vẫn
 * đúng khung nên các khung tiếp theo đọc được bình thường. Khác với
 * {@link FrameCodec.FrameTooLargeException}, lỗi đó làm hỏng cả luồng và bắt
 * buộc phải đóng kết nối.
 *
 * <p>Kế thừa {@link IllegalArgumentException} để code đang bắt ngoại lệ đó vẫn chạy đúng.
 */
public final class MalformedEnvelopeException extends IllegalArgumentException {

    /** {@code id} của yêu cầu nếu đọc được, ngược lại {@code null}. */
    private final String requestId;

    /**
     * @param message   mô tả lỗi, dùng làm {@code message} trong phản hồi {@code ERROR}
     * @param requestId {@code id} của yêu cầu nếu đọc được, hoặc {@code null}
     * @param cause     lỗi gốc (ví dụ lỗi cú pháp JSON), hoặc {@code null}
     */
    public MalformedEnvelopeException(String message, String requestId, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
    }

    /**
     * {@code id} của yêu cầu bị lỗi, để phản hồi {@code ERROR} dùng lại cho client
     * ghép cặp được. Trả về {@code null} nếu payload hỏng tới mức không đọc được
     * {@code id}; khi đó server tự sinh id dạng {@code s-…}.
     */
    public String requestId() {
        return requestId;
    }
}
