# Speedo Refactor Plan: tách Odometer / SpeedPresenter / TripClock

> Dành cho agent thực thi. Làm từng task theo thứ tự; mỗi task kết thúc với toàn bộ test xanh (trừ các test đặc tả được ghi rõ là "chưa pass" ở Task 1). Nguồn phân tích: `audit_reports/feat-aa-glass-theme/2026-10-22-speedo-algorithm-audit.md` (mã lỗi SP-1 … SP-11).

## 0. Mục tiêu

**Vấn đề:** `TripEngine` gộp ba việc vào một class với khoảng 20 biến trạng thái chồng chéo: hiển thị tốc độ, cộng quãng đường, và đếm thời gian. Mô phỏng trên mã thật cho thấy:

| Kịch bản | Hiện tại | Mục tiêu |
|---|---|---|
| Cứ 10 fix thì 1 fix thiếu Doppler | −20% quãng đường | ±2% |
| Kẹt xe 3 m/s, speedAccuracy kém | −49% | ±3% |
| Hầm 120 s, có NETWORK dự phòng | −20% | ±2% |
| Dừng 5 phút trong hầm | Hiển thị 54 km/h, quãng đường hiển thị +4.5 km | "—" sau 10 s, km đứng yên |
| Ngắt AA 1 giờ giữa 2 lượt lái 10 phút | 79 phút, TB 13.5 km/h | 20 phút, TB 54 km/h |
| Đang chạy, fix thiếu Doppler | Hiển thị "0" | "—" hoặc tốc độ gần nhất ≤ 3 s |
| Gai Doppler ở fix đầu tiên | Max 216 km/h | Max ≤ tốc độ thật + 5% |

**Cách làm:** refactor theo kiểu thay thế dần (strangler), **không viết lại từ đầu**. Giữ `TripEngine` làm facade với API công khai không đổi (`start/pause/reset/restore/accept/tick/motion/unavailable/state`), để controller, view và service không phải sửa trong các Task 2–6. Bên trong, chuyển dần sang các thành phần mới. Giữ nguyên các phần đang tốt: `valid()`, `plausible()`, kiểm tra gia tốc, nối gap 3 fix có kẹp, phát hiện đỗ trong hầm, hysteresis đi/dừng.

## 1. Kiến trúc đích

```
LocationManager (1 luồng: FUSED nếu API ≥ 31, ngược lại GPS; KHÔNG NETWORK, KHÔNG PASSIVE)
        │ Location
        ▼
FixNormalizer ── Location → Fix{timeMs từ elapsedRealtimeNanos, lat, lon, acc, speed?, σv?, quality}
        │              quality: TRUSTED (acc ≤ 20 m) | COARSE (20–300 m) | REJECT
        ├────────► Odometer        quãng đường, movingMs, estimated, uncertainty, incomplete
        ├────────► SpeedPresenter  tốc độ hiển thị, cờ ước lượng, maxSpeed
        ▼
TripClock ── RUNNING | SUSPENDED (hệ thống) | PAUSED (người dùng); totalMs
        ▼
TripEngine (facade) → TripEngine.State (immutable) → TripStore / View
```

Package giữ nguyên: `me.aap.fermata.ui.glass.speedo`. Các class mới là package-private, final, thuần Java (không phụ thuộc Android), để test bằng JUnit thường.

## 2. Ràng buộc

- **Điều kiện bắt đầu:** agent "vòng 2" (`2026-10-22-trip-tracking-service-fixes.md`) đã xong. Task 7 sửa `GlassSpeedoTripController` nên **bắt buộc** làm sau vòng 2. Task 1–6 chỉ đụng `TripEngine`, `TripStore`, class mới, view và test.
- **KHÔNG** commit, push, merge, rebase, stash hay reset.
- **KHÔNG** gỡ cài đặt app trên `15c36230` (dùng `adb install -r -d`).
- **KHÔNG** sửa `MainActivityDelegate.java`, `ControlPanelView.java`, `MediaSessionCallback.java`, `FermataMediaService.java`.
- Lệnh Gradle: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat <task> -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
- Test nhanh: `:fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.speedo.*"`
- 38 test hiện có trong `TripEngineTest` là bộ chống hồi quy. Test nào mâu thuẫn với quy tắc mới thì **sửa expectation và ghi lý do** trong báo cáo, không xoá âm thầm.
- Tên field lưu trữ trong `TripStore` (`speedo_trip_v2`) phải tương thích ngược: đọc được dữ liệu cũ.

## 3. Quyết định sản phẩm (mặc định đề xuất — người dùng xác nhận trước Task 2)

| # | Câu hỏi | Mặc định trong plan |
|---|---|---|
| D1 | Có tự tạo trip mới sau khi xe đỗ lâu? | **Có**, sau 4 giờ ở trạng thái SUSPENDED (hằng số `AUTO_NEW_TRIP_MS`, chưa cần UI cài đặt) |
| D2 | Rời dashboard trên điện thoại (không AA) thì đồng hồ thế nào? | **Tạm dừng** (SUSPENDED), giống ngắt AA |
| D3 | Hiển thị độ bất định / ước tính trên thẻ? | **Có, tối giản:** hiển thị "≈" trước số km khi `estimated/distance > 10%`; không hiển thị ± |
| D4 | Biển báo tốc độ (`setSpeedLimit`) chưa có nguồn dữ liệu | **Xoá** code UI không dùng |

---

## 4. Các task

### Task 1: Test đặc tả từ mô phỏng (viết trước, phần lớn sẽ FAIL)

**File mới:** `fermata/src/test/java/me/aap/fermata/ui/glass/speedo/TripScenarioTest.java`, cùng helper `TripScenario` trong thư mục test.

Helper `TripScenario`: sinh fix 1 Hz, chuyển động thẳng theo vĩ độ (`lat = 10 + m/111320`, `lon = 106`), `t0 = 1_000_000 ms`. Mỗi giây gọi `accept(fix, t)`, `tick(t)`, `motion(t, stationary, NaN)`, giống heartbeat. Seed ngẫu nhiên cố định.

| Test | Tham số | Kỳ vọng |
|---|---|---|
| `steadyDopplerBaseline` | 15 m/s, 600 s, acc 5, σv 0.3 | sai số ≤ 1%, `incomplete=false` |
| `interleavedMissingDoppler10pct` | Như trên, mỗi fix thứ 10 không có speed | ≤ 2%, `incomplete=false` |
| `interleavedMissingDoppler20pct` | Mỗi fix thứ 5 không có speed | ≤ 2% |
| `lowSpeedPoorSpeedAccuracy` | 3 m/s; σv ~ U[0.5, 1.5]; speed = v + N(0, σv/2) | ≤ 3% |
| `positionsOnlyNoDoppler` | 15 m/s, không có speed | ≤ 2% (hiện đang pass, phải giữ) |
| `tunnelNoFix120s` | Không có fix từ giây 201 đến 320 | ≤ 2% (hiện đang pass, phải giữ) |
| `tunnelWithCoarseFixes` | Giây 211–320 có fix acc 150, không speed, lệch +80 m | ≤ 2% |
| `stoppedInsideTunnel` | Không fix giây 201–520; xe đứng yên 211–510; IMU luôn báo "đang chạy" | Tại t = 500: `displaySpeed` là "—"; km hiển thị không tăng quá giá trị tại t = 210 + 10 s × v. Cuối: sai số ≤ 3% |
| `suspendDoesNotCountTime` | Lái 600 s → `suspend` → 1 giờ → `resume` → lái 600 s | `totalMs` = 1200 s ± 2 s; TB = 54 ± 1 km/h; không nối vị trí qua lúc nghỉ |
| `autoNewTripAfterLongSuspend` | `suspend` 5 giờ → `resume` | Bắt đầu trip mới (`distance = 0`), trip cũ được lưu làm "previous" |
| `restoreDoesNotAddOfflineTime` | `TripStore` lưu `active=true`, giả lập process chết 1 giờ | `load()` không cộng 1 giờ |
| `unknownSpeedWhileTrackingIsNotZero` | Fix có Doppler 15 → fix không Doppler | `displaySpeedKmh` là tốc độ gần nhất (≤ 3 s) hoặc "unknown", **không phải 0** |
| `firstFixSpikeDoesNotSetMax` | Fix đầu 60 m/s, sau đó 10 m/s | `maxSpeedKmh` ≤ 37.8 |
| `parkedJitterNoDistanceNoIncomplete` | 120 s đứng yên, nhiễu ±6 m, 30% fix thiếu speed | `distance = 0`, `incomplete = false` |
| `staleSpeedHiddenAfter10s` | Mất fix khi đang 15 m/s | 3–10 s: ước lượng (cờ `estimatedSpeed = true`); > 10 s: unknown |

- [ ] Viết toàn bộ test. Chạy và ghi lại test nào pass, test nào fail (dự kiến fail: interleaved…, lowSpeed…, tunnelWithCoarse…, stoppedInsideTunnel, suspend…, autoNewTrip…, restore…, unknownSpeed…, firstFixSpike…, parkedJitter… (cờ incomplete), staleSpeed…).
- [ ] Đánh dấu các test chưa pass bằng `@Ignore("SP-x: enabled in Task N")`. Mỗi task phải bỏ `@Ignore` của test tương ứng.

### Task 2: TripClock và suspend (SP-3)

**File:** `TripClock.java` (mới), `TripEngine.java`, `TripStore.java`, test.

- [ ] `TripClock` có trạng thái `PAUSED | RUNNING | SUSPENDED`. Các hàm: `start(now)`, `pause(now)`, `suspend(now)`, `resume(now)`, `tick(now)`, `totalMs()`. Chỉ cộng `totalMs` khi `RUNNING`.
- [ ] Thêm `TripEngine.suspend(now)` (public): `tick(now)`, chuyển clock sang SUSPENDED, `clearAnchors()`, `signal = PAUSED`. **Không** ghi `user_paused`.
- [ ] `TripEngine.start(now)`: khi đang SUSPENDED thì chuyển sang `resume(now)`, để hết lỗi "thoát sớm vì đã active". Nếu thời gian suspend ≥ `AUTO_NEW_TRIP_MS` (D1) thì lưu snapshot trip cũ (`previousTrip`) rồi `reset(now)`.
- [ ] `State` thêm trường `suspended` (hoặc dùng `Signal.PAUSED` kèm cờ). `TripStore` lưu thêm `suspended_at_wall` (wall clock, chỉ dùng cho D1 qua lần khởi động lại process) và `previous_*` cho trip trước.
- [ ] `TripStore.load()`: **bỏ** khối `total += elapsed - savedAt`. Trip active trong lần lưu trước được khôi phục ở trạng thái SUSPENDED.
- [ ] Bỏ `@Ignore`: `suspendDoesNotCountTime`, `autoNewTripAfterLongSuspend`, `restoreDoesNotAddOfflineTime`.

### Task 3: SpeedPresenter (SP-4, SP-5, SP-6)

**File:** `SpeedPresenter.java` (mới), `TripEngine.java`, `GlassSpeedoTripView.java`, test.

- [ ] **Đầu vào:** Fix đã chuẩn hoá, `motion(now, stationary, vehicleSpeed)`, `tick(now)`.
- [ ] **Đầu ra:** `displaySpeedKmh` (float, `-1` = unknown), `speedIsEstimate` (boolean), `maxSpeedKmh`.
- [ ] **Quy tắc hiển thị:**
  - Doppler hợp lệ: EMA α = 0.5; dưới 1.5 km/h hiển thị 0.
  - Fix không có Doppler: giữ tốc độ gần nhất nếu nó ≤ 3 s tuổi, ngược lại unknown. **Không bao giờ trả 0** vì thiếu dữ liệu.
  - Không có fix: 0–3 s giữ tốc độ thật; 3–10 s là ước lượng (`speedIsEstimate = true`, dùng `vehicleSpeed` nếu có, IMU đứng yên thì 0, ngược lại tốc độ cuối); trên 10 s là unknown.
  - Fix GNSS TRUSTED có Doppler đầu tiên sau gap: hiển thị ngay, không chờ 3 fix của Odometer.
- [ ] **Tốc độ tối đa:** `max = max(max, min(v[k], v[k−1]))`, chỉ từ 2 Doppler liên tiếp, cách nhau ≤ 2 s, cùng qua kiểm tra gia tốc. Không lấy từ chord hay ước lượng.
- [ ] `TripEngine.State.speedKmh` lấy từ presenter. Thêm `speedIsEstimate` vào `State` (thêm constructor phụ để không làm vỡ code gọi hiện có).
- [ ] **View:** `speedKmh < 0` thì luôn hiển thị "—" (bỏ điều kiện `isTracking`). Khi `speedIsEstimate` thì hiển thị "~N" với alpha 0.6. `getDistanceKm()` **không** cộng `gapMeters` nữa (km đứng yên trong hầm).
- [ ] Bỏ `@Ignore`: `unknownSpeedWhileTrackingIsNotZero`, `firstFixSpikeDoesNotSetMax`, `staleSpeedHiddenAfter10s`, cùng phần hiển thị của `stoppedInsideTunnel`.
- [ ] Thêm test Robolectric cho view: `TRACKING` với speed `-1` thì `tvSpeedValue` là "—".

### Task 4: FixNormalizer (SP-8, chuẩn bị cho SP-2)

**File:** `FixNormalizer.java` (mới), `TripEngine.Fix` (mở rộng), test.

- [ ] Thêm vào `Fix`: `Quality quality` (`TRUSTED` khi acc ≤ 20, `COARSE` khi ≤ 300), `double speedSigma` (σv; thiếu thì `DEFAULT_SPEED_SIGMA = 1.5`). Giữ constructor cũ (suy ra quality từ accuracy).
- [ ] `trustedSpeed()` cũ chuyển thành: speed hữu hạn, nằm trong [0, 70], và `σv ≤ max(3.0, 0.5·v)` thì giữ, kèm σv. Không loại bỏ chỉ vì σv lớn hơn 0.35·v. Ngưỡng là hằng số đặt tên rõ, kèm ghi chú "hiệu chỉnh ở Task 8".
- [ ] `valid()` giữ nguyên. Thêm loại bỏ fix có `timeMs ≤ last.timeMs` ngay tại normalizer.
- [ ] Test đơn vị cho bảng phân loại.

### Task 5: Odometer (SP-1, SP-2, SP-7)

**File:** `Odometer.java` (mới), `TripEngine.java`, test.

Quy tắc cho mỗi fix B sau anchor A (Δt = B.t − A.t):

1. **COARSE:** không cập nhật anchor, không cộng, không đổi trạng thái gap (chỉ báo "có tín hiệu" cho UI). Đây là phần sửa SP-2.
2. **Δt > `FRESH_MS` (8 s), hoặc đang gap:** đi đường `recover()` hiện có (3 fix nhất quán, kẹp `[chord − u, min(70·Δt, 1.5·chord + u, 1.3·v·Δt)]`, có kiểm tra đỗ trong hầm). **Chuyển nguyên logic, không đổi công thức.** Giá trị tham khảo cho phần nối là ước lượng chạy ngầm (giới hạn `gapSpeed × min(gap, 120 s)`), không phải `gapMeters` hiển thị.
3. **Đứng yên:** Doppler < 0.5 m/s trong 3 fix liên tiếp, hoặc IMU báo đứng yên. Khi đó `d = 0` (ép về 0, ZUPT); hysteresis 3 fix để vào hoặc ra trạng thái moving (giữ logic `accountMotion`).
4. **Có ít nhất một Doppler (A hoặc B):** `v0 = A.v ?? B.v`, `v1 = B.v ?? A.v`, `d = ½(v0 + v1)·Δt`. Tốc độ "gần nhất" của A được giữ tối đa 2 s.
5. **Không có Doppler, cả hai đều TRUSTED:** tích luỹ vào `pendingWindow` từ anchor TRUSTED gần nhất. Khi cửa sổ dài 3–10 s và `chord > 2σ` thì cộng chord, ghi vào `estimated`. Cửa sổ quá 10 s mà không có bằng chứng chuyển động thì bỏ (đứng yên), **không** bật `incomplete`, trừ khi có Doppler > 0.3 m/s trong cửa sổ.
6. **Một `pendingSegment` duy nhất:** mọi đoạn chưa tính phải được cộng (theo quy tắc 4/5) hoặc khai báo mất (bật `incomplete`). Bỏ bộ đôi `fallbackAnchor` / `lastSpeed` hiện tại.

Yêu cầu thêm:
- [ ] `uncertainty` cộng theo RSS: `u_total = √(Σ u_i²)`. Ghi chú thay đổi so với cách cộng tuyến tính cũ.
- [ ] `movingMs` chỉ tăng khi moving.
- [ ] Bỏ `@Ignore`: `interleavedMissingDoppler*`, `lowSpeedPoorSpeedAccuracy`, `tunnelWithCoarseFixes`, `parkedJitterNoDistanceNoIncomplete`, phần quãng đường của `stoppedInsideTunnel`.
- [ ] Toàn bộ 38 test cũ phải pass (hoặc có giải thích bằng văn bản nếu đổi expectation).

### Task 6: Dọn TripEngine facade

- [ ] Xoá các field cũ không còn dùng (`gapMeters` hiển thị, `fallbackAnchor`, `lastSpeed`, `pendingDistance`…). `TripEngine` chỉ điều phối `FixNormalizer → Odometer / SpeedPresenter / TripClock` và tạo `State`.
- [ ] `getMovingAverageSpeedKmh()`: chỉ trả `-1` khi `estimated / distance > 10%` hoặc `incomplete`.
- [ ] Mục tiêu kích thước: `TripEngine` ≤ 200 dòng; mỗi class mới ≤ 250 dòng.

### Task 7: Đơn giản hoá controller (sau vòng 2)

**File:** `GlassSpeedoTripController.java`, `SpeedoKinematicFusionHub.java` (xoá), test.

- [ ] **Bỏ `SpeedoKinematicFusionHub`** (SP-9): `handleLocation` chuyển `Location` sang `FixNormalizer` rồi gọi thẳng `engine.accept`, không còn đệm 80 ms. Xoá class và test của nó.
- [ ] **Bỏ NETWORK dự phòng** (SP-2): xoá `networkListener`, `networkRegistered` và khối đăng ký trong heartbeat.
- [ ] **Watchdog:** khi provider vẫn đang bật thì không đăng ký lại vì "không có tiến triển" (tránh churn trong hầm). Chỉ đăng ký lại khi: provider bị tắt rồi bật, đăng ký thất bại, hoặc không có callback nào quá 60 s.
- [ ] `stop()` gọi `engine.suspend(now)` trên worker trước `checkpoint()` (SP-3 phía controller). `start()` gọi `engine.start(now)` (sẽ resume).
- [ ] D2: dashboard trên điện thoại khi rời đi gọi `controller.stop()`, tức suspend. Kiểm tra lại logic gọi trong `GlassDashboardController` sau vòng 2.
- [ ] `motion(now, stationary, vehicleSpeedMps)`: giữ tham số, hiện truyền `NaN`; thêm TODO trỏ tới mục "tốc độ xe" ở cuối plan.

### Task 8: Ghi log chuyến đi và test phát lại

**File:** `TripLogRecorder.java` (mới, chỉ bật trong bản debug hoặc qua cài đặt ẩn), thư mục test `src/test/resources/trips/`, `TripReplayTest.java`.

- [ ] Ghi mỗi `Location` nhận được vào CSV trong `getExternalFilesDir("trip_logs")`: `elapsedNanos,provider,lat,lon,acc,hasSpeed,speed,hasSpeedAcc,speedAcc,isMock`. Thêm sự kiện IMU tóm tắt mỗi giây (`var, gyroMax`) và sự kiện `start/stop/suspend/pause`. Lấy về bằng `adb pull`.
- [ ] Chỉ bật khi `BuildConfig.DEBUG` **hoặc** một pref ẩn. Mặc định tắt. Không gửi đi đâu.
- [ ] `TripReplayTest` đọc CSV, phát qua `TripEngine`, so với `expected.json` của từng file (quãng đường thực tế từ odometer xe hoặc Google Maps, thời gian, số đoạn ước tính). Cho phép sai số ≤ 2% với đường thường, ≤ 4% với chuyến có hầm.
- [ ] Commit kèm 1–2 log mẫu nhỏ (≤ 200 KB) **sau khi** người dùng đồng ý, vì log có toạ độ thật. Nếu không đồng ý thì chỉ giữ cấu trúc test và để file log ngoài repo.

### Task 9: UI và chuỗi

- [ ] D3: hiển thị "≈" trước số km khi `estimated/distance > 10%`. Dùng string mới `speedo_estimated_prefix` hoặc tái dùng `speedo_estimated`.
- [ ] D4: xoá `setSpeedLimit`, `speedLimitContainer`, `tvSpeedLimit` cùng resource liên quan.
- [ ] Dùng hoặc xoá các string còn thừa (`speedo_signal_lost` dùng cho nhãn "> 10 s mất GPS", `speedo_speed_unknown` dùng cho contentDescription khi "—"). Đồng bộ đủ 6 locale.
- [ ] Định dạng số theo `Locale` của app (`getLocalizedContext`), không cứng `Locale.US`.
- [ ] Lint: không còn `UnusedResources` cho `speedo_*`.

### Task 10: Kiểm thử trên thiết bị

**Trên DHU với máy `15c36230`** (theo quy trình mục 4 của `2026-10-22-trip-tracking-service.md`):

| ID | Kịch bản | Kỳ vọng |
|---|---|---|
| V1 | Kết nối AA, đứng yên 2 phút | Tốc độ 0 hoặc "—", km không tăng |
| V2 | Ngắt AA 5 phút rồi nối lại | Thời gian lái không tăng 5 phút; log có `suspend/resume` |
| V3 | Tắt Location 30 s trong lúc chạy (Quick Settings) | Sau 10 s thẻ hiển thị "—" kèm nhãn mất GPS; bật lại thì hiện tốc độ ngay |
| V4 | Rời dashboard trên điện thoại 2 phút rồi quay lại | Thời gian không tăng 2 phút |

**Lái xe thật** (người dùng thực hiện, bật `TripLogRecorder`):

| ID | Tuyến | Đo |
|---|---|---|
| R1 | Đường phố có kẹt xe, ≥ 5 km | Sai số so với odometer xe ≤ 3% |
| R2 | Đường cao tốc, ≥ 10 km | ≤ 2%; max speed hợp lý |
| R3 | Qua ít nhất 1 hầm hoặc cầu chui dài | Tốc độ "—" sau 10 s trong hầm; km đứng yên; sau khi ra hầm km nhảy đúng một lần; sai số ≤ 4% |
| R4 | Vào hầm xe, đỗ, rút AA | Không cộng quãng đường hay thời gian sau khi rút |

Kéo log về bằng `adb pull`, chuyển thành golden test (Task 8).

---

## 5. Hiệu chỉnh sau khi có log (không chặn release)

- Ngưỡng `σv` (Task 4), `CURVE_PRIOR`, `GAP_REL_SIGMA`.
- Ngưỡng IMU đứng yên (`var`, `gyro`, số cửa sổ), dựa trên phân bố log lúc đỗ và lúc chạy.
- Tần suất fix thật của FUSED trên các máy khác nhau.

## 6. Ngoài phạm vi (ghi nhận cho sau)

- **Tốc độ từ xe:** qua Car App Library `CarHardwareManager → CarInfo.addSpeedListener` (quyền `CAR_SPEED`). App đang dùng `CarActivity` của SDK cũ, cần nghiên cứu khả thi riêng. `motion(..., vehicleSpeedMps)` đã có sẵn chỗ nhận.
- **GPS của xe qua Android Auto:** kiểm tra trên DHU hoặc xe xem `LocationManager` có nhận luồng này không (log `provider`).
- **Kalman 1D [quãng đường, tốc độ]:** chỉ cân nhắc nếu golden test cho thấy cách ước lượng theo đoạn không đạt ±2%.

## 7. Báo cáo

Tạo `audit_reports/feat-aa-glass-theme/2026-10-2x-speedo-refactor-report.md`:
- Bảng test đặc tả (Task 1): trạng thái trước và sau.
- Danh sách test cũ bị đổi expectation, kèm lý do.
- Kích thước các class sau refactor.
- Kết quả V1–V4 (bằng chứng: log, dumpsys, ảnh). R1–R4 ghi "chờ người dùng lái" nếu chưa có.
- Trung thực: kịch bản nào chưa chạy thì ghi "KHÔNG CHẠY ĐƯỢC" kèm lý do.

## 8. Định nghĩa hoàn thành

- [ ] Mọi test trong `TripScenarioTest` pass, không còn `@Ignore`.
- [ ] 38 test `TripEngineTest` cũ pass, hoặc có giải thích bằng văn bản.
- [ ] Không còn `SpeedoKinematicFusionHub`, không còn NETWORK trong speedo.
- [ ] `TripEngine` là facade ≤ 200 dòng; logic nằm trong `FixNormalizer`, `Odometer`, `SpeedPresenter`, `TripClock`.
- [ ] `:fermata:testAutoDebugUnitTest`, `:fermata:testMobileDebugUnitTest` xanh; `lintAutoDebug` 0 error.
- [ ] V1–V4 PASS trên DHU.
- [ ] Chưa commit gì.
