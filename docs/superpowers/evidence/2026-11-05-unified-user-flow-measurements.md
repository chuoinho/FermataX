# Unified User Flow Measurements (2026-11-05)

## Task 2: Đo màn khóa vuốt trên Android 11–12 (fmx30, fmx31)

### Mục tiêu
Kiểm tra xem khi máy ở màn khóa vuốt (`keyguardLocked=true`, `keyguardSecure=false`), các app chạy trên VirtualDisplay của Car Apps có tiếp tục chạy không hay bị hệ thống dừng (không còn resume).

### Kết quả đo
1. **fmx31 (Android 12, SDK 31)**:
   - `resumedOn=null`
   - `keyguardLocked=true`
   - `keyguardSecure=false`
   - `isPhoneLocked=false` (do client hiện tại chỉ check `isKeyguardSecure() && isKeyguardLocked()`)
   - Test `swipeLockTellsTheCarWhenAppsStop` timed out waiting for `client.isPhoneLocked()`.

2. **fmx30 (Android 11, SDK 30)**:
   - `resumedOn=null`
   - `keyguardLocked=true`
   - `keyguardSecure=false`
   - `isPhoneLocked=false`
   - Test `swipeLockTellsTheCarWhenAppsStop` timed out waiting for `client.isPhoneLocked()`.

### Kết luận nhánh rẽ
Trên cả Android 11 và 12, app **không còn resume** trên màn hình xe khi máy ở màn khóa vuốt.
-> **Thực hiện nhánh A**: Cài đặt `keyguardHoldsPhone(int sdk, boolean secure, boolean keyguardLocked)` trả về `keyguardLocked && (secure || sdk < 33)`.

### Kết quả đo Step 7 (chạy 5 lần lặp mỗi test trên fmx30 và fmx31)
1. **fmx31 (Android 12, SDK 31)**:
   - `swipeLockTellsTheCarWhenAppsStop`: **5/5 PASS**
   - `panelOffThenPowerKeyAsksToUnlock`: **5/5 PASS**
2. **fmx30 (Android 11, SDK 30)**:
   - `swipeLockTellsTheCarWhenAppsStop`: **5/5 PASS**
   - `panelOffThenPowerKeyAsksToUnlock`: **5/5 PASS**


## Task 6: kiểm thử tổng hợp, đo lại sau khi sửa test

Bản đầu của mục này ghi sai nguyên nhân lỗi (Chrome, "chỉ root/shell gọi được `cmd activity`"). Số dưới đây thay thế bản đó.

Unit test: auto 1.863, mobile 1.597, 0 fail, mỗi bộ 2 skipped.

Device test, 18 test, emulator WHPX, 0 `FATAL EXCEPTION IN SYSTEM`:

| AVD | Android | Kết quả cuối | Skipped | Ghi chú |
|---|---|---|---|---|
| fmx30 | 11 | OK (18) | 6 | |
| fmx31 | 12 | OK (18) | 6 | |
| fmx32 | 12L | 2 pass, 10 fail | 6 | Đo trước khi ẩn. Car Apps đã ẩn trên SDK 32 |
| fmx33 | 13 | OK (18), hai lần | 2 | Một lần sau boot fail `touchesAndButtonsDriveTheCarDisplay` |
| fmx34 | 14 | OK (18), bản code cuối | 2 | |
| fmx35 | 15 | OK (18), hai lần | 2 | Một lần fail `releaseBringsThePanelBack`, không có log |

Lỗi thất thường, chưa rõ nguyên nhân:
- `touchesAndButtonsDriveTheCarDisplay` trên 13: chạy riêng 6/8 pass. Một lần `assertNotNull` trong `showFragment`, một lần `Timed out waiting for the app to open from a touch`.
- `releaseBringsThePanelBack` trên 15: `Timed out waiting for the panel to come back` 1 lần trong bộ đầy đủ; sau đó chạy riêng 10/10 và bộ đầy đủ 2/2. Chưa loại trừ khả năng panel không bật lại thật.

Nguyên nhân các lỗi đã sửa, đều ở test:
- `panelOffKeepsTheCarRunningAndThePowerKeyBringsItBack`, 13-15: `PhoneDimActivity` bị hủy 0,15 giây sau khi bấm nguồn, nhưng dòng `mControlTarget` trong `dumpsys activity activities` vẫn còn tên này. Test đổi sang `PhoneDimActivity.isShowing()`.
- Cùng test, 11-12: sau khi bấm nguồn máy về màn khóa vuốt nên xe yêu cầu mở khóa. Test đổi sang đợi `isPhoneLocked()`.
- `connectsAttachesAndReleasesInAnyPhoneState`, 11-12: test coi màn khóa vuốt là không khóa và đòi dim lock trong lúc máy khóa.

## Panel kẹt tắt khi server không quay lại (đã sửa)

Tái hiện trên Android 14, server bị kill và không khởi động lại: panel vẫn tắt sau một lần bấm nguồn và sau khi hết giờ tắt màn, 2/2 mỗi trường hợp.

Sửa trong `CarDisplayClient`: khi server chết lúc panel đang tắt, client giữ receiver `ACTION_SCREEN_OFF` và đánh thức máy một lần ở lần màn tắt kế tiếp; hệ thống tự bật lại panel khi thức. Không cần server. Server nối lại thì receiver bị gỡ.

Sau khi sửa, 2 test mới đạt 2/2 mỗi test trên 11, 12, 14 và đạt trong bộ đầy đủ trên 13, 15. Giới hạn: máy bật "giữ màn sáng khi sạc" (image 11 và 12 mặc định bật) thì không có timeout, cần một lần bấm nguồn. Nếu tiến trình app cũng chết thì không có receiver.

Bộ đầy đủ sau khi sửa: 20 test; 11 và 12 skip 6, 13, 14, 15 skip 2. Unit auto 1.865, mobile 1.597, 0 fail. Bảng trên là các lần chạy 18 test trước đó.

## Android 12L (SDK 32, `fmx32`, SE1B.240122.005)

- Với uid 1000, `am start` và `cmd activity start` trả về 255, không có output, logcat không có exception. Server cần uid 1000 dưới SDK 33 nên không mở được app, panel cũng không tắt vì `PhoneDimActivity` không được mở.
- Vẫn chạy dưới uid 1000: `cmd activity help`, `cmd package`, `cmd window`, `settings`, `input`, `dumpsys`. Shell và root đều `am start` được. Android 12 cùng uid chạy bình thường.
- Quyết định của chủ dự án: ẩn Car Apps trên SDK 32. Chưa tìm ra nguyên nhân, chưa thử máy thật.

## Redmi Note 8 `15c36230`, Android 16, DHU, versionCode 333

Bản code cuối, bấm trên DHU, chủ máy tự mở khóa và tự bấm nút nguồn. Ảnh chỉ nằm ở thư mục tạm.

| Bước | Kết quả | Đo được |
|---|---|---|
| Mở Car Apps khi máy khóa | Xe hiện "The phone is locked / Unlock it to continue" | `isKeyguardShowing=true` |
| Mở khóa | Xe tự hiện launcher, màn điện thoại vẫn sáng, không có màn đen | `powerMode=On`, `PhoneDimActivity` 0 |
| Chạm Maps trên xe | Maps mở trên xe | `topResumedActivity` là `MapsActivity` |
| Bấm nút mới | Điện thoại tắt và khóa, Maps vẫn chạy | 22:41:20 `Setting power mode 0`, keyguard true |
| Bấm nút mới lần nữa | Máy không sáng lại | vẫn `powerMode=Off` |
| Chạm trên xe khi điện thoại tắt | Maps phản hồi | ảnh trước và sau khác nhau |
| Nút nguồn thật, một lần | Màn sáng, chủ máy xác nhận | 23:01:46 `Waking up ... WAKE_REASON_POWER_BUTTON`, `Setting power mode 2`; Maps vẫn resume |
| Màn khóa hết giờ | Điện thoại tắt, xe vẫn chạy | 23:01:57 `Setting power mode 0` |
| Thoát DHU | Màn xe biến mất, điện thoại ở trạng thái thường | 23:02:33 `shutdown`, `fermatax-car` 0, nhóm 0 `Dozing`, `PhoneDimActivity` 0, 0 fatal |

Server vẫn sống ít nhất 40 giây sau khi thoát, đang chờ `IdleExitPolicy`; không đợi tới lúc nó thoát.

## Chưa kiểm

Máy thật Android 11-15, `su 1000` dưới Magisk, ROM hãng, Android Auto trên 11-15.

## Kiểm lại ngày 2026-11-06

- fmx35, bộ đầy đủ trên bản debug có thêm log chẩn đoán: 5/5 lượt `OK (20 tests)`; `releaseBringsThePanelBack` chưa tái hiện lỗi. Emulator đã dừng sau script.
- Test mới `panelOffPowerKeyAndPinResumeTheCarApp`: fmx30 5/5, fmx31 5/5, mỗi lượt `OK (1 test)`. PIN do `TestDevice` tự đặt và xóa.
- Unit chạy thật với `--rerun-tasks`: Auto 1.866, Mobile 1.597, 0 fail, mỗi bộ 2 skipped.
- `assembleAutoRelease` và `verifyAutoReleaseIdentity` đạt sau khi giới hạn suppression lint `BlockedPrivateApi` cho nhánh reflection cũ trong `Hidden.initPanelControl`. APK có chữ ký v2, R8 giữ `Main`, `ICarDisplayServer`, `PhoneDimActivity`; manifest chứa `me.app.fermataX.auto.cardisplay`. Chưa cài APK release lên emulator.
- Bộ device test cuối: fmx30 `OK (21 tests)`; fmx31 dừng ở `touchesAndButtonsDriveTheCarDisplay` (test thứ 18), không có dòng tổng kết. Tiến trình ADB instrument treo và đã được buộc dừng. Log emulator có `Failed to find ColorBuffer: 14` và `Failed to bind to texture: invalid color buffer`, nhưng chưa chứng minh quan hệ nhân quả. Script dừng, fmx33–35 chưa chạy ở vòng này. Không còn emulator/QEMU khi kiểm bằng `tasklist`.
- Rà độc lập xác nhận `Hidden.setPhysicalDisplayPowerMode` và `CarDisplayClient.restorePanelAndWake` xóa cờ trạng thái trước khi biết panel bật thành công. Chưa có log runtime chứng minh đường lỗi này gây lần timeout trên Android 15.

## Kiểm lại chiều 2026-11-06 (HEAD 4182e4fb)

- Lần treo fmx31 là do máy chủ thiếu bộ nhớ ảo, không phải app: Windows ghi `Resource-Exhaustion-Detector 2004` mỗi 5 phút từ 10:02 (qemu 5,1 GB, Gradle 2,5 GB, ứng dụng khác); qemu bị rút về 16 KB working set, `adb shell` và console emulator đều không trả lời. Sau khi đóng bớt ứng dụng và tắt Gradle trước khi mở emulator, commit charge 19-22/29,7 GB và không treo lại.
- Một lượt bị giết giữa test để lại `TestDevice.PIN` trên fmx31, nên lần boot sau user chưa mở khóa (`Package ... is not encryption aware`). Script đo nay mở khóa và xóa PIN sau boot.
- Chạy `adb root` làm `am instrument` khởi server bằng uid 0: trên Android 13 `createVirtualDisplay` từ chối (`packageName must match the calling uid`), 17/21 fail `attach=-1`. Bản thật chạy `su 2000` nên không đi đường này; đường dự phòng root khi su không đổi được uid vẫn hỏng trên 13, chưa sửa.
- Bộ đầy đủ không root, mỗi máy một lượt: fmx31 `OK (21 tests)`, fmx33 `OK (21 tests)`, fmx34 `OK (21 tests)`, fmx35 `OK (21 tests)`; 0 `FATAL EXCEPTION IN SYSTEM`. fmx30 `OK (21 tests)` từ lượt sáng.
- `touchesAndButtonsDriveTheCarDisplay` chạy riêng trên fmx31 (có root): 4/5, một lần `Timed out waiting for the app to open from a touch`. Một lượt có root trên fmx34 fail `lockedPhoneShowsNothingUntilItIsUnlockedOnce` (không thấy hình Chrome sau 20 s), lượt không root đạt. Cả hai chưa rõ nguyên nhân.
- APK Auto release (build 15:10, chữ ký v2 `CN=FermataX Upload`) trên fmx35: cài và mở app không crash; chạy đúng lệnh `ServerCommand` dưới uid shell in `SERVER_READY uid=10214 version=333 pkg=me.app.fermataX.auto` và `BINDER_HANDOFF_SUCCESS authority=me.app.fermataX.auto.cardisplay ok=true`. Không có xe nên chưa kiểm hình và chạm trên bản release.
