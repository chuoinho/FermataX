# Phone Apps không root Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phone Apps chạy trên máy không root sau một lần thiết lập duy nhất; từ đó FermataX tự khởi động server, kể cả sau reboot và sau khi cập nhật app, mà không hỏi người dùng cấp quyền nào.

**Architecture:** Server, AIDL và giao diện xe giữ nguyên. `ServerStarter` chọn root hoặc ADB; ADB nội bộ kết nối ngắn, chạy đúng lệnh `ServerCommand.build(...)` rồi đóng. Một job duy nhất lo việc tự phục hồi.

**Tech Stack:** Java, libadb-android ghim đúng phiên bản (JitPack có lọc group), `JobScheduler`, Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-25-virtual-car-display-design.md` mục 4.2, 4.5, 4.6; `docs/superpowers/specs/2026-11-03-car-apps-phase2-comparison-and-plan.md` bước B, C, D; `docs/superpowers/specs/2026-11-05-car-apps-unified-user-flow-design.md` mục 6.

## Global Constraints

- **Không có hộp thoại xin quyền hay trang cấp quyền mới.** Chỉ thêm `RECEIVE_BOOT_COMPLETED` (hệ thống tự cấp khi cài). `WRITE_SECURE_SETTINGS` do app tự cấp qua ADB. Không gọi xin `POST_NOTIFICATIONS`, overlay, pin, hay vị trí cho tính năng này.
- Người dùng chỉ làm một lần, và chỉ những gì Android bắt buộc: bật Developer Options, bật Wireless debugging, nhập mã 6 số.
- Phạm vi người dùng chốt ngày 2026-11-09: root Android 11–17 (SDK 30–37), vẫn loại 12L (SDK 32) qua `CarAppsFeature.isBlockedSdk`; không root chỉ Android 13–17 (SDK 33–37).
- Chỉ hiện Phone Apps và cài đặt liên quan khi được hỗ trợ. Theo chỉ đạo UI mới của người dùng, SDK 30–31 không root phải ẩn các mục này, thay cho thông báo "cần root" kèm lý do. SDK 32 vẫn ẩn; root AVAILABLE/GRANTED giữ hành vi hiện có. Không thêm hộp thoại hay trang xin quyền.
- Không Shizuku. Không mirror. Không chế độ TCP 5555. Không giữ phiên ADB mở.
- Đường root giữ nguyên hành vi: mọi unit test hiện có vẫn đạt.
- Khóa ADB chỉ nằm trong thư mục riêng của app, chỉ kết nối `127.0.0.1`; không ghi khóa hay mã ghép đôi vào log và diagnostics.
- Tên hiển thị: "Phone Apps". Tên trong code và key cài đặt giữ `carapps`.
- Chuyển mã từ CarStream được người dùng xác nhận có ủy quyền; viết lại theo logic, giữ thông báo giấy phép của libadb-android.
- Làm trên nhánh riêng sau khi phát hành giai đoạn 1. Không push, không merge vào main, không tag. Trailer commit: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Emulator chỉ dưới WHPX, mỗi lần một cái, mọi lệnh adb có `-s`.

## Review Focus

1. Thông báo của app đang bị tắt: vẫn ghép đôi được, không xin quyền. Test ở Task 4.
2. App tự cập nhật: server khớp phiên bản mới được khởi động lại khi có Wi‑Fi. Test ở Task 5.
3. App bị kill sau khi tự bật `adb_wifi_enabled`: lần chạy sau trả lại giá trị cũ. Test ở Task 5.
4. Job và phiên xe cùng khởi động server: chỉ một lệnh được chạy. Test ở Task 3.
5. Người dùng thu hồi ủy quyền ADB: về "chưa thiết lập", không kẹt "Starting". Test ở Task 2.

---

### Task 1: Đo Android 11–12 và chốt phạm vi

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/carapps/CarAppsFeature.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/carapps/CarAppsReadinessModel.java`, `fermata/src/main/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilder.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/carapps/AdbSupportTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/carapps/CarAppsReadinessModelTest.java`, `fermata/src/test/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilderTest.java`, `fermata/src/test/java/me/aap/fermata/ui/carapps/CarAppsFeatureTest.java`

**Interfaces:**
- Produces: `static boolean CarAppsFeature.adbSupportedSdk(int sdk)`.

- [ ] **Step 1: Đo trên AA_API30, AA_API31.** Chạy lệnh của `ServerCommand.build` bằng `adb -s emulator-5554 shell`, Magisk từ chối app. Qua DHU: mở Chrome từ drawer, chạm link mở activity mới, bàn phím. Đạt khi activity mới ở lại display xe (`dumpsys activity activities`). Ghi vào `.scratchpad/phase2/sdk11-12.md`.
- [ ] **Step 2: Viết test.** `assertTrue(adbSupportedSdk(33))` đến 37; `assertFalse` cho 29 và 32; 30, 31 theo Step 1.
- [ ] **Step 3: Chạy, mong đợi FAIL:** `./gradlew.bat -q :fermata:testAutoDebugUnitTest --tests '*AdbSupportTest'`.
- [ ] **Step 4: Cài hàm.** Người dùng đã thay yêu cầu "cần root" kèm lý do bằng **ẩn mục Phone Apps và cài đặt liên quan khi không được hỗ trợ**. SDK 30–31 ABSENT: ẩn tile/Readiness và không có action; AVAILABLE/GRANTED: giữ hành vi root hiện có. SDK 32 vẫn bị chặn; SDK 33+ chưa mở luồng ADB trong Task 1. Task 3 chỉ mở ABSENT 33–37 để thiết lập ADB, tiếp tục ẩn ABSENT 30–31 và SDK 32. Không thêm thông báo, hộp thoại hay trang cấp quyền.
- [ ] **Step 5: Chạy, mong đợi PASS, commit.**

### Task 2: ADB nội bộ và cổng go/no-go

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/carapps/adb/LocalAdb.java`
- Modify: `build.gradle` (JitPack với `content { includeGroup 'com.github.MuntashirAkon.spake2-java' }`), `gradle/libs.versions.toml`, `fermata/build.gradle`, `proguard-rules.pro`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/carapps/adb/LocalAdbTest.java`

**Controller compatibility correction (2026-11-09):** libadb 3.1.1 declares `boolean pair(int,String)`, so Java cannot also inherit the required `FutureSupplier<Void> pair(int,String)`. Composition preserves the public asynchronous API. Stock 3.1.1 has unbounded socket operations and identity-bearing TLS context caching; use its pinned original 19-file source in `depends/libadb` with four scoped lifecycle/identity patches (AbsAdbConnectionManager, AdbConnection, PairingConnectionCtx, SslUtils). Keep this module Auto-only. JitPack is filtered to the exact published transitive group `com.github.MuntashirAkon.spake2-java`; do not add an unused binary libadb dependency or repository group. Reuse existing Bouncy Castle jdk18on 1.84 rather than adding duplicate jdk15to18 1.81 classes. Ship preserved license texts, attribution, patch provenance, and corresponding source/relink instructions.

**Interfaces:**
- Produces: `class LocalAdb` wraps a private `AbsAdbConnectionManager` subclass, khóa RSA 2048 tạo một lần ở `getFilesDir()/adb/`, và:
  - `FutureSupplier<Void> pair(int port, String code)`
  - `FutureSupplier<Void> runAndClose(int port, String command)`: kết nối, chạy `shell:` + lệnh, đóng
  - `boolean hasKey()`; `void forgetKey()`

  Mạng chạy ngoài main thread, timeout 15 s. Lỗi xác thực ném `AdbPairingRequiredException`.

- [x] **Step 1: Chọn phiên bản, đọc LICENSE, ghi vào phần giấy phép của app.**
- [x] **Step 2: Viết test** với connection giả: khóa giữ nguyên qua hai instance; host luôn `127.0.0.1`; `runAndClose` đóng kết nối cả khi lỗi; `AdbPairingRequiredException` được báo cho caller.
- [x] **Step 3: Chạy, mong đợi FAIL. Cài. Chạy, mong đợi PASS.**
- [ ] **Step 4: Spike go/no-go trên máy thật Android 16 không root**, gọi `LocalAdb` từ mã debug tạm, không commit mã này. Ghi vào `.scratchpad/phase2/spike.md`:
  1. Ghép đôi khi Settings và FermataX chia đôi màn hình: mã nhập được, Settings không đóng hộp thoại.
  2. Tìm cổng bằng mDNS, `runAndClose` chạy server, log `started as uid 2000`.
  3. Đóng phiên ADB, tắt Wireless debugging, tắt Wi‑Fi: server vẫn sống.
  4. `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` thành công; tự bật `adb_wifi_enabled` không hiện hộp thoại trên Wi‑Fi đã tin cậy.
  5. Build release: `io.github.muntashirakon.**` không bị R8 làm hỏng.

  **Mục 2 hoặc 3 không đạt: dừng và báo người dùng trước khi sang Task 3.**
- [ ] **Step 5: Commit `LocalAdb` và test.**

### Task 3: Tách đường khởi động và mở cổng tính năng

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/carapps/ServerStarter.java`
- Modify: `CarDisplayClient.java` (`startServer`), `AutoStartPolicy.java`, `CarAppsFragment.java` (`onRootChanged`), `CarAppsUiState.java` trong `fermata/src/auto/java/me/app/fermatax/auto/carapps/`; `CarAppsFeature.java`, `CarAppsReadinessModel.java`, `CarAppsChecks.java` trong `fermata/src/main/java/me/aap/fermata/ui/carapps/`; `fermata/src/main/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilder.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/carapps/ServerStarterTest.java`

**Interfaces:**
- Consumes: `CarAppsFeature.adbSupportedSdk`, `LocalAdb.runAndClose`.
- Produces: `enum ServerStarter.Channel { ROOT, ADB, NONE }`; `static Channel ServerStarter.choose(RootState root, boolean adbPaired, int sdk)`; `static boolean CarAppsFeature.canRun()`; `static void CarAppsFeature.setAdbPaired(boolean)`; `static boolean CarAppsFeature.isAdbPaired()`. Mọi chỗ đang so `RootState.GRANTED` để quyết định chạy đổi sang `canRun()`. Mục Phone Apps hiện trên mọi SDK được hỗ trợ, kể cả chưa thiết lập.

- [ ] **Step 1: Viết test.**

```java
assertEquals(Channel.ROOT, choose(RootState.GRANTED, true, 36));
assertEquals(Channel.ADB, choose(RootState.ABSENT, true, 36));
assertEquals(Channel.ADB, choose(RootState.AVAILABLE, true, 36));
assertEquals(Channel.NONE, choose(RootState.ABSENT, false, 36));
assertEquals(Channel.NONE, choose(RootState.ABSENT, true, 32));
```

Thêm ở `CarDisplayClientTest`: kênh ADB không gọi `su`; kênh ROOT sinh lệnh như hiện tại; hai lần `ensureRunning` đồng thời chỉ chạy một lệnh; `AdbPairingRequiredException` làm `isAdbPaired()` thành false và state thành `FAILED`.
- [ ] **Step 2: Chạy, mong đợi FAIL.**
- [ ] **Step 3: Cài.** Nhánh ROOT chuyển nguyên khối `Su.get()` hiện có, gồm `RootProbe.recordGranted(ctx, false)` khi lỗi. `onRootChanged` cũng chạy khi `setAdbPaired` đổi.
- [ ] **Step 4: Chạy toàn bộ `testAutoDebugUnitTest`, mong đợi PASS, commit.**

### Task 4: Thiết lập một lần

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/carapps/adb/AdbPairing.java`, `AdbSetupActivity.java`
- Modify: `fermata/src/auto/AndroidManifest.xml`, `ReadinessPrefsBuilder.java`, `CarAppsReadinessModel.java`, `car_apps_strings.xml` (9 ngôn ngữ)
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/carapps/adb/AdbPairingTest.java`

**Interfaces:**
- Consumes: `LocalAdb`, `CarAppsFeature.setAdbPaired`, `AdbRecoveryJob.schedule` (Task 5; gọi qua hook đến khi Task 5 xong).
- Produces: `static void AdbPairing.start(Context)`; `static boolean AdbPairing.validCode(String)`.

- [ ] **Step 1: Viết test:** `validCode` nhận `"123456"`, từ chối `"12345"`, `"12a456"`, null; mã sai hiện lỗi và cho nhập lại; thông báo tắt thì không gọi `requestPermissions`; ghép xong gọi `pm grant` đúng một lần, `setAdbPaired(true)`, xếp job; `pm grant` thất bại vẫn coi là ghép xong.
- [ ] **Step 2: Chạy, mong đợi FAIL.**
- [ ] **Step 3: Cài luồng.** Một nút "Set up without root" trong Readiness. `AdbSetupActivity` hiện ô nhập mã và mở trang Wireless debugging cạnh nó bằng `FLAG_ACTIVITY_LAUNCH_ADJACENT`; tự tìm cổng `_adb-tls-pairing._tcp`; ghép xong tự cấp quyền, chạy server, báo "Xong". Developer Options chưa bật: một màn hướng dẫn và nút mở trang Giới thiệu điện thoại, tự sang bước sau khi phát hiện đã bật.
- [ ] **Step 4: Chọn đường theo spike mục 1.** Chia đôi màn hình đạt: chỉ giữ đường này. Không đạt: dùng thông báo `RemoteInput` khi `areNotificationsEnabled()`, thông báo tắt thì chỉ dẫn bật trong Settings như một lựa chọn, không xin quyền.
- [ ] **Step 5: Chạy test; trên máy thật từ đầu đến "Xong" dưới 1 phút; commit.**

### Task 5: Tự phục hồi

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/carapps/adb/AdbRecoveryReceiver.java`, `AdbRecoveryJob.java`, `AdbRecoveryPolicy.java`
- Modify: `fermata/src/auto/AndroidManifest.xml` (`RECEIVE_BOOT_COMPLETED`, receiver cho `BOOT_COMPLETED` và `MY_PACKAGE_REPLACED`, job `BIND_JOB_SERVICE`), `CarDisplayClient.java`, `CarAppsUiState.java`, `car_apps_strings.xml`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/carapps/adb/AdbRecoveryPolicyTest.java`

**Interfaces:**
- Consumes: `CarDisplayClient.ensureRunning`, `ServerStarter.choose`.
- Produces: `static void AdbRecoveryJob.schedule(Context)`: job một lần, cùng ID, điều kiện `NETWORK_TYPE_UNMETERED`; `enum AdbRecoveryPolicy.Action { NOTHING, WAIT_WIFI, RESTORE_ADB_SETTING, ENABLE_ADB, CONNECT }`; `static Action next(boolean paired, boolean serverRunning, boolean wifi, boolean adbWifiOn, boolean canWriteSecure, boolean restorePending)`; `static boolean adbRunning(int sdk, String adbEnabled, String initSvcAdbd)`, SDK 37 đọc `init.svc.adbd`.

- [ ] **Step 1: Viết test:**

```java
assertEquals(NOTHING, next(false,false,true,false,true,false));
assertEquals(RESTORE_ADB_SETTING, next(true,true,true,true,true,true));
assertEquals(NOTHING, next(true,true,true,true,true,false));
assertEquals(WAIT_WIFI, next(true,false,false,false,true,false));
assertEquals(WAIT_WIFI, next(true,false,true,false,false,false));
assertEquals(ENABLE_ADB, next(true,false,true,false,true,false));
assertEquals(CONNECT, next(true,false,true,true,true,false));
```

Job: ghi marker `restorePending` **trước** khi bật `adb_wifi_enabled`, xóa sau khi trả lại; lỗi kết nối vẫn trả lại giá trị cũ; thử tối đa 3 lần với backoff hệ thống. Receiver: `BOOT_COMPLETED` và `MY_PACKAGE_REPLACED` đều gọi `schedule`, chỉ khi `isAdbPaired()`.
- [ ] **Step 2: Chạy, mong đợi FAIL.**
- [ ] **Step 3: Cài theo spec 4.5 bước 2–5.** Tìm cổng `_adb-tls-connect._tcp`. Máy root không xếp job. Mở Phone Apps khi server chưa chạy và có Wi‑Fi: thử ngay, không chờ job. Không có Wi‑Fi: xe báo "Kết nối Wi‑Fi một lần để khởi động".
- [ ] **Step 4: Chạy test. Trên máy thật Android 16: (a) reboot, có Wi‑Fi nhà, không chạm điện thoại, sau đó tắt Wi‑Fi, mở Phone Apps thì drawer hiện; (b) cài đè bản mới `versionCode` lớn hơn, kết quả tương tự. Commit.**

### Task 6: Kiểm chứng Android 11–17

**Files:**
- Modify: `docs/car-apps-real-device-test.md` (thêm kịch bản không root)
- Create: `.scratchpad/phase2/matrix.md`

- [ ] **Step 1: Chạy toàn bộ unit test.** Mong đợi 0 lỗi.
- [ ] **Step 2: Chạy đủ T1, T3–T14 theo kênh ADB trên AA_API31, 33, 37**, Magisk từ chối app. Bản 31 không được hỗ trợ theo Task 1: xác nhận báo "cần root" kèm lý do và thay bằng AA_API34.
- [ ] **Step 3: Smoke T1, T3, T12 trên các bản còn lại (30, 34, 35, 36).** Nếu Wireless debugging không dùng được trên emulator: ghi rõ và ghép qua `adb`.
- [ ] **Step 4: Hồi quy kênh root** trên một bản 11–15 và một bản 16–17.
- [ ] **Step 5: Máy thật Android 16, build release:** thiết lập từ đầu, xác nhận không có hộp thoại xin quyền nào; T2, T15.
- [ ] **Step 6: Ghi bảng kết quả vào `matrix.md`, nêu rõ phần chưa kiểm được (11–15 chỉ có emulator). Commit nếu có thay đổi.**
