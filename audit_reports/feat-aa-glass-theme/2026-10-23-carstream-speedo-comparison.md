# Báo cáo phân tích thẻ Speedo của CarStream và so sánh với FermataX

Báo cáo này đối chiếu chi tiết giải pháp đo, lọc, hiển thị tốc độ (speedo) và đo hành trình (trip computer) giữa ứng dụng CarStream (phiên bản 1.24.93 / 733) và FermataX (nhánh `feat-aa-glass-theme`), nhằm đánh giá ưu khuyết điểm thuật toán và rút ra khuyến nghị tối ưu hóa cho FermataX.


> Ghi chú: mục 3–5 mô tả FermataX tại commit `f97de985`, trước các cải tiến S1, S2, S4, S5 (LocationHub HIGH_ACCURACY + watchdog đăng ký lại, con số luôn hiển thị, ngưỡng 0 có độ trễ, IMU ở tốc độ thấp) được làm ngay sau báo cáo này.

---

## 1. Nguồn và cách xuất

### Thông tin phiên bản gói ứng dụng
- Tên gói (package): `com.carstream.tech`
- Phiên bản hiển thị (versionName): `1.24.93`
- Mã phiên bản nội bộ (versionCode): `733`
- Nguồn trích xuất: bản sao lưu kéo trước đó tại `E:\Chatgpt\carview\device-pulled-apk\`. Sau khi nghiệm thu, Claude đã đối chiếu trực tiếp với máy `15c36230` (`adb shell sha256sum` trên `base.apk` đang cài): SHA-256 `cbd7b549d5236eedfc7f74560f07054fbd471eb0bf7b6aabedd9f4fa539622a2`, versionCode 733, **trùng khớp** với bản đã phân tích.

### Danh sách tập tin APK và mã băm SHA-256
Các tập tin split APK của CarStream từ đợt kéo trước được sao chép và tổ chức tại `E:\Chatgpt\carview\device-pulled-apk\v733-2026-10-23\`:
1. `base.apk`:
   - SHA-256: `CBD7B549D5236EEDFC7F74560F07054FBD471EB0BF7B6AABEDD9F4FA539622A2`
   - Kích thước: 7,780,667 bytes
2. `split_config.arm64_v8a.apk`:
   - SHA-256: `E8E3D5F9919DC4E62E73184B4EBE98ACFAA2865DD9E9236B3F198A925239B66E`
   - Kích thước: 131,711 bytes
3. `split_config.xxhdpi.apk`:
   - SHA-256: `365EFE7D9DBC9F6121878E93FA9A0D6CF75049D1CCF095268072F4ADBC7EAD2F`
   - Kích thước: 45,719 bytes

### Lệnh đã thực hiện
```powershell
# 1. Kiểm tra thiết bị mục tiêu (không kết nối được trong phiên này)
adb devices -l
adb -s 15c36230 shell pm path com.carstream.tech

# 2. Tính mã băm SHA-256 của các file APK đã kéo từ trước
Get-FileHash -Algorithm SHA256 "E:\Chatgpt\carview\device-pulled-apk\*.apk"

# 3. Sao chép và tổ chức lại các tập tin APK
New-Item -ItemType Directory -Force -Path "E:\Chatgpt\carview\device-pulled-apk\v733-2026-10-23"
Copy-Item "E:\Chatgpt\carview\device-pulled-apk\base.apk" "E:\Chatgpt\carview\device-pulled-apk\v733-2026-10-23\"

# 4. Dịch ngược base.apk bằng jadx 1.5.6 (ngoài repo)
E:\Chatgpt\carview\.tooling\jadx\bin\jadx.bat -d E:\Chatgpt\carview\carstream-v733-speedo E:\Chatgpt\carview\device-pulled-apk\v733-2026-10-23\base.apk
```

### Nơi lưu trữ mã nguồn dịch ngược
Mã nguồn dịch ngược được lưu trữ hoàn toàn bên ngoài repository FermataX để đảm bảo không nhiễm mã bên thứ ba vào cây git:
- Thư mục dịch ngược độc lập: `E:\Chatgpt\carview\carstream-v733-speedo\`
- Thư mục đối chiếu tham chiếu trước đó: `E:\Chatgpt\carview\carstream-v733-decompiled\`

---

## 2. Logic speed của CarStream

CarStream tổ chức luồng xử lý tốc độ theo mô hình phân tầng: tầng dịch vụ định vị nền (`qq`), tầng chuyển tiếp sự kiện (`oq`), tầng tính toán hành trình cho launcher (`Speedo`) và tầng giao diện đồ họa (`SpeedBubble` trên Android Auto, `CoDienNha` trên điện thoại).

### 2.1. Đăng ký và quản lý dịch vụ định vị (Location Provider)
- Việc đăng ký định vị tập trung trong hàm `d(Context)` tại `defpackage/qq.java:64` (được gọi từ `qq.a` tại `defpackage/qq.java:38` và `qq.b` tại `defpackage/qq.java:56`).
- Lớp `qq` khởi tạo một luồng riêng mang tên `"cnt-gps"` (`HandlerThread("cnt-gps")`) tại `defpackage/qq.java:73` để nhận dữ liệu định vị nền, tránh gây nghẽn main UI thread.
- Lệnh đăng ký tại `defpackage/qq.java:78`:
  ```java
  h.requestLocationUpdates("gps", 1000L, 0.0f, i, a.getLooper());
  ```
  CarStream chỉ yêu cầu duy nhất nhà cung cấp phần cứng `"gps"` (GPS_PROVIDER), không sử dụng `fused` hay `network` cho việc đo tốc độ chính. Khoảng thời gian yêu cầu tối thiểu là `1000L` ms (tần suất ~1 Hz) và khoảng cách tối thiểu là `0.0f` mét.
- Cơ chế giám sát Watchdog tại `defpackage/qq.java:86` và `defpackage/x.java:119`: CarStream đặt một vòng lặp kiểm tra định kỳ 10 giây một lần thông qua `c.postDelayed(new x(applicationContext, 4), 10000L)` (được lên lịch tại `defpackage/qq.java:86`). Nếu thời gian trôi qua kể từ lần cuối nhận điểm GPS vượt quá 20 giây (`jCurrentTimeMillis > 20000` tại `defpackage/x.java:124`), CarStream thực hiện gỡ bỏ listener hiện tại và gọi lại hàm khởi tạo `qq.d(this.b)` để tái đăng ký dịch vụ định vị phần cứng (`defpackage/x.java:134`).

### 2.2. Tiếp nhận điểm đo, trích xuất tốc độ và bù trừ (Offset)
- Điểm đo từ Android OS được nhận tại listener callback `onLocationChanged` ở `defpackage/oq.java:12`.
- Cập nhật thời gian nhận điểm và trích xuất tốc độ thô tại `defpackage/oq.java:13` - `defpackage/oq.java:14`:
  ```java
  qq.b = System.currentTimeMillis();
  qq.d = location.hasSpeed() ? Math.round(location.getSpeed() * 3.6f) : -1;
  qq.g = location;
  ```
  Nếu vị trí không có trường vận tốc (`!location.hasSpeed()`), biến tốc độ tĩnh toàn cục `qq.d` được gán ngay thành `-1`. Nếu có, vận tốc Doppler $m/s$ được nhân với $3.6$ để đổi sang $km/h$ và làm tròn số nguyên gần nhất (`Math.round`).
- Cơ chế bù trừ tốc độ theo đồng hồ xe (`tocDoBu`): CarStream đọc giá trị tùy biến của người dùng từ SharedPreferences `"CanhNhatTube"` trong hàm `g(Context)` tại `defpackage/yv.java:112` (trực tiếp tại dòng `defpackage/yv.java:116`). Giá trị này cho phép tài xế bù sai số giữa GPS và đồng hồ táp-lô ô tô trong khoảng từ `-10%` đến `+10%`.
- Khi phân phối vận tốc cho các listener tại `defpackage/oq.java:23`:
  ```java
  int iMax = qq.d;
  int i = yv.b;
  if (iMax > 0 && i != 0) {
      iMax = Math.max(0, Math.round(((i + 100) * iMax) / 100.0f));
  }
  n4Var.a(iMax);
  ```

### 2.3. Hiển thị trên Android Auto (CarScreen & SpeedBubble)
- Màn hình xe Android Auto (`CarScreen`) khởi tạo và quản lý overlay tốc độ qua lớp `defpackage/nq.java:50` (phương thức `c()`) và `defpackage/nq.java:108` (phương thức `d(String)`). Khi tính năng hiển thị tốc độ bật (`showSpeed = true`), `nq` đăng ký lắng nghe với `qq` tại `defpackage/nq.java:103` - `defpackage/nq.java:104`.
- Khi có giá trị tốc độ mới, `n4` bắn runnable sang Handler main thread và gọi vào phương thức cập nhật `SpeedBubble.D(int i)` tại `com/carstream/tech/core/speed/SpeedBubble.java:217`.
- `SpeedBubble` lưu giá trị vào thuộc tính `this.a = i`, sau đó yêu cầu vẽ lại (`requestLayout()`, `invalidate()`, `v()`).
- Khi vẽ số hiển thị, phương thức `J()` tại `com/carstream/tech/core/speed/SpeedBubble.java:310` trả về con số hiển thị:
  ```java
  public final String J() {
      int i = this.a;
      return i < 0 ? "--" : String.valueOf(i);
  }
  ```
  Nếu `this.a < 0`, giá trị hiển thị là `"--"`. Nếu $\ge 0$, nó hiển thị trực tiếp con số nguyên km/h.
- Tần suất vẽ giao diện (UI update rate): `SpeedBubble` điều khiển vòng lặp vẽ thông qua `Choreographer.FrameCallback` (`defpackage/jq.java:16` và `com/carstream/tech/core/speed/SpeedBubble.java:713`). Khi xe vượt quá tốc độ giới hạn (`p()`) hoặc gần chạm ngưỡng (`o()`), hệ thống kích hoạt animation nhấp nháy ánh sáng neon / viền cảnh báo chạy đồng bộ ở tần suất quét màn hình 60 Hz (VSYNC). Khi ở trạng thái nghỉ nhưng có biển báo hiển thị, `Choreographer` được gọi trễ 200 ms (tần suất 5 Hz).

### 2.4. Tính toán quãng đường và tốc độ trên Launcher điện thoại (Speedo & CoDienNha)
- Trên giao diện launcher điện thoại và kính HUD (`KinhActivity`), CarStream sử dụng lớp `com/carstream/tech/launcher/Speedo.java:22`.
- `Speedo` đăng ký nhận `Location` từ `qq` qua callback `pq`.
- Tại `com/carstream/tech/launcher/Speedo.java:126`, `Speedo` kiểm tra:
  ```java
  float speed = location.hasSpeed() ? location.getSpeed() * 3.6f : 0.0f;
  if (speed < 3.0f) {
      speed = 0.0f;
  }
  boolean z = !location.hasAccuracy() || location.getAccuracy() < 25.0f;
  ```
  CarStream áp dụng ngưỡng lọc cận dưới (deadband): nếu tốc độ nhỏ hơn 3.0 km/h, vận tốc bị gán ép về 0.0 km/h nhằm chống hiện tượng trôi dạt khi xe dừng.
- Độ chính xác vị trí (`accuracy`): `Speedo` chỉ tính cộng dồn khoảng cách di chuyển (`tripM += distanceTo`) khi thỏa mãn điều kiện `z`: tức là vị trí không có sai số hoặc độ sai lệch bán kính phải nhỏ hơn 25 mét (`location.getAccuracy() < 25.0f`) và vận tốc lớn hơn 0 (`com/carstream/tech/launcher/Speedo.java:132`).
- Giá trị này được chuyển đến `com/carstream/tech/launcher/CoDienNha.java:772` để hiển thị trên `tvToc`. View này được khởi tạo mặc định bằng ký tự `"--"` tại `com/carstream/tech/launcher/CoDienNha.java:600`.

---

## 3. Logic speed của FermataX

FermataX sở hữu kiến trúc trip computer phân tách đa tầng rất nghiêm ngặt, kết hợp giữa định vị vệ tinh, giải thuật lọc toán học, cơ chế dead reckoning khi mất tín hiệu và cảm biến chuyển động phần cứng IMU.

### 3.1. Đa hợp dịch vụ định vị (LocationHub)
- Thay vì mỗi module tự đăng ký định vị với hệ điều hành, FermataX sử dụng bộ tập trung `fermata/src/main/java/me/aap/fermata/location/LocationHub.java:31` để ghép kênh (multiplex) một kênh nhận định vị duy nhất trong tiến trình.
- Kênh lắng nghe chạy trên luồng nền `"LocationHub"` (`fermata/src/main/java/me/aap/fermata/location/LocationHub.java:63`). Khi có vị trí mới, `onLocationChanged` phân phối an toàn tới các consumer tại `fermata/src/main/java/me/aap/fermata/location/LocationHub.java:68`.
- Lựa chọn nguồn phát định vị thông minh: Trên Android 12+ (API 31+), `LocationHub` ưu tiên đăng ký `LocationManager.FUSED_PROVIDER` với chu kỳ 1000L ms (`fermata/src/main/java/me/aap/fermata/location/LocationHub.java:193`). Nếu đăng ký fused thất bại hoặc thiết bị chạy Android đời cũ hơn, hệ thống tự động dự phòng (fallback) về `LocationManager.GPS_PROVIDER` (`fermata/src/main/java/me/aap/fermata/location/LocationHub.java:204`).

### 3.2. Đánh giá tính tin cậy của Fix (FixNormalizer)
- Trước khi dữ liệu GPS được nạp vào bộ tính toán, `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/FixNormalizer.java:4` kiểm tra tính hợp lệ bằng các quy tắc vật lý chặt chẽ:
  + Vị trí phải có độ chính xác bán kính $\le 300$ mét (`MAX_ACCURACY = 300`). Vị trí được coi là đáng tin cậy cao (`positionTrusted`) khi có sai số $\le 20$ mét (`TRUSTED_ACCURACY = 20`).
  + Vận tốc Doppler được chuẩn hóa qua hàm `trustedSpeed` tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/FixNormalizer.java:31`:
    * Vận tốc tối đa cho phép là 70 m/s (~252 km/h, `MAX_MPS = 70`).
    * Nếu vận tốc $< 0.15$ m/s (~0.54 km/h), hệ thống tự động đưa về `0` m/s (`fermata/src/main/java/me/aap/fermata/ui/glass/speedo/FixNormalizer.java:34`).
    * Khai thác độ lệch chuẩn vận tốc Doppler (`speedAccuracy` - sigma): Nếu vận tốc nhỏ hơn độ bất định ($v \le \sigma$) và vận tốc $\le 1.5$ m/s, trả về 0. Nếu $\sigma$ vượt quá ngưỡng sai số cho phép ($\sigma > \max(3.0, 0.5 \times v)$), Doppler bị bác bỏ hoàn toàn và trả về `Double.NaN`.

### 3.3. Làm mượt và bảo vệ tốc độ hiển thị (SpeedPresenter)
- Tốc độ hiển thị được quản lý độc lập với công tơ mét quãng đường bởi `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:7`.
- Bộ lọc làm mượt Exponential Moving Average (EMA) với hệ số $\alpha = 0.5$ tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:39`:
  ```java
  value = (doppler && fresh && mps > 0) ? ALPHA * mps + (1 - ALPHA) * value : mps;
  ```
  Nhờ bộ lọc EMA, con số tốc độ hiển thị trên màn hình không bị nhảy giật cục giữa các giây khi Doppler GPS dao động nhẹ.
- Chống xung tốc độ cực đại giả (Spike Outlier Filter): Tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:45`, tốc độ cực đại `maxKmh` chỉ chấp nhận cập nhật khi có tối thiểu hai lần đo Doppler liên tiếp trong vòng 2 giây (`MAX_PAIR_MS = 2000`) xác nhận cùng vượt ngưỡng:
  ```java
  maxKmh = (float) Math.max(maxKmh, Math.min(mps, prevDoppler) * 3.6);
  ```
  Điều này triệt tiêu hoàn toàn lỗi hiển thị tốc độ tối đa phi thực tế (ví dụ 180 km/h) do lỗi nhảy tọa độ tức thời khi xe vừa ra khỏi hầm.
- Quản lý hết hạn dữ liệu (Stale/Timeout):
  + Tốc độ được giữ tươi (`HOLD_MS`) trong vòng 3,000 ms.
  + Nếu quá 3 giây nhưng chưa vượt quá 8,000 ms (`ESTIMATE_MS = 8000`), và không có fix mới nào bị mất trường speed, hệ thống đánh dấu trạng thái tốc độ ước tính (`speedEstimated`).
  + Nếu sau 3 giây mà liên tục có fix gửi về nhưng fix đó lại không có speed (`lastFixAt > observedAt`), hoặc nếu thời gian trôi qua quá 8 giây mà không có fix nào, hàm `speedKmh` tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:54` trả về `-1`.
- Cận dưới vận tốc: Tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:70`, mọi vận tốc nhỏ hơn 1.5 km/h (`ZERO_KMH = 1.5`) được hiển thị bằng `0` km/h.

### 3.4. Quản lý khoảng trống tín hiệu, hầm và Odometer (Odometer & TripEngine)
- Tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/Odometer.java:85`, khi xe đi vào khu vực mất tín hiệu GPS (như hầm đường bộ, nhà để xe), hàm `motion()` thực hiện dead reckoning nội suy vận tốc và quãng đường ước tính dựa trên vận tốc cuối cùng hoặc nguồn tốc độ xe (OBD).
- Cầu nối hầm (`bridge`): Khi xe ra khỏi hầm và bắt lại sóng GPS thành công với 3 fix liên tiếp (`Result.RECOVERING`), Odometer sẽ nối khoảng trống quãng đường một cách toán học mà không làm sai lệch đồng hồ.
- Quản lý trạng thái tín hiệu tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/TripEngine.java:164`: Nếu độ tuổi dữ liệu vượt quá 8,000 ms (`FRESH_MS`), tín hiệu tự động chuyển sang `Signal.STALE`.

### 3.5. Cảm biến gia tốc IMU và Vòng lặp giám sát (GlassSpeedoTripController)
- Lớp điều phối `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java:34` quản lý việc đăng ký cảm biến gia tốc tuyến tính (`TYPE_LINEAR_ACCELERATION`) và con quay hồi chuyển (`TYPE_GYROSCOPE`).
- Cơ chế kiểm tra IMU chỉ kích hoạt trong khoảng trống mất tín hiệu GPS (GPS Gap > 2s):
  + Tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java:271`, phương thức `updateImuRegistration` chỉ đăng ký lắng nghe IMU khi thời gian kể từ callback định vị cuối cùng vượt quá 2 giây (`now - lastCallback > 2000` ms). Ngược lại, khi các fix GPS vẫn tiếp tục gửi về (`now - lastCallback <= 2000`), hệ thống gọi `unregisterImu()` tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java:281` để hủy đăng ký hoàn toàn nhằm tiết kiệm năng lượng.
  + Nhịp đập hệ thống (heartbeat 1 Hz) tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java:230` lấy trạng thái đứng yên từ bộ phát hiện `stationaryDetector.update()` khi IMU đang đăng ký, rồi chuyển vào `engine.motion(time, stationary, Double.NaN)` tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java:239`.
  + Trạng thái `stationary` chỉ được tiêu thụ bởi `Odometer.motion()` (`fermata/src/main/java/me/aap/fermata/ui/glass/speedo/Odometer.java:85`), và phương thức này trả về `Double.NaN` trừ khi điểm fix cuối cùng đã trôi qua hơn 2 giây (`now - last.timeMs() > 2000` tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/Odometer.java:86`). `TripEngine.motion()` chỉ chuyển tiếp vận tốc sang bộ hiển thị (`speed.observe(v, now, false)`) trong trường hợp gap này (`fermata/src/main/java/me/aap/fermata/ui/glass/speedo/TripEngine.java:172`).
  + Do đó, IMU và `StationaryDetector` chỉ tham gia dẫn đường quán tính (gap dead reckoning) và ép vận tốc ước tính về 0 để hiển thị trong khoảng trống mất tín hiệu GPS.
- Cơ chế bảo vệ khi xe dừng nhưng fix GPS vẫn tới:
  Khi xe dừng chờ đèn đỏ mà fix GPS vẫn đến liên tục, IMU không hề hoạt động. Việc dập tắt hiện tượng nhảy số khi dừng xe hoàn toàn phụ thuộc vào `FixNormalizer.trustedSpeed` tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/FixNormalizer.java:34` ($v < 0.15$ m/s) và `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/FixNormalizer.java:38` ($v \le \sigma$ khi $v \le 1.5$ m/s), cùng ngưỡng hiển thị `ZERO_KMH = 1.5` km/h tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:11` và `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:71`. Nếu nhiễu Doppler đa đường vượt qua các ngưỡng này (ví dụ nhiễu vận tốc 0.8 m/s với $\sigma = 0.5$ m/s), màn hình vẫn hiển thị khoảng 3 km/h chứ không thể giữ số 0 tuyệt đối.

### 3.6. Hiển thị giao diện người dùng (GlassSpeedoTripView)
- Thẻ giao diện `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripView.java:35` được thiết kế theo phong cách kính mờ ô tô (Automotive Frosted Glass).
- Khởi tạo mặc định: Ô giá trị tốc độ `tvSpeedValue` khởi tạo bằng ký tự `"--"` tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripView.java:156`.
- Cập nhật số tốc độ tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripView.java:366`:
  ```java
  if (!Float.isFinite(speedKmh) || speedKmh < 0f) {
      tvSpeedValue.setText("—");
      tvSpeedValue.setAlpha(1f);
      speedArcView.setSpeedRatio(0f);
  } else {
      boolean estimate = tripState != null && tripState.speedEstimated();
      float displayedSpeed = isMetric ? speedKmh : (speedKmh * 0.621371f);
      String formatted = String.format(locale, "%.0f", displayedSpeed);
      tvSpeedValue.setText(estimate ? "~" + formatted : formatted);
      tvSpeedValue.setAlpha(estimate ? 0.6f : 1f);
      speedArcView.setSpeedRatio(Math.min(1.0f, speedKmh / 220f));
  }
  ```
  Khi tốc độ không hợp lệ hoặc âm (mất tín hiệu/chưa có tọa độ), thẻ hiển thị gạch ngang `"—"` (hoặc `"--"` lúc khởi tạo) và cung kim tốc độ `SpeedArcView` hạ về 0. Khi ở chế độ ước tính trong hầm, giá trị có thêm dấu ngã `~` phía trước và độ trong suốt mờ xuống 60%.

---

## 4. So sánh

Bảng đối chiếu tổng hợp các tiêu chí kỹ thuật giữa CarStream và FermataX:

| Khía cạnh | CarStream (1.24.93 / 733) | FermataX |
| :--- | :--- | :--- |
| **Nguồn tốc độ** (Speed source) | Gọi trực tiếp `location.hasSpeed()` và `location.getSpeed() * 3.6f` từ `LocationListener` của Android. Đăng ký cứng nhà cung cấp `"gps"`. | Lấy Doppler speed qua `FixNormalizer.trustedSpeed()`, đọc `speedAccuracy()`. Đa hợp nguồn qua `LocationHub` (ưu tiên `FUSED_PROVIDER`, dự phòng `GPS_PROVIDER`). |
| **Tần suất cập nhật vị trí** | Yêu cầu 1000 ms (`1000L` ms, ~1 Hz) với độ lệch khoảng cách `0.0f` m qua `HandlerThread("cnt-gps")`. | Yêu cầu 1000 ms (~1 Hz) qua `LocationHub`. Luồng riêng `LocationHub` và `GlassSpeedo`. |
| **Lọc và làm mượt** (Smoothing) | **Không có bộ lọc toán học**. Lấy giá trị tức thời làm tròn số nguyên (`Math.round`). | **Bộ lọc EMA** (Exponential Moving Average) với hệ số $\alpha = 0.5$ trong `SpeedPresenter`. Giữ chuyển động êm ái, chống số nhảy giật cục. |
| **Xử lý độ chính xác** (Accuracy) | `SpeedBubble` (Android Auto) bỏ qua accuracy. `Speedo` (Launcher) bỏ qua fix khi `accuracy >= 25.0f` trong tính quãng đường `tripM`. | Kiểm tra phân tầng: Bác bỏ fix khi `accuracy > 300m`, tin cậy cao khi `accuracy <= 20m`. Bác bỏ Doppler khi sai số Doppler $\sigma > \max(3.0, 0.5v)$. |
| **Hết hạn / quá hạn** (Stale/timeout) | Watchdog chạy mỗi 10 giây: nếu quá 20 giây (`> 20000` ms) không nhận được fix thì tự hủy và đăng ký lại định vị phần cứng. | Hết hạn đa tầng: 3 giây giữ giá trị (`HOLD_MS`), từ 3 đến 8 giây hiển thị ước tính `~`, sau 8 giây (`FRESH_MS`) chuyển `Signal.STALE` và trả về `-1`. |
| **Mất GPS / hầm đường bộ** (GPS loss) | Không có dead reckoning và không có thời hạn cho số đang hiện: `SpeedBubble` giữ nguyên tốc độ cuối cho tới khi có fix mới hoặc provider bị tắt. Watchdog 20 s (`defpackage/x.java:119`) chỉ đăng ký lại listener, không xoá số. | Kích hoạt dead reckoning trong `Odometer.motion()`. Hỗ trợ cầu nối hầm (`bridge`) tính lại quãng đường sau khi ra khỏi hầm. Hiển thị `~` (3–8 s) rồi `"—"`. |
| **Xử lý xe đứng yên** (Stop / stationary) | Lọc cận dưới tại `Speedo`: nếu vận tốc $< 3.0$ km/h thì ép về `0.0` km/h. `SpeedBubble` không lọc cận dưới. | Khi có fix GPS sống: dựa vào `trustedSpeed` ($< 0.15$ m/s hoặc $v \le \sigma$ khi $v \le 1.5$ m/s) và `ZERO_KMH = 1.5` km/h; nhiễu Doppler vượt ngưỡng vẫn hiện số (~3 km/h). IMU/`StationaryDetector` chỉ kích hoạt khi mất fix quá 2s để đưa vận tốc ước tính về 0 trong gap dead reckoning. |
| **Hiển thị "--" hoặc 0** (Dash display) | Hiển thị `"--"` khi mới khởi tạo (`a = -1`), khi `!location.hasSpeed()`, hoặc khi provider bị tắt (`onProviderDisabled`). Hiển thị `0` khi đứng yên. | Hiển thị `"--"` lúc khởi tạo, `"—"` khi mất tín hiệu / quá hạn / GPS tắt / thiếu quyền. Hiển thị `0` khi xe dừng hẳn. |
| **Làm tròn và đơn vị** | Làm tròn số nguyên `Math.round(speed)`. Có tính năng bù trừ tốc độ theo đồng hồ xe (`tocDoBu` từ -10% đến +10%). Cố định đơn vị km/h. | Định dạng chuỗi `String.format(locale, "%.0f", displayedSpeed)`. Hỗ trợ chuyển đổi linh hoạt cả 2 hệ đơn vị mét (`KM/H`) và dặm (`MPH`). |
| **Tần suất vẽ giao diện** (UI update rate) | Điều khiển qua `Choreographer.FrameCallback`: chạy **60 Hz (VSYNC)** khi có hiệu ứng nhấp nháy/cảnh báo, 5 Hz (200 ms) khi có biển báo tĩnh, hoặc theo sự kiện GPS 1 Hz. | Cập nhật theo sự kiện định vị 1 Hz và nhịp đập heartbeat 1 Hz. Sử dụng `SpeedArcView` vẽ kim tốc độ bằng canvas 2D mượt mà. |
| **Vòng đời khi app bị che / màn hình tắt** | Duy trì dịch vụ phát đa phương tiện và định vị nền nếu service đang chạy; gỡ bỏ cập nhật khi tắt hiển thị trong `defpackage/nq.java:50` (`nq.c()`, nhánh gỡ bỏ tại `defpackage/nq.java:82`). | Chỉ khi người dùng bật ghi vị trí nền và cấp quyền "Luôn cho phép": `TripTrackingService` chạy foreground và tiếp tục ghi. Nếu không, khi dashboard dừng thì controller nhả `LocationHub` và tạm dừng chuyến (không nối quãng qua lần dừng). |

---

## 5. Khi nào hiện "--" hoặc số nhảy

### 5.1. Khi nào hiện ký tự `"--"` (hoặc `"—"`)
- **Ở CarStream**:
  1. Khi màn hình vừa khởi tạo và `SpeedBubble` chưa nhận được bất kỳ tọa độ vị trí nào từ hệ điều hành (giá trị khởi tạo thuộc tính `this.a = -1` trong `com/carstream/tech/core/speed/SpeedBubble.java:75`).
  2. Khi Android trả về điểm `Location` nhưng không đo được vận tốc tức thời (`location.hasSpeed()` trả về `false`), gán `qq.d = -1` tại `defpackage/oq.java:14`.
  3. Khi dịch vụ GPS bị người dùng hoặc hệ thống tắt (sự kiện callback `onProviderDisabled`), gán `qq.d = -1` tại `defpackage/oq.java:38`.
  4. Trên launcher, `Speedo.start()` phát `-1.0f` (`com/carstream/tech/launcher/Speedo.java:206`), nhưng `CoDienNha.datToc` hiển thị `Math.round(f)` nên màn hình hiện `-1`, không phải `"--"`. Fix không có tốc độ được `Speedo` quy về `0` (`com/carstream/tech/launcher/Speedo.java:126`).
  5. Trên giao diện launcher `CoDienNha`, `tvToc` chỉ mang `"--"` lúc tạo view (`com/carstream/tech/launcher/CoDienNha.java:600`); lần cập nhật đầu tiên ghi đè bằng số.
- **Ở FermataX**:
  1. Khi mới mở app hoặc chưa cấp quyền định vị, thẻ hiển thị `"--"` (khởi tạo tại `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripView.java:156`).
  2. Khi tín hiệu đang ở trạng thái chờ điểm đo đầu tiên (`Signal.WAITING`) hoặc đang tạm dừng (`Signal.PAUSED`), hàm `TripEngine.state()` trả về vận tốc `-1`, giao diện hiển thị `"—"`.
  3. Khi thiết bị mất tín hiệu GPS quá 8 giây (`age > FRESH_MS = 8000`), `TripEngine` chuyển sang `Signal.STALE` và tốc độ trả về `-1`, trừ khi IMU báo xe đứng yên trong khoảng mất tín hiệu: khi đó `TripEngine.motion()` tiếp tục quan sát `0` nên thẻ hiện `0`.
  4. Khi hệ điều hành liên tục gửi điểm định vị nhưng các điểm này đều thiếu trường vận tốc (`lastFixAt > observedAt`), `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedPresenter.java:59` trả về `-1`.
  5. Khi người dùng tắt GPS (`Signal.GPS_OFF`) hoặc thu hồi quyền truy cập vị trí (`Signal.PERMISSION`).

### 5.2. Khi nào xảy ra hiện tượng "số nhảy" (Jitter / Fluctuations)
- **Ở CarStream**:
  + **Hiện tượng**: Số tốc độ có xu hướng giật cục, nhảy liên tục giữa các giá trị (ví dụ: $0 \rightarrow 3 \rightarrow 1 \rightarrow 4 \rightarrow 0$) khi xe chạy chậm, dừng đèn đỏ, hoặc di chuyển trong khu vực đô thị có nhiều tòa nhà cao tầng.
  + **Nguyên nhân**: CarStream không sử dụng bất kỳ bộ lọc làm mượt nào (no smoothing filter / no EMA). Giá trị thô từ `location.getSpeed() * 3.6f` được làm tròn số nguyên trực tiếp qua `Math.round()` và đưa ngay lên màn hình. Hơn nữa, trên Android Auto (`SpeedBubble`), CarStream hoàn toàn không kiểm tra độ chính xác `accuracy`, nên những sai lệch tức thời của GPS Doppler khi bị phản xạ tín hiệu (multipath) đều phản ánh thẳng lên con số hiển thị.
- **Ở FermataX**:
  + **Hiện tượng**: Số tốc độ thay đổi êm dịu hơn nhờ bộ lọc EMA; tuy nhiên khi xe dừng chờ đèn đỏ mà fix GPS vẫn tới, con số hiển thị **không phải luôn đứng yên tuyệt đối ở 0 km/h**. Nếu có nhiễu Doppler đa đường (multipath) vượt ngưỡng lọc (chẳng hạn vận tốc đo được 0.8 m/s với sai số $\sigma = 0.5$ m/s), màn hình vẫn có thể hiển thị khoảng 3 km/h.
  + **Cơ chế và giới hạn**:
    1. Bộ lọc Exponential Moving Average (EMA) với $\alpha = 0.5$ tại `SpeedPresenter` hấp thụ và làm mượt 50% xung dao động tức thời của Doppler giữa các giây.
    2. Bảo vệ chống nhảy số khi dừng xe với fix GPS đang hoạt động (live fixes) chỉ dựa vào `FixNormalizer.trustedSpeed` (vận tốc $< 0.15$ m/s hoặc $v \le \sigma$ khi $v \le 1.5$ m/s) và ngưỡng hiển thị `ZERO_KMH = 1.5` km/h tại `SpeedPresenter`.
    3. Cảm biến gia tốc và con quay hồi chuyển (`StationaryDetector`) **chỉ hoạt động khi mất fix quá 2 giây**: IMU chỉ được đăng ký khi `now - lastCallback > 2000` ms và chỉ can thiệp thông qua dead reckoning (`Odometer.motion()`) để đưa vận tốc ước tính về 0 và hiển thị 0 trong khoảng trống tín hiệu (gap). Khi xe dừng mà fix GPS vẫn gửi về bình thường, IMU hoàn toàn không đăng ký và không can thiệp dập tắt nhiễu GPS.
    4. Bộ lọc hai Doppler liên tiếp (`MAX_PAIR_MS = 2000`) ngăn chặn tình trạng tốc độ cực đại `maxKmh` bị nhảy vọt lên con số ảo khi xe qua gầm cầu hoặc cửa hầm.

---

## 6. Điểm CarStream làm tốt hơn và rủi ro nếu áp dụng

### 6.1. Những điểm CarStream làm tốt hơn
1. **Hiệu ứng đồ họa mượt mà (60 FPS fluid rendering)**:
   CarStream tận dụng `Choreographer.FrameCallback` để đồng bộ việc vẽ `SpeedBubble` trực tiếp với chu kỳ quét của màn hình (VSYNC 60 Hz). Nhờ đó, các hiệu ứng cảnh báo viền sáng neon, nhịp đập (pulse) cảnh báo khi xe chạy quá tốc độ giới hạn, và hoạt ảnh co giãn của thẻ pill khi xuất hiện biển báo trông rất hiện đại và thu hút thị giác.
2. **Đa dạng kiểu dáng hiển thị (8 styles tùy biến)**:
   CarStream xây dựng sẵn 8 kiểu hiển thị khác nhau kế thừa từ `SpeedBubble` (`SpeedPill`, `SpeedCard`, `SpeedDial`, `SpeedNeon`, `SpeedRail`, `SpeedSpokes`, `SpeedLiquid`, `SpeedRuler`). Điều này mang lại sự cá nhân hóa cao cho người dùng trên màn hình xe hơi với nhiều tỷ lệ hiển thị khác nhau.
3. **Tính năng bù trừ tốc độ theo đồng hồ xe (`tocDoBu`)**:
   CarStream cung cấp thiết lập bù trừ độ lệch từ -10% đến +10%. Trên thực tế, hầu hết các đồng hồ cơ trên táp-lô ô tô đều được nhà sản xuất cài đặt hiển thị cao hơn tốc độ thực tế từ 3 đến 5 km/h (để đảm bảo an toàn). Tính năng này giúp tài xế có thể tinh chỉnh số trên màn hình Android Auto trùng khớp 100% với đồng hồ trước vô lăng, tránh cảm giác hoang mang.
4. **Tích hợp cảnh báo biển báo và giới hạn tốc độ**:
   Thẻ tốc độ của CarStream liên kết chặt chẽ với cơ sở dữ liệu biển báo (`wk`), tự động hiển thị biển giới hạn tốc độ tương ứng với cung đường hiện tại và khoảng cách tới biển báo/camera phạt nguội.
5. **Cơ chế Watchdog tự phục hồi kết nối định vị**:
   Vòng giám sát 10 giây kiểm tra mốc 20 giây giúp CarStream chủ động gỡ và đăng ký lại `LocationManager` khi GPS bị "treo". FermataX cũng có watchdog (60 s, `GlassSpeedoTripController.java:257-262`), nhưng nó gọi `LocationHub.acquire`, hàm này bỏ qua khi đã đăng ký, nên không tạo yêu cầu mới tới hệ điều hành. Đây là khoảng trống mà việc đăng ký lại kiểu CarStream lấp được.
6. **Ngưỡng lọc cận dưới khi dừng xe (3.0 km/h deadband)**:
   Trên launcher `Speedo`, CarStream ép vận tốc `< 3.0` km/h về `0` (`com/carstream/tech/launcher/Speedo.java:126-129`). Lưu ý: bong bóng tốc độ trên màn hình xe (`SpeedBubble`) không có ngưỡng này, còn FermataX ngoài `ZERO_KMH = 1.5` km/h đã có `FixNormalizer.trustedSpeed` quy Doppler về 0 khi `v <= sigma` và `v <= 1.5` m/s (khoảng 5,4 km/h).

### 6.2. Rủi ro và hạn chế nếu FermataX áp dụng nguyên bản
1. **Tiêu hao năng lượng và quá nhiệt (Overheating / Battery drain)**:
   Việc chạy vòng lặp `Choreographer` ở tần suất 60 Hz liên tục để vẽ giao diện thẻ tốc độ sẽ chiếm dụng GPU và CPU của điện thoại. Trong bối cảnh FermataX vốn phải gánh các tác vụ nặng như giải mã video, stream màn hình xe qua Android Auto và định vị nền, việc render 60 Hz cho một thẻ đo tốc độ 1 Hz là lãng phí và dễ dẫn đến quá nhiệt, hạ xung hoặc giật khung hình video.
2. **Nguy cơ sai lệch số liệu và giật cục nếu bỏ bộ lọc EMA**:
   Nếu FermataX chuyển sang hiển thị trực tiếp giá trị thô `Math.round(location.getSpeed() * 3.6f)` như CarStream, người dùng sẽ lập tức gặp hiện tượng số nhảy loạn xạ khi đi vào đường hầm, dưới bóng cây rậm rạp hoặc các tuyến phố trung tâm có nhà cao tầng.
3. **Phá vỡ tính chính xác của Trip Computer**:
   CarStream không có cơ chế dead reckoning hay cầu nối hầm (`bridge`). Nếu xe đi qua hầm dài 2 km, CarStream sẽ bỏ sót toàn bộ quãng đường này trong thống kê hành trình. Ngược lại, FermataX có thuật toán tính toán độ bất định (`uncertainty`) và nội suy quán tính giúp công tơ mét không bị mất dữ liệu.
4. **Phụ thuộc vào dịch vụ bản đồ bên ngoài**:
   Tính năng cảnh báo biển báo của CarStream phụ thuộc vào hạ tầng backend riêng (`/v1/bd/quyen`), đòi hỏi duy trì kết nối mạng liên tục và phân tích gói tin, điều này có thể gây lỗi hoặc không khả thi ở những vùng mất sóng di động.
5. **Đánh đổi giữa dập nhiễu Doppler khi dừng và độ nhạy ở tốc độ bò**:
   Nếu FermataX nâng ngưỡng lọc cận dưới lên 3.0 km/h như CarStream để dập tắt hoàn toàn nhiễu Doppler khi dừng đèn đỏ, ứng dụng sẽ mất khả năng phản hồi tốc độ khi xe di chuyển rất chậm ("bò" xe nhích từng mét trong lúc ùn tắc giao thông đô thị).

---

## 7. Giới hạn

1. **Mức độ làm rối mã nguồn (Obfuscation)**:
   Mã nguồn CarStream trong bản APK 1.24.93 được tối ưu và làm rối bằng R8/ProGuard (`r8-map-id-6c6f3df337888c24e1dd32e26212a1689e42f8ce343ebf3fc6b4bc5f0a4da7ec`). Các định danh lớp và phương thức trong `defpackage` (`qq`, `oq`, `yv`, `nq`, `fs`, `as`, `cs`, `x6`, `p4`...) đều bị đổi tên thành các ký tự ngắn gọn. Việc xác định vai trò của các lớp này dựa trên cấu trúc tham số gọi hàm của Android SDK (`LocationManager`, `LocationListener`, `Choreographer`), chuỗi logcat (`"cnt-gps"`, `"TIM XE"`, `"CanhNhatTube"`, `"tocDoBu"`) và luồng truyền dữ liệu giữa các biến.
2. **Dữ liệu tài nguyên được đóng gói nhị phân**:
   Một số file tài nguyên giao diện và kịch bản hỗ trợ của CarStream được nén hoặc mã hóa dưới dạng file nhị phân trong thư mục `assets/tk/...bin` (như `assets/tk/b.bin`, `assets/tk/xe/...bin`), không thể đọc trực tiếp bằng trình biên dịch thông thường.
3. **Giao thức mạng máy chủ cảnh báo**:
   Cơ chế cấp quyền và đồng bộ dữ liệu biển báo cảnh báo giao thông sử dụng API mạng `ah.l("/v1/bd/quyen", ...)` có mã hóa token người dùng. Phạm vi báo cáo này tập trung vào logic đo đạc và hiển thị tốc độ trên thiết bị, không can thiệp hay phân tích sâu giao thức máy chủ này.
4. **Môi trường thiết bị phần cứng thực tế và tình trạng đối chiếu**:
   Trong phiên của worker, thiết bị `15c36230` không kết nối được nên APK được lấy từ bản kéo trước. Sau đó Claude đã xác nhận trên máy: SHA-256 của `base.apk` đang cài trùng khớp bản đã phân tích. Các nhận định về độ nhạy, độ trễ và tác động pin vẫn dựa trên phân tích tĩnh mã dịch ngược.
