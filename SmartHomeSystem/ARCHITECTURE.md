# Kiến trúc hệ thống Smart Home IoT

## 1. Tổng quan

Hệ thống 3 tầng: **Node cảm biến/thiết bị (ESP32)** → **Gateway (Raspberry Pi 4, AOSP Android 15)** → **Cloud (Firebase) + Mobile App**.

Yêu cầu mở rộng quan trọng: hệ thống phải hỗ trợ thêm nhiều node cùng loại (nhiều phòng, mỗi phòng vài node) và thêm loại node mới trong tương lai. Mỗi node có **ID riêng** để xác định vị trí trong nhà.

---

## 2. Các Node (ESP32)

### Node 1 — Môi trường (`node_sensor_enviroment`)
- MCU: ESP32-S3
- Cảm biến: DHT22 (nhiệt độ, độ ẩm), MQ7 (khí CO), GP2Y1014 (bụi mịn PM2.5), CJMCU-680 (áp suất khí quyển, chất lượng không khí, độ ẩm, nhiệt độ)
- Giao tiếp với gateway: BLE Mesh (control-plane) + Wi-Fi (data-plane)

### Node 2 — Nhận diện & khóa cửa (`node_rfid_finger_print`)
- MCU: ESP32-S3
- Cảm biến: TZM1026 V1.0 (vân tay, UART), RFID-RC522
- Điều khiển: module relay 3 chân (Vcc, GND, SIG) để mở/đóng khóa cửa
- Dữ liệu sinh trắc học (vân tay) **giữ tại node, không gửi lên cloud**

### Node 3 — Camera (`node_camera`)
- Module: ESP32-CAM
- Chức năng: quay video, gửi từng frame ảnh tới gateway qua **RTSP**
- Gateway nhận frame, chạy model AI (YOLOv12n) để xử lý

---

## 3. Gateway (Raspberry Pi 4 Model B, 4GB RAM, AOSP Android 15)

### Vai trò
- Giao tiếp với tất cả các node qua BLE Mesh + Wi-Fi
- System App (Java + Kotlin) hiển thị/visualize dữ liệu từ 3 node
- Gom dữ liệu từ các node, đẩy lên Firebase Realtime Database (RTDB); bản ghi dùng khóa
  idempotency ổn định và hàng đợi cục bộ để replay sau khi mất mạng
- Chạy inference YOLOv12n cho luồng camera (qua ONNX Runtime)

### Build system
- **Soong only** — không dùng Gradle, không có `build.gradle`
- Build bằng `mm` / `mma` / `m` trong cây nguồn AOSP, cấu hình qua `Android.bp`

### Tích hợp ONNX Runtime trên AOSP
- Dùng `libs/onnxruntime-android.aar` làm nguồn duy nhất cho Java API và JNI libraries.
- Khai báo AAR bằng `android_library_import` với `extract_jni: true`. AAR hiện có cả
  `libonnxruntime.so` và `libonnxruntime4j_jni.so`; chỉ đóng gói một file `.so` sẽ gây lỗi tải JNI
  lúc runtime.
- Gateway được giới hạn `compile_multilib: "64"` vì artifact hiện tại chỉ có ABI `arm64-v8a`.

### Tiền xử lý ảnh cho YOLOv12n
- Convert Bitmap (ARGB) → FloatArray NCHW theo đúng thứ tự kênh **R-G-B** (loại bỏ kênh Alpha)
- Lưu lại `scale`, `padLeft`, `padTop` từ bước letterbox resize (640×640) để dùng ở bước post-processing — cần thiết để map bounding box về đúng tọa độ ảnh gốc (unletterbox)

---

## 4. Mobile App (`smart_home_mobile_app`)

- App riêng trên điện thoại, dùng để tracking và quan sát chỉ số/biểu đồ cảm biến trong nhà
- Nhận dữ liệu từ Firebase (đã được Gateway gom và đẩy lên)

---

## 5. Authentication (dùng chung cho cả System App và Mobile App)

- **OAuth2 Authorization Code + PKCE qua system browser** cho Google / Facebook / Apple —
  không dùng WebView nhúng. Google cấm app-controlled embedded user-agent và trả
  `disallowed_useragent`; system browser cũng tách cookie/credential của provider khỏi app.
- Flow: mở system browser → authorization URL của provider → callback bằng custom scheme
  (ví dụ `smarthome://oauth-callback`) → kiểm tra redirect/state/TTL → lấy authorization code →
  đổi lấy token.
- **Firebase Auth làm lớp trung gian thống nhất**: sau khi có OAuth token, gọi Firebase Auth REST API (`signInWithCredential`, qua HTTPS — không cần Google Play Services SDK) để tạo session Firebase chung giữa Gateway và Mobile App

### Quyết định đã chốt: Redirect URI & Client ID/Secret

**Redirect URI scheme**
- Đăng ký custom scheme (`smarthome://oauth-callback`) tại Firebase Console → Authentication → Sign-in method → từng provider (Google/Facebook/Apple) → Authorized redirect URIs
- Khai báo cùng scheme trong `AndroidManifest.xml` để hệ thống điều hướng callback đúng Activity
- Không hardcode scheme trong code: định nghĩa hằng số `OAUTH_REDIRECT_URI` trong file config riêng (ví dụ `OAuthConfig.kt`); giá trị scheme có thể khác nhau theo môi trường (dev/staging/prod), truyền qua file cấu hình không commit git

**Client ID / Client Secret**
- **Client ID**: public, có thể đặt trong file cấu hình thường (không nhạy cảm), tách riêng để gọn
- **Client Secret**: chỉ lưu trên **Gateway (Raspberry Pi 4)**, không bao giờ đưa vào Mobile App
  - Token exchange (đổi authorization code lấy token) do **Gateway thực hiện**, đóng vai trò backend
  - Mobile App chỉ: mở system browser → lấy authorization code → gửi code cho Gateway qua kênh bảo mật (HTTPS hoặc local IP đã xác thực)
  - Gateway trả về Firebase custom token/session để Mobile App đăng nhập Firebase
  - Lưu trữ: file JSON/XML nhỏ tại `/data/secure/smarthome_oauth_secrets.json`, quyền đọc giới hạn chỉ root/ứng dụng, không commit git (đã thêm `.gitignore`), copy thủ công vào thiết bị khi triển khai
  - Không dùng Firebase Remote Config cho secret (không đủ an toàn); nếu cần linh hoạt hơn về sau có thể cân nhắc Cloud Secret Manager + API nội bộ, nhưng ở giai đoạn này dùng file cục bộ trên Gateway là đủ và đơn giản nhất

---

## 6. Bảo mật & dữ liệu

- HMAC-SHA256 hardware fingerprinting cho việc xác thực node
- Dữ liệu sinh trắc học (vân tay) giữ tại node, không đưa lên cloud
- Descriptor schema có versioning để hỗ trợ thêm node/loại node mới sau này

---

## 7. Khả năng mở rộng (Scalability)

- Mỗi node có ID riêng, gắn với vị trí cụ thể trong nhà (phòng nào)
- Hệ thống phải cho phép: thêm nhiều node cùng loại (nhiều phòng), thêm loại node mới mà không phải sửa kiến trúc lõi

---

## 8. Đường dẫn thư mục dự án

| Thành phần | Đường dẫn |
|---|---|
| Node 1 — Môi trường | `/home/huynn/smart_home/node_sensor_enviroment` |
| Node 2 — RFID/Vân tay | `/home/huynn/smart_home/node_rfid_finger_print` |
| Node 3 — Camera | `/home/huynn/smart_home/node_camera` |
| System App (Gateway, AOSP) | `/home/huynn/aosp/source/packages/apps/SmartHomeSystem` |
| Mobile App | `/home/huynn/smart_home/smart_home_mobile_app` |

---

## 9. Lộ trình công việc đã chốt

### Giai đoạn A — Đánh giá hiện trạng (làm trước khi code tiếp)
1. Review **System App** (Gateway): kiểm tra backend/frontend hiện có đã ổn định chưa, có phần nào lỗi thời/sai so với kiến trúc đã chốt ở trên không
2. Review giao thức đang dùng ở **System App** và **Mobile App** (BLE Mesh, Wi-Fi, RTSP, Firebase, OAuth2) — đối chiếu với data pipeline/kiến trúc đã chốt, liệt kê chỗ lệch
3. Nếu UI System App/Mobile App chưa đạt: thiết kế lại theo phong cách **liquid glass**, tông màu sang trọng kiểu Apple

### Giai đoạn B — Chuẩn bị kỹ thuật trước khi code
4. Chuẩn bị sẵn file `.aar`/`.so` của `onnxruntime-android` (tải từ Maven Central, giải nén `.aar` lấy `.so` theo ABI `arm64-v8a`) trước khi viết `Android.bp`
5. Setup OAuth2: đăng ký redirect URI scheme trên Firebase Console, tạo file config Client ID/Secret theo quyết định ở mục 5

### Giai đoạn C — Triển khai
6. Code/hoàn thiện 5 source: `node_sensor_enviroment`, `node_rfid_finger_print`, `node_camera`, System App (Gateway), Mobile App

### Giai đoạn D — Sau khi code xong (bắt buộc trước khi coi là hoàn thành)
7. **Refactor & clean code** cho cả 5 source:
   - Xóa file thừa, file không còn liên quan đến hệ thống (code thử nghiệm, file mẫu mặc định từ template/AOSP/IDE, asset không dùng)
   - Đồng bộ coding convention/structure giữa các module trong cùng 1 source
   - Loại bỏ code chết (dead code), hàm/class không còn được gọi
8. **Viết test case** cho từng source để rà soát lỗi logic và lỗi syntax:
   - Node firmware (ESP32): unit test cho phần xử lý dữ liệu cảm biến, parser giao tiếp (UART/BLE), không cần test phần phụ thuộc hardware trực tiếp — có thể mock driver
   - System App (Gateway, Java/Kotlin trên AOSP): test cho luồng nhận dữ liệu từ node, luồng đẩy Firebase, luồng inference YOLOv12n (tiền xử lý ảnh, post-processing tọa độ box), luồng OAuth2 token exchange
   - Mobile App: test cho luồng hiển thị dữ liệu, luồng login/logout/register
   - Mục tiêu: phát hiện lỗi logic (sai luồng dữ liệu, sai tính toán tọa độ/scale, race condition khi nhiều node gửi data) và lỗi syntax/runtime (crash, null pointer, kiểu dữ liệu sai) trước khi coi hệ thống ổn định
