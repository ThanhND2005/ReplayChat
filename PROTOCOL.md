# PROTOCOL.md — RelayChat Protocol Specification v1

> **Đây là tài liệu giao thức chính thức (nguồn chuẩn duy nhất).**  
> Mọi thay đổi phải qua Pull Request và được **cả hai thành viên** duyệt.  
> Phiên bản: **v1** · Cập nhật lần cuối: T1 (thêm §3.1 quy tắc phản hồi, §3.2 Envelope sai định dạng; thống nhất `AUTH_FAIL`)

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Khung dữ liệu (Wire Frame)](#2-khung-dữ-liệu-wire-frame)
3. [Cấu trúc Envelope (thông điệp)](#3-cấu-trúc-envelope-thông-điệp)
4. [Bảng message types](#4-bảng-message-types)
5. [Mã lỗi](#5-mã-lỗi)
6. [Luồng đăng ký & đăng nhập](#6-luồng-đăng-ký--đăng-nhập)
7. [Luồng quản lý phòng](#7-luồng-quản-lý-phòng)
8. [Luồng gửi tin nhắn](#8-luồng-gửi-tin-nhắn)
9. [Luồng gửi file](#9-luồng-gửi-file)
10. [Luồng đồng bộ hai server](#10-luồng-đồng-bộ-hai-server)
11. [Heartbeat & timeout](#11-heartbeat--timeout)
12. [Kênh file (cổng 9001)](#12-kênh-file-cổng-9001)
13. [Quy ước correlation ID](#13-quy-ước-correlation-id)

---

## 1. Tổng quan

RelayChat chạy trên **TCP** (và TLS 1.3 khi được bật). Có ba kênh riêng:

| Kênh | Cổng | Mục đích |
|------|------|----------|
| **Control** | 9000 | Mọi lệnh chat: xác thực, phòng, tin nhắn, PING/PONG |
| **File** | 9001 | Nhận/gửi file nhị phân (kênh riêng để không chặn chat) |
| **Sync** | 9100 | Đồng bộ tin nhắn và presence giữa hai server |

Kênh **control** và **sync** dùng cùng một định dạng: **khung độ dài + JSON UTF-8**.  
Kênh **file** dùng giao thức nhị phân tối giản (xem §12).

---

## 2. Khung dữ liệu (Wire Frame)

```
 0         4
 ┌─────────┬────────────────────────────────────────────────────────┐
 │ length  │  payload                                               │
 │ uint32  │  JSON UTF-8, tối đa 1 MiB (1 048 576 byte)            │
 │ big-end │                                                        │
 └─────────┴────────────────────────────────────────────────────────┘
```

- **`length`** — 4 byte, big-endian unsigned integer, bằng độ dài của `payload` tính theo byte.
- **`payload`** — JSON UTF-8. Tối đa **1 048 576 byte** (1 MiB).
- Nếu `length > 1 048 576`: server/client đóng kết nối ngay, không phản hồi.
- Phải dùng `DataInputStream.readFully` để đọc — không được giả sử một lần `read()` trả đủ byte.

---

## 3. Cấu trúc Envelope (thông điệp)

Mọi thông điệp trên kênh control/sync đều là một JSON object với các trường sau:

```json
{
  "v":    1,
  "type": "MSG_SEND",
  "id":   "c-1042",
  "ts":   1757812345123,
  "body": {
    "room": "ltm",
    "text": "chào cả nhóm"
  }
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
|--------|------|----------|-------|
| `v` | `int` | ✔ | Phiên bản giao thức. Luôn là `1`. |
| `type` | `string` | ✔ | Tên message type (xem §4). |
| `id` | `string` | ✔ | Correlation ID. Client dùng tiền tố `c-`, server dùng `s-`. Phản hồi copy lại `id` của yêu cầu. |
| `ts` | `long` | ✔ | Unix epoch milliseconds (đồng hồ của người gửi). |
| `body` | `object` | ✔ | Dữ liệu tuỳ theo `type`. Có thể là `{}` nếu không cần thêm trường. Bên nhận coi `body` bị thiếu là `{}`. |

### 3.1 Quy tắc phản hồi

Mỗi yêu cầu client gửi (C→S) nhận **đúng một phản hồi kết thúc**, mang **cùng `id`** với yêu cầu:

| Kết quả | `type` của phản hồi | `body` |
|---------|---------------------|--------|
| Thành công | **Trùng `type` của yêu cầu** (ví dụ `ROOM_JOIN` → `ROOM_JOIN`) | Dữ liệu kết quả, xem §4 |
| Thất bại | `ERROR` | `{ "code": 404, "message": "..." }` |

Các yêu cầu có tên phản hồi riêng (đã có sẵn trong `MessageType`):

| Yêu cầu | Thành công | Thất bại |
|---------|-----------|----------|
| `REGISTER`, `LOGIN`, `RESUME` | `AUTH_OK` | `AUTH_FAIL` (body giống `ERROR`) |
| `MSG_SEND` | `MSG_ACK` | `ERROR` |
| `MSG_HISTORY` | Chuỗi `MSG_DELIVER`, kết thúc bằng `MSG_ACK { "historyEnd": true }` | `ERROR` |
| `FILE_OFFER` | `FILE_UPLOAD_TOKEN` | `ERROR` |
| `PING` | `PONG` | — |

Thông điệp server **tự phát**, không phải phản hồi cho yêu cầu nào (`MSG_DELIVER` tin của người khác, `PRESENCE`, `ROOM_MEMBERS`, `FILE_AVAILABLE`), dùng `id` do server sinh với tiền tố `s-`.

### 3.2 Envelope sai định dạng

Bên nhận trả `ERROR 400` và **giữ nguyên kết nối** khi payload:

- không phải JSON object;
- thiếu hoặc `null` ở `v`, `type`, `id`, `ts`;
- sai kiểu dữ liệu (ví dụ `ts` là chuỗi, `v` là số thực), hoặc `id` rỗng;
- có `type` không nằm trong §4, hoặc `v` khác `1`;
- có `body` nhưng không phải object.

`id` của `ERROR` là `id` của yêu cầu nếu đọc được, nếu không thì server tự sinh `s-…`.
Trong code: `Json.envelopeFromJson` ném `MalformedEnvelopeException`, lấy `id` bằng `requestId()`.

> Khác với khung dài quá 1 MiB (§2): trường hợp đó luồng byte đã hỏng nên phải đóng kết nối. Envelope sai định dạng chỉ hỏng một thông điệp, các khung sau vẫn đọc được.

---

## 4. Bảng message types

### 4.1 Xác thực (Auth)

| Type | Hướng | Mô tả | Body |
|------|-------|-------|------|
| `REGISTER` | C→S | Đăng ký tài khoản mới | `{ "username": "...", "password": "..." }` |
| `LOGIN` | C→S | Đăng nhập | `{ "username": "...", "password": "..." }` |
| `RESUME` | C→S | Khôi phục phiên sau reconnect/failover | `{ "sessionToken": "...", "lastMsgId": "A-57" }` |
| `LOGOUT` | C→S | Kết thúc phiên | `{}` |
| `AUTH_OK` | S→C | Xác thực thành công | `{ "sessionToken": "...", "username": "..." }` |
| `AUTH_FAIL` | S→C | Xác thực thất bại | `{ "code": 401, "message": "..." }` |

### 4.2 Quản lý phòng (Room)

| Type | Hướng | Mô tả | Body |
|------|-------|-------|------|
| `ROOM_LIST` | C→S | Yêu cầu danh sách phòng | `{}` |
| `ROOM_CREATE` | C→S | Tạo phòng mới | `{ "name": "...", "password": "..." }` (password tuỳ chọn) |
| `ROOM_JOIN` | C→S | Vào phòng | `{ "room": "...", "password": "..." }` (password nếu cần) |
| `ROOM_LEAVE` | C→S | Rời phòng | `{ "room": "..." }` |
| `ROOM_MEMBERS` | S→C | Danh sách thành viên phòng (push khi ai vào/ra) | `{ "room": "...", "members": [{ "username": "...", "online": true }] }` |

Phản hồi theo quy tắc chung §3.1: thành công trả về **cùng `type` với yêu cầu**, thất bại trả `ERROR`.

| Yêu cầu | Body phản hồi khi thành công | Lỗi thường gặp |
|---------|------------------------------|----------------|
| `ROOM_LIST` | `{ "rooms": [{ "name": "ltm", "protected": false, "memberCount": 3 }] }` | `401` |
| `ROOM_CREATE` | `{ "room": "ltm" }` | `401`, `409` phòng đã tồn tại |
| `ROOM_JOIN` | `{ "room": "ltm" }` | `401`, `403` sai mật khẩu phòng, `404` |
| `ROOM_LEAVE` | `{ "room": "ltm" }` | `401`, `404` |

### 4.3 Tin nhắn (Messaging)

| Type | Hướng | Mô tả | Body |
|------|-------|-------|------|
| `MSG_SEND` | C→S | Gửi tin nhắn vào phòng | `{ "room": "ltm", "text": "..." }` |
| `MSG_ACK` | S→C | Xác nhận cho người gửi (chỉ gửi lại cho client đã gửi) | `{ "msgId": "A-57", "ts": 1757812345200 }` |
| `MSG_DELIVER` | S→C | Phát tin đến các thành viên phòng (kể cả người gửi) | `{ "msgId": "A-57", "room": "ltm", "sender": "alice", "text": "...", "ts": 1757... }` |
| `MSG_HISTORY` | C→S | Xin lịch sử tin nhắn (phân trang) | `{ "room": "ltm", "before": "A-57", "limit": 50 }` |
| `PRESENCE` | S→C | Thông báo online/offline | `{ "username": "bob", "online": false, "room": "ltm" }` |

> **MSG_HISTORY response:** server gửi một `MSG_DELIVER` cho mỗi tin trong trang, theo thứ tự cũ → mới, rồi kết thúc bằng một `MSG_ACK` có trường `{ "historyEnd": true }`.

### 4.4 Truyền file (File)

| Type | Hướng | Mô tả | Body |
|------|-------|-------|------|
| `FILE_OFFER` | C→S | Thông báo muốn gửi file | `{ "name": "report.pdf", "size": 104857600, "sha256": "abc...", "room": "ltm" }` |
| `FILE_UPLOAD_TOKEN` | S→C | Cấp token và port cho kênh file | `{ "token": "xyz...", "filePort": 9001 }` |
| `FILE_AVAILABLE` | S→C | File đã sẵn sàng để tải | `{ "fileId": "...", "name": "report.pdf", "size": 104857600, "sha256": "abc...", "room": "ltm", "uploader": "alice" }` |

### 4.5 Đồng bộ hai server (Sync — kênh 9100)

| Type | Hướng | Mô tả | Body |
|------|-------|-------|------|
| `SYNC_HELLO` | S→S | Bắt tay nhận diện | `{ "id": "A", "version": 1 }` |
| `SYNC_MSG` | S→S | Nhân bản tin nhắn sang server kia | `{ "msgId": "A-57", "room": "ltm", "sender": "alice", "text": "...", "ts": 1757... }` |
| `SYNC_PRESENCE` | S→S | Nhân bản sự kiện online/offline | `{ "username": "alice", "online": true }` |
| `SYNC_PING` | S→S | Keep-alive trên kênh sync | `{}` |

### 4.6 Hệ thống (System)

| Type | Hướng | Mô tả | Body |
|------|-------|-------|------|
| `PING` | C→S | Heartbeat từ client | `{}` |
| `PONG` | S→C | Phản hồi heartbeat | `{}` |
| `ERROR` | S→C | Lỗi chung | `{ "code": 401, "message": "Unauthorized" }` |

---

## 5. Mã lỗi

| Code | Tên | Tình huống |
|------|-----|-----------|
| `400` | Bad Request | Thiếu trường bắt buộc, sai kiểu dữ liệu |
| `401` | Unauthorized | Chưa xác thực hoặc token hết hạn |
| `403` | Forbidden | Không có quyền thực hiện thao tác |
| `404` | Not Found | Phòng, người dùng, file, tin nhắn không tồn tại |
| `409` | Conflict | Username đã tồn tại, phòng đã tồn tại |
| `413` | Payload Too Large | Payload vượt 1 MiB (tin nhắn) hoặc 100 MB (file) |
| `429` | Too Many Requests | Vượt giới hạn 20 tin/giây mỗi phiên |
| `500` | Internal Server Error | Lỗi không mong muốn phía server |

---

## 6. Luồng đăng ký & đăng nhập

### 6.1 Đăng ký

```
Client                          Server
  │                               │
  │── REGISTER {username,password}──▶│
  │                               │  (kiểm username chưa tồn tại)
  │                               │  (hash password với BCrypt)
  │                               │  (lưu vào SQLite)
  │◀── AUTH_OK {sessionToken, username} ─│
  │       hoặc AUTH_FAIL {code: 409}     │  (username đã tồn tại)
  │       hoặc AUTH_FAIL {code: 400}     │  (username/password sai ràng buộc)
```

**Ràng buộc:**
- `username`: 3–30 ký tự, chỉ gồm `[a-zA-Z0-9_-]`
- `password`: tối thiểu 6 ký tự
- Mật khẩu **không bao giờ** gửi lại hoặc lưu dạng rõ; chỉ lưu BCrypt hash

### 6.2 Đăng nhập

```
Client                          Server
  │                               │
  │── LOGIN {username, password} ──▶│
  │                               │  (tìm user trong DB)
  │                               │  (BCrypt.checkpw)
  │                               │  (tạo sessionToken = 32 byte SecureRandom)
  │◀── AUTH_OK {sessionToken, username} ─│
  │       hoặc AUTH_FAIL {code: 401}     │  (sai username hoặc mật khẩu)
```

### 6.3 Khôi phục phiên (Reconnect / Failover)

```
Client                          Server B (sau khi mất kết nối tới A)
  │                               │
  │── RESUME {sessionToken, lastMsgId: "A-57"} ──▶│
  │                               │  (xác minh token còn hạn)
  │                               │  (lấy các tin từ A-57 trở đi)
  │◀── AUTH_OK ──────────────────│
  │◀── MSG_DELIVER (tin bị lỡ) ──│  (gửi tuần tự, lọc trùng theo msgId)
  │       hoặc AUTH_FAIL {code: 401} │  (token hết hạn → client đăng nhập lại)
```

---

## 7. Luồng quản lý phòng

### 7.1 Vào phòng

```
Client                          Server
  │── ROOM_JOIN {room:"ltm"} ──▶ │
  │◀── {type:"ROOM_JOIN", body:{room:"ltm"}} ─│  (thành công)
  │◀── ROOM_MEMBERS {room:"ltm", members:[...]} ─│
  │◀── MSG_DELIVER (50 tin gần nhất) ──│  (lịch sử tự động)
  │◀── PRESENCE (danh sách online) ────│
```

---

## 8. Luồng gửi tin nhắn

```
Client A (Alice)         Server              Client B (Bob) — cùng phòng
  │                        │                        │
  │── MSG_SEND ────────────▶│                        │
  │   {room:"ltm",          │  (gán msgId: "A-57")   │
  │    text:"hello"}        │  (lưu DB)               │
  │◀── MSG_ACK ────────────│  {msgId:"A-57"}         │
  │   {msgId:"A-57"}        │                        │
  │                         │── MSG_DELIVER ─────────▶│
  │◀── MSG_DELIVER ────────│   {msgId:"A-57",        │
  │   (Alice cũng nhận)     │    sender:"alice",      │
  │                         │    text:"hello", ...}   │
```

> **Ghi chú:** Server gửi `MSG_DELIVER` cho **tất cả** thành viên phòng, kể cả người gửi (để client đồng bộ trạng thái "tin đã được server xử lý").

---

## 9. Luồng gửi file

```
Client (Sender)          Server (9000)       Server (9001 – File)     Client (Receiver)
  │                          │                      │                       │
  │─ FILE_OFFER ────────────▶│                      │                       │
  │  {name, size, sha256,    │ (kiểm size ≤ 100MB)  │                       │
  │   room}                  │ (tạo upload token)   │                       │
  │◀─ FILE_UPLOAD_TOKEN ─────│                      │                       │
  │   {token, filePort:9001} │                      │                       │
  │                          │                      │                       │
  │──── TCP connect ─────────────────────────────▶  │                       │
  │──── [token(64B)][size(8B)][sha256(32B)] ──────▶ │                       │
  │──── file data (chunks 64 KB) ─────────────────▶ │                       │
  │                          │                      │ (kiểm SHA-256)        │
  │                          │◀─ upload OK ─────────│                       │
  │                          │──── FILE_AVAILABLE ──────────────────────────▶│
  │                          │    {fileId, name,    │                       │
  │                          │     size, sha256,    │                       │
  │                          │     room, uploader}  │                       │
```

**Ràng buộc:**
- File tối đa **100 MiB**
- Token chỉ dùng **một lần**, hết hạn sau **60 giây**
- SHA-256 mismatch → server huỷ file, báo `ERROR 400` trên kênh control

---

## 10. Luồng đồng bộ hai server

```
Server A                    Server B
  │                            │
  │── SYNC_HELLO {id:"A"} ────▶│
  │◀── SYNC_HELLO {id:"B"} ────│
  │                            │
  │  [Client ở A gửi tin]      │
  │── SYNC_MSG {msgId:"A-57",──▶│  (B lưu tin, phát MSG_DELIVER cho client của B)
  │    room, sender, text, ts} │
  │                            │
  │  [Mỗi 10 giây]             │
  │── SYNC_PING ───────────────▶│
  │◀── SYNC_PING ───────────────│
```

**Chống trùng lặp:** Server B kiểm `msgId` trước khi lưu — bỏ qua nếu đã có.  
**Mã tin:** Dạng `{serverId}-{seq}` (ví dụ `A-57`, `B-12`).

---

## 11. Heartbeat & timeout

| Tham số | Giá trị |
|---------|---------|
| Client gửi `PING` mỗi | 5 giây |
| Server đóng phiên nếu không nhận gì trong | 15 giây |
| Client thử kết nối lại với độ trễ | 1s → 2s → 4s (exponential backoff) |
| Client chuyển server dự phòng sau | 3 PONG bị lỡ liên tiếp |

---

## 12. Kênh file (cổng 9001)

Kênh file **không dùng** định dạng khung JSON. Header nhị phân:

```
 ┌─────────────────────────────────────────────────────┐
 │ token      │ 64 bytes  │ ASCII hex upload token      │
 │ size       │  8 bytes  │ big-endian uint64, byte count│
 │ sha256     │ 32 bytes  │ raw SHA-256 digest bytes     │
 ├────────────┼───────────┼─────────────────────────────┤
 │ data       │ N bytes   │ file content in 64 KB chunks │
 └─────────────────────────────────────────────────────┘
```

Server đọc tuần tự, kiểm SHA-256 sau khi nhận đủ `size` byte. Kết nối file channel đóng ngay sau khi upload xong (hoặc lỗi).

---

## 13. Quy ước correlation ID

| Tiền tố | Người dùng | Ví dụ |
|---------|-----------|-------|
| `c-` | Client | `c-1042` |
| `s-` | Server — thông điệp server tự phát hoặc `ERROR` không đọc được `id` yêu cầu | `s-200` |
| `A-` | Server A (message ID) | `A-57` |
| `B-` | Server B (message ID) | `B-12` |

- Server sao chép `id` của yêu cầu vào phản hồi để client ghép cặp.
- `msgId` dùng trong `MSG_ACK`, `MSG_DELIVER`, `SYNC_MSG` phải là dạng `{serverId}-{seq}`.

---

*Kết thúc PROTOCOL.md v1*
