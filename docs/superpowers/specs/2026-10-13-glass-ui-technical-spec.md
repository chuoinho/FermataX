# Đặc tả kỹ thuật hoàn thiện Glass toàn app

Ngày: 2026-10-13. Nhánh mục tiêu: `feat/aa-glass-theme`. Tài liệu này thay thế các đề xuất trước về Glass day/night tự động. Đây là đặc tả triển khai, chưa phải mô tả code đã hoàn tất.

## 1. Hợp đồng sản phẩm

| ID | Yêu cầu bắt buộc |
|---|---|
| G01 | Chỉ dùng presentation Glass khi theme của bề mặt hiện tại là Glass. Không thay giao diện Classic/Material/Modern bằng Glass. |
| G02 | Day/Night do người dùng chọn. Chỉ có hai lựa chọn **Sáng / Tối**; không có Auto/System, không đổi theo giờ, cảm biến, đèn xe hoặc `Configuration.uiMode`. |
| G03 | Một lựa chọn Glass Day/Night dùng chung cho phone và AA của cùng app. Chọn theme phone/AA vẫn độc lập qua preferences hiện có. Bề mặt dùng theme khác không bị đổi palette. |
| G04 | Lưu lựa chọn qua khởi động lại, reconnect AA, đổi orientation, cập nhật app và backup/restore. Đổi theme khác rồi trở lại Glass giữ lựa chọn. |
| G05 | Đồng bộ mọi UI do Fermata sở hữu: shell, nội dung, popup/form, trạng thái phụ và keyboard AA; không chỉ dashboard. |
| G06 | Phone và AA dùng chung semantic tokens, khác ngân sách bố cục/vùng chạm. Không ép bố cục xe lên phone. |
| G07 | Giữ dữ liệu, engine/ownership, điều hướng, validator, IME, callback và accessibility khi sửa presentation. |

UI hệ thống cấp quyền/SAF, website trong WebView và UI ngoài quyền kiểm soát của SDK là ngoại lệ được ghi rõ. Chrome do Fermata sở hữu quanh các nội dung này vẫn phải đồng bộ.

## 2. Preferences, migration và cơ chế theme

### 2.1 Nguồn cấu hình

Giữ `THEME_MAIN`, `THEME_AA`, `THEME_GLASS = 9`. Thêm **một** preference dùng chung `GLASS_APPEARANCE`: `DAY = 0`, `NIGHT = 1`; không thêm bản phone/AA riêng và không thêm AUTO.

Đề xuất mặc định khi chưa có giá trị: **NIGHT** để gần diện mạo Glass hiện hành. Đây là giá trị cố định, không đọc `uiMode` để suy ra. Giá trị không hợp lệ được normalize một lần về NIGHT và persist; không normalize liên tục trong mỗi lần draw.

Trong Settings → Giao diện, thêm “Chế độ theme kính” với Sáng/Tối khi phone hoặc AA đang chọn Glass. Subtitle nói rõ áp dụng cho Glass trên điện thoại và Android Auto. Thao tác từ host nào cũng ghi cùng preference.

**Opt-in:** giữ lựa chọn theme hợp lệ đã lưu. Với cài mới hoặc theme chưa có/không hợp lệ, dùng fallback không phải Glass thay vì tự kích hoạt Glass. Kiểm tra `THEME_AA` hiện mặc định Glass và `normalizeThemeAA` đang fallback Glass: cần điều chỉnh để phù hợp G01, nhưng không ghi đè lựa chọn Glass hợp lệ của người dùng hiện hữu.

### 2.2 Một nơi quyết định palette

`MainActivityPrefs` sở hữu giá trị manual; `GlassThemeColors` sở hữu palette; `MainActivityDelegate` nối palette/presentation tới utils. Renderer không tự suy ra day/night. `GlassDrawables` và widgets nhận appearance/palette đã resolve.

Trong Glass, thay mọi nhánh đọc `UI_MODE_NIGHT_MASK` để chọn màu bằng nguồn manual, gồm dashboard, media/weather/speedo/quick bar, dialog, decorators, shell, radar, widgets và factory drawable. Không xóa xử lý `uiMode` của theme khác hoặc dùng `AppCompatDelegate.setDefaultNightMode` toàn app chỉ để đổi Glass.

Utils không import ngược lớp Fermata. Mở rộng điểm nối theme hiện có trên `ActivityDelegate` chỉ đủ cung cấp semantic text/icon/state hoặc appearance mà component utils cần; không xây một framework theme mới.

### 2.3 Native style và cập nhật đang mở

Chỉ đổi drawable chưa đủ: EditText hint/cursor, checkbox/radio, native dialog, spinner/dropdown và text appearance phải dùng style/token manual. Rà nhánh `applyTheme` của delegate để native widget không tiếp tục lấy sáng/tối từ hệ thống khi Glass.

Đổi appearance: persist → phát preference change → refresh palette/view hoặc recreate host theo cơ chế hiện có. Chọn cách nhỏ nhất giữ đúng state. Không tạo trình phát mới, không restart service. Cả host phone/AA đang hoạt động nhận thay đổi; host chưa mở đọc preference mới khi tạo view. Nếu dùng nhiều process, xác minh propagation thực tế, không giả định listener trong process này cập nhật process khác.

Màn đang mở giữ route, vị trí cuộn, selection, playback và input draft. Dialog/form hoặc được cập nhật tại chỗ, hoặc khôi phục draft/validator/cancel sau recreate; không tự submit hoặc làm mất giá trị chưa lưu. Dọn listener/timer khi view bị hủy. Không có frame nội dung Glass với palette hệ thống xen vào khi tạo view/placeholder.

## 3. Design tokens và component

Mở rộng `GlassThemeColors`, `GlassDimensions`, `GlassDrawables` hiện có, không tạo bộ token thứ hai.

| Nhóm | Hợp đồng |
|---|---|
| Màu | Background/surface/elevated/input/border/divider; text primary/secondary/tertiary/on-accent; icon; action/selected/focus/disabled/error/success/warning. Day và Night có giá trị phù hợp riêng. |
| Accent | Cyan/blue là nhận diện. Text hành động trên nền sáng dùng biến thể đủ tương phản; không dùng cyan sáng cho mọi text. |
| Typography | Roles: page title, section, media title, body, summary/caption, time/status, validation. Dùng sp và giữ font scale; không hard-code trắng hoặc shrink title vô hạn. |
| Kích thước đề xuất | Phone title 20–24sp, body 16sp, summary 14sp; AA title 22–24sp, body 18–20sp, caption 14–16sp. Xác nhận bằng thiết bị/bounds trước chốt; không giảm controls để vừa chữ. |
| Spacing/radius | Một scale spacing 4/8/12/16/24dp và radius theo vai trò card/input/button/sheet. Không bọc mọi row bằng nhiều capsule; danh sách có thể dùng divider. |
| Vùng chạm | Mục tiêu Glass AA 76dp, phone 48dp cho action chính theo yêu cầu trước. Icon nhỏ hơn vùng chạm. Không ghi đè policy SmartTop legacy 56–76dp / delegated 64dp ngoài Glass. |
| Trạng thái | Normal, pressed, focused, selected, disabled và loading phải phân biệt, nhất quán. Action destructive dùng cả nhãn/ngữ cảnh, không chỉ màu. |
| Accessibility | Content description, checked/range semantics, focus order, TalkBack và AA rotary/key. Cancel drag không được commit như release. |

Không tạo hiệu ứng blur mới như một điều kiện hoàn tất. Giữ drawables/surface hiện có và đo hiệu năng; screenshot không chứng minh 60 FPS.

## 4. Phạm vi toàn app và cách triển khai

| ID | Thành phần | Yêu cầu kỹ thuật / điểm sửa |
|---|---|---|
| C01 | Topbar | `FermataToolBarView` + mediators: height/insets/title/action/focus theo host; bỏ header/action trùng. Nếu bỏ gear phải còn đường Settings rõ. Không thay route. |
| C02 | Navbar | `FermataNavBarView`, `NavBarMediator`, `PhoneBottomMenuController`: icon/label/selected/focus và insets; không thêm hệ điều hướng. |
| C03 | Phone shell | Header/banner/bottom composition; player và dashboard không lặp controls hoặc Settings. Banner chỉ chiếm diện tích khi hữu ích. |
| C04 | Text/icon/button | Thay màu hard-code bằng semantic tokens; dùng icon resources, typography roles; ellipsis/xuống dòng có chủ ý; button primary/secondary/icon/destructive/loading. |
| C05 | Menu/popup | Utils OverlayMenuView/ItemView: surface, header, choice/list rows, separator, icon/text/focus; day mode không dùng chữ trắng cố định. |
| C06 | Dialog | GlassDialogBuilder + caller: alert/confirm/input/single/multi-choice/custom/list; giữ validation và cancel. Nối shortcut/EQ/Car Finder đang bypass factory. |
| C07 | Settings/form | PreferenceView/decorator/GenericDialogFragment: section/summary/input/select/switch/number/action; form source có RecyclerView riêng cũng nhận style, không chỉ SettingsFragment. |
| C08 | Media lists/grid | MediaItemView/MediaLibDecorator: artwork/title/subtitle/progress/badge/checkbox/selection/context actions; bảo toàn adapter/recycling, loading/empty/error/retry. |
| C09 | File/network source | FilePicker path/breadcrumb/row/confirm; SMB/SFTP, TV M3U/Xtream/Stalker, Radio, Podcast, OPDS/Audiobookshelf input dùng component chung. Giữ auth/input type/IME/validator. |
| C10 | Player/EQ | ControlPanel/timeline/subtitle/audio track/video info/EQ band/curve/modal: palette manual, capability và focus. Không đổi semantics playback chỉ để tint. |
| C11 | Keyboard AA | CarKeyboardOverlay: panel/input/key/selected/pressed/disabled/day/night manual; giữ model/editor/IME/back và draft. |
| C12 | Onboarding/voice/status | InitialSetup/VoiceRecognitionSession/readiness: listening/loading/permission/error/success; banner/progress/snackbar/tooltip app-owned dùng token. |
| C13 | Backup/diagnostics/about | Áp dụng row/dialog/result states chung; giữ dữ liệu, validation và operation callback. Không viết lại backend. |
| C14 | Car Finder | Radar/map chrome/control/settings/confirmation; palette manual. Bản đồ hoặc tile bên ngoài cần scrim/control đủ tương phản, không chỉ đổi radar. |
| C15 | Web addons | YouTube/Web/TikTok/Stremio header/address/menu/capsule/loading/error/fullscreen do app sở hữu. Không sửa toàn bộ website thành Glass. |
| C16 | Cast/engines | Kiểm chứng controls app-owned và dialog chọn thiết bị; Exo/VLC dùng presentation chung. Ghi ngoại lệ SDK/system rõ. |

## 5. Dashboard, media và responsive

### 5.1 Quyết định bố cục

Dùng **bounds nội dung sau insets, đơn vị dp**, font scale và minimum size của child. Aspect ratio chỉ là tín hiệu phụ. Không chia đều ba thẻ mọi viewport. Media/nav có ưu tiên hơn thông tin speedo phụ khi không đủ chỗ.

WIDE/SQUARE/SPLIT có thể giữ enum hiện có nhưng phải resolve đủ geometry/visibility mỗi lần; split vào/ra khôi phục child đúng. Không đo/layout với chiều rộng hoặc chiều cao âm. Test actual child bounds, không chỉ enum.

Phone Glass: height theo nội dung, cuộn trang; weather được phép dài. AA: thông tin/action chính trong vùng đầu; giảm nội dung phụ trước khi giảm vùng chạm. Không mặc định thêm carousel hoặc bắt vuốt thẻ để tìm nút.

### 5.2 Hero media

Giữ controller/binder/engine của Fermata. Một snapshot nguyên tử chứa identity, title/artwork, trạng thái, timeline và capabilities; bỏ callback async cũ theo generation/identity. Duration unknown/live không giữ duration bài trước; seek ngược hợp lệ.

| Trạng thái | Presentation / action |
|---|---|
| Idle + recent | Nội dung gần đây, artwork fallback, tiếp tục phát/mở thư viện; không ghi NOW PLAYING sai. |
| Idle không recent | CTA mở thư viện, không timeline 00:00/00:00 hoặc transport vô nghĩa. |
| Connecting/buffering | Nhãn/progress rõ, giữ metadata phù hợp; action theo capability. |
| Playing/paused | Artwork/title ưu tiên; play/pause và thao tác phụ đủ vùng chạm; timeline có thời gian. |
| Live | Nhãn LIVE; không duration/seek giả; action nguồn hỗ trợ. |
| Error | Thông báo ngắn và retry/đổi nguồn khi có thể; không tiếp tục trông đang phát. |

Bố cục spacious/compact/short là policy của view hiện có, không ba controller. Port ý tưởng `CoDienNha.apNhacBoCuc` chuyển hàng transport theo **chiều rộng card**; title tối đa hai dòng khi đủ chiều cao, artwork cân đối và placeholder có chủ ý. Prev/next/favorite chỉ hiện hoặc enabled khi hợp đồng action cho phép.

Fullscreen: lần chạm đầu khi controls ẩn chỉ hiện controls; lần chạm tiếp theo theo hợp đồng pause đã yêu cầu. Phải test event sequence; không coi hai single tap là mặc nhiên double tap. Không thêm double-tap seek 10s từ tài liệu cũ. Thay đổi hành vi ngoài Glass, nếu cần, là task riêng.

### 5.3 Weather/navigation/clock/quick bar

Weather lấy location/cấu hình chung: chưa có quyền hướng dẫn cấp trên phone; phone đã set location thì AA refresh đọc lại trước khi quyết định mở detail. Có location label, update timestamp, loading/stale/error/retry. Hourly bắt đầu từ giờ hiện tại theo timezone địa điểm; phone đủ chi tiết và chiều cao, AA nhóm dự báo hữu ích vừa màn.

Navigation phân biệt thiếu quyền, chưa mở Maps, chưa có hướng dẫn và parse lỗi; giữ bridge/parser. Clock cập nhật theo phút/đổi ngày và định dạng giờ/locale, tách khỏi manual Day/Night.

Quick bar: màn nhỏ bốn ô, đủ chỗ sáu ô; số ô do width/touch budget quyết định, không widthPixels toàn thiết bị. **Quy ước đề xuất cho implementation:** bốn/sáu là tổng ô, gồm Addons/Exit; ô tùy chỉnh chưa vừa vẫn lưu và truy cập qua picker/menu. Nếu sản phẩm muốn bốn/sáu chỉ tính shortcut, điều chỉnh quy ước trước commit UI, không tự làm mất cấu hình. Không cần carousel ngang mặc định.

## 6. Port CarStream và ranh giới

Nguồn đối chiếu: `C:\Users\ttanh\.gemini\antigravity\brain\5e578c45-dc39-4d1a-955e-3fcae5f13a5a\scratch\carstream\src` — 14 file decompiled, thiếu Ui/Slider/Glyph/RadarView và model/helper liên quan.

Port chọn lọc: `CoDienBoCuc` đo chiều cao/visibility/ngân sách bố cục; `CoDienNha` media reflow và clock update; `DanDuong` lý do nav rỗng. Tham khảo Weather cache/location presentation và LuoiApp tile khi hữu ích.

Không copy nguyên literal float/decompile hoặc kéo dependency tree. Không port CarLauncherActivity, MediaHub, AppRepo, weather/GPS backend thứ hai, giá xăng, âm lịch hay ảnh xe không liên quan. Ghi nguồn/revision/license được xác minh cho phần nhập code; thư mục decompiled không tự chứng minh nguồn open source.

## 7. Tiêu chí nghiệm thu

| ID | Kiểm chứng |
|---|---|
| V01 | Phone Glass Day/Night và AA Glass Day/Night đúng manual preference kể cả khi `uiMode` hệ thống đối nghịch; giờ/đèn/cảm biến/reconnect không đổi lựa chọn. |
| V02 | Phone Glass/AA Classic và phone Classic/AA Glass: bề mặt không Glass giữ giao diện cũ. Cài mới/invalid theme không tự opt-in Glass. |
| V03 | Đổi manual mode khi đang ở list/form/dialog/player; giữ input draft, route/scroll và playback; cả host cập nhật hoặc restore đúng. |
| V04 | Khởi động lại, nâng cấp giữ dữ liệu, backup/restore và chuyển theme rồi quay lại giữ appearance. |
| V05 | Nội dung dài, không artwork, no recent, connecting/error/live, selection/disabled và font scale không overlap/clipping/action vô nghĩa. |
| V06 | Bounds AA mục tiêu 640×360, 800×480, 1024×600dp; square/ultrawide/split; phone 360/393/412dp portrait/landscape. Tất cả là viewport sau insets, không số pixel ảnh. |
| V07 | Font scale 1.0/1.3/1.5, focus/rotary/TalkBack/cancel drag và vùng chạm. Không bật Glass là không cài Glass listeners/styles. |
| V08 | Nâng cấp mặc định giữ dữ liệu; fresh install riêng. DHU/màn xe kiểm chứng resize/reconnect/gesture/frame time; screenshot không đủ chứng minh hiệu năng. |

Mỗi screen/state/modal có owner, trạng thái migration, ảnh day/night gắn commit/APK/viewport và test thao tác. Không đánh dấu xong vì có `isGlassTheme`, tint hoặc test tìm chuỗi source.

## 8. Các quyết định đề xuất chưa phải code đã làm

NIGHT mặc định cố định; preference Day/Night dùng chung; tổng bốn/sáu ô quick bar; kích thước chữ là mốc thiết kế cần kiểm chứng. Phone Glass là presentation của theme được chọn; không tự kích hoạt AA preview khi đổi palette. Chính sách SmartTop legacy và engine/ownership giữ nguyên ngoài phạm vi Glass.

Audit và tài liệu không thay source. Chưa kiểm chứng runtime toàn ma trận hoặc xác minh nguồn/license donor.
