# Detailed Findings Log — Audit Nhánh `feat/aa-glass-theme`

Tài liệu này ghi nhận đầy đủ tất cả các phát hiện trong quá trình audit mã nguồn nhánh `feat/aa-glass-theme` tại commit `e108dceadf7c7613840e738664f1071c4bc2fdc4`, so với base branch `main` (`984bbae4302274c7dcef0e13ae9ded9a949d460e`).

---

### FINDING BUG-001: Lỗi Không Tương Thích Ngược Android 9–13 (`NoSuchMethodError` do `String.formatted`)

- **ID**: BUG-001
- **Title**: Crash `NoSuchMethodError` khi khởi tạo YouTube Web Routing trên thiết bị chạy Android 9 đến Android 13
- **Category**: Logic & Compatibility (NewApi)
- **Severity**: Critical
- **Confidence**: High (Xác nhận 100% qua Android Lint Report và Android API Reference)
- **Status**: CONFIRMED
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4` (Được kế thừa từ commit `789c74d0` trên base branch `main`)
- **File path**: `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeSelectionRouting.java`
- **Line range**: 40–42
- **Evidence**:
  ```java
  // modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeSelectionRouting.java:40-42
  document.addEventListener('click', state.intentListener, true);
  """.formatted(intentEvent);
  ```
  Kết quả Android Lint:
  ```text
  modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeSelectionRouting.java:41: Error: Call requires API level 34 (current min is 28): java.lang.String#formatted [NewApi]
  ```
- **Description**: Phương thức `java.lang.String.formatted(Object... args)` là tính năng của Java 15, chỉ được Android Framework đưa vào từ API Level 34 (Android 14). Tuy nhiên, file cấu hình `gradle/libs.versions.toml` và build script định nghĩa `minSdkVersion = 28` (Android 9.0 Pie). Khi bytecode chạy trên Android 9, 10, 11, 12 hoặc 13, máy ảo ART không tìm thấy method `String.formatted` và ném `NoSuchMethodError`.
- **Preconditions / trigger**: Ứng dụng được cài đặt trên bất kỳ thiết bị nào có hệ điều hành Android 9.0 đến Android 13 (API 28 đến 33) và người dùng mở YouTube Web Addon.
- **Expected behavior**: Mã JavaScript injection được format chuỗi an toàn bằng `String.format(Locale.ROOT, ...)` hoặc string concatenation tương thích với Android API 28+.
- **Actual behavior**: App crash đột ngột với `java.lang.NoSuchMethodError: No virtual method formatted([Ljava/lang/Object;)Ljava/lang/String; in class Ljava/lang/String;`.
- **Impact**: Người dùng trên các phiên bản Android 9–13 (chiếm phần lớn các thiết bị Android Auto hoặc Head Unit trên ô tô) sẽ bị crash hoàn toàn addon YouTube.
- **Reproduction steps / verification procedure**:
  1. Chạy lệnh:
     ```powershell
     $env:ALLOW_LEGACY_TEST_SIGNING="true"; $env:APP_ID_SFX=".test"; .\gradlew lintAutoDebug --offline
     ```
  2. Quan sát kết quả tại `fermata/build/intermediates/lint_intermediate_text_report/autoDebug/lintReportAutoDebug/lint-results-autoDebug.txt:131`.
- **Root cause**: Nhà phát triển sử dụng tiện ích mới của Java 15 (`formatted`) mà không để ý rằng Android chỉ hỗ trợ API này từ Android 14 (API 34).
- **Minimal fix recommendation**:
  Thay thế `""".formatted(intentEvent)` bằng `String.format(Locale.ROOT, """...""", intentEvent)` hoặc thay placeholder `%d` bằng phép nối chuỗi / replace.
- **Required regression tests**: Chạy lại `:web:lintAutoDebug` và kiểm thử injection script trên emulator API 28 và API 33.
- **Release blocking**: YES
- **Related findings**: Không có.

---

### FINDING BUG-002: Tiềm Ẩn Crash `SecurityException` Khi Ngắt Kết Nối Bluetooth Trên Android 12+

- **ID**: BUG-002
- **Title**: Thiếu kiểm tra quyền `BLUETOOTH_CONNECT` và thiếu `try-catch` khi nhận sự kiện `BluetoothDevice.ACTION_ACL_DISCONNECTED`
- **Category**: Logic & Security / Crash
- **Severity**: High
- **Confidence**: High (Xác nhận qua Lint `MissingPermission` và Android 12 Bluetooth Permissions Specification)
- **Status**: CONFIRMED
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4` (Kế thừa từ commit `e20ffab9` trên base branch `main`)
- **File path**: `fermata/src/main/java/me/aap/fermata/carfinder/CarLocatorManager.java`
- **Line range**: 68–83
- **Evidence**:
  ```java
  // fermata/src/main/java/me/aap/fermata/carfinder/CarLocatorManager.java:68-73
  if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
      BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
      if (device != null) {
          BluetoothClass btClass = device.getBluetoothClass();
  ```
  Kết quả Android Lint:
  ```text
  fermata/src/main/java/me/aap/fermata/carfinder/CarLocatorManager.java:72: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]
  fermata/src/main/java/me/aap/fermata/carfinder/CarLocatorManager.java:72: Error: Missing permissions required by BluetoothDevice.getBluetoothClass: android.permission.BLUETOOTH_CONNECT [MissingPermission]
  ```
- **Description**: Từ Android 12 (API 31), phương thức `device.getBluetoothClass()` yêu cầu ứng dụng phải được cấp runtime permission `android.permission.BLUETOOTH_CONNECT`. `CarLocatorManager` đăng ký `BroadcastReceiver` lắng nghe `ACTION_ACL_DISCONNECTED`. Khi xe ngắt Bluetooth, `onReceive()` được thực thi ngay trên Main Thread. Vì không có kiểm tra quyền runtime và không bọc `try-catch (SecurityException)`, hệ điều hành sẽ quăng `SecurityException`, làm sập ứng dụng ngay lập tức.
- **Preconditions / trigger**: Thiết bị chạy Android 12 trở lên, quyền `BLUETOOTH_CONNECT` chưa được cấp hoặc bị thu hồi trong App Info, và xe ngắt kết nối Bluetooth.
- **Expected behavior**: Ứng dụng kiểm tra `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED` hoặc bọc lời gọi trong khối `try-catch (SecurityException)`.
- **Actual behavior**: App crash với `java.lang.SecurityException: Need BLUETOOTH_CONNECT permission for getBluetoothClass`.
- **Impact**: Crash trên Main Thread của ứng dụng khi người dùng tắt máy xe hoặc ngắt kết nối xe hơi.
- **Reproduction steps / verification procedure**:
  1. Chạy Android Lint kiểm tra lỗi `MissingPermission`.
  2. Cài app trên thiết bị Android 12+, vào Settings tắt quyền "Nearby devices" (BLUETOOTH_CONNECT), kết nối và ngắt kết nối một tai nghe/Bluetooth xe hơi.
- **Root cause**: Gọi API phần cứng Bluetooth trên Android 12+ mà thiếu guard permission hoặc exception handler.
- **Minimal fix recommendation**:
  Bọc lời gọi `device.getBluetoothClass()` trong khối `try { ... } catch (SecurityException | Exception e) { Log.w("CarFinder: Cannot access bluetooth class", e); }` và kiểm tra quyền trước khi truy xuất.
- **Required regression tests**: Test `CarLocatorManager` với Bluetooth disconnect broadcast mock.
- **Release blocking**: YES
- **Related findings**: Các cảnh báo `MissingPermission` ở dòng 138, 141, 144 trong cùng file (tuy nhiên dòng 138-144 đã được bọc trong `try-catch (Throwable ignored)`).

---

### FINDING RELEASE-001: Rào Cản Khóa Ký Phát Hành Cục Bộ (`Settings.gradle` Keystore Verification Gate)

- **ID**: RELEASE-001
- **Title**: Chặn cấu hình build release do chỉ có chứng chỉ kiểm thử cũ `fermatax-test-legacy.jks`
- **Category**: Release Configuration & Security
- **Severity**: Critical (cho quy trình đóng gói Release)
- **Confidence**: High
- **Status**: CONFIRMED
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4`
- **File path**: `settings.gradle`
- **Line range**: 42–84
- **Evidence**:
  ```groovy
  // settings.gradle:77-80
  if (certSha256 == legacyCertSha256 && !allowLegacyTestSigning) {
      throw new GradleException(
          "The insecure deterministic legacy upload certificate is not allowed.")
  }
  ```
- **Description**: `settings.gradle` cài đặt chốt bảo mật chống rò rỉ khóa ký debug/test ra môi trường production. Nó kiểm tra mã băm SHA-256 của file keystore trong `local.properties`. Nếu file keystore là `fermatax-test-legacy.jks`, Gradle yêu cầu biến môi trường `$env:ALLOW_LEGACY_TEST_SIGNING="true"` và `$env:APP_ID_SFX=".test"`. Nếu không có, mọi tác vụ Gradle đều bị chặn ở bước đánh giá (Settings Evaluation).
- **Preconditions / trigger**: Chạy tác vụ build release mà không có keystore chính thức và không cung cấp cờ test môi trường.
- **Expected behavior**: Môi trường CI/CD phát hành chính thức phải truyền file keystore có mã băm SHA-256 hợp lệ (`a86d576ff1ec...`).
- **Actual behavior**: Build release chính thức không thể thực hiện trên máy phát triển cá nhân nếu thiếu chứng chỉ phát hành chuẩn.
- **Impact**: Đảm bảo an toàn 100% không để lộ APK giả mạo release, nhưng là điều kiện tiên quyết cần CI/CD chính thức để build bản xuất xưởng.
- **Reproduction steps / verification procedure**:
  Chạy `.\gradlew tasks` mà không set `$env:ALLOW_LEGACY_TEST_SIGNING="true"`.
- **Root cause**: Cơ chế bảo vệ cố ý của kiến trúc dự án.
- **Minimal fix recommendation**: Giữ nguyên cơ chế này; khi đóng gói bản release chính thức, cung cấp Keystore phát hành chuẩn qua biến môi trường CI/CD.
- **Release blocking**: YES (đối với artifact release cuối cùng; điều kiện môi trường).
- **Related findings**: RELEASE-002.

---

### FINDING RELEASE-002: Bất Đối Xứng Danh Tính Ứng Dụng Trong Tác Vụ `verifyAutoReleaseIdentity`

- **ID**: RELEASE-002
- **Title**: Tác vụ `verifyAutoReleaseIdentity` cưỡng chế kiểm tra danh tính `me.app.fermataX.auto`, xung đột với cờ test `.test`
- **Category**: Release & CI Validation
- **Severity**: High
- **Confidence**: High
- **Status**: CONFIRMED
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4`
- **File path**: `fermata/build.gradle`
- **Line range**: 138–142
- **Evidence**:
  ```groovy
  // fermata/build.gradle:138-142
  def actualApplicationId = manifest.documentElement.getAttribute('package')
  if (actualApplicationId != expectedAutoReleaseApplicationId) {
      throw new GradleException("Auto release package mismatch: expected " +
              "${expectedAutoReleaseApplicationId}, found ${actualApplicationId}")
  }
  ```
  Lỗi khi chạy thực tế:
  ```text
  Auto release package mismatch: expected me.app.fermataX.auto, found me.app.fermataX.auto.test
  ```
- **Description**: Tác vụ Gradle `verifyAutoReleaseIdentity` được thiết kế để bảo vệ danh tính phát hành Auto: nó mở file merged `AndroidManifest.xml` của variant `autoRelease` và xác nhận giá trị `package` cùng ContentProvider authority phải tuyệt đối bằng `me.app.fermataX.auto`. Khi kỹ sư dùng keystore kiểm thử legacy với biến môi trường `$env:APP_ID_SFX=".test"`, application ID bị biến đổi thành `me.app.fermataX.auto.test`, dẫn đến kiểm tra này ném ngoại lệ.
- **Preconditions / trigger**: Chạy `gradlew verifyAutoReleaseIdentity` trong môi trường kiểm thử dùng suffix `.test`.
- **Expected behavior**: Tác vụ chỉ pass khi đóng gói cấu hình release sạch không kèm hậu tố test.
- **Actual behavior**: Build dừng lại với `GradleException`.
- **Impact**: Ngăn ngừa vô tình release bản build thử nghiệm có hậu tố `.test`.
- **Reproduction steps / verification procedure**:
  Chạy `$env:ALLOW_LEGACY_TEST_SIGNING="true"; $env:APP_ID_SFX=".test"; .\gradlew verifyAutoReleaseIdentity --offline`.
- **Root cause**: Thiết kế xác thực nghiêm ngặt để phân tách tuyệt đối giữa flavor kiểm thử và flavor release thương mại.
- **Minimal fix recommendation**: Chạy kiểm tra này trên runner CI/CD với cấu hình phát hành thực (không truyền `$env:APP_ID_SFX`).
- **Release blocking**: YES (Điều kiện bắt buộc cho bản build release).
- **Related findings**: RELEASE-001.

---

### FINDING QUALITY-001: Áp Lực Vượt Giới Hạn Dòng Class Hotspot (`ArchitectureBoundaryTest`)

- **ID**: QUALITY-001
- **Title**: Dung lượng các class hotspot đang áp sát ngưỡng tối đa cho phép trong kiểm tra kiến trúc
- **Category**: Maintainability & Architecture
- **Severity**: Medium
- **Confidence**: High
- **Status**: CONFIRMED
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4`
- **File path**: `fermata/src/test/java/me/aap/fermata/architecture/ArchitectureBoundaryTest.java`
- **Line range**: 134–156
- **Evidence**:
  Kết quả đếm dòng non-blank hiện tại:
  - `MediaSessionCallback.java`: **2,351** / 2,360 dòng (chỉ còn **9 dòng** trống trước khi fail test).
  - `YoutubeMediaEngine.java`: **1,187** / 1,190 dòng (chỉ còn **3 dòng** trống trước khi fail test).
  - `MainActivityDelegate.java`: **1,270** / 1,291 dòng (còn 21 dòng trống).
  - `ControlPanelView.java`: **853** / 956 dòng (còn 103 dòng trống).
  - `YoutubeWebView.java`: **1,094** / 1,248 dòng (còn 154 dòng trống).
- **Description**: Dự án có cơ chế kiểm tra kiến trúc `ArchitectureBoundaryTest.hotspotClassesCannotGrowPastTheRecordedBaseline()` nhằm ngăn chặn các file "God class" phình to vô hạn. Hiện tại, hai file cốt lõi là `MediaSessionCallback.java` và `YoutubeMediaEngine.java` chỉ còn cách giới hạn lần lượt 9 dòng và 3 dòng. Bất kỳ thay đổi, thêm comment hoặc format nào mà không chú ý có thể làm gãy build toàn bộ dự án.
- **Preconditions / trigger**: Thêm logic vào `MediaSessionCallback` hoặc `YoutubeMediaEngine` mà không tách helper class.
- **Expected behavior**: Các chức năng mới nên được ủy quyền (delegate) ra các controller/helper độc lập thay vì ghi trực tiếp vào các class trung tâm.
- **Actual behavior**: Hiện tại bài test vẫn PASS, nhưng biên an toàn (safety margin) cực kỳ hẹp.
- **Impact**: Rủi ro cao làm gãy CI build trong các lượt commit tiếp theo nếu nhóm phát triển không nhận thức được trần dòng code.
- **Reproduction steps / verification procedure**:
  Chạy `.\gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.architecture.ArchitectureBoundaryTest"`.
- **Root cause**: Sự tích lũy trách nhiệm (Single Responsibility Violation) lịch sử trong `MediaSessionCallback` và `YoutubeMediaEngine`.
- **Minimal fix recommendation**: Khi cần sửa hoặc thêm tính năng cho MediaSession hay YouTube engine, bắt buộc phải extract logic sang các class phụ trách chuyên biệt.
- **Required regression tests**: `ArchitectureBoundaryTest`.
- **Release blocking**: NO (Hiện tại vẫn PASS, là rủi ro bảo trì trung hạn).
- **Related findings**: Không có.

---

### FINDING QUALITY-002: Cảnh Báo Ép Cam Kết Đồng Bộ SharedPreferences (`commit()` thay vì `apply()`)

- **ID**: QUALITY-002
- **Title**: Sử dụng `SharedPreferences.Editor.commit()` gây I/O đồng bộ trên Main Thread
- **Category**: Performance & Code Quality
- **Severity**: Medium
- **Confidence**: High
- **Status**: CONFIRMED
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4`
- **File path**: `fermata/src/main/java/me/aap/fermata/carfinder/CarFinderPrefs.java`
- **Line range**: 76, 85, 98
- **Evidence**:
  ```java
  // fermata/src/main/java/me/aap/fermata/carfinder/CarFinderPrefs.java:76
  getPrefs(context).edit().putString(KEY_PARKING_LOCATION, location.toJson()).commit();
  ```
  Lint Warning:
  ```text
  Consider using apply() instead; commit writes its data to persistent storage immediately, whereas apply will handle it in the background [ApplySharedPref]
  ```
- **Description**: `commit()` ghi dữ liệu xuống đĩa flash ngay lập tức một cách đồng bộ. Khi được gọi trên Main Thread trong quá trình ngắt kết nối xe hơi, nó có thể gây hiện tượng giật cục (jank) hoặc chậm phản hồi UI.
- **Preconditions / trigger**: Sự kiện ngắt kết nối xe hơi lưu vị trí đỗ xe.
- **Expected behavior**: Sử dụng `apply()` để việc ghi đĩa được thực hiện bất đồng bộ trong background thread pool.
- **Actual behavior**: Ghi đĩa đồng bộ chặn Main UI Thread một khoảng thời gian ngắn.
- **Impact**: Có thể gây jank nhẹ khi đóng ứng dụng hoặc nhận disconnect broadcast.
- **Reproduction steps / verification procedure**: Kiểm tra cảnh báo Android Lint `ApplySharedPref`.
- **Root cause**: Thói quen dùng `commit()` để đảm bảo dữ liệu ghi xong trước khi tiến trình có thể bị hệ thống dọn dẹp.
- **Minimal fix recommendation**: Chuyển sang `apply()` nếu không cần kết quả trả về boolean ngay lập tức, hoặc chạy trên executor background.
- **Release blocking**: NO
- **Related findings**: `AndroidDiagnosticsRuntime.java:425`.

---

### FINDING QUALITY-003: Sử Dụng `String.toLowerCase()` Không Chỉ Định `Locale.ROOT`

- **ID**: QUALITY-003
- **Title**: Nguy cơ sai lệch chuỗi định danh nội bộ khi người dùng cài ngôn ngữ đặc biệt (Thổ Nhĩ Kỳ, Azerbaijan)
- **Category**: Code Quality / I18N
- **Severity**: Low
- **Confidence**: High
- **Status**: CONFIRMED
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4`
- **File path**: `fermata/src/main/java/me/aap/fermata/diagnostics/DiagnosticJson.java:23`, `modules/radio/.../RadioBrowserStation.java:49`
- **Line range**: 23–24 (DiagnosticJson)
- **Evidence**:
  ```java
  field(json, "scope", event.getScope().name().toLowerCase());
  ```
  Lint Warning:
  ```text
  Implicitly using the default locale is a common source of bugs: Use toLowerCase(Locale) instead. For strings meant to be internal use Locale.ROOT, otherwise Locale.getDefault(). [DefaultLocale]
  ```
- **Description**: Khi gọi `toLowerCase()` mà không truyền `Locale`, Java sử dụng `Locale.getDefault()`. Với các ngôn ngữ như Thổ Nhĩ Kỳ (`tr-TR`), ký tự chữ hoa 'I' biến thành 'ı' (chữ i không chấm) thay vì 'i', khiến các so sánh chuỗi enum hoặc JSON field bị sai.
- **Preconditions / trigger**: Thiết bị người dùng cài đặt hệ thống ngôn ngữ Thổ Nhĩ Kỳ hoặc Azerbaijan.
- **Expected behavior**: Sử dụng `toLowerCase(Locale.ROOT)` cho các định danh máy/hệ thống nội bộ.
- **Actual behavior**: Sử dụng Locale của thiết bị cho các chuỗi protocol/log.
- **Impact**: Rất thấp; chủ yếu ảnh hưởng đến format log chẩn đoán hoặc parse stream radio khi máy đặt locale Thổ Nhĩ Kỳ.
- **Reproduction steps / verification procedure**: Kiểm tra cảnh báo Android Lint `DefaultLocale`.
- **Root cause**: Bỏ qua tham số Locale khi biến đổi chuỗi enum.
- **Minimal fix recommendation**: Thay bằng `.toLowerCase(Locale.ROOT)`.
- **Release blocking**: NO
- **Related findings**: Không có.

---

### FINDING UX-001: Ràng Buộc Khả Năng Tiếp Cận Trên Màn Hình Xe Hơi Cực Thấp (H < 262dp)

- **ID**: UX-001
- **Title**: Kích thước vùng chạm dọc trên Glass Navigation Rail không đạt chuẩn 48dp đối với màn hình xe hơi cực thấp (H < 262dp)
- **Category**: Automotive UX & Accessibility
- **Severity**: Medium
- **Confidence**: Medium
- **Status**: NEEDS_VERIFICATION
- **Branch và commit SHA**: `feat/aa-glass-theme` @ `e108dceadf7c7613840e738664f1071c4bc2fdc4`
- **File path**: `fermata/src/main/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailView.java`
- **Line range**: 385–390
- **Evidence**:
  ```java
  // GlassNavigationRailView.java:385-390
  } else {
      // Ultra-low viewport H < 262dp: minimal overhead (4dp total) to maximize button touch area
      vPad = GlassDimensions.dpToPx(getContext(), 2);
      containerPad = 0;
      btnMargin = 0;
  }
  ```
- **Description**: Glass Navigation Rail chứa 5 nút điều hướng chính. Để đáp ứng chuẩn Android Automotive Accessibility, mỗi nút cần có chiều cao tối thiểu 48dp (hoặc 76dp cho chuẩn car tối ưu). Khi chiều cao màn hình xe hơi thấp hơn 262dp, tổng chiều cao 5 nút vượt quá không gian khả dụng, buộc code phải co cụm nút xuống dưới 48dp để tránh tràn viền (clipping).
- **Preconditions / trigger**: Hiển thị trên màn hình head-unit siêu hẹp hoặc chia đôi màn hình có chiều cao dọc dưới 262dp.
- **Expected behavior**: Vùng chạm luôn đạt tối thiểu 48dp theo chuẩn của Google Play và Android Automotive.
- **Actual behavior**: Code tự động giảm padding và co nút lại để vừa màn hình, khiến vùng chạm nhỏ hơn tiêu chuẩn khuyến nghị.
- **Impact**: Người lái xe có thể khó chạm chính xác vào nút điều hướng khi đang lái xe trên các loại màn hình đặc thù này.
- **Reproduction steps / verification procedure**: Kiểm thử giao diện trên DHU với cấu hình độ phân giải thấp (ví dụ: màn hình 800x480 bị chia khung theo chiều ngang).
- **Root cause**: Giới hạn hình học vật lý của màn hình nhỏ khi bố trí 5 nút theo chiều dọc.
- **Minimal fix recommendation**: Với màn hình H < 262dp, xem xét chuyển Navigation Rail sang dock ngang (Bottom Dock) hoặc cho phép cuộn.
- **Release blocking**: NO (Các xe hơi hiện đại hầu như đều có H >= 480dp; trường hợp H < 262dp là biên rất hiếm).
- **Related findings**: Không có.
