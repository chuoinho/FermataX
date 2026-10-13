# Plan

Hoàn thiện Glass toàn app bằng component Fermata hiện có, port có chọn lọc bố cục dashboard và bổ sung presentation còn thiếu. Day/Night do người dùng chọn, một preference dùng chung phone/AA; migration Glass không thay giao diện hoặc hành vi theme khác.

## Scope

- In: đặc tả G01–G07, component C01–C16 và nghiệm thu V01–V08 trong `glass-ui-technical-spec-2026-10-13.md`; shell, text/icon/button, list/form/menu/dialog, player/dashboard, phone/AA và manual appearance.
- Out: framework UI mới, dịch vụ playback/weather/GPS thay thế, chức năng giá xăng/âm lịch/launcher, website/system UI ngoài app, release ký/publish. Không sửa hoặc gỡ app của người dùng để chuẩn bị kiểm thử.

## Action items

- [ ] **Chốt baseline và inventory.** Kiểm tra HEAD/diff/AGENTS/MASTER_CONTEXT, commit hai tài liệu vào repo khi triển khai; lập bảng screen + state + modal cho C01–C16. Chốt NIGHT mặc định và quy ước bốn/sáu tổng ô; ghi khác biệt Glass so với SmartTop legacy. Gate: mọi mục có owner/component/caller/tiêu chí test, không chỉ checklist class.
- [ ] **Thêm manual Glass appearance và opt-in.** Sửa `MainActivityPrefs`, `InterfacePrefsBuilder`, theme apply/preference listener trong `MainActivityDelegate`; thêm strings; giữ THEME_MAIN/AA độc lập, một GLASS_APPEARANCE chung. Sửa default/fallback AA tự chọn Glass cho trường hợp chưa chọn, giữ giá trị hợp lệ đã lưu. Gate: V01/V02/V04 bằng test preferences/host với uiMode đối nghịch.
- [ ] **Hợp nhất palette/style và cập nhật lifecycle.** Sửa `GlassThemeColors/Dimensions/Drawables` và bridge `ActivityDelegate` utils; bỏ nguồn uiMode cho Glass, sửa native style/hint/cursor/state và observer/recreate. Gate: đổi mode ở phone/AA đang mở giữ playback/form/route/scroll; tắt Glass không còn decoration/listener Glass. Không đảo dependency utils → Fermata.
- [ ] **Đồng bộ component chung.** Sửa toolbar/navbar/phone shell, PreferenceView, MediaItemView, decorators, OverlayMenuView/ItemView, DialogView/GlassDialogBuilder, FilePicker path và input. Nối AlertDialog bypass shortcut/EQ/Car Finder; bổ sung form host cho Podcast/OPDS/Audiobookshelf và nguồn VFS. Gate: C01–C09 có normal/selected/disabled/error/focus day/night; giữ validator/IME/cancel và adapter recycling.
- [ ] **Hoàn thiện dashboard/media/weather.** Port nguyên tắc đo/visibility từ CoDienBoCuc và media reflow từ CoDienNha sau xác minh provenance; sửa split, actual bounds, hero state/recent, snapshot/artwork/callback identity, seek capability, weather location/cache/hourly/nav reasons/clock và quick bar. Gate: không còn P1 audit; test child bounds, split vào/ra, bài A callback sau B, live duration, idle/recent và location cập nhật từ phone.
- [ ] **Hoàn thiện presentation đặc thù còn lại.** Sửa ControlPanel/EQ/subtitle/video info, AA keyboard, onboarding/voice, Car Finder/map chrome, backup/diagnostics và TV/Radio/Podcast/Audiobook/Web addon states. Giữ engine/controller/source logic; chỉ viết presentation mới khi component chung chưa đáp ứng. Gate: C10–C16 đủ inventory, ngoại lệ SDK/website/system ghi rõ, gesture kiểm thử bằng event thật.
- [ ] **Kiểm chứng ma trận và hồi quy.** Chạy test phù hợp Auto/Mobile, architecture/single-writer guards và Lint theo CI; dùng hành vi/bounds assertions, không test source-string để chứng minh UX. Kiểm tra V01–V08, mixed themes, font scale, keyboard/insets, reconnect, portrait/landscape/split, focus/rotary và các form/modal đang mở. Gate: mọi lỗi có test tái hiện và bằng chứng sửa; ảnh gắn HEAD/APK/viewport/appearance.
- [ ] **Đóng release candidate và tài liệu.** Build universal APK theo pipeline repo; kiểm tra upgrade giữ dữ liệu trước, fresh install riêng; chạy DHU/màn xe và đo frame time. Ghi kết quả/ngoại lệ/rủi ro, cập nhật MASTER_CONTEXT và spec thực tế. Rollback presentation bằng lựa chọn theme trước, không reset preferences/media. Gate: không còn P1, P2 ảnh hưởng đọc/chạm/focus đã giải quyết, đủ bằng chứng thiết bị; publish là bước riêng.

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
