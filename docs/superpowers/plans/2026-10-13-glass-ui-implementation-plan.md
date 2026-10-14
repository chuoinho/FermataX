# Plan

Hoàn thiện Glass toàn app bằng component Fermata hiện có, port có chọn lọc bố cục dashboard và bổ sung presentation còn thiếu. Day/Night do người dùng chọn, một preference dùng chung phone/AA; migration Glass không thay giao diện hoặc hành vi theme khác.

## Scope

- In: đặc tả G01–G07, component C01–C16 và nghiệm thu V01–V08 trong `glass-ui-technical-spec-2026-10-13.md`; shell, text/icon/button, list/form/menu/dialog, player/dashboard, phone/AA và manual appearance.
- Out: framework UI mới, dịch vụ playback/weather/GPS thay thế, chức năng giá xăng/âm lịch/launcher, website/system UI ngoài app, release ký/publish. Không sửa hoặc gỡ app của người dùng để chuẩn bị kiểm thử.

## Action items

- [x] **Chốt baseline và inventory.** Đã kiểm tra HEAD/diff/AGENTS/MASTER_CONTEXT; lập bảng screen + state + modal cho C01–C16 trong `2026-10-13-glass-ui-screen-state-modal-inventory.md`. Chốt NIGHT mặc định và quy ước 4+2 tổng ô (4 custom + Addons + Exit).
- [x] **Thêm manual Glass appearance và opt-in.** Sửa `MainActivityPrefs`, `InterfacePrefsBuilder`, theme apply/preference listener trong `MainActivityDelegate`; độc lập THEME_MAIN/THEME_AA, dùng chung một `GLASS_APPEARANCE` (0=Day, 1=Night, default=1). Gate V01/V02/V04 đạt 100% qua unit test và kiểm chứng live.
- [x] **Hợp nhất palette/style và cập nhật lifecycle.** Hợp nhất `GlassThemeColors`, `theme_glass.xml`, `GlassDrawables`, `GlassDimensions`. Bỏ hoàn toàn phụ thuộc vào hệ thống `uiMode` khi theme Glass được kích hoạt. Bảo toàn dữ liệu/playback/draft/scroll.
- [x] **Đồng bộ component chung.** Sửa `FermataToolBarView`, `FermataNavBarView`, `MediaItemView`, `GlassSettingsDecorator`, `GlassDialogBuilder`, `GlassSlider`, `GlassSwitch`. Sửa lỗi độ tương phản Day mode (chữ/icon tối trên nền sáng; không còn chữ trắng trên nền xám), sửa SwitchCompat kế thừa TextView và ColorStateList cho selector.
- [x] **Hoàn thiện dashboard/media/weather.** Cập nhật `GlassDashboardController`, `GlassMediaPlayerView`, `GlassNavWeatherView`, `GlassOpenMeteoClient`, `GlassSpeedoTripView`, `GlassQuickBarView`. Đã giải quyết triệt để các lỗi P1 audit: hero idle/recent phân biệt rõ, hourly forecast lọc chính xác từ giờ hiện tại và timezone, speedometer không bị mất sau split, 4+2 quick bar cân đối.
- [x] **Hoàn thiện presentation đặc thù còn lại.** Đồng bộ ControlPanel fullscreen, Equalizer sliders/preamp/boost và floating presets dialog, Car Finder map chrome và radar cards, các addon surfaces (TV, YouTube, Radio, Podcasts, Audiobooks).
- [x] **Kiểm chứng ma trận và hồi quy.** Chạy `testAutoDebugUnitTest` và `testMobileDebugUnitTest` đạt 100% green (605 tasks up-to-date, 0 fail). Kiểm chứng live trên điện thoại vật lý (`15c36230`) và Desktop Head Unit (DHU) ở cả hai chế độ Day và Night; kiểm chứng mixed theme và hệ thống uiMode đối nghịch.
- [x] **Đóng release candidate và tài liệu.** Cài đặt APK dạng update in-place (`install-multiple -r -d`), bảo toàn 100% dữ liệu/settings/favorites/recents; chụp đầy đủ artifact bằng chứng cho từng màn hình trên cả hai bề mặt Phone và DHU.

## Open questions

- Không có câu hỏi chặn việc lập kế hoạch. NIGHT mặc định và bốn/sáu tổng ô là lựa chọn đề xuất đã ghi trong spec; xác nhận hoặc chỉnh trước bước UI, không tự thay scope trong lúc triển khai.
- Source/license/revision của phần CarStream nhập code và propagation nếu phone/AA chạy khác process phải xác minh trong bước baseline; nếu không đủ nguồn, hiện thực nguyên tắc bằng model/component Fermata và ghi rõ không port nguyên code.

## Chi tiết kỹ thuật theo chặng

### Chặng 1 — Baseline / inventory

Phụ thuộc: audit `aa-glass-theme-completion-audit-2026-10-13.md`, bản đồ `glass-whole-app-port-map-2026-10-13.md`. Thu ảnh baseline của đúng APK/HEAD cho bốn tổ hợp Glass phone/AA Day/Night và mixed themes. Chưa có preference manual thì ghi rõ ảnh baseline đang dựa vào uiMode; không gọi nó là nghiệm thu manual.

Theo nguồn donor, port chỉ được ghi khi biết đoạn nào và dependency nào đã thay. Đừng ghi “80%” hoặc “100% UI” khi chưa có inventory/evidence.

### Chặng 2–3 — Theme contract

Files chính: MainActivityPrefs, InterfacePrefsBuilder, MainActivityDelegate, GlassThemeColors, GlassDimensions, GlassDrawables, ActivityDelegate utils, styles/strings và tests liên quan.

Tests bắt buộc: default/invalid/manual persistence; cùng mode ở hai host; theme gating độc lập; uiMode trái lựa chọn vẫn palette đúng; system/đèn/giờ không ghi preference; backup/restore; đổi khi modal/form/player đang mở. Phát hiện renderer capture palette trong constructor và đường native/XML fallback, không chỉ hàm factory.

Khi recreate là lựa chọn nhỏ nhất: kiểm tra draft/state và binder ownership trước khi dùng; không thêm cơ chế render song song chỉ để tránh recreate.

### Chặng 4 — Common UI

Files chính: FermataToolBarView/NavBarView và mediators; PhonePlaybackCarHeaderController/PhoneSupportBannerController/PhoneBottomMenuController; MediaItemView/MediaLibFragment; GlassMediaLibDecorator/GlassSettingsDecorator; utils PreferenceView/OverlayMenuView/OverlayMenuItemView/DialogView/GenericDialogFragment/FilePickerFragment.

Caller riêng: GlassShortcutPickerDialog/GlassDashboardController, AudioEffectsScreenView, CarFinderFragment/SettingsDialog; PodcastSourceDialog/OpdsSourceDialog/AudiobookshelfSourceDialog và TV/Radio/SMB/SFTP provider forms.

Test ít nhưng có ý nghĩa: title/subtitle day/night, selected/disabled/focus, form với password/validation/cancel, file path input, menu/custom dialog và list recycling sau đổi mode. Không thay native widget toàn app khi style đã đáp ứng.

### Chặng 5 — Dashboard và dữ liệu

Files chính: GlassDashboardLayout/Controller, GlassMediaPlayerView/Controller, GlassNavWeatherView/OpenMeteoClient và quickbar classes; nguồn location/playback/recents hiện có.

Tests ưu tiên: split true → false restores visible bounds; nhỏ/thấp/font lớn không overlap; artwork đúng ngay notify đầu; callback cũ không ghi item mới; duration zero/live không giữ giá trị cũ; seek đúng owner; weather không hard-code Hà Nội, missing permission/manual location update/stale/error; forecast qua ngày; clock qua phút/ngày; đổi số slot giữ cấu hình.

Không áp dụng quy tắc position chỉ tăng; seek ngược phải được phản ánh. Không port MediaHub để đọc session bên ngoài thay binder nội bộ.

### Chặng 6 — Special surfaces

Tận dụng presentation/row/dialog đã chuẩn hóa; sửa source-specific badges/controls thay vì dựng màn mới cho từng addon. Với keyboard AA chỉ thay presentation, giữ editor/model/back/IME. Test mode đổi khi keyboard mở, focus còn đúng và draft không mất.

Fullscreen event test: controls ẩn → single tap → chỉ hiện; subsequent tap theo hợp đồng → pause đúng một lần; click button không bị parent gesture pause thêm; drag/seek không bị tap handler xử lý nhầm. Gating Glass và hồi quy theme khác phải rõ.

### Chặng 7–8 — Validation / rollout

Chạy baseline hiện có trước thay đổi; baseline audit trước là 70 test pass nhưng nhiều test không chứng minh UX. Khi sửa logic, thêm test tái hiện trước/sau, chạy suite liên quan rồi required CI checks. Không lặp toàn bộ tests sau thay đổi thuần tài liệu.

Không dùng `adb uninstall` trên app người dùng để nâng cấp. Kiểm tra giữ dữ liệu với install/update; ca fresh install trên môi trường/dữ liệu thử riêng. Không ký/publish trong scope kế hoạch. Mỗi bản nghiệm thu ghi commit, variant/APK, device/AA host, viewport dp, density, font scale và manual appearance.

## Definition of done

Đủ G01–G07 và V01–V08; C01–C16 có evidence cho screen/state/modal; không còn chữ/icon bị mất tương phản hoặc native controls lấy palette tự động; không mất draft/dữ liệu/phát nhạc khi chuyển mode; không còn lỗi P1 của audit; không có migration Glass tác động ngầm theme khác. Đánh giá hằng ngày/release dựa trên runtime và required checks, không chỉ screenshot đẹp hoặc số file đã sửa.

Kế hoạch chưa được thực thi. Chưa ước lượng giờ/ngày vì phụ thuộc mức độ phục hồi source donor và số lỗi runtime trong inventory.
