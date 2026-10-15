# Redmi Watch 5 Companion (Google Maps Bridge & Gemini AI)

Ứng dụng Android Companion đóng vai trò cầu nối thông minh dành cho **Redmi Watch 5 Lite** (và các dòng Redmi Watch / Xiaomi Band chạy HyperOS RTOS):
1. **Google Maps Navigation Bridge:** Tự động bắt thông báo điều hướng từ Google Maps khi bạn di chuyển, chuyển đổi thành thông báo chỉ đường trực quan (kèm biểu tượng rẽ ➡️ ⬅️ ↗️ ↖️, khoảng cách, tên đường) và gửi sang đồng hồ với chế độ rung ưu tiên.
2. **Trợ lý AI Gemini:** Kết nối Google Gemini API (`gemini-2.5-flash`), nhận lệnh qua giọng nói tiếng Việt hoặc văn bản, tối ưu câu trả lời ngắn gọn (dưới 35 từ) và đẩy thẳng lên màn hình đồng hồ Redmi Watch.

---

## 🛠 Cấu trúc dự án
```
redmi-watch-companion/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/antigravity/redmicompanion/
│   │   │   ├── MainActivity.kt                      # Giao diện chính, cấp quyền, hỏi đáp giọng nói
│   │   │   ├── service/
│   │   │   │   ├── MapsNotificationListenerService.kt # Lắng nghe Google Maps & trích xuất chỉ đường
│   │   │   │   ├── GeminiService.kt                 # Gọi Gemini API với System Instructions cho đồng hồ
│   │   │   │   └── GeminiTileService.kt             # Phím tắt Quick Settings trên Android
│   │   │   └── util/
│   │   │       └── NotificationHelper.kt            # Kênh thông báo độ ưu tiên cao & nhịp rung cho đồng hồ
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       └── values/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Hướng dẫn cài đặt & sử dụng

### Bước 1: Mở dự án trong Android Studio
1. Mở **Android Studio** $\rightarrow$ **Open** $\rightarrow$ Chọn thư mục `redmi-watch-companion`.
2. Chờ Gradle sync xong $\rightarrow$ Kết nối điện thoại Android của bạn qua cáp USB (bật USB Debugging) $\rightarrow$ Nhấn **Run (Shift + F10)** để cài lên điện thoại.

### Bước 2: Cấp quyền trên điện thoại
1. Mở ứng dụng **Redmi Watch Companion**.
2. Nhấn nút **"Mở cài đặt cấp quyền thông báo"** $\rightarrow$ Tìm ứng dụng **Redmi Watch Companion** và gạt bật cho phép truy cập thông báo.
3. Cấp quyền **Microphone** (để nói lệnh) và quyền **Thông báo**.

### Bước 3: Cấu hình Mi Fitness (BƯỚC QUAN TRỌNG NHẤT)
Để các thông báo chỉ đường và Gemini xuất hiện trên Redmi Watch 5 Lite:
1. Mở ứng dụng **Mi Fitness** trên điện thoại.
2. Chọn tab **Thiết bị (Device)** $\rightarrow$ **Thông báo ứng dụng (App Notifications)**.
3. Đảm bảo công tắc thông báo đang **BẬT**.
4. Trong danh sách ứng dụng, tìm và **BẬT** ứng dụng **Redmi Watch Companion**.

---

## 💡 Hướng dẫn sử dụng thực tế

### 1. Chỉ đường Google Maps khi đi xe máy / ô tô
* Mở **Google Maps** trên điện thoại và chọn điểm đến $\rightarrow$ Bấm **Bắt đầu (Start)**.
* Khi bạn di chuyển, mỗi khi sắp đến chỗ rẽ:
  * Ứng dụng Companion sẽ trích xuất mũi tên (➡️, ⬅️, ↗️,...), khoảng cách và tên đường.
  * Đồng hồ Redmi Watch 5 Lite sẽ rung 2 nhịp dứt khoát và hiển thị ngay trên mặt đồng hồ mà bạn không cần rút điện thoại ra xem.

### 2. Sử dụng Trợ lý AI Gemini
* Mở app Companion, nhập **Gemini API Key** (lấy miễn phí tại [Google AI Studio](https://aistudio.google.com/)).
* Bấm **"🎤 Nói lệnh"** và nói câu hỏi tiếng Việt (ví dụ: *"Thời tiết hôm nay thế nào?"*, *"Cách xử lý khi xe thủng lốp"*).
* Gemini AI sẽ tóm tắt phản hồi cực ngắn gọn (1-2 câu) và rung báo câu trả lời ngay lập tức trên cổ tay của bạn.
* Bạn cũng có thể kéo thanh thông báo trạng thái của Android xuống, thêm ô phím tắt **"Hỏi Gemini"** để kích hoạt nhanh bất cứ lúc nào!
