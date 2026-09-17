# RelayChat — Git & GitHub Workflow

> Tài liệu này là quy ước bắt buộc cho cả nhóm.  
> Cả hai thành viên phải đọc và tuân thủ trước khi commit đầu tiên.

---

## 1. Cấu trúc nhánh

```
main          ← luôn build được; chỉ chứa code đã duyệt
│
├── feature/frame-codec         ← Người A
├── feature/echo-client         ← Người B
├── feature/iterative-server    ← Người A
└── ...
```

| Loại nhánh | Định dạng | Ví dụ |
|-----------|-----------|-------|
| Tính năng | `feature/<ten-viec>` | `feature/frame-codec` |
| Sửa lỗi | `fix/<mo-ta-loi>` | `fix/partial-read-crash` |
| Thí nghiệm | `exp/<ma-thi-nghiem>` | `exp/e1-benchmark` |
| Báo cáo | `docs/<noi-dung>` | `docs/protocol-v1` |

**Quy tắc:**
- **`main` không push trực tiếp** — mọi thay đổi phải qua Pull Request.
- Mỗi nhánh chỉ giải quyết **một việc duy nhất**.
- Xoá nhánh sau khi PR được gộp.

---

## 2. Quy ước commit

Dùng định dạng [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <mô tả ngắn>

[body tùy chọn — giải thích tại sao, không phải làm gì]

[footer tùy chọn — Closes #<issue>]
```

### Các `type` được dùng

| Type | Khi nào dùng |
|------|-------------|
| `feat` | Thêm tính năng mới |
| `fix` | Sửa lỗi |
| `test` | Thêm / sửa test |
| `docs` | Tài liệu, PROTOCOL.md |
| `refactor` | Tái cấu trúc code (không thêm tính năng, không sửa lỗi) |
| `chore` | Công việc khác: cấu hình Maven, .gitignore, CI |
| `perf` | Tối ưu hiệu năng |
| `exp` | Thí nghiệm (E1–E4) |

### Các `scope` (module)

`common` · `server` · `client` · `loadtest` · `protocol` · `build` · `ci`

### Ví dụ commit

```
feat(common): add FrameCodec with readFully for partial-read safety

Uses DataInputStream.readFully so OS partial reads never corrupt frames.
Rejects payloads > 1 MiB immediately after reading the 4-byte header.

Closes #3
```

```
test(common): add JUnit 5 tests for split frames and oversized frames
```

```
docs(protocol): write PROTOCOL.md v1 — wire frame, message table, error codes
```

---

## 3. Quy trình Pull Request

1. **Tạo nhánh** từ `main`:
   ```bash
   git checkout main && git pull
   git checkout -b feature/<ten-viec>
   ```

2. **Commit thường xuyên** — mỗi commit nhỏ và tập trung vào một việc.

3. **Push và mở PR**:
   ```bash
   git push -u origin feature/<ten-viec>
   ```
   Tiêu đề PR = commit message đầu tiên của nhánh.

4. **Người kia review** — bắt buộc đối với:
   - Bất kỳ thay đổi nào trong `common/`
   - Bất kỳ thay đổi nào trong `PROTOCOL.md`
   - Thay đổi `pom.xml` ở root

5. **Gộp bằng "Squash and merge"** để `main` có lịch sử sạch.

6. **Xoá nhánh** sau khi merge.

---

## 4. GitHub Projects — Bảng công việc

Cột | Ý nghĩa
----|--------
**Backlog** | Việc chưa bắt đầu (từ lộ trình)
**In Progress** | Đang làm (gán người, gán nhánh)
**Review** | PR đã mở, chờ duyệt
**Done** | Đã merge vào `main`

**Quy tắc:**
- Mỗi issue = một dòng trong checklist của lộ trình
- Khi mở PR, liên kết issue bằng `Closes #<số>`
- Chỉ tích checklist trong `Ke-hoach-RelayChat.md` khi issue chuyển sang **Done**

---

## 5. Bảo vệ nhánh main (cài trên GitHub)

Vào **Settings → Branches → Add rule** cho `main`:

- [x] Require a pull request before merging
- [x] Require approvals: **1**
- [x] Dismiss stale PR approvals when new commits are pushed
- [x] Require status checks to pass (khi có CI)

---

## 6. Nhịp họp

| Thời gian | Độ dài | Nội dung |
|-----------|--------|---------|
| Tối thứ Hai | 15 phút | Chốt việc trong tuần theo lộ trình |
| Chủ nhật | 30 phút | Chạy thử cho nhau, tích checklist |

> Bị kẹt quá **nửa ngày** → nhắn người kia ngay, không chờ đến buổi họp.
