# Audit logic và thuật toán thẻ Speedo — 2026-10-22

## 1. Phạm vi và phương pháp

- **Mã được audit:** snapshot lúc 19:44 ngày 22/10 của `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/`, gồm `TripEngine`, `SpeedoKinematicFusionHub`, `GlassSpeedoTripController`, `TripStore`, `GlassSpeedoTripView`, `TripTrackingService/Registry/AutoBinder`. Lúc đó agent khác đang sửa vòng 2 (controller, service). Các phát hiện về `TripEngine`, `TripStore` và view **không** nằm trong phạm vi vòng 2 nên vẫn còn nguyên.
- **Đọc mã:** từng nhánh của `TripEngine.accept / recover / motion / tick` và các hàm liên quan.
- **Mô phỏng số:** chạy trực tiếp `TripEngine.java` thật (biên dịch riêng bằng JDK 21) qua các kịch bản lái xe giả lập, 1 Hz, chuyển động thẳng. Harness nằm trong scratchpad của session này (`.../scratchpad/sim/Sim.java`, `Sim2.java`). Nên chuyển thành unit test (mục 6).

### Kết quả mô phỏng

| # | Kịch bản | Thực tế | Engine | Sai lệch | Ghi chú |
|---|---|---:|---:|---:|---|
| A1 | 15 m/s, 10 phút, mọi fix có Doppler | 9 000 m | 8 985 m | −0.2% | ✅ |
| A2 | Như A1, cứ 10 fix thì 1 fix thiếu Doppler | 9 000 m | 7 200 m | **−20%** | ❌ SP-1, kèm `incomplete=true` |
| A3 | Cứ 5 fix thì 1 fix thiếu Doppler | 9 000 m | 5 400 m | **−40%** | ❌ SP-1 |
| A4 | Hoàn toàn không có Doppler (chỉ vị trí) | 9 000 m | 8 945 m | −0.6% | ✅ (nghịch lý: *ít* dữ liệu lại đúng hơn A2) |
| A5 | Phố 4 m/s, không Doppler | 2 400 m | 2 381 m | −0.8% | ✅ |
| B1 | Hầm 120 s không có fix | 9 000 m | 8 976 m | −0.3% | ✅ nối gap tốt |
| B2 | Hầm 120 s, NETWORK dự phòng bật sau 10 s | 9 000 m | 7 170 m | **−20%** | ❌ SP-2 |
| B3 | Dừng 300 s trong hầm (IMU không báo đứng yên) | 7 500 m | 7 664 m | +2.2% | ⚠ SP-4: **hiển thị 54 km/h suốt 290 s đứng yên**, quãng đường hiển thị tạm thời +4.5 km |
| C1 | Hai lượt lái 10 phút, ngắt AA 1 giờ ở giữa | 20 phút, TB 54 km/h | **79 phút, TB 13.5 km/h** | — | ❌ SP-3 |
| D1 | Đang chạy, 1 fix thiếu Doppler | — | `speedKmh=-1`, `TRACKING` | — | ❌ SP-5: view hiển thị **"0"** |
| E1 | Fix đầu tiên có Doppler gai 60 m/s | max 36 km/h | **max 216 km/h** | — | ❌ SP-6 |
| F1 | Đỗ 120 s, GPS rung, 30% fix thiếu Doppler | 0 m | 0 m | — | ✅ không cộng nhiễu; ⚠ `incomplete=true` sai |

Thêm một mô phỏng với `speedAccuracy` ngẫu nhiên (giả định, cần log thật để hiệu chỉnh):

| Tốc độ | speedAcc | Doppler bị `trustedSpeed` loại | Sai lệch quãng đường |
|---|---|---:|---:|
| 3 m/s (kẹt xe) | 0.5–1.5 | 244/600 | **−49%** |
| 5 m/s | 0.5–2.0 | 112/600 | **−24%** |
| 10 m/s | 0.5–4.0 | 96/600 | **−17%** |
| 15 m/s | 0.3–1.0 | 0/600 | −0.2% |

**Kết luận chính:** engine đúng trong các trường hợp "sạch" (Doppler liên tục, hoặc chỉ có vị trí, hoặc mất hẳn tín hiệu). Nhưng nó **mất quãng đường có hệ thống** khi dữ liệu xen kẽ, mà đó lại là tình huống phổ biến nhất ngoài đời: kẹt xe, phố nhiều nhà cao tầng, tín hiệu GPS yếu.

---

## 2. Phát hiện

### 🔴 SP-1 (Critical) — Fix thiếu Doppler xen kẽ làm mất 17–49% quãng đường
`TripEngine.java:201-238, 241`

Cơ chế, với F1 (có Doppler) → F2 (không) → F3 (có) → F4 (có):
1. **Tại F2:** `speed=NaN` nên rơi vào nhánh "window". `window = 1 s < 3 s` nên không cộng gì. `lastSpeed = trustedSpeed(F2) = NaN`.
2. **Tại F3:** `speed` hữu hạn nhưng `lastSpeed = NaN`, nên **vẫn** rơi vào nhánh window (nhánh trapezoid đòi *cả hai* tốc độ hữu hạn). `window` tính từ `fallbackAnchor=F1` là 2 s < 3 s nên không cộng gì.
3. **Tại F4:** vào nhánh trapezoid, chỉ tích phân F3→F4. Dòng 205 thấy `fallbackAnchor (F1) < last (F3)` nên đặt `incomplete = true`. **Đoạn F1→F3 (2 giây) bị mất hẳn.**

Mỗi fix thiếu Doppler làm mất khoảng 2 giây quãng đường và bật cờ "Missing data". `trustedSpeed()` loại Doppler khi `speedAccuracy > max(1.0, 0.35·v)`, nên ở tốc độ thấp (kẹt xe) điều này xảy ra rất thường xuyên.

**Sửa:** không bao giờ bỏ một đoạn có ít nhất một nguồn tốc độ:
```java
boolean hasNow = Double.isFinite(speed), hasPrev = Double.isFinite(lastSpeed);
if (hasNow || hasPrev) {
    double v0 = hasPrev ? lastSpeed : speed, v1 = hasNow ? speed : lastSpeed;
    // Đoạn fallbackAnchor→last chưa được tính (sau một chuỗi fix không Doppler): cộng chord nếu vị trí đáng tin
    if (fallbackAnchor != null && fallbackAnchor.timeMs < last.timeMs
            && fallbackAnchor.positionTrusted() && last.positionTrusted()) {
        accountMotion(fallbackAnchor.distanceTo(last), last.timeMs - fallbackAnchor.timeMs, v0);
    } else if (fallbackAnchor != null && fallbackAnchor.timeMs < last.timeMs) {
        incomplete = true;
    }
    accountMotion((v0 + v1) * 0.5 * dt / 1000.0, dt, v1);
    fallbackAnchor = fix;
} else { /* nhánh window hiện tại, chỉ khi CẢ HAI đều NaN */ }
```
Đồng thời, ở dòng 241, giữ `lastSpeed` là tốc độ hữu hạn gần nhất (có hạn tuổi ≤ 2 s) thay vì gán NaN chỉ vì fix hiện tại thiếu Doppler. Test cần có: A2, A3 và Sim2 phải trong ±2%.

### 🔴 SP-2 (High) — Fix NETWORK dự phòng làm hỏng việc nối quãng đường qua hầm
`GlassSpeedoTripController.java:301-313` (bật NETWORK sau 10 s không có GNSS), `TripEngine.accept`

Fix NETWORK (độ chính xác 20–2000 m, không có Doppler) vẫn qua được `valid()` (≤ 300 m), nên trở thành `last`. Hệ quả:
- `last` luôn mới, nên `tick()` không bao giờ thấy gap > 8 s, `motion()` dừng ước lượng (`now - last.timeMs ≤ 2 s`), và `recover()` (vốn nối gap rất tốt, xem B1 −0.3%) **không bao giờ chạy**.
- Khi GPS trở lại, nhánh window cần vị trí đáng tin (≤ 20 m), mà anchor lại là fix NETWORK, nên **không cộng gì**. Kết quả: mất cả đoạn hầm (B2: −20%), cộng thêm `incomplete=true`.

**Sửa (khuyến nghị):** bỏ hoàn toàn NETWORK dự phòng khỏi speedo. Speedo chỉ cần GNSS; NETWORK chỉ hữu ích cho thời tiết hoặc bản đồ (đã có `CarLocatorManager`). Nếu vẫn muốn giữ, `TripEngine.accept` phải bỏ qua fix có `accuracy > 50` khi chọn anchor: không cập nhật `last`, không gọi `report()`.

### 🔴 SP-3 (High) — Đồng hồ trip không dừng khi ngừng theo dõi; trip không bao giờ kết thúc
`GlassSpeedoTripController.stop()`, `TripEngine.start():98`, `TripStore.load()`

- `controller.stop()` (khi ngắt AA, hoặc rời dashboard trên điện thoại) chỉ gỡ GPS và lưu checkpoint, **không** báo cho engine. Engine vẫn `active=true` với `lastTick` cũ.
- Lần `start()` sau, `engine.start()` thoát sớm vì `active` đã true, nên `tick()` cộng **toàn bộ khoảng thời gian bị ngắt** vào `totalMs`. Mô phỏng C1: 1 giờ ngắt AA biến 20 phút thành 79 phút, tốc độ trung bình 54 thành 13.5 km/h.
- `TripStore.load()` cũng cố ý cộng `elapsed - savedAt` khi process bị kill mà trip đang active (cùng boot). Điện thoại ít khi khởi động lại, nên trip hôm qua cộng thêm cả đêm vào thời gian lái.
- Vị trí `last` cũ cũng được giữ lại. Nếu lần kết nối sau xảy ra trong vòng 10 phút và gần chỗ cũ, `recover()` nối chord. Nếu không thì đặt `incomplete=true`.

**Sửa:**
1. Thêm `TripEngine.suspend(now)`: gọi `tick(now)`, rồi `lastTick = -1`, `clearAnchors()`, `signal = PAUSED`, nhưng **không** ghi `user_paused`. `controller.stop()` gọi hàm này. `start()` tiếp tục đồng hồ từ `now`.
2. `TripStore.load()`: bỏ việc cộng `elapsed - savedAt`.
3. **Quyết định sản phẩm (cần người dùng chọn):** tự bắt đầu trip mới nếu xe đỗ quá N giờ (đồng hồ trip trên ô tô thường dùng ~4 giờ), hay giữ trip đến khi người dùng reset.

### 🟠 SP-4 (High, UX/an toàn) — Khi mất GPS, tốc độ cuối cùng bị giữ nguyên vô thời hạn
`TripEngine.motion():150-162`, `tick():143`

Khi không có fix quá 2 s, `motion()` ước lượng theo tốc độ cuối cùng (`gapSpeed`) và đặt `speedKmh` bằng giá trị đó **mãi mãi**, cho tới khi có fix mới hoặc IMU báo đứng yên. Đây chính là triệu chứng gốc "đứng ở ≈25 KM/H". Mô phỏng B3: xe đứng yên 290 s trong hầm mà thẻ vẫn hiển thị 54 km/h, và quãng đường hiển thị (`getDistanceKm` có cộng `gapMeters`) phình thêm 4.5 km rồi tụt lại khi có tín hiệu. IMU chỉ cứu được nếu điện thoại thật sự rất yên. Xe nổ máy chờ thường rung vượt ngưỡng `var < 0.01`, nên không cứu được.

**Sửa:**
- Chỉ hiển thị tốc độ ước lượng tối đa ~10 s (`STALE_SPEED_MS`), sau đó hiển thị "—". Nên làm mờ hoặc thêm ký hiệu "~" trong lúc ước lượng.
- Giới hạn `gapMeters` hiển thị ở mức `gapSpeed × 30 s`. `recover()` vẫn dùng chord bị kẹp nên quãng đường cuối không bị ảnh hưởng.

### 🟠 SP-5 (Medium) — View hiển thị "0" khi đang chạy mà tốc độ chưa biết
`GlassSpeedoTripView.updateSpeed`, dòng 5–14 của hàm

Điều kiện `(!finite || speed < 0) && !isTracking` nghĩa là khi `signal == TRACKING` mà `speedKmh == -1` (fix thiếu Doppler, xem D1), view hiển thị `max(0, …)`, tức "0". Xe đang chạy 50 km/h có thể nhấp nháy về 0.
**Sửa:** `speedKmh < 0` thì luôn hiển thị "—", bất kể signal.

### 🟠 SP-6 (Medium) — Tốc độ tối đa nhận cả gai nhiễu
`TripEngine.report()`, `accept()` dòng 185-189 và 223-230

- Fix đầu tiên của trip được nhận vô điều kiện, kể cả Doppler (không có fix trước để kiểm tra gia tốc). Mô phỏng E1 cho max 216 km/h.
- Tốc độ tính từ nhánh window (`chord / window`) cũng được đưa vào max. Với vị trí sai ±20 m trong 3 s, có thể thổi lên tới khoảng +60 km/h.

**Sửa:** chỉ cập nhật `maxSpeedKmh` từ Doppler đã qua kiểm tra liên tục (không phải fix đầu, có `|Δv| ≤ 8·dt + 2` so với fix trước), và nên yêu cầu 2 fix liên tiếp nhất quán.

### 🟡 SP-7 (Medium) — Cờ `incomplete` và `estimated` bật sai, che mất trung bình khi chạy
`TripEngine.java:205, 235`, `State.getMovingAverageSpeedKmh()`

- F1 (đỗ xe, Doppler lúc có lúc không) và A2 đều bật `incomplete=true` dù không mất dữ liệu thật.
- `estimated > 0` (chỉ cần một đoạn window hoặc một lần nối hầm) làm trung bình khi chạy trả `-1` cho tới hết trip.

Hiện tác động nhìn thấy còn nhỏ, vì UI chưa hiển thị trung bình khi chạy hay `speedo_incomplete` (SP-11). Nhưng nếu bật UI lên mà không sửa, "Missing data" sẽ hiện gần như mọi lúc. Sửa SP-1 sẽ loại phần lớn nguyên nhân. Ngoài ra, chỉ nên ẩn trung bình khi chạy nếu `estimated / distance > 10%`.

### 🟡 SP-8 (Low/Medium) — Ngưỡng tin Doppler quá chặt ở tốc độ thấp
`TripEngine.trustedSpeed()`: `speedAccuracy > max(1.0, 0.35·v)` thì trả NaN. Ở 3 m/s, chỉ cần `speedAcc > 1.05` là bị loại, trong khi sai số 1 m/s ở 3 m/s vẫn tốt hơn nhiều so với không có số liệu. Sau khi sửa SP-1, tác hại giảm hẳn, nhưng vẫn nên nới thành `max(1.5, 0.5·v)` sau khi có log thật. Các hằng số `CURVE_PRIOR`, `GAP_REL_SIGMA` cũng được chính mã ghi là "uncalibrated prior".

### 🟡 SP-9 (Low) — Fusion hub thừa sau khi chỉ còn một nguồn vị trí
`SpeedoKinematicFusionHub`: với F-4 (chỉ FUSED hoặc GPS), mỗi epoch chỉ có khoảng 1 mẫu, nên phép trung bình nghịch đảo phương sai không còn tác dụng. Khi có nhiều mẫu, vị trí lại là *trung bình trong 2 s* nhưng gắn thời điểm `maxFixTime`, nên ở 30 m/s vị trí bị trễ tới vài chục mét. Nên đơn giản hoá thành pass-through (chuyển `Location` sang `Fix`) để bớt một lớp đệm 80 ms và bớt bề mặt lỗi.

### 🟡 SP-10 (Low) — IMU và độ bất định chưa hiệu chỉnh
- **IMU:** `StationaryDetector` dùng ngưỡng cứng (`var < 0.01 m²/s⁴`, `gyro < 0.03 rad/s`, 2 cửa sổ 1 s, `SENSOR_DELAY_NORMAL`, khoảng 5 mẫu mỗi cửa sổ). Xe nổ máy chờ có thể không bao giờ được coi là đứng yên, còn đường cao tốc rất êm có thể bị báo đứng yên nhầm. Cần ghi log thật để chỉnh.
- **Độ bất định:** `uncertainty` được cộng tuyến tính các sai số độc lập. Lẽ ra nên cộng bình phương rồi lấy căn (RSS). Cách hiện tại thận trọng, không sai về hướng.

### 🟡 SP-11 (Low) — Tính năng chết, UI không hiển thị điều engine biết
- `GlassSpeedoTripView.setSpeedLimit()` **không được gọi ở đâu cả**, nên khung biển báo tốc độ không bao giờ hiện.
- `getMovingAverageSpeedKmh()`, `uncertaintyMeters`, `estimatedMeters`, cờ `incomplete` đều được tính nhưng không hiển thị. 4 string `speedo_estimated`, `speedo_incomplete`, `speedo_signal_lost`, `speedo_speed_unknown` không dùng.
- Định dạng số dùng `Locale.US` (tiếng Việt quen dùng `12,5 km`).

Cần quyết định: hiển thị (ví dụ `12.4 km ±0.3`, nhãn "ước tính") hay xoá đi.

---

## 3. Những điểm thiết kế tốt (giữ nguyên)

- Toàn bộ thời gian tính theo `elapsedRealtime`, kiểm tra `valid()` chặt (tuổi, tương lai, NaN, độ chính xác).
- `plausible()` chặn nhảy vị trí, cộng thêm kiểm tra gia tốc (`|Δv| ≤ 8·dt + 2`).
- Nối gap cần 3 fix nhất quán, có kẹp `[chord − u, min(70·dt, 1.5·chord + u, 1.3·v·dt)]`. Mô phỏng B1 và B3 cho sai số cuối ≤ 2.2%.
- Có hysteresis đi/dừng (3 fix) nên đứng yên không bị cộng nhiễu (F1: 0 m).
- Phát hiện đỗ trong hầm (`stoppedCount ≥ 3 && chord ≤ 2u`).
- Không giữ anchor vị trí qua lần khởi động lại process.
- Có 38 unit test cho engine, đặt tên rõ ràng theo kịch bản.

**Khoảng trống của test hiện tại:** không có test nào cho Doppler xen kẽ, NETWORK trong hầm, đồng hồ khi stop rồi start, hay việc view hiển thị khi `TRACKING` mà speed `-1`. Các lỗi SP-1 đến SP-5 vì vậy lọt qua.

## 4. Thứ tự sửa đề xuất

| Ưu tiên | Mục | File | Có xung đột với agent vòng 2? |
|---|---|---|---|
| 1 | SP-1 | `TripEngine` | Không (vòng 2 không đụng TripEngine) |
| 2 | SP-3 (engine + store) | `TripEngine`, `TripStore` | Không |
| 3 | SP-4, SP-6 | `TripEngine` | Không |
| 4 | SP-5 | `GlassSpeedoTripView.updateSpeed` | Thấp (vòng 2 sửa `confirmResetTrip` cùng file) |
| 5 | SP-2 (bỏ NETWORK), SP-3 (`controller.stop` gọi `suspend`) | `GlassSpeedoTripController` | **Có**: làm sau khi vòng 2 xong |
| 6 | SP-7 … SP-11 | nhiều file | Làm sau |

## 5. Cần quyết định sản phẩm

1. Có tự tạo trip mới sau khi xe đỗ lâu (ví dụ > 4 giờ) không, hay trip chỉ kết thúc khi người dùng reset?
2. Rời dashboard trên điện thoại (không có AA) thì *tạm dừng* đồng hồ (khuyến nghị) hay *tiếp tục* đồng hồ mà không đo quãng đường?
3. Hiển thị độ bất định và "Missing data" trên thẻ, hay xoá phần này cho gọn?

## 6. Test cần thêm (chuyển từ harness mô phỏng)

- `interleavedMissingDopplerKeepsDistanceWithin2Percent` (A2, A3, Sim2)
- `networkFallbackDuringTunnelDoesNotDropBridge` (B2), hoặc test rằng speedo không đăng ký NETWORK
- `stopThenStartDoesNotCountSuspendedTime` (C1), `restoreDoesNotAddOfflineTime`
- `staleSpeedHiddenAfterTenSecondsWithoutFix` (B3)
- `firstFixSpikeDoesNotSetMaxSpeed` (E1)
- `viewShowsDashWhenTrackingWithUnknownSpeed` (D1, Robolectric)
