# Dual Sensor Fusion Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai kiến trúc Dual Sensor Fusion Hub phân lập cho Speedometer (`SpeedoKinematicFusionHub`) và Find Car (`ParkingLocationSmoother` MLE Clustering & Ramp Anchor), giải quyết triệt để vấn đề giằng xé đa nguồn định vị và nâng cao độ chính xác điểm đỗ xe.

**Architecture:** 
1. `SpeedoKinematicFusionHub` đặt giữa các Android LocationListeners và `TripEngine`, thực hiện gom nhịp 1.0s Epoch, tính tọa độ theo Inverse-Variance Weighting ($1/\sigma^2$) và chọn vận tốc Doppler kết hợp ZUPT (Zero Velocity Update).
2. `ParkingLocationSmoother` nâng cấp thuật toán gom cụm Maximum Likelihood Centroid cho đỗ xe ngoài trời và thuật toán chốt cửa dốc hầm (Last Surface Ramp Anchor) khi đỗ xe trong hầm ngầm.

**Tech Stack:** Java 17, Android Location API, JUnit 4, Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-21-sensor-fusion-hub-design.md`

## Global Constraints
- `MainActivityDelegate.java` số dòng không trống $\le 1291$ (hiện tại 1270).
- Không thực hiện lệnh `git commit` hay `git merge`.
- Không gỡ cài đặt app trên thiết bị thử nghiệm (luôn dùng `adb install -r -d`).
- Chạy toàn bộ unit test với `set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest`.

## Review Focus
1. Bất định phương sai mẫu cực đoan: Mẫu GPS $\sigma = 3\text{m}$ kết hợp mẫu trạm BTS $\sigma = 300\text{m} \implies$ trọng số GPS phải chiếm $> 99.9\%$, không bị lệch điểm.
2. Vận tốc rỗng (`hasSpeed() == false`): Trạm BTS 4G không được phép làm rớt tốc độ về `-1` hay dấu `—`.
3. ZUPT khi dừng đèn đỏ: Khi gia tốc kế báo xe đứng yên, tốc độ bắt buộc bằng $0\text{ km/h}$.
4. Xe chui xuống hầm: Tọa độ điểm đỗ chốt ngay tại cửa dốc hầm trước khi mất sóng vệ tinh, cấm dùng tọa độ trạm BTS 4G cách xa 500m.
5. Xe đỗ ngoài trời: Cụm điểm dừng 10s cuối triệt tiêu nhiễu đa đường (multipath), đưa bán kính sai số về $\le 2\text{m}$.

---

### Task 1: Xây dựng `SpeedoKinematicFusionHub` & Unit Tests

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/SpeedoKinematicFusionHub.java`
- Create: `fermata/src/test/java/me/aap/fermata/ui/glass/speedo/SpeedoKinematicFusionHubTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class SpeedoKinematicFusionHub {
      public synchronized void addSample(Location location, long now);
      @Nullable
      public synchronized TripEngine.Fix fuseAndDrain(long now, boolean isStationary);
      public synchronized void reset();
  }
  ```

- [ ] **Step 1: Viết test failing trong `SpeedoKinematicFusionHubTest.java`**
  - Test 1: `fusesMultipleCoordinatesByInverseVariance`: nạp 1 mẫu GPS ($\sigma = 5$) và 1 mẫu BTS ($\sigma = 150$), tọa độ hòa trộn lệch không quá $0.05\text{m}$ so với GPS.
  - Test 2: `dopplerSpeedTakesPriorityOverMissingSpeed`: mẫu BTS không có tốc độ không làm mất tốc độ của GPS.
  - Test 3: `zuptForcesZeroVelocity`: khi `isStationary == true`, tốc độ đầu ra luôn là $0.0\text{ m/s}$.

- [ ] **Step 2: Chạy test kiểm tra xem test có fail (do chưa có class) không**
  - Run: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.ui.glass.speedo.SpeedoKinematicFusionHubTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
  - Expected: FAIL (Compilation error: cannot find symbol `SpeedoKinematicFusionHub`).

- [ ] **Step 3: Triển khai mã nguồn `SpeedoKinematicFusionHub.java`**
  - Thuật toán Epoch 1.0s, lưu danh sách mẫu trong chu kỳ.
  - Hàm `fuseAndDrain(long now, boolean isStationary)`:
    - Tính trọng số $w_i = \frac{1}{\max(\sigma_i, 1.0)^2}$, tính trung bình có trọng số cho latitude, longitude, tính $\sigma_{fused}$.
    - Tìm vận tốc Doppler từ các mẫu có `hasSpeed()`, hoặc ép $0.0\text{ m/s}$ nếu `isStationary`.
    - Trả về đối tượng `TripEngine.Fix` hoàn chỉnh.

- [ ] **Step 4: Chạy test xác nhận PASS**
  - Run: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.ui.glass.speedo.SpeedoKinematicFusionHubTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
  - Expected: PASS 100%.

---

### Task 2: Tích hợp `SpeedoKinematicFusionHub` vào `GlassSpeedoTripController`

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripControllerTest.java`

**Interfaces:**
- `GlassSpeedoTripController` khởi tạo `SpeedoKinematicFusionHub fusionHub = new SpeedoKinematicFusionHub()`.
- Các `LocationListener` chuyển toàn bộ mẫu vào `fusionHub.addSample(location, nowTime)`.
- Nhịp `heartbeat` hoặc timer chu kỳ gọi `fusionHub.fuseAndDrain(nowTime, stationary)` rồi chuyển cho `engine.accept(fusedFix, nowTime)`.

- [ ] **Step 1: Cập nhật `GlassSpeedoTripController.java`**
  - Thay thế việc gọi trực tiếp `engine.accept(fix)` trong từng callback phân tán bằng cơ chế nạp mẫu vào `fusionHub`.
  - Trong nhịp `heartbeat` (1Hz) và khi có fix mới: kích hoạt `fuseAndDrain` và nạp vào `TripEngine`.
  - Xóa bỏ các biến cờ rườm rà không cần thiết.

- [ ] **Step 2: Chạy unit test kiểm tra toàn bộ phân hệ Speedo**
  - Run: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.ui.glass.speedo.* -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
  - Expected: PASS toàn bộ các bài test `TripEngineTest`, `SpeedoKinematicFusionHubTest`, `GlassSpeedoTripControllerTest`.

---

### Task 3: Nâng cấp `ParkingLocationSmoother` với MLE Clustering & Ramp Anchor

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/carfinder/ParkingLocationSmoother.java`
- Modify: `fermata/src/main/java/me/aap/fermata/carfinder/CarLocatorManager.java`
- Test: `fermata/src/test/java/me/aap/fermata/carfinder/ParkingLocationSmootherTest.java`

- [ ] **Step 1: Viết test failing trong `ParkingLocationSmootherTest.java`**
  - Test 1: `outdoorStationaryClusteringYieldsHighPrecision`: Nạp 5 mẫu GPS dừng tĩnh tại T0 có nhiễu Gaussian nhẹ $\implies$ kết quả ước lượng hội tụ về trọng tâm với accuracy cải thiện hơn.
  - Test 2: `basementOutageLocksToRampEntrance`: Xe có tín hiệu GPS tốt ngoài trời, sau đó tín hiệu mất hẳn hoặc vọt lên $> 60\text{m}$ (kèm mẫu BTS trạm xa) $\implies$ kết quả chốt tại điểm GPS mặt đất cuối cùng trước cửa hầm, loại bỏ hoàn toàn mẫu BTS xa.

- [ ] **Step 2: Chạy test kiểm tra FAIL**
  - Run: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.carfinder.ParkingLocationSmootherTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`

- [ ] **Step 3: Triển khai thuật toán trong `ParkingLocationSmoother.java`**
  - Cập nhật `estimateParkedLocation`:
    - Phân tách ngưỡng hầm ngầm: Nhận diện thời điểm tín hiệu vệ tinh bắt đầu sụp giảm $\implies$ chốt điểm Last Surface Ramp Anchor.
    - Đối với ngoài trời: Chạy thuật toán MLE Weighted Centroid trên các mẫu $v \le 1.5\text{ m/s}$ trong 15s cuối.

- [ ] **Step 4: Chạy test xác nhận PASS**
  - Run: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.carfinder.ParkingLocationSmootherTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
  - Expected: PASS 100%.

---

### Task 4: Kiểm thử toàn diện, Đóng gói v325 và Cài đặt thực tế

- [ ] **Step 1: Kiểm tra ràng buộc hệ thống**
  - Kiểm tra số dòng `MainActivityDelegate.java`: `(Get-Content ...).Count <= 1291`.
  - Chạy toàn bộ test suite dự án: `:fermata:testAutoDebugUnitTest`.

- [ ] **Step 2: Nâng version code lên v325**
  - Sửa `gradle/libs.versions.toml`: `appVersionCode = "325"`.

- [ ] **Step 3: Đóng gói Release AAB & Universal APK**
  - Command: `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat verifyWebOnlyProductionGraph :fermata:bundleAutoRelease :fermata:packageAutoReleaseUniversalApk -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
  - Copy file ra `dist/FermataX-v325-auto-test.aab` và `dist/FermataX-v325-auto-test-universal.apk`.

- [ ] **Step 4: Cài đặt và Xác thực trực tiếp trên thiết bị `15c36230`**
  - Run: `adb -s 15c36230 install -r -d dist\FermataX-v325-auto-test-universal.apk`.
  - Xác thực `versionCode=325` qua `dumpsys package`.
  - Kiểm tra logcat `FermataTrip` và `CarLocator` hoạt động mượt mà.
