# Bản đồ hoàn thiện Glass toàn app: port CarStream, sửa hiện có, viết mới

Ngày rà soát: 2026-10-13. App: `E:\antigravity\fermata`, nhánh `feat/aa-glass-theme` đã audit tại HEAD `ba3cb1ac`.

Nguồn đối chiếu trực tiếp: `C:\Users\ttanh\.gemini\antigravity\brain\5e578c45-dc39-4d1a-955e-3fcae5f13a5a\scratch\carstream\src`.

## Kết luận quyết định

**Nên port có chọn lọc các thuật toán trình bày dashboard; phần lớn việc đồng bộ toàn app là hoàn thiện component Fermata đang có.** Nguồn CarStream này không có bộ UI hoàn chỉnh cho settings, media library, input form, EQ, file picker hay bàn phím AA. Copy launcher không giải quyết các khoảng hở đó.

Ba hướng được phân biệt trong tài liệu:

- **Port có chọn lọc:** chuyển một ý tưởng/thuật toán cụ thể từ source CarStream sang component hiện tại, phục hồi code và điều chỉnh cho Fermata. Không copy nguyên file decompiled.
- **Hoàn thiện hiện có:** giữ logic và component của Fermata, sửa presentation/token/caller. Đây là hướng chủ đạo.
- **Viết presentation mới:** tạo nội dung/trạng thái/bố cục còn thiếu, ưu tiên thêm vào class hiện có. Không đồng nghĩa tạo một framework hoặc một class cho mỗi màn.

Không đặt mục tiêu tỷ lệ port 80%. Điều kiện thành công là toàn bộ UI do Fermata sở hữu tuân theo hợp đồng Glass, dùng được trên phone/AA và day/night, gồm cả các luồng phụ.

## 1. Nguồn CarStream thực sự cung cấp gì?

Thư mục được chỉ định có **14 file Java**. Code chứa dấu hiệu decompile: `public static synthetic`, kiểu biến sai, `void` dùng như object, float hiển thị thành số nguyên thô, tham chiếu anonymous/inner class không có file đi kèm. Ví dụ `CoDienMau.java:3` có hằng float `16944`; `CoDienBoCuc.java:90` có `void v0_0 = this`; `GiaXang` có `None` và cú pháp static constructor không hợp lệ Java. Không sử dụng trực tiếp các literal này làm dp/sp.

**Không có trong đúng thư mục này:** `Ui.java`, `Slider.java`, `Glyph.java`, `RadarView.java`, `AppRepo.java` và nhiều class/inner class được tham chiếu. Các tài liệu trước đó trỏ vào `build/decompiled/carstream/...` là một nguồn khác; không được lấy file ở đó rồi ghi như thể nó có trong `scratch/carstream/src`.

| File nguồn | Chức năng và bằng chứng | Quyết định cho Fermata |
|---|---|---|
| `CoDienBoCuc.java` | `do_` dòng 37 đo chiều cao tự nhiên; `onLayout` dòng 88 tính theo density, khôi phục visibility và phân bổ không đều | **Port có chọn lọc** nguyên tắc đo theo nội dung, visibility theo mode và ngân sách không gian. Không bê layout launcher: SPLIT của nguồn ưu tiên app grid, khác yêu cầu media/nav của Fermata. |
| `CoDienNha.java` | `apNhacBoCuc` dòng 245 dùng chiều rộng thực của thẻ nhạc để chuyển transport sang hàng hai; `veNhac` dòng 714; `datNhac` dòng 920; `nhipDongHo` dòng 1176 | **Port có chọn lọc** reflow media, phân biệt nhãn playing/paused và cập nhật clock khi giá trị đổi. Xây idle/recent/live/error riêng cho Fermata. |
| `MediaHub.java` | Đọc active MediaSession qua MediaSessionManager, chọn session và gửi transport; `start` dòng 360 | **Không port controller**. Fermata có binder/engine/timeline/ownership riêng; chỉ đối chiếu cách xử lý metadata khi cần, tránh tạo nguồn playback cạnh tranh. |
| `CoDienMau.java` | Day/night tách text/surface/border; `cua` dòng 87 kết hợp chế độ và cấu hình | **Hoàn thiện token đã có**, không tạo bộ màu thứ hai. Mở rộng semantic màu chữ/icon/trạng thái và sửa caller hard-code. |
| `CarLauncherActivity.java` | `buildGanDay` dòng 208; shell/status/weather/media; lifecycle kết nối các dịch vụ | Chỉ **tham khảo composition**, không port Activity hoặc lifecycle launcher. “Gần đây” ở đây là app gần dùng từ AppRepo, không phải recent media của Fermata. |
| `LuoiApp.java` | Grid icon/label, `coBaoNhieuCot` dòng 35 dựa trên display width; tối thiểu ba cột | Tham khảo tile, **hoàn thiện GlassAddonsCatalog hiện có**. Không copy PackageManager/AppRepo hoặc rule ba cột, vì viewport split/phone nhỏ có thể không đủ. |
| `QsPanel.java` | `build` dòng 44 tạo tile Wi-Fi/Bluetooth/night + brightness/volume; dùng Ui/Slider/VolumeCtl thiếu trong thư mục | Tham khảo visual của sheet/tile **nếu cần cho thao tác hiện có**. Không port OS toggles và không thêm một Quick Settings mới chỉ để đồng bộ app. |
| `Weather.java` | Đọc/ghi cache, gom forecast, lấy location, tên địa điểm, mưa/AQI; `nap` dòng 332 | Tham khảo cách trình bày dữ liệu/location/cache. **Tích hợp dịch vụ Fermata**, không port thêm weather backend thứ hai, endpoint proxy hoặc lifecycle riêng. |
| `DanDuong.java` | Parser notification, `lyDoTrong` dòng 223 phân biệt thiếu quyền, chưa mở Maps, thấy ứng viên nhưng chưa đọc được | **Port có chọn lọc trạng thái rỗng/lý do** và mapping presentation vào nav hiện có. Không thay toàn bộ notification parser đang có. |
| `Speedo.java` | GPS/trip state và lưu dữ liệu | **Giữ GlassSpeedoTripController hiện có**; nguồn chủ yếu là logic, không phải component UI toàn app. Kiểm chứng visibility/readability. |
| `AnhXe.java` | Load/cache/crop/EXIF và lưu ảnh xe | Không cần cho đồng bộ. Chỉ dùng khi sản phẩm yêu cầu chọn/crop ảnh xe riêng; Fermata đã có đường quản lý media/ảnh. |
| `KhungXe.java` | View ảnh xe, pan/scale và chỉnh vị trí | Không thay thế radar/map của Car Finder. Không port cho scope hiện tại. |
| `AmLich.java` | Chuyển ngày âm lịch | Không liên quan đồng bộ giao diện; không port. |
| `GiaXang.java` | Dịch vụ giá xăng, fallback dữ liệu đóng dấu ngày | Không liên quan đồng bộ; không port. |

DanDuong/Weather và các class khác còn nhiều model/inner class/helper chưa có. Nếu chuyển thuật toán, viết lại bằng model có sẵn của Fermata, tránh truy kéo toàn bộ cây dependency decompiled. Trước khi nhập code ngoại bộ, ghi nguồn/revision/license được xác minh; tài liệu này chỉ xác nhận nội dung local, chưa xác minh quyền tái sử dụng.

## 2. Bản đồ toàn app

Các trạng thái ghi “cần kiểm chứng” là phạm vi phải hoàn thiện, không phải khẳng định mọi trạng thái đó đang lỗi. Việc fragment không gọi Glass riêng cũng không chứng minh thiếu theme: nhiều màn nhận presentation qua component chung.

| Khu vực / điểm đến | Hiện trạng xác nhận hoặc cần kiểm chứng | Hướng xử lý và nguồn |
|---|---|---|
| Dashboard AA: `GlassDashboardLayout` | Chia theo tỷ lệ và đều cột/hàng; lỗi visibility khi thoát split | **Port chọn lọc** đo tự nhiên/visibility từ `CoDienBoCuc`; bổ sung policy theo card bounds, minimum content/touch budget. Giữ thứ tự ưu tiên media/nav của Fermata. |
| Dashboard phone / preview | Thẻ bị ép theo ngân sách chiều cao cố định; shell nhiều tầng | **Viết bố cục phone còn thiếu** trong component hiện có: chiều cao theo nội dung, cuộn trang, weather được dài. CarStream launcher không cung cấp hợp đồng phone phù hợp. |
| Hero media: `GlassMediaPlayerView` | Artwork 72dp, title một dòng, controls 40–44dp, ProgressBar chỉ báo | **Port reflow** từ `CoDienNha.apNhacBoCuc`; **viết mới presentation các trạng thái** idle/recent/loading/error/live, thêm seek theo capability nếu có. Bố cục compact/spacious/short dùng actual card bounds. |
| Media controller | Snapshot/artwork thứ tự sai, timeline callback cũ chưa được loại | **Sửa controller hiện có**, giữ media engine/binder/ownership Fermata. **Không port MediaHub**: nguồn đó đọc active sessions của launcher, không tương đương media nội bộ và sẽ tạo quyền/nguồn trạng thái khác. |
| Weather card + weather chip | Hà Nội hard-code; hourly từ đầu ngày, có vuốt ngang; lỗi không phản hồi | **Sửa/tích hợp nguồn dữ liệu chung**, tham khảo Weather cache/location presentation. **Viết mới UI state** permission/location/stale/error và bố cục phone đầy đủ. Không tạo backend cạnh tranh. |
| Navigation card | Có notification bridge và nav/weather switching | **Port chọn lọc** ý nghĩa `DanDuong.lyDoTrong`; thêm UI hướng dẫn cấp quyền trên phone, chưa có navigation, parse lỗi và action phù hợp. Dùng bridge hiện có. |
| Clock/date/status header | Clock/date tạo một lần; còn gear và các vùng header trùng | Tham khảo `CoDienNha.nhipDongHo`; **hoàn thiện lifecycle/locale** và composition shell Fermata. Status chip chỉ giữ thông tin cần thiết. |
| Speed/trip | View/controller Glass đã có | **Hoàn thiện hiện có**, sửa visibility và bố cục chữ; không port GPS service thứ hai. |
| Quick bar + shortcut picker | Sáu ô cố định; picker/replace/remove dùng AlertDialog trực tiếp | **Hoàn thiện manager/view**, chốt rule nhỏ bốn/đủ sáu và cách đếm Addons/Exit; nối dialog chung. QsPanel không phải nguồn shortcut picker tương đương. |
| Addons catalog | Grid Glass đã có | Tham khảo tile `LuoiApp`, **sửa grid hiện có theo chiều rộng container**, không display width toàn thiết bị; hoàn thiện disabled/install/readiness/empty. Không port app launcher repository. |
| Phone header/banner/bottom nav | Đã có Glass gate, chủ yếu đổi surface | **Hoàn thiện composition** và selected/focus/text/icon tokens. Một vùng điều hướng/ngữ cảnh rõ, tránh Settings trùng. Không port CarLauncherActivity. |
| AA toolbar/nav rail/context navigation | Có Glass trong toolbar/nav/menu; các mediator vẫn dùng cấu trúc chung | **Hoàn thiện component hiện có**, kiểm tra focus/rotary/selected/icon/touch targets và split insets. Nguồn launcher không thay thế host AA. |
| Folders/Favorites/Playlists/Recent | Kế thừa `MediaLibFragment` và `MediaItemView`; có decorator nhưng state tint hard-code trắng | **Hoàn thiện media row/grid dùng chung**, title/subtitle/artwork/checkbox/progress/badges/menu, day/night và font scale. CarStream không có media library tương đương. |
| TV: M3U/Xtream/Stalker | `TvFragment` kế thừa MediaLib; nguồn/provider riêng | **Sửa presentation chung** cho danh sách/group/channel; hoàn thiện form thêm/sửa nguồn và loading/error/refresh. Giữ parser/auth/source model. Không có nguồn port tương đương trong 14 file. |
| Radio | Kế thừa MediaLib; `RadioSourceProvider` dùng VfsProviderBase | **Hoàn thiện row, live state, source form và search/menu** qua component chung. Không port Weather/launcher để thay một media source screen. |
| Podcast | MediaLib + `PodcastSourceDialog` tạo RecyclerView/PreferenceViewAdapter trong GenericDialogFragment | **Hoàn thiện form host/rows** và episode/download/played/empty/error presentation. Dialog export đã gọi factory, không cần viết lại callback export. |
| Audiobook | OPDS/Audiobookshelf source dialogs cũng tạo form RecyclerView riêng | **Hoàn thiện form dùng chung**, chapter/progress/download/resume presentation. Giữ login, URL/password/input/validation và cancel. Không có CarStream tương đương. |
| Settings toàn app | Decorator + `PreferenceView`; title/subtitle trắng cố định | **Hoàn thiện component preference**, section/summary/input/toggle/number/select/action. Form nguồn không tự nhận `GlassSettingsDecorator` chỉ vì dùng PreferenceViewAdapter. Không cần settings framework mới. |
| Backup/restore, diagnostics, readiness/about | Dùng preference rows và nhiều `UiUtils.showAlert` | **Hoàn thiện chung**. `UiUtils.showAlert` đã đi qua dialog factory nên không coi mọi alert là bỏ sót. Kiểm tra progress/result/error/destructive confirm và form tùy chỉnh. Không thay logic backup. |
| EQ/audio effects | Main surface/curve có Glass; popup virtualizer/numeric vẫn AlertDialog; band có màu hard-code | **Hoàn thiện EQ hiện có**, action/modal/day-night và accessibility. Không có EQ trong thư mục donor. Không ép port Slider thiếu source. |
| Player audio/video/fullscreen | ControlPanel có Glass surface/tint nhưng gesture thay chung; timeline native | **Hoàn thiện controls hiện có**, capabilities/live/seek/focus/first-tap contract; giữ ownership và logic video. QsPanel volume slider không phải timeline media. |
| Subtitle/audio track/video info | Có Glass ở SubtitlesView/VideoInfoView; các lựa chọn/menu còn phải kiểm chứng | **Hoàn thiện presentation/token** và list selection/input states; giữ file/track logic. Nguồn donor không có màn tương đương. |
| Menu/context menu/dialog/dropdown | Utils overlay có Glass nhưng chữ trắng cố định; AlertDialog trực tiếp bypass factory | **Hoàn thiện hệ thống modal hiện có**, list/choice/custom view/buttons và tất cả caller. Không dựng GlassQuickSheet mới chỉ để đổi tên hệ thống menu. |
| File picker / SMB / SFTP | FilePicker có surface Glass, input path vẫn background resource cũ; các provider dùng form utils | **Hoàn thiện path/breadcrumb/row/input/confirm/error** và form nguồn qua utils. Giữ navigation/selection/auth. Android SAF bên ngoài app là ngoại lệ hệ thống. |
| Onboarding / voice | Có Glass gate nhưng button nền night cố định, voice text sáng hard-code | **Hoàn thiện hiện có**, semantic tokens và permission/loading/error/listening states. Giữ workflow cấp quyền. Không có donor tương đương. |
| Bàn phím AA | `CarKeyboardOverlay` dùng nhiều màu panel/input/key hard-code, không có Glass gate tại các điểm đọc | **Viết presentation Glass còn thiếu trong overlay hiện có**, giữ keyboard model/editor/IME/back/focus behavior. Không có keyboard trong nguồn donor. |
| Car Finder | Radar Glass đã có, map và dialog/cài đặt là luồng riêng | **Hoàn thiện radar/map chrome/control/dialog**. Không port KhungXe/AnhXe để thay bản đồ. RadarView không tồn tại ở đúng thư mục này. |
| YouTube/Web/TikTok/Stremio | Toolbar/TikTok capsule có Glass; JS dialogs dùng factory; browser screens inflate XML riêng | **Hoàn thiện chrome do app sở hữu**: header/address/menu/loading/error/capsule/fullscreen. Website trong WebView không phải phần để port Glass. |
| Cast, ExoPlayer, VLC | Cast chọn thiết bị có thể gồm UI SDK; engines chủ yếu không sở hữu màn riêng | **Theo caller để style app-owned controls**, engine dùng controls chung. Không port media engine; UI hệ thống/SDK không kiểm soát được phải ghi ngoại lệ. |

## 3. Các điểm nối quan trọng đã xác nhận

### A. Day/night chưa thống nhất ở component chung — sửa trước

- `depends/utils/src/main/java/me/aap/utils/pref/PreferenceView.java:857–859`: Glass title trắng, subtitle trắng alpha.
- `depends/utils/src/main/java/me/aap/utils/ui/menu/OverlayMenuItemView.java:109–110,243–244`: text trắng hoặc cyan cố định, trong khi menu surface chọn day/night.
- `fermata/src/main/java/me/aap/fermata/ui/view/MediaItemView.java:518–522`: title trắng cố định.
- `InitialSetupFragment.java:145,149`: pill truyền `true` cho night; `VoiceRecognitionSession.java:170` dùng text màu sáng cố định.

**Thay đổi:** lấy semantic colors từ hệ thống Glass hiện có qua điểm nối utils phù hợp; không import lớp Fermata trực tiếp vào utils làm đảo chiều dependency. Kiểm tra normal/selected/disabled/error/focus. Sửa ba component row/menu/preference sẽ bao phủ nhiều màn hơn việc port từng fragment.

### B. Factory chưa bao phủ mọi dialog

Bypass xác nhận: shortcut picker/replace/remove; EQ virtualizer/numeric; Car Finder confirmations/settings. Dùng lại factory/overlay hiện có, bảo toàn validation và callback.

Các đường đã có: `UiUtils.showAlert` tại dòng 91–93 gọi `ActivityDelegate.createDialogBuilder`; JS alert/confirm/prompt của Web và podcast private export cũng gọi factory. Không cần viết lại toàn bộ đường này; cần hoàn thiện presentation của builder và custom content.

`DialogView` có alertTitle và android message/button IDs được builder truy cập; cần kiểm chứng style các custom view, single-choice/multi-choice/list, không chỉ title/message/button.

### C. Form nguồn không phải SettingsFragment

`PodcastSourceDialog.java:53–59`, `AudiobookshelfSourceDialog.java:50–56`, `OpdsSourceDialog.java:48` tự tạo RecyclerView với PreferenceViewAdapter trong GenericDialogFragment. Chúng không đi qua `SettingsFragment.onViewCreated` nơi gắn GlassSettingsDecorator.

**Thay đổi:** style form host/row chung tại điểm có thể bao phủ những caller đó, hoặc áp dụng decorator đã có cho đúng RecyclerView. Giữ URL/user/password, IME next/done, scroll khi keyboard mở, validator và promise cancel. Thay mọi form bằng dialog mới sẽ tăng rủi ro mà không giúp đồng bộ.

### D. Slider không cần port tiếp chỉ để thay tên

`MainActivityDelegate.createSlider` đã tồn tại, nhưng `PreferenceView.setNumberPreference` tại dòng 590 chỉ tint SeekBar, không sử dụng factory slider. Do đó “có GlassSlider class” không chứng minh settings đã migration widget. ControlPanel/EQ cũng có native slider.

**Quyết định:** style native SeekBar nếu đủ hợp đồng; custom GlassSlider chỉ dùng khi cần hình thức/hành vi đặc biệt và đã có cancel/keyboard/rotary/accessibility. Source Slider gốc không có trong thư mục người dùng chỉ định nên không ghi nhiệm vụ “port Slider từ src này”.

## 4. Phần cần viết mới thực sự

Các mục sau là presentation chưa có tương đương phù hợp trong nguồn donor; phần lớn có thể bổ sung vào class hiện có:

1. **Hero state presentation:** chưa phát có recent/resume; chưa có recent mở library; loading/buffering; error/retry; live; playing/paused theo capability. Dùng dữ liệu recents/playback Fermata, không dùng recent app của launcher.
2. **Policy bố cục media/dashboard:** ngân sách width/height/font scale/touch target theo container; reflow kế thừa ý tưởng donor nhưng có fallback cho màn thấp, split và phone. Không thu nhỏ nút để ép đủ ba cột.
3. **Weather/nav state presentation:** permission trên phone, chưa có location, stale/cache/error và navigation unavailable. Phone được hiển thị chi tiết dài; AA không cần vuốt để thao tác chính.
4. **Presentation Glass của keyboard AA:** theme panel/key/input/states/day-night trong overlay đang có.
5. **Trạng thái đặc thù media source:** channel/live, episode download/played, chapter/resume, source login/loading/error; dùng rows/badges/actions chung, chỉ thêm bố cục riêng khi dữ liệu thực sự khác.

Không cần viết mới navigation framework, backup framework, preference framework, dialog framework, media service, weather service hay addon repository.

## 5. Thứ tự triển khai đề xuất

| Thứ tự | Gói công việc | Điều kiện hoàn tất |
|---|---|---|
| 1 | Token day/night và component chung: preference/media row/menu/dialog | Không còn text/icon hard-code trái semantic theme; normal/selected/disabled/focus đọc được ở cả hai chế độ. |
| 2 | Dashboard/media/weather/navigation | Actual card bounds quyết định bố cục; split vào/ra đúng; idle có recent; weather đúng location; lỗi/quyền/live có UI riêng. |
| 3 | Modal và input form toàn app | Shortcut/EQ/Car Finder, source TV/Radio/Podcast/Audiobook/SMB/SFTP, path file picker dùng hợp đồng chung; giữ validator/IME/cancel. |
| 4 | Phone/AA shell, keyboard, playback controls | Một phân cấp header rõ, không lặp action; đúng vùng chạm/focus; first-tap gesture và seek capability được test hành vi. |
| 5 | Các màn phụ và chrome addon | Onboarding/voice/backup/diagnostics/subtitle/video info/car finder/web chrome đủ các states; ngoại lệ system/SDK/WebView ghi rõ. |
| 6 | Kiểm chứng toàn app | Mỗi screen/state/modal có ảnh và test thao tác phone/AA/day/night, viewport nhỏ/split/font scale; nâng cấp giữ dữ liệu; DHU/xe thật. |

Chốt lại các yêu cầu trước khi sửa: rule quick bar nhỏ bốn/đủ sáu đang đếm shortcut hay tổng ô; AA 76dp/phone 48dp còn là hợp đồng không; phone Glass chính thức hay preview; loại bỏ gear trên Modern có áp dụng cho Glass không. Không dùng những xung đột này làm lý do bỏ qua các lỗi đã xác nhận.

## 6. Gate đồng bộ toàn app

Mỗi mục trong bảng toàn app cần chứng minh: typography/spacing/icon/surface đúng token; day/night không có vùng chữ mất tương phản; content dài và font scale không đè controls; touch/focus/rotary hoạt động; loading/empty/error/selected/disabled không quay về presentation cũ; modal/form dùng cùng ngôn ngữ thiết kế.

Đồng bộ không có nghĩa tất cả đều là card bo tròn hoặc đều dùng cùng kích thước. Dashboard, danh sách, form và player có cấu trúc phù hợp công việc riêng; AA và phone có ngân sách khác nhau.

## Phạm vi và giới hạn

Rà inventory giao diện main app, auto keyboard, modules TV/Radio/Podcast/Audiobook/Web và đường UI chung trong depends/utils; đối chiếu 14 file donor bằng cấu trúc và các đoạn liên quan. Không đọc mọi dòng backend/engine/network vì không thuộc quyết định port UI. Không chạy source donor, không nhập code hoặc sửa app. Không chạy lại test vì chỉ bổ sung báo cáo; 70 test pass ở audit trước vẫn không chứng minh migration hoàn tất. Chưa kiểm chứng runtime mọi màn hoặc source/license donor.
