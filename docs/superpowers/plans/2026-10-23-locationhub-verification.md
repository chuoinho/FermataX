# Prompt kiểm thử: LocationHub trên máy thật và DHU

> Dán toàn bộ file này cho agent kiểm thử. Agent cần **terminal** (adb, gradle, DHU), nên phải chạy ở chế độ tương tác, không phải headless.
> Repo: `E:\antigravity\fermata`, nhánh `feat/aa-glass-theme` (HEAD `5011f72c`, có `LocationHub`).

## 0. Mục tiêu
Xác minh trên điện thoại `15c36230` (Redmi Note 8, Android 16) và Desktop Head Unit (DHU) rằng thay đổi `LocationHub` hoạt động đúng:
1. **Một nguồn vị trí:** trong phiên Android Auto, app chỉ có **1** request vị trí khi speedo, CarFinder và thời tiết cùng chạy.
2. **Thời tiết:** tải 1 lần khi mở dashboard trên xe, sau đó 60 phút/lần, toạ độ gửi đi làm tròn 2 chữ số.
3. **CarFinder:** lưu chỗ đỗ đúng khi ngắt AA, kể cả lúc Google Maps đang chiếm màn hình xe; ghi được log nguồn vị trí (`buffer` / `system` / `stale`).
4. **Không hồi quy:** speedo vẫn chạy (V1–V3 cũ), CarFinder bật GPS sau khi cấp quyền muộn.

**Đây là việc kiểm thử. KHÔNG sửa code sản phẩm.** Gặp lỗi thì ghi lại kèm bằng chứng và đề xuất cách sửa, không tự sửa.

## 1. Ràng buộc
- **KHÔNG** commit, push, merge, rebase, stash, reset; không sửa file trong `fermata/src/main/`.
- **KHÔNG** gỡ cài đặt app (luôn dùng `adb install -r -d`). Không đổi cài đặt bảo mật của máy.
- Được phép bật/tắt Location bằng `adb shell cmd location set-location-enabled true|false`, và cấp/thu hồi quyền **của chính app** bằng `pm grant` / `pm revoke`, miễn là **khôi phục lại** sau mỗi kịch bản.
- **Báo cáo trung thực:** kịch bản nào không chạy được thì ghi **KHÔNG CHẠY ĐƯỢC** kèm lý do, không ghi PASS. Không có số liệu thật thì không bịa.

## 2. Chuẩn bị

### 2.1 Test tự động (chạy trước, ghi kết quả)
```bat
cd /d E:\antigravity\fermata
node tools\delegate\gradle-tap.mjs --tests "me.aap.fermata.acceptance.*"
node tools\delegate\gradle-tap.mjs
```
Kỳ vọng: acceptance 15/15; toàn bộ fermata autoDebug khoảng 1 369 pass, 0 fail. Script in TAP ra stdout, log Gradle ra stderr. Script đã tự truyền các cờ `-PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true`.

### 2.2 Build và cài
```bat
set ALLOW_LEGACY_TEST_SIGNING=true
set APP_ID_SFX=.test
gradlew.bat :fermata:assembleAutoDebug --offline -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true
adb -s 15c36230 install -r -d fermata\build\outputs\apk\auto\debug\fermata-2.0.1-me.app.fermataX.auto.test-auto-debug.apk
```
- Package trên máy: **`me.app.fermataX.auto.test.debug`**.
- Phải truyền `-P…`. Nếu chỉ đặt biến môi trường, các module con có thể fail ở `processAutoDebugResources` (thiếu chuỗi base).
- Nếu shell không tìm thấy `gradlew.bat` trong thư mục hiện tại, gọi bằng đường dẫn tuyệt đối `E:\antigravity\fermata\gradlew.bat`.

### 2.3 Chạy DHU sao cho điều khiển được
1. Trên điện thoại: Android Auto → Developer settings → **Start head unit server**. Nếu DHU kết nối mà không hiện hình thì bật lại mục này.
2. Không dùng `run_dhu.bat` (console tương tác, không gửi lệnh được). Chạy DHU đọc lệnh từ một file (Git Bash):
   ```bash
   CMDS=/c/Users/ttanh/AppData/Local/Temp/dhu_cmds.txt; : > "$CMDS"
   adb -s 15c36230 forward tcp:5277 tcp:5277
   cd "/c/Users/ttanh/AppData/Local/Android/Sdk/extras/google/auto"
   tail -f "$CMDS" | ./desktop-head-unit.exe -c config/default_720p.ini > /c/Users/ttanh/AppData/Local/Temp/dhu.log 2>&1
   ```
   Chạy lệnh này ở nền. Nếu stdin đóng, DHU thoát ngay sau `connected`.
3. Gửi lệnh bằng cách ghi thêm một dòng vào file, ví dụ:
   - `echo "screenshot C:/Users/ttanh/AppData/Local/Temp/shots/x.png" >> "$CMDS"`
   - `echo "tap 40 462" >> "$CMDS"`: icon FermataX trên thanh bên trái của xe.
   - `echo "tap 40 258" >> "$CMDS"`: Google Maps.
   - Gõ `help` để xem danh sách lệnh. Thoát DHU (tương đương rút AA) bằng `echo quit >> "$CMDS"`.

   Nếu chụp màn hình báo "Don't have video focus" thì phiên chiếu chưa lên: kiểm tra head unit server.
4. Cài lại APK thường làm đứt phiên AA. Sau khi cài, khởi động lại DHU và mở FermataX.
5. Thẻ speedo có thể đang ở "Trip paused" do lần thử trước; bấm "Resume trip" (khoảng `tap 403 102`) trước khi đo.

### 2.4 Lệnh lấy bằng chứng (`PKG=me.app.fermataX.auto.test.debug`)
```bash
adb -s 15c36230 shell dumpsys location | grep -E "^\s+10504/|10504/me.app.*Request\["   # đổi 10504 theo uid thật của app
adb -s 15c36230 shell dumpsys package $PKG | grep userId                               # lấy uid
adb -s 15c36230 shell dumpsys activity processes $PKG | grep -E "curProcState|curCapability"
adb -s 15c36230 logcat -d | grep -E "CarFinder|LocationHub|FermataTrip|open-meteo|Weather" | tail -50
MSYS_NO_PATHCONV=1 adb -s 15c36230 shell ls -la /sdcard/Android/data/$PKG/files/trip_logs
```
- Trong `dumpsys location`, listener **đang hoạt động** nằm dưới từng provider (`gps provider:` / `fused provider:` → `listeners:`). Các dòng `+registration` / `-registration` là lịch sử. Đếm request **đang hoạt động**, không đếm dòng lịch sử.
- `curCapability` có chữ **L** nghĩa là app đang có quyền vị trí tiền cảnh.

## 3. Kịch bản

| ID | Kịch bản | Bước | Kỳ vọng / bằng chứng cần lưu |
|---|---|---|---|
| T1 | Một request vị trí | Mở FermataX trên xe (dashboard Glass hiện speedo, thời tiết). Nếu bật auto-save chỗ đỗ của CarFinder thì giữ bật. Chờ 30 s | Đúng **1** listener đang hoạt động của app (FUSED hoặc GPS, 1 s). Không có NETWORK hay PASSIVE của app. Lưu output `dumpsys location` |
| T2 | Speedo vẫn chạy | Đứng yên 2 phút trên dashboard | 0 km/h, km không tăng, TIME tăng khoảng 2 phút, nhãn "GPS connected"; file `trip_logs/*.csv` có khoảng 1 dòng `L` mỗi giây |
| T3 | Thời tiết tải đúng 1 lần khi mở | `adb logcat -c`, khởi động lại DHU, mở FermataX, chờ 60 s | Log hoặc network chỉ có **1** request Open-Meteo. URL có `latitude=xx.xx&longitude=yyy.yy` (2 chữ số). Nếu log không ghi URL, dùng proxy hoặc ghi rõ "không quan sát được URL" |
| T4 | Quay lại dashboard không tải thêm | Mở Maps trên xe 20 s → quay lại FermataX, làm 3 lần | Không có request thời tiết mới (trên AA chỉ tải khi start và theo lịch 60 phút) |
| T5 | Lịch 60 phút | Không thể chờ 60 phút trong buổi test thì ghi **KHÔNG CHẠY ĐƯỢC (cần ≥60 phút)**. Nếu có thời gian: để dashboard mở 61 phút | Sau khoảng 60 phút có đúng 1 request mới |
| T6 | CarFinder bật GPS sau khi cấp quyền muộn | `pm revoke` FINE+COARSE → mở CarFinder trên **điện thoại** (tab Find Car) → khi hộp thoại hiện thì cấp quyền | Bản đồ hoặc vị trí người dùng cập nhật; `dumpsys location` có listener của app. Sau đó **khôi phục quyền** |
| T7 | Lưu chỗ đỗ khi FermataX đang hiển thị | Auto-save bật. Mở FermataX trên xe 1 phút → `echo quit` (rút AA) | Log `CarFinder` có dòng tính điểm đỗ kèm nguồn `buffer`; CarFinder hiện chỗ đỗ đúng vị trí hiện tại |
| T8 | Lưu chỗ đỗ khi Maps chiếm màn hình | Auto-save bật. Mở FermataX → mở Maps trên xe → chờ **2 phút** → `echo quit` | Ghi nguồn đã dùng (`buffer` / `system` / `stale`) và kết quả của `getLastKnownLocation` khi app ở nền (log). Chỗ đỗ lưu được có đúng không (so với vị trí thật). Lưu `curProcState`/`curCapability` trong lúc Maps hiển thị. **Đây là điểm chưa biết, cần dữ liệu thật** |
| T9 | Ngắt AA thì gỡ GPS | Sau T7 hoặc T8, chờ 10 s | `dumpsys location`: không còn listener nào của app (có `-registration` của LocationHub) |
| T10 | Tắt Location 30 s | Trên dashboard xe: `set-location-enabled false` → 30 s → `true` | Speedo hiện "—" và "Turn on GPS"; bật lại thì có tốc độ trong khoảng ≤ 25 s. **Bật lại Location** khi xong |

Mỗi kịch bản cần: thời điểm bắt đầu/kết thúc, lệnh đã chạy, ảnh DHU (`screenshot`), đoạn log hoặc dumpsys liên quan.

## 4. Báo cáo
Tạo `audit_reports/feat-aa-glass-theme/2026-10-23-locationhub-device-verification.md`:
- Thiết bị, Android, phiên bản Android Auto, commit đã build, versionCode đã cài.
- Kết quả 2.1 (số test pass/fail/skip của từng lệnh, ghi riêng, không cộng dồn).
- Bảng T1–T10: **PASS / FAIL / KHÔNG CHẠY ĐƯỢC**, kèm bằng chứng (đường dẫn ảnh, trích log, dumpsys).
- Riêng **T8**: mô tả kỹ những gì quan sát được (process state, quyền vị trí, nguồn điểm đỗ, độ lệch so với thực tế). Kết quả này quyết định có cần quyền vị trí nền cho CarFinder hay không.
- Danh sách lỗi phát hiện (nếu có): mô tả, cách tái hiện, file/dòng nghi vấn, đề xuất sửa. **Không tự sửa code.**
- Ghi rõ những gì không kiểm chứng được và lý do.
