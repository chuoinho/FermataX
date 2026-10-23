# Plan: Thẻ speedo kết hợp + nguồn vị trí dùng chung (LocationHub)

> Dành cho agent thực thi, làm trên working tree hiện tại của `feat/aa-glass-theme`.
>
> Bối cảnh đọc trước:
> - `audit_reports/feat-aa-glass-theme/2026-10-22-speedo-refactor-report.md`, mục 5.1: Android 16 chặn FGS location khi app chỉ chạy qua AA. App khác chiếm màn hình xe thì FermataX bị đưa về trạng thái cached và mất quyền vị trí, đo được khoảng trống 61 s.
> - `2026-10-22-speedo-algorithm-audit.md` (SP-x), `2026-10-22-full-branch-audit.md` (F-4, F-10, F-11).

## 0. Quyết định đã chốt

| # | Quyết định |
|---|---|
| Q1 | **Thẻ speedo kết hợp.** Mặc định chỉ hiển thị **tốc độ**. **Thống kê chuyến đi** (distance / trip avg / time) là tuỳ chọn, chỉ hoạt động khi người dùng bật **và** cấp quyền vị trí "Luôn cho phép" (`ACCESS_BACKGROUND_LOCATION`). |
| Q2 | Một **nguồn vị trí dùng chung** (`LocationHub`) phục vụ speedo, thời tiết và CarFinder. |
| Q3 | **Thời tiết** cập nhật định kỳ **60 phút/lần** khi dùng AA. Toạ độ gửi đi làm tròn 2 chữ số thập phân. |

## 1. Ràng buộc

- **KHÔNG** commit, push, merge, rebase, stash hay reset. **KHÔNG** gỡ cài đặt app trên `15c36230` (dùng `adb install -r -d`).
- **KHÔNG** sửa `MainActivityDelegate.java`, `ControlPanelView.java`, `MediaSessionCallback.java`, `FermataMediaService.java`.
- Gradle: `ALLOW_LEGACY_TEST_SIGNING=true APP_ID_SFX=.test ./gradlew.bat <task> --offline -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true`. Phải truyền `-P…`; nếu không, các module con fail ở `processAutoDebugResources`.
- Ghi file bằng script phải giữ LF (`open(p,'wb')`). Python ở chế độ text trên Windows ghi CRLF.
- **Viết test trước** cho mọi thay đổi logic.

## 2. Kiến trúc đích

```
                    ┌──────────────────── LocationHub (process singleton, 1 HandlerThread) ───────────────────┐
LocationManager ──► │ 1 request: FUSED (API ≥ 31) hoặc GPS, 1 s, chỉ khi có ít nhất 1 consumer đang giữ         │
                    │ phát Location cho consumer; giữ "latest" + tuổi                                           │
                    └──────────┬─────────────────────────┬──────────────────────────┬───────────────────────────┘
                               ▼                         ▼                          ▼
                    GlassSpeedoTripController      CarLocatorManager         WeatherLocationPolicy
                    (tốc độ; + trip nếu bật)       (ring buffer đỗ xe)       (60 phút trên AA, làm tròn)

TripMode = OFF | ON
  ON  ⇔ người dùng bật "Thống kê chuyến đi" ∧ có ACCESS_BACKGROUND_LOCATION ∧ có FINE/COARSE
  OFF ⇒ thẻ chỉ có tốc độ; không TripTrackingService; không notification; không TripStore ghi
  ON  ⇒ TripTrackingService (FGS location) chạy suốt phiên AA, giữ hub + controller, kể cả khi app khác chiếm màn hình
```

Consumer giữ hub theo kiểu đếm tham chiếu (`acquire(tag)` / `release(tag)`). Hub chỉ đăng ký với `LocationManager` khi số consumer > 0.

---

## 3. Phần A: LocationHub (mục 2)

### A1. `LocationHub` (mới, `me.aap.fermata.location.LocationHub`)
- [ ] Singleton theo process, có `HandlerThread("LocationHub")` cố định (không quit), giống bài học vòng 2.
- [ ] **API:**
  - `acquire(String tag, Listener l)`, `release(String tag)`;
  - `@Nullable Location latest()`, `long latestAgeMs()`;
  - `@Nullable Location freshSystemLocation(long maxAgeMs)`: đọc `getLastKnownLocation` của FUSED/GPS, chỉ trả khi tuổi ≤ maxAge tính theo `elapsedRealtimeNanos`.
- [ ] **Một request duy nhất:** FUSED nếu API ≥ 31 và provider tồn tại, ngược lại GPS. 1 000 ms, 0 m. Không NETWORK, không PASSIVE.
- [ ] Listener được gọi trên thread của hub. Mỗi listener bọc `try/catch` để một consumer lỗi không làm hỏng các consumer khác.
- [ ] Theo dõi quyền và provider: mất quyền hoặc tắt Location thì gỡ đăng ký và báo trạng thái (`onAvailability(Availability)`: OK / NO_PERMISSION / LOCATION_OFF). Bật lại thì đăng ký lại.
- [ ] Watchdog: chỉ đăng ký lại khi chưa đăng ký được hoặc không có callback nào quá 60 s.
- [ ] **Test (Robolectric):**
  - hai consumer chỉ tạo 1 request;
  - release hết thì gỡ request;
  - một listener ném exception không chặn listener khác;
  - mất quyền thì báo NO_PERMISSION;
  - `freshSystemLocation` lọc đúng theo tuổi.

### A2. Speedo dùng hub
- [ ] `GlassSpeedoTripController`: bỏ `registerLocation()` / `unregisterLocation()` và các listener riêng. `start` gọi `hub.acquire("speedo", this::handleLocation)`, `stop` gọi `release`. Giữ cơ chế đếm số người giữ đã có.
- [ ] Các test controller đang kiểm `ShadowLocationManager` đổi sang kiểm hub có đúng 1 request.

### A3. CarFinder dùng hub (`CarLocatorManager`)
- [ ] Bỏ 4 listener GPS / NETWORK / FUSED / PASSIVE. Khi theo dõi khi lái (auto-save, phiên AA) thì gọi `hub.acquire("carfinder", …)` và nạp điểm vào `ParkingLocationSmoother` cùng Kalman như cũ (trên thread của hub, không phải main).
- [ ] Màn hình CarFinder đang mở cũng `acquire("carfinder-ui")`.
- [ ] **Lúc ngắt kết nối (tính điểm đỗ):** nếu điểm mới nhất trong bộ đệm cũ hơn 30 s (app vừa bị app khác chiếm màn hình), thử `hub.freshSystemLocation(30_000)`; có thì nạp vào bộ đệm trước khi tính.
  - Ghi log nguồn đã dùng: `buffer` / `system` / `stale`.
  - **Cần đo:** khi app ở nền và chỉ có quyền "trong khi dùng", `getLastKnownLocation` có trả về không. Ghi kết quả vào báo cáo. Nếu TripMode ON thì hub vẫn chạy nên không gặp vấn đề này.
- [ ] `queryBestKnownUserLocation`: bỏ kiểu trả `currentUserLocation` vô thời hạn (F-10). Ưu tiên `hub.latest()` nếu tuổi ≤ 2 phút, sau đó `freshSystemLocation(10 phút)`, cuối cùng mới tới phương án cũ.
- [ ] **Test:**
  - `ParkingLocationSmoother` nhận điểm từ hub;
  - bộ đệm cũ hơn 30 s thì dùng vị trí hệ thống;
  - bộ đệm mới thì không;
  - CarFinder và speedo cùng chạy vẫn chỉ có 1 request.

### A4. Thời tiết (`GlassDashboardController` + `GlassOpenMeteoClient`)
- [ ] Tách `WeatherLocationPolicy` (thuần Java, test được). Nó quyết định khi nào tải lại:
  - **Trên AA:** lần đầu khi dashboard hiển thị, sau đó **mỗi 60 phút**, theo `Handler` trên main thread, chỉ khi dashboard còn sống.
  - **Ngoài AA (điện thoại):** khi mở dashboard, nếu dữ liệu cũ hơn 60 phút.
- [ ] Nguồn toạ độ: `hub.latest()` nếu tuổi ≤ 5 phút, ngược lại `freshSystemLocation(30 phút)`. Không có thì `acquire("weather")` một lần, nhận 1 điểm rồi `release`, timeout 15 s. **Bỏ** phụ thuộc `CarLocatorManager`.
- [ ] Toạ độ gửi Open-Meteo: `%.2f` (F-11). Khoá cache gồm lat/lon đã làm tròn, đơn vị và thời gian; thời gian sống của cache là 60 phút.
- [ ] **Test:**
  - lịch 60 phút (đồng hồ giả);
  - không gọi lại khi cache còn hạn;
  - toạ độ trong URL có đúng 2 chữ số thập phân;
  - đổi đơn vị thì tải lại.

---

## 4. Phần B: quyền vị trí nền tuỳ chọn (sửa ngày 2026-10-23 theo kết quả T8)

### Bằng chứng từ kiểm thử thiết bị (Android 16, Android Auto 17.7)
- Khi FermataX hiển thị trên xe, tiến trình ở BTOP (state 3) và có quyền vị trí tiền cảnh (`curCapability` có **L**); hub nhận vị trí 1 Hz (T1, T2).
- Khi Google Maps chiếm màn hình xe, tiến trình xuống `curProcState=15`, **mất L**; listener của hub bị hạ xuống chu kỳ 10 phút và `(inactive)`; `getLastKnownLocation` cũng chỉ trả vị trí cũ. CarFinder lưu chỗ đỗ từ nguồn `stale` (T8).
- `TripTrackingService` (FGS loại `location`) bị Android **từ chối** khi app chỉ có quyền "trong khi dùng", kể cả lúc `APP_VISIBLE` và khi người dùng bấm trên màn hình xe (log `SecurityException … must be in the eligible state`).
- Hệ quả: chỉ khi có **`ACCESS_BACKGROUND_LOCATION`** thì service mới được phép chạy từ nền, giữ tiến trình ở mức foreground service và giữ GPS 1 Hz khi app khác chiếm màn hình. **Cần đo xác nhận** (H3, H4).

### Quyết định
Một tuỳ chọn duy nhất **"Ghi vị trí khi dùng app khác trên xe"** (mặc định tắt). Khi bật và đã cấp "Luôn cho phép":
- `TripTrackingService` chạy suốt phiên Android Auto và giữ hub. **Hai** tính năng cùng hưởng: thống kê chuyến đi (distance / avg / time) và vị trí đỗ xe của CarFinder (bộ đệm luôn mới).
- Khi tắt: thẻ speedo chỉ hiện tốc độ; không khởi động service (không còn log `SecurityException` mỗi lần kết nối); CarFinder giữ cơ chế dự phòng hiện có (`buffer` / `system` / `stale`).

### B1. `TripModePolicy` (mới, `ui.glass.speedo`)
- [ ] Hàm thuần `evaluate(userEnabled, hasForeground, hasBackground, supportedInBuild)` → `OFF | ON | NEEDS_LOCATION_PERMISSION | NEEDS_BACKGROUND_PERMISSION`. Dưới API 29 không có quyền nền riêng: coi như đã có.
- [ ] `current(Context)`: đọc tuỳ chọn (lưu trong `TripStore`, pref `trip_stats_enabled`), quyền thật và cờ build. Có `isEnabled` / `setEnabled` công khai cho màn hình cài đặt.
- [ ] Test thuần cho mọi tổ hợp.

### B2. `TripTrackingService` chỉ chạy khi ON
- [ ] `TripTrackingService.start()` trả về ngay nếu `TripModePolicy.current(ctx) != ON`. Tất cả đường khởi động (AutoBinder, toggle trip, chạm thẻ) đều đi qua đây.
- [ ] `TripTrackingAutoBinder`: khởi động ở `CONNECTED` **hoặc** `APP_VISIBLE` (có quyền nền thì không cần đợi xe hiển thị app).
- [ ] Không còn trạng thái `BACKGROUND_BLOCKED` khi OFF. Nếu ON mà vẫn bị từ chối (bất thường) thì giữ cách báo hiện tại.
- [ ] Tắt tuỳ chọn trong cài đặt: gọi `TripTrackingService.stop()`.

### B3. Thẻ speedo
- [ ] `GlassSpeedoTripView.setTripMode(mode)`:
  - **ON**: giữ nguyên giao diện hiện tại.
  - **Khác ON**: ẩn hàng thống kê, nút Pause/Resume và chạm giữ để reset; thay bằng một dòng gợi ý: "Bật trong Cài đặt trên điện thoại để ghi chuyến đi" (OFF), "Cần quyền vị trí 'Luôn cho phép'" (NEEDS_BACKGROUND_PERMISSION), "Cần quyền vị trí" (NEEDS_LOCATION_PERMISSION).
- [ ] `GlassDashboardController` gọi `setTripMode(TripModePolicy.current(ctx))` ở `start()` và `onHostResumed()`.
- [ ] **Đơn giản hoá so với bản trước**: bỏ cờ `recordTrip` (bộ đo vẫn chạy khi dashboard hiển thị để có tốc độ; tổng chỉ là số liệu, không có toạ độ, nên lưu vẫn an toàn). Bỏ notification "mở cài đặt" từ xe sang điện thoại: xe chỉ hiện dòng gợi ý. Không phóng to phần tốc độ.

### B4. Luồng bật trên điện thoại
- [ ] **Chỗ bật:** `InterfacePrefsBuilder`, khi theme Glass đang dùng, thêm một nút "Ghi vị trí khi dùng app khác trên xe". Phụ đề cho biết trạng thái hiện tại (`TripModePolicy.current`).
- [ ] **Bật:**
  1. Hiện **hộp thoại công bố rõ ràng** (Play bắt buộc), nói rõ: thu thập vị trí cả khi chạy nền, chỉ trong phiên Android Auto, để tính thống kê chuyến đi và lưu chỗ đỗ xe; không chia sẻ ra ngoài; tắt được bất cứ lúc nào.
  2. Đồng ý: xin FINE/COARSE nếu chưa có (`checkPermissions`), rồi xin `ACCESS_BACKGROUND_LOCATION` (API 30+ hệ thống mở trang "Luôn cho phép").
  3. Chỉ lưu `enabled = true` khi đã có quyền nền. Bị từ chối thì báo ngắn và giữ tắt.
- [ ] **Tắt:** lưu `enabled = false`, dừng service, giữ tổng đã lưu.
- [ ] **Chuỗi:** tiêu đề, phụ đề trạng thái, hộp thoại công bố, các dòng gợi ý trên thẻ, thông báo bị từ chối. Đủ **6 ngôn ngữ**.

### B5. Khai báo quyền và cờ build
- [ ] Main manifest khai báo `ACCESS_BACKGROUND_LOCATION`.
- [ ] Gradle property `-PNO_BACKGROUND_LOCATION=true`: thêm manifest phụ với `tools:node="remove"` cho quyền này và `BuildConfig.TRIP_BACKGROUND_SUPPORTED = false` (khi đó `TripModePolicy` luôn OFF). Dùng để phát hành bản không có quyền nền nếu Play từ chối, không cần sửa code.
- [ ] Test chính sách manifest cho trường hợp mặc định.

## 5. Kiểm thử trên DHU với máy `15c36230`

**Chuẩn bị:**
- DHU chạy với console đọc lệnh từ file: `tail -f cmds.txt | desktop-head-unit.exe -c config/default_720p.ini`; lệnh `tap x y`, `screenshot <file>`.
- Icon FermataX trên thanh bên trái của xe: `tap 40 462`. Google Maps: `tap 40 258`.

**Cấp và thu hồi quyền nền bằng adb** (chỉ để kiểm thử; luồng giao diện thật kiểm ở H1):
```bat
adb -s 15c36230 shell pm grant me.app.fermataX.auto.test.debug android.permission.ACCESS_BACKGROUND_LOCATION
adb -s 15c36230 shell pm revoke me.app.fermataX.auto.test.debug android.permission.ACCESS_BACKGROUND_LOCATION
```

| ID | Kịch bản | Kỳ vọng / bằng chứng |
|---|---|---|
| H1 | Luồng bật trên điện thoại: công tắc → hộp thoại công bố → "Luôn cho phép" | Ảnh từng bước; `dumpsys package` có `ACCESS_BACKGROUND_LOCATION: granted=true` |
| H2 | TripMode OFF trên xe | Thẻ chỉ có tốc độ và dòng "Bật thống kê chuyến đi"; không có TripTrackingService; `dumpsys location` có **đúng 1** request của app |
| H3 | TripMode ON, kết nối AA | `TripTrackingService` foreground `types=0x8`; log **không** có "can not have location"; thẻ có thống kê |
| H4 | TripMode ON, mở Maps trên xe 3 phút rồi quay lại | `dumpsys activity processes`: procState ≤ 4 và capability có **L** trong lúc Maps hiển thị; log trip **không** có khoảng trống > 2 s |
| H5 | TripMode OFF, mở Maps 60 s | Đúng 1 khoảng trống (chấp nhận được); quay lại thì tốc độ hiện trong ≤ 3 s |
| H6 | Thu hồi quyền nền khi đang ON | Thẻ về chế độ chỉ tốc độ kèm gợi ý; tổng đã lưu còn nguyên khi cấp lại quyền |
| H7 | Thời tiết trên AA | Gọi lần đầu khi dashboard hiện; sau đó mỗi 60 phút (log có thời điểm). URL có lat/lon 2 chữ số thập phân. Có thể dùng hằng số debug 2 phút để kiểm nhanh, phải ghi rõ trong báo cáo |
| H8 | CarFinder: auto-save bật, mở Maps, rút AA (thoát DHU) | Log ghi nguồn điểm đỗ (`buffer` / `system` / `stale`) và kết quả `getLastKnownLocation` khi app ở nền |
| H9 | Số request vị trí | Trong phiên AA có speedo, CarFinder và thời tiết cùng chạy: `dumpsys location` có **1** request của app |

Không làm được kịch bản nào thì ghi **KHÔNG CHẠY ĐƯỢC** kèm lý do, không ghi PASS.

### Kết quả đo 2026-10-23 (máy 15c36230, Android 16 / SDK 36, DHU, đứng yên trong nhà)
- H1 PASS: công tắc → hộp thoại công bố → trang hệ thống → "Luôn cho phép"; `ACCESS_BACKGROUND_LOCATION: granted=true`, `trip_stats_enabled=true`.
- H2 PASS: chế độ tắt, thẻ chỉ có tốc độ + gợi ý, không có TripTrackingService.
- H3 **một phần**: lúc CONNECTED (app cached, `uidState: LAST`) Android **vẫn từ chối** FGS dù có quyền nền (`ForegroundServiceStartNotAllowedException`, đã bắt lỗi). Quyền nền không phải ngoại lệ khởi động FGS. Service chỉ khởi động khi mở FermataX trên xe (APP_VISIBLE, BTOP); log có "can not have location access" (không có while-in-use) nhưng nhờ quyền nền vẫn nhận vị trí. Hệ quả: chưa mở FermataX trên xe thì chưa ghi chuyến.
- H4 PASS (bằng chứng gián tiếp): Maps chiếm màn hình 3 phút, procState 4, request vẫn `@+1s` và vẫn nhận vị trí cùng nhịp lúc app hiển thị (30 s do đứng yên). T8 trước đó bị bóp còn 10 phút. Chưa đo khi chạy xe thật.
- H8 PASS: rút AA, nguồn điểm đỗ là `system` (T8: `stale`); conf thấp do sai số 100 m trong nhà.
- H5, H6, H7, H9: KHÔNG CHẠY lần này.

## 6. Tài liệu cho Google Play (nếu phát hành qua Play)
- [ ] Soạn nháp trong `docs/play/background-location-declaration.md`:
  - mô tả tính năng;
  - lý do cần quyền nền (khi đang chạy Android Auto, app khác như Maps chiếm màn hình thì Android ngừng cấp vị trí cho FermataX);
  - kịch bản video 30 s.
- [ ] Cập nhật mục Data safety: vị trí chính xác, dùng cho chức năng của app, không chia sẻ.
- [ ] Ghi chú: nếu bị từ chối, build lại với `-PNO_BACKGROUND_LOCATION=true` (B6).

## 7. Báo cáo
`audit_reports/feat-aa-glass-theme/2026-10-2x-speedo-hybrid-locationhub-report.md`:
- danh sách file đã đổi;
- test mới;
- kết quả Gradle (số test, lint);
- bảng H1–H9 kèm bằng chứng;
- các điểm "cần đo" ở A3 và B4 đã có kết quả gì.

## 8. Thứ tự làm
1. **A1 → A2 → A3 → A4.** Hub trước để mọi thứ dùng chung. Chạy test sau mỗi bước.
2. **B1 → B2 → B3 → B4 → B5 → B6.**
3. Toàn bộ test và lint, build, cài, H1–H9, báo cáo.

## 9. Định nghĩa hoàn thành
- [ ] Trong phiên AA, app chỉ có **1** request vị trí.
- [ ] Thời tiết 60 phút/lần trên AA, toạ độ làm tròn, không còn phụ thuộc CarFinder.
- [ ] CarFinder dùng hub và có phương án dự phòng bằng vị trí hệ thống khi đỗ.
- [ ] TripMode OFF: thẻ chỉ có tốc độ, không service, không ghi trip.
- [ ] TripMode ON (có quyền nền): ghi liên tục kể cả khi Maps chiếm màn hình (H4 PASS).
- [ ] Luồng xin quyền có hộp thoại công bố, đủ 6 ngôn ngữ.
- [ ] Cờ build bỏ được quyền nền.
- [ ] Test xanh, lint 0 error, chưa commit.
