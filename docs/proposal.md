# Đề xuất đề tài — Giám sát và cấu hình máy tính trong LAN qua UDP

> M1 · Bản dự thảo ngày 15/10/2026 để nhóm review. Chưa có mã chức năng hoặc kết quả thực nghiệm. Thông tin thành viên, phân công và các lựa chọn dưới đây cần được nhóm xác nhận trước khi nộp.

## 1. Nhóm và đề tài

- **Đề tài:** Hệ thống giám sát và cấu hình máy tính trong mạng LAN qua UDP.
- **Ngữ cảnh:** phòng thực hành hoặc nhóm máy Windows có sự đồng ý của người sử dụng.
- **Thông tin nhóm:** nhóm tự điền tên, họ tên, mã sinh viên và lớp trong README; chưa suy diễn từ tài khoản GitHub.

## 2. Vấn đề

Người quản lý cần biết máy nào đang gửi cập nhật, tài nguyên nào gần quá tải và ứng dụng nào đang chạy mà không kiểm tra từng máy. Task Manager chỉ giúp quan sát tại máy; các bộ quản trị chuyên dụng là phương án thay thế, nhưng phạm vi đồ án tập trung một hệ thống LAN nhỏ, cài đặt rõ ràng và giao thức do nhóm thiết kế. Danh sách tiến trình không chứng minh người dùng đang thao tác; mất cập nhật cũng không xác định được máy tắt hay mất mạng.

## 3. Ý tưởng

Agent đọc CPU, RAM, dung lượng đĩa và gửi báo cáo định kỳ qua UDP đến một server. Server dùng Producer–Consumer để xử lý nhiều agent, hiển thị bảng console và cảnh báo theo ngưỡng. Người quản lý có thể yêu cầu cập nhật ngay, xem danh sách ứng dụng và thay đổi chu kỳ báo cáo. Trọng tâm kỹ thuật là hai mức độ tin cậy: báo cáo chấp nhận mất gói, còn lệnh cấu hình có retry và chống thực hiện lặp. Đây là mục tiêu học tập và áp dụng cho tình huống nhỏ, không tuyên bố thay thế sản phẩm quản trị thương mại.

## 4. Phạm vi

| Bắt buộc cho bản cuối | Tùy chọn nếu còn thời gian | Ngoài phạm vi |
|---|---|---|
| Agent Windows; một server; console; CPU/RAM/đĩa, trạng thái, cảnh báo | Giao diện đồ họa; xuất cảnh báo ra CSV | Theo dõi website, nội dung ứng dụng hoặc bàn phím |
| Ứng dụng đang chạy; so snapshot để nhận biết mở/đóng quan sát được | Agent hệ điều hành khác | Truyền file, điều khiển chuột/phím, chạy lệnh hệ thống |
| Phiên, HMAC, seq; GET_REPORT/GET_APPS/SET_INTERVAL; retry/chống trùng | Biểu đồ tài nguyên | Failover, nhiều server, lịch sử dài hạn, mã hóa nội dung |
| Docker, .env, test lỗi và đo tải bằng agent giả lập | | |

## 5. Thách thức mạng chính

UDP có thể mất, trùng hoặc đảo thứ tự gói. Báo cáo dùng số thứ tự theo phiên để bảo vệ dữ liệu mới; phiên mới phân biệt agent restart. Với lệnh, phản hồi có thể mất sau khi agent đã thực hiện: server thử lại cùng id/seq, agent lưu kết quả và từ chối lệnh cũ. Bắt tay challenge giúp đăng ký không bị phát lại tùy ý; HMAC xác thực nhưng không mã hóa. GET_APPS dùng snapshot phân trang, tránh thông điệp lớn. Hàng đợi và cache đều có giới hạn khi quá tải.

## 6. Phác thảo thiết kế

- **Transport — giao thức vận chuyển: UDP.** Báo cáo tài nguyên được gửi định kỳ, nên có thể chấp nhận mất một bản cập nhật và chờ bản mới. Các lệnh cần kết quả sẽ được bổ sung phản hồi, timeout, retry và chống thực hiện lặp ở tầng ứng dụng.
- **Giao thức tầng ứng dụng: LANMON/1**, do nhóm thiết kế để **chạy trên UDP**. LANMON viết tắt của LAN Monitoring, `/1` là phiên bản 1. LANMON/1 quy định cấu trúc thông điệp, các lệnh, quản lý phiên, mã lỗi và xác thực; đây không phải tên khác của UDP hay một giao thức vận chuyển mới.
- **Framing và định dạng:** mỗi datagram UDP chứa một phong bì JSON UTF-8 có mã xác thực HMAC, tối đa 1.200 byte. Payload JSON được mã hóa base64url để ký trên dữ liệu xác định; base64 không phải mã hóa bảo mật. Chi tiết: [protocol-design.md](protocol-design.md).
- **Năm nhóm lệnh đầu:** REGISTER (agent → server, bắt tay), REPORT (agent → server, báo cáo định kỳ), GET_REPORT (server → agent, lấy báo cáo ngay), GET_APPS (server → agent, lấy danh sách ứng dụng phân trang), SET_INTERVAL (server → agent, đổi chu kỳ). PING và UNREGISTER hỗ trợ vòng đời.
- **Đa luồng:** receiver → hàng đợi giới hạn → worker cố định; cập nhật nguyên tử theo agent; sender riêng và console đọc snapshot. Chi tiết: [architecture.md](architecture.md).
- **Ngôn ngữ và công cụ:** Java 21, Maven; agent Windows native, server đóng gói bằng Docker.

## 7. Phương án đã cân nhắc

| Lựa chọn đề xuất | Phương án khác | Đánh đổi cần nhóm xác nhận |
|---|---|---|
| UDP và độ tin cậy có chọn lọc | TCP cho toàn bộ | Có bài toán mất/trùng/đảo gói để thực nghiệm; phải tự thiết kế retry và giới hạn tải |
| Console, một server | GUI, nhiều server | Giảm thời gian giao diện/vận hành, tập trung giao thức |
| JSON và HMAC trên byte payload | Binary hoặc JSON canonical | Dễ đọc; phong bì base64 tốn byte nhưng tránh ký sai vì thứ tự khóa/cách biểu diễn số |
| Producer–Consumer | Xử lý hết trên receiver | Giới hạn tài nguyên, tách nhận gói khỏi xử lý; cần bảo vệ seq khi worker chạy song song |

## 8. Phân công dự kiến

| Thành viên | Phần sẽ phụ trách (module / tài liệu) |
|---|---|
| | |
| | |

Nhóm tự điền sau khi thống nhất, trước khi chốt M1. Chia theo thành phần hoặc luồng chức năng như server, agent, codec/xác thực kèm kiểm thử và tài liệu tương ứng; không chia thành một người chỉ viết code, một người chỉ viết tài liệu. Mỗi người cần hiểu và giải thích được phần mình nhận trách nhiệm.

## 9. Rủi ro và kiểm chứng dự kiến

| Rủi ro | Biện pháp giảm thiểu / kiểm chứng dự kiến |
|---|---|
| API Windows không hỗ trợ một số metric; lấy mẫu có thể bỏ lỡ ứng dụng mở/đóng nhanh | Biểu diễn metric không lấy được là null; đối chiếu công cụ hệ điều hành; ghi rõ giới hạn lấy mẫu thay vì khẳng định đã ghi nhận mọi lần mở/đóng. |
| UDP mất, trùng, đảo thứ tự gói hoặc server quá tải | Dùng phiên, số thứ tự, retry/chống trùng cho lệnh và hàng đợi giới hạn; test lỗi có seed; đo 10/50/100 agent và ghi cấu hình/số lần chạy. Không đặt trước kết quả đạt được. |
| Thu thập dữ liệu riêng hoặc lộ khóa xác thực | Agent chạy công khai trên máy được đồng ý; không thu website/nội dung/bàn phím; không đưa khóa thật vào Git. HMAC không che nội dung khỏi người bắt gói, nên v1 giới hạn trong LAN được quản lý. |
| Thiếu thời gian hoàn thành hoặc lệch hạn môn học | Giữ phạm vi một server và console; lịch nội bộ: tuần 1 đề xuất/thiết kế, tuần 2 skeleton Docker (M2), tuần 3–4 chức năng, tuần 5 kiểm thử, tuần 6 hoàn thiện (M3). Hạn chính thức lấy từ LMS, không suy ra từ Excel. |

## 10. Phản hồi giảng viên

Chưa có phản hồi; nhóm bổ sung sau khi được góp ý M1.
