# Đặc tả Thiết kế Kiến trúc: Dual Sensor Fusion Hub cho Speedometer và Find Car

**Tài liệu**: `docs/superpowers/specs/2026-10-21-sensor-fusion-hub-design.md`  
**Ngày lập**: 2026-10-21  
**Trạng thái**: Bản thảo thiết kế (Design Proposal)  
**Phạm vi**: `fermata/ui/glass/speedo` và `fermata/carfinder`

---

## 1. Bối cảnh & Vấn đề cần giải quyết

### 1.1 Hiện trạng
Ứng dụng FermataX tích hợp 4 kênh cung cấp vị trí từ hệ điều hành Android:
1. `GPS_PROVIDER`: Tọa độ vệ tinh, vận tốc Doppler cực chuẩn, cập nhật 1Hz.
2. `FUSED_PROVIDER`: Tọa độ kết hợp từ Android Auto / Google FLP, cập nhật 1Hz.
3. `PASSIVE_PROVIDER`: Dòng dữ liệu vị trí thụ động từ Google Maps / Waze khi đang dẫn đường.
4. `NETWORK_PROVIDER`: Tọa độ trạm phát sóng di động (BTS 4G/Wi-Fi), không có vận tốc, sai số 50m–500m.

### 1.2 Vấn đề kỹ thuật
* **Đối với Đồng hồ đo tốc độ (Speedometer & Trip Computer)**:
  Các callback đến bất đồng bộ ở các mili-giây khác nhau. Nếu nạp thô từng callback vào bộ đếm động học (`TripEngine`), gói tin trạm BTS không có vận tốc (`speed = NaN`) và bước nhảy tọa độ 300m sẽ làm giằng xé bộ lọc, gây nhấp nháy tốc độ giữa `0` và `—`, nhấp nháy trạng thái giữa `GPS connected` và `Speed unavailable` / `Signal lost`.
* **Đối với Tìm xe (Car Locator)**:
  Khi tắt máy dưới hầm ngầm, GPS vệ tinh mất tín hiệu. Nếu chỉ lưu tọa độ cuối cùng lúc ngắt kết nối một cách ngây thơ, app sẽ bắt nhầm tọa độ trạm 4G BTS cách xa hàng trăm mét, khiến người dùng bị chỉ đường sai vị trí xe.

### 1.3 Mục tiêu thiết kế
Tách bạch bài toán thành **Hai Hub dung hợp chuyên biệt (Dual Dedicated Fusion Engines)**:
1. **`SpeedoKinematicFusionHub`**: Bộ dung hợp chuỗi thời gian động học 1Hz phục vụ Speedometer & Trip.
2. **`ParkingAnchorFusionHub`**: Bộ dung hợp điểm neo tĩnh phục vụ lưu vị trí đỗ xe chính xác (ngoài trời & hầm ngầm).

---

## 2. Kiến trúc tổng thể

```
                             [ 4 Nguồn định vị Android ]
                      (GPS, Fused, Passive, Network, IMU)
                                       │
                   ┌───────────────────┴───────────────────┐
                   ▼                                       ▼
    [ SpeedoKinematicFusionHub ]               [ ParkingAnchorFusionHub ]
    (Chuyên động học - Nhịp 1Hz)              (Chuyên điểm neo tĩnh đỗ xe)
                   │                                       │
                   ▼                                       ▼
          [ TripEngine.accept ]                  [ CarFinderPrefs.save ]
          • Tốc độ tức thời (km/h)               • Điểm đỗ ngoài trời (MLE)
          • Quãng đường (km)                     • Điểm cửa dốc hầm (Ramp)
          • Vận tốc trung bình, thời gian        • Nhãn tin cậy (high/basement)
```

---

## 3. Thiết kế chi tiết Phân hệ 1: `SpeedoKinematicFusionHub`

### 3.1 Vị trí & Trách nhiệm
* **Lớp mới**: `me.aap.fermata.ui.glass.speedo.SpeedoKinematicFusionHub`
* **Trách nhiệm**: Đứng giữa `GlassSpeedoTripController` (các listener của Android) và `TripEngine`.
* **Cơ chế thu thập**:
  - Nhận mẫu liên tục từ `onLocationChanged()` của cả 4 provider.
  - Gom toàn bộ mẫu nhận được trong khung thời gian $1.0\text{s}$ (1-second Epoch Buffer).
  - Định kỳ mỗi $1.0\text{s}$ (hoặc khi hết khung thời gian), tính toán hòa trộn và đẩy duy nhất **1 gói `TripEngine.Fix` tối ưu** vào `TripEngine`.

### 3.2 Thuật toán hòa trộn Tọa độ (Inverse-Variance Weighting)
Với tập hợp các mẫu $\{ (lat_i, lon_i, \sigma_i) \}$ nhận được trong khung 1 giây:
1. Trọng số sai số của từng mẫu:
   $$w_i = \frac{1}{\max(\sigma_i, 1.0)^2}$$
2. Tọa độ hòa trộn:
   $$lat_{fused} = \frac{\sum_{i} w_i \cdot lat_i}{\sum_{i} w_i}, \quad lon_{fused} = \frac{\sum_{i} w_i \cdot lon_i}{\sum_{i} w_i}$$
3. Độ bất định tổng hợp:
   $$\sigma_{fused} = \sqrt{\frac{1}{\sum_{i} w_i}}$$

*Đặc tính*: Khi có mẫu GPS ($\sigma \approx 5\text{m}$), $w_{gps} = 0.04$, trong khi trạm BTS ($\sigma \approx 150\text{m}$) có $w_{bts} \approx 0.000044$. Trọng số GPS áp đảo $99.9\%$, giúp khử nhiễu hoàn toàn từ trạm BTS mà không cần ngắt kết nối mạng.

### 3.3 Thuật toán hòa trộn Vận tốc & ZUPT (Zero Velocity Update)
1. **Lọc nguồn tin cậy**:
   - Chỉ các mẫu có `hasSpeed() == true` và $v \ge 0$ mới được đưa vào pool tính vận tốc.
   - Nguồn không có vận tốc (mạng di động) bị gán $w_{speed} = 0$, tuyệt đối không được tham gia vào vận tốc.
2. **Khóa dừng xe (ZUPT qua IMU)**:
   - Nếu cảm biến gia tốc `stationaryDetector.update() == true`:
     $$\implies v_{fused} = 0.0\text{ m/s}, \quad \sigma_{speed} = 0.1\text{ m/s}$$
   - Khóa cứng vận tốc ở $0\text{ km/h}$, triệt tiêu trôi số khi dừng xe đèn đỏ.
3. **Khi đang di chuyển**:
   - Chọn vận tốc Doppler từ nguồn có `speedAccuracy` tốt nhất (hoặc trung bình trọng số giữa GPS và Android Auto Fused speed).

---

## 4. Thiết kế chi tiết Phân hệ 2: `ParkingAnchorFusionHub` (Find Car)

### 4.1 Vị trí & Trách nhiệm
* **Nâng cấp**: Mở rộng `me.aap.fermata.carfinder.ParkingLocationSmoother` thành bộ đệm đa nguồn kết hợp giải thuật Maximum Likelihood Clustering và Ramp Entrance Anchoring.
* **Trách nhiệm**: Khi sự kiện ngắt kết nối xe xảy ra ($T_0$: ngắt Bluetooth xe, rút cáp USB, hoặc thoát Android Auto), phân tích cửa sổ lịch sử 15–30 giây cuối cùng để chốt điểm đỗ tối ưu.

### 4.2 Giải thuật Ngữ cảnh A: Đỗ xe Ngoài trời (Outdoor MLE Clustering)
* **Điều kiện kích hoạt**: Trong cửa sổ 15 giây trước $T_0$, có ít nhất 2 mẫu GPS với $\sigma \le 25\text{m}$ và mẫu gần nhất tại $T_0$ vẫn duy trì $\sigma \le 35\text{m}$.
* **Thuật toán**:
  1. Loại bỏ các mẫu khi xe đang chạy nhanh ($v > 1.5\text{ m/s}$).
  2. Lấy các mẫu khi xe đã chậm lại hoặc dừng hẳn ($v \le 1.5\text{ m/s}$) trong 10 giây cuối.
  3. Tính trọng tâm Maximum Likelihood Centroid (Weighted Least Squares):
     $$P_{park} = \frac{\sum w_i P_i}{\sum w_i}, \quad w_i = \frac{1}{\sigma_i^2}$$
  4. Triệt tiêu sai số dội sóng kính nhà cao tầng (multipath), độ chính xác đạt bán kính $1\text{m} - 3\text{m}$.
  5. Gán nhãn `confidenceLevel = "high"`.

### 4.3 Giải thuật Ngữ cảnh B: Đỗ xe Hầm ngầm (Basement Ramp Entrance Anchor)
* **Điều kiện nhận diện**:
  - Tại thời điểm ngắt xe $T_0$, tín hiệu GPS bị mất hoàn toàn hoặc sai số vọt lên $> 60\text{m}$ (trong xe dưới hầm chỉ còn sóng trạm BTS 4G hoặc Wi-Fi).
  - Trước đó (trong vòng 60–90 giây) đã có chuỗi tín hiệu GPS mặt đất tốt ($\sigma \le 25\text{m}$).
* **Thuật toán**:
  1. Quét ngược dòng thời gian từ $T_0$ về trước để tìm **Điểm GPS mặt đất tin cậy cuối cùng (Last Reliable Surface Fix)** trước khi độ suy hao tín hiệu bắt đầu tăng vọt.
  2. Điểm này chính là vị trí **Cửa dốc dẫn xuống hầm bãi đỗ (Basement Ramp Entrance)**.
  3. Chốt tọa độ điểm đỗ xe tại chính cửa dốc hầm này.
  4. **Loại trừ trạm sóng BTS 4G**: Tuyệt đối không lấy tọa độ trạm BTS 4G dưới hầm để thay thế điểm đỗ xe, vì cột sóng BTS cách xa bãi đỗ 500m–1km sẽ làm người dùng đi lạc.
  5. Gán nhãn `confidenceLevel = "basement"`, ghi chú: *"Bãi đỗ ngầm - Cửa dốc vào hầm"*.

---

## 5. Kế hoạch Kiểm thử & Xác thực (Test Strategy)

### 5.1 Unit Tests cho `SpeedoKinematicFusionHubTest`
1. `fusesGpsAndCellularFixesWithoutSpeedDrop`: Cung cấp đồng thời GPS (speed 0, acc 20m) và Cell (acc 150m, no speed) trong 1 chu kỳ $\implies$ đầu ra giữ nguyên speed 0, tọa độ lệch $< 0.1\text{m}$ so với GPS.
2. `zuptOverridesStationaryDrift`: Khi gia tốc kế báo tĩnh, đầu ra luôn ép $0\text{ km/h}$ bất chấp nhiễu tọa độ.
3. `deadReckoningBridgeDuringCompleteOutage`: Khi mất cả GPS và Fused quá 5s, nguồn mạng di động đóng vai trò giữ nhịp không cho hệ thống sập về lỗi.

### 5.2 Unit Tests cho `ParkingLocationSmootherTest`
1. `outdoorStationaryClusteringYieldsHighPrecision`: Cung cấp đám mây điểm 10 giây cuối có nhiễu Gaussian $\implies$ tọa độ chốt hội tụ về trọng tâm với độ bất định nhỏ hơn từng mẫu riêng lẻ.
2. `basementOutageLocksToRampEntrance`: Mô phỏng xe chạy ngoài trời rồi chui xuống hầm mất GPS tại $T_0 \implies$ kết quả chốt đúng ngay điểm trước khi mất sóng với nhãn `"basement"`, không bị kéo lệch theo trạm BTS.

### 5.3 Ràng buộc hệ thống
- `MainActivityDelegate.java` số dòng không trống $\le 1291$ (hiện tại 1270).
- Không thực hiện lệnh `git commit` hay `git merge`.
- Không gỡ cài đặt app trên thiết bị thử nghiệm (`adb install -r -d`).

---

## 6. Kết luận & Các bước tiếp theo
Tài liệu đặc tả này phân lập triệt để 2 nghiệp vụ, xử lý tận gốc cả 2 bài toán mà không gây xung đột chéo. Sau khi bạn duyệt bản đặc tả này, tôi sẽ chuyển sang lập kế hoạch thực thi chi tiết (Implementation Plan).
