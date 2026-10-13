# Audit phương án hoàn thiện `feat/aa-glass-theme`

Ngày: 2026-10-13. Repository: `E:\antigravity\fermata`. HEAD được audit: `ba3cb1ac`.

## Kết luận

**Chưa nên coi nhánh này là release candidate cho sử dụng hằng ngày.** Nền tảng theme, token và các điểm tích hợp đã hình thành; hướng tái sử dụng UI hiện có hợp lý. Tuy nhiên, phương án hiện tại ưu tiên phủ màu/surface toàn app trước khi chứng minh dashboard, media, weather và thao tác trên xe hoạt động đúng.

Nên giữ hệ thống Glass hiện có, dừng mở rộng phạm vi, hoàn thiện các luồng chính theo thứ tự: hợp nhất yêu cầu → sửa dữ liệu và playback → bố cục thích nghi → thao tác và accessibility → đồng bộ màn phụ → kiểm chứng nâng cấp và thiết bị xe. Không cần viết lại toàn bộ UI hoặc port thêm một framework để xử lý các vấn đề dưới đây.

## Phạm vi và bằng chứng

- So sánh nhánh với `main`: 101 file, khoảng 7.768 dòng thêm và 72 dòng xóa; đọc các phần liên quan dashboard, media controller/view, weather, quick bar, theme, widget, shell, settings và player chung.
- Đọc đặc tả AA Glass ban đầu và hai tài liệu comprehensive trong `docs/superpowers`. Hai tài liệu comprehensive xuất hiện dưới dạng **untracked** tại thời điểm kiểm tra: chưa thể coi là hợp đồng thiết kế đã được version cùng HEAD.
- Chạy `:fermata:testAutoDebugUnitTest --offline` với nhóm `me.aap.fermata.ui.glass.*`, `ControlPanelViewGestureTest`, `SubtitlesAndVideoInfoGlassTest`: **70 test / 23 suite, 0 failure, 0 error; BUILD SUCCESSFUL**.
- Xem hai ảnh có sẵn `live_glass_dashboard_new3.png`, `live_glass_dashboard_active.png`. Chưa xác minh APK tạo ảnh có cùng HEAD; ảnh là bằng chứng thị giác bổ trợ, không thay thế kiểm tra runtime của bản build hiện tại.
- Không sửa source, không đổi nhánh, không cài/gỡ app. Các lỗi bên dưới dựa trên đường đi source cụ thể; chưa dựng thêm test tái hiện riêng cho từng lỗi.

## Các vấn đề cần sửa

### 1. P1 — Weather hiển thị địa điểm cố định và không có trạng thái lỗi

`fermata/src/main/java/me/aap/fermata/ui/glass/dashboard/GlassDashboardController.java:186` gọi `fetchForecast(21.0285, 105.8542, ...)`: tọa độ Hà Nội được hard-code, không lấy địa điểm đã thiết lập trên phone. `onError` tại dòng 195 rỗng.

Hậu quả: người dùng đi nơi khác vẫn nhận weather Hà Nội; lỗi mạng không tạo trạng thái có thể hiểu hoặc phục hồi. Thẻ đẹp nhưng thông tin sai là vấn đề cần chặn release.

**Xử lý:** dùng nguồn location/cấu hình weather chung đang có trong app. Phân biệt thiếu quyền, chưa có địa điểm, đang cập nhật, dữ liệu mới, cache cũ và lỗi. AA thiếu quyền phải hướng dẫn cấp trên phone. Khi đã thiết lập trên phone, thao tác cập nhật thẻ cần đọc lại cấu hình trước; chỉ mở chi tiết khi thẻ đã có dữ liệu hợp lệ. Hiển thị tên địa điểm và thời điểm cập nhật để người dùng biết đang xem dữ liệu nào.

### 2. P1 — Layout responsive chỉ xét tỷ lệ, chưa xét kích thước thực của thẻ

`GlassDashboardLayout.java:80–187` chọn WIDE/SQUARE/PORTRAIT theo aspect ratio rồi chia đều vùng body. Header 42dp, quick bar 64dp, padding/gap cố định. Không có hợp đồng minimum width/height theo nội dung hoặc font scale.

Ví dụ ở viewport nội dung 800×480dp: ba cột chỉ khoảng **246dp/cột**. Media đã dành 32dp padding, artwork 72dp và gap 14dp; vùng title còn khoảng **128dp**. Ở 360×640dp portrait, body khoảng 482dp, mỗi thẻ chỉ khoảng **151dp cao** — không đủ cho media hiện tại gồm header, artwork, timeline và transport. Đây là tính toán từ layout; mức clipping thực tế cần đo với density/insets của thiết bị.

**Xử lý:** lựa chọn layout bằng **chiều rộng/chiều cao nội dung sau insets, đơn vị dp**, sau đó kiểm tra ngân sách mỗi thẻ. Media cần ưu tiên diện tích hơn speedometer; màn thấp cần giảm nội dung phụ, không thu nhỏ nút chính. Phone dùng chiều cao nội dung tự nhiên và cuộn trang; weather được phép dài hơn. AA ưu tiên màn chính không phải vuốt để tìm thao tác quan trọng.

### 3. P1 — Thẻ tốc độ không trở lại sau khi thoát split mode

`GlassDashboardLayout.java:172` đặt speedo `GONE` trong SPLIT. Các nhánh WIDE/SQUARE/PORTRAIT không khôi phục `VISIBLE`; `layoutChild` lại bỏ qua child đang `GONE`.

**Đường tái hiện theo source:** layout bình thường → `setSplit(true)` và layout → `setSplit(false)` và layout: thẻ tốc độ vẫn ẩn.

**Xử lý:** xác lập visibility đầy đủ khi chuyển mode, trước khi đo/layout. Test phải gắn child thật, chuyển split vào/ra và kiểm tra visibility/bounds; test enum mode hiện có không bắt được lỗi này.

### 4. P1 — Media Glass thiếu trạng thái mở app chưa phát và recent

`GlassMediaPlayerView.java` luôn trình bày NOW PLAYING, artwork, 00:00/00:00 và các nút transport. `GlassMediaPlayerController.resetToIdle()` chỉ trả về “No Media Playing”. Không có bố cục recent/resume, connecting/buffering, error/retry hoặc live riêng.

Ảnh dashboard active có title “Unknown”, artwork rỗng và timeline 00:00/00:00. Không thể dùng ảnh này để kết luận chính xác runtime của HEAD, nhưng source xác nhận thiếu mô hình trạng thái cần thiết.

**Xử lý:** tái sử dụng trạng thái playback/recents của app. Tối thiểu có: chưa phát có recent; chưa có recent có CTA mở thư viện; connecting/buffering; playing/paused; live; error có retry. Chưa phát không hiển thị thời gian giả hoặc favorite/skip vô nghĩa. Recent phải có đường tiếp tục phát rõ, không chỉ tồn tại ở catalog riêng.

### 5. P1 — Nút media nhỏ và title thiếu không gian trên xe

`GlassMediaPlayerView.java:91,108–118,185,209`: artwork 72dp, title 16sp một dòng, artist 13sp một dòng; play 44dp, prev/next/favorite 40dp. Không thấy touch delegate mở rộng hoặc mô tả accessibility cho các nút glyph. `GlassDimensions` quy định tối thiểu 56dp nhưng chính media cũng chưa đạt giá trị này.

**Xử lý:** tách kích thước icon khỏi vùng chạm; giữ artwork và title là điểm nhấn. Theo yêu cầu trước đó, dùng vùng chạm AA 76dp / phone 48dp nếu đây vẫn là hợp đồng sản phẩm. Thẻ hẹp chỉ giữ play và một thao tác phụ cần thiết; các thao tác còn lại đưa vào menu rõ ràng. Title được hai dòng khi đủ chiều cao, ellipsis khi bắt buộc; không chạy marquee mặc định. Dùng icon resource đồng bộ thay ký tự Unicode, thêm tên hành động và trạng thái focus/pressed/disabled.

### 6. P1 — Playback controller có dữ liệu không đồng bộ và callback cũ

`GlassMediaPlayerController.java:110–111,137–138`: gọi `updatePlayback` → notify listener **trước khi** gán artwork. Khi callback chạy ngay trên main thread, view nhận artwork cũ; bài paused không có ticker để bảo đảm cập nhật lại.

`onPlaybackTimelineChanged` chỉ thay duration khi giá trị >0, nên snapshot duration 0 không xóa duration cũ. `onTick` đọc position bất đồng bộ nhưng callback không kiểm tra engine/item hoặc generation hiện hành: kết quả từ bài trước có thể ghi vào bài mới. Cần kiểm thử các thứ tự callback trước khi kết luận mức độ xuất hiện ngoài đời.

**Xử lý:** tạo một snapshot nhất quán rồi notify một lần; dùng item identity/generation để bỏ callback cũ; giữ một nguồn timeline có thẩm quyền. Duration unknown/live phải là trạng thái riêng, không dùng duration bài trước. Không áp dụng quy tắc “timeline không bao giờ lùi” vì seek ngược là thao tác hợp lệ.

### 7. P1 — Timeline chưa seek được; transport chưa theo capability

Media card sử dụng `ProgressBar` tại `GlassMediaPlayerView.java:132`; không có wiring seek tương tác. Controller có `seekTo` nhưng thẻ không sử dụng. Các nút transport hiển thị cố định, controller gọi thẳng callback của media session.

**Xử lý:** quyết định rõ timeline chỉ báo hay seek; nếu thiết kế hứa seek thì nối qua cơ chế binder/ownership và capability chung của app. Không mở seek cho live/non-seekable; không để skip trông khả dụng khi nguồn không hỗ trợ. Không tạo đường điều khiển riêng bỏ qua bảo vệ hiện có chỉ để phục vụ Glass.

### 8. P1 — Kế hoạch kiểm thử chưa chứng minh hành vi; bước uninstall làm mất dữ liệu

`GlassDashboardLayoutTest` kiểm tra enum mode và split flag, không kiểm tra layout child. `ControlPanelViewGestureTest` kiểm tra chuỗi có trong source, không gửi gesture và không quan sát playback. Vì vậy 70 test pass chưa chứng minh không clipping hoặc không pause ngoài ý muốn.

Nhiệm vụ 6.2 của migration plan đề xuất `adb uninstall` rồi `install -r`. Uninstall xóa dữ liệu app, làm mất khả năng kiểm tra giữ preferences, recent và cấu hình khi nâng cấp. Chụp screenshot cũng không chứng minh độ mượt.

**Xử lý:** mặc định nâng cấp giữ dữ liệu bằng install phù hợp; fresh install là ca riêng trên dữ liệu thử. Bổ sung test bounds/visibility và event sequence thật; đo frame time trên thiết bị thay vì ghi “60 FPS” như một kết quả đã đạt.

### 9. P2 — Hourly forecast bắt đầu từ 00:00 và buộc vuốt ngang

`GlassOpenMeteoClient.java:203–214` lấy 24 phần tử đầu từ hourly array, không lọc theo giờ hiện tại. Ảnh 11:06 vẫn bắt đầu 00:00. `GlassNavWeatherView.java:239` dùng `HorizontalScrollView`; chữ giờ 9sp và nhiệt độ 12sp.

**Xử lý:** lọc từ giờ hiện tại theo timezone địa điểm; giữ timestamp đầy đủ, không chỉ HH:mm để xử lý qua ngày. AA hiển thị một nhóm giờ tương lai có ích vừa màn; chi tiết dài mở bằng hành động rõ. Phone có thể hiển thị thêm thông tin và tăng chiều cao tự nhiên.

### 10. P2 — Đồng hồ dashboard không cập nhật liên tục

`GlassDashboardController.java:110–118` tạo clock/date một lần; không có lịch cập nhật trong controller đã kiểm tra. HH:mm cố định cũng bỏ qua lựa chọn 12/24 giờ.

**Xử lý:** cập nhật theo phút và đổi ngày, gắn vào lifecycle; dùng lựa chọn thời gian/locale của hệ thống. Dừng callback khi view bị hủy.

### 11. P2 — Widget tùy biến cần hoàn thiện semantics và cancel

`GlassSlider` là custom View: ACTION_UP và ACTION_CANCEL chung nhánh cập nhật giá trị và gọi stop tracking. Không thấy xử lý bàn phím/rotary hoặc range accessibility. `GlassSwitch` implements Checkable nhưng chưa có semantics tương đương native switch. Hai widget được cung cấp từ factory trong `MainActivityDelegate.java:1208–1218`; mức ảnh hưởng từng preference cần kiểm tra end-to-end với thư viện utils.

**Xử lý:** ưu tiên style native widget nếu đáp ứng thiết kế; nếu giữ custom thì bổ sung range/checked role, hành động accessibility, focus/rotary/key, disabled state và cancel semantics. Không tự động thay mọi SeekBar chỉ vì muốn đồng nhất hình thức. EQ và control panel hiện vẫn có SeekBar native, nên tài liệu “thay toàn bộ bằng GlassSlider” chưa phản ánh code thực tế.

### 12. P2 — Hợp đồng gesture mâu thuẫn và tác động cả theme khác

`ControlPanelView.java:408–437` thay đổi tap/double-tap/grace period không được giới hạn bởi `isGlassTheme`. Double tap sau khoảng debounce gọi play/pause; comprehensive spec lại đề xuất double tap seek 10 giây. Yêu cầu trước đó là lần chạm đầu hiện controls, lần thứ hai pause; cần phân biệt hai single tap với double tap của GestureDetector.

**Xử lý:** viết event contract duy nhất, gồm controls đang ẩn/hiện, khoảng cách hai lần chạm, vị trí chạm và nút cụ thể. Test event sequence và số lần gọi pause/seek. Nếu đổi hành vi chung có chủ ý, sửa cam kết “Classic/Material hoàn toàn không đổi” và kiểm tra các theme đó; nếu chỉ đổi Glass, phải đặt phạm vi đúng.

### 13. P2 — Quick bar cố định sáu slot chưa giải quyết màn nhỏ

`GlassQuickBarView` luôn có bốn slot tùy chỉnh + Addons + Exit. Ở chiều rộng nhỏ, weight chia đều làm slot hẹp; label 10–11sp. Spec AA ban đầu quy định sáu slot, khác yêu cầu trước đó màn nhỏ bốn/màn đủ sáu.

Đây là **xung đột yêu cầu**, không mặc định kết luận code sai với spec Glass. Phải quyết định bốn/sáu đang đếm shortcut hay tổng số ô, có tính Addons/Exit hay không.

**Xử lý:** tính số slot từ chiều rộng khả dụng và minimum touch target, không từ độ phân giải pixel. Bảo đảm shortcut được lưu không mất khi đổi số slot; có đường mở các mục còn lại, không buộc vuốt quick bar. Nếu giữ sáu trên mọi màn, phải công bố minimum supported viewport và có fallback khi không đủ.

### 14. P2 — Shell phone và dashboard có phân cấp thị giác trùng lặp

Hai ảnh hiện có cho thấy banner AA, header Dashboard/gear, header clock/weather/gear và bottom Settings cùng tồn tại. Dashboard active còn có nhiều nền capsule xám đặc, chữ phụ nhỏ và glyph đen trên nền tối (gear/Addons/Exit). Media có khoảng trống lớn nhưng vùng title và controls vẫn nhỏ.

**Xử lý:** chỉ giữ một header phục vụ ngữ cảnh hiện tại; lối vào Settings trên phone đã có ở bottom nav. Banner trạng thái nên gọn và chỉ chiếm chỗ khi hữu ích. Giảm số lớp capsule, thống nhất màu icon theo semantic token; media ưu tiên artwork/title, weather dùng chiều cao theo thông tin. Kiểm tra riêng day/night, không dùng cyan trắng sáng làm màu text hành động trên mọi nền sáng.

### 15. P2 — Phương án port và tuyên bố hoàn thiện thiếu bằng chứng

Spec dùng nguồn `build/decompiled/carstream/...`, mục tiêu tái sử dụng ~80% và 100% phủ Glass. Đường dẫn decompiled không chứng minh upstream/revision/license hoặc mức tái sử dụng. Kế hoạch thiên về danh sách class và bài test có tên đúng hơn là kết quả trải nghiệm cụ thể.

**Xử lý:** với phần thực sự lấy từ ngoài, ghi URL nguồn chính thức, revision, license và mapping file được dùng. Bỏ chỉ tiêu “80%” nếu không có phép đo. Chuyển “100% Glass” thành inventory màn hình có bằng chứng day/night, phone/AA, tương tác và fallback. Java thuần là lựa chọn kỹ thuật, không phải tiêu chí thành công thị giác.

## Điều chỉnh phương án hoàn thiện

### Chặng 1 — Chốt hợp đồng sản phẩm

Hợp nhất AA spec và comprehensive spec thành tài liệu được commit. Ghi rõ phone Glass chính thức hay chỉ preview; bốn/sáu slot tính thế nào; gear thuộc Modern hay Glass; touch target AA/phone; thao tác fullscreen. Yêu cầu xóa gear trước đây nói về Modern, không tự động suy ra mọi gear Glass đều là lỗi. Tuy nhiên trùng lối vào Settings trong cùng màn vẫn cần xử lý.

Đầu ra: một bảng requirement → file/caller → tiêu chí kiểm chứng; bỏ cam kết tuyệt đối không thay Classic nếu thực tế chủ động thay gesture chung.

### Chặng 2 — Sửa tính đúng trước hình thức

Weather dùng đúng location và lỗi/quyền/cache. Media nhận snapshot nguyên tử, loại callback cũ, xử lý duration unknown/live; trạng thái idle có recent và CTA; clock cập nhật. Sửa split visibility. Các lỗi này không cần thêm framework UI.

Đầu ra: test tái hiện cụ thể và luồng playback/location ổn định sau đổi bài, reconnect và quay lại dashboard.

### Chặng 3 — Hoàn thiện bố cục và thao tác chính

Thay chia đều ba thẻ bằng phân bổ theo nội dung và mức ưu tiên. Media có biến thể spacious/compact/short dựa trên card bounds; biến thể là một policy trong component hiện có, tránh tạo ba hệ thống controller. Phone cho phép chiều cao theo nội dung; AA giảm thông tin phụ khi thiếu diện tích. Quick bar theo ngân sách vùng chạm đã chốt.

Đầu ra: không overlap/clipping, controls chính không bị thu nhỏ, title vẫn đọc được, weather phone không bị cắt và AA không cần vuốt để thao tác chính.

### Chặng 4 — Accessibility, theme và parity

Hoàn thiện focus/rotary/TalkBack, cancel của slider, icon/action labels, day/night contrast. Tái sử dụng styled native widgets khi không có nhu cầu hành vi khác. Kiểm tra các màn settings, EQ, dialogs, file picker, catalog, voice, car finder và addons theo inventory; không thêm port chỉ để đạt số lượng component.

Đầu ra: chuyển theme và thao tác bằng cảm ứng/rotary đều dùng được; Classic/Material không có thay đổi ngoài danh sách đã được chấp thuận.

### Chặng 5 — Gate release dựa trên bằng chứng

1. Test hành vi: split vào/ra; metadata/artwork cùng lần notify; callback bài A đến sau B; live duration; lỗi/retry; tap khi controls ẩn/hiện; cancel slider; restore preferences sau nâng cấp.
2. Test hình học: viewport nội dung ví dụ 640×360, 800×480, 1024×600dp; màn gần vuông, ultrawide, split hẹp; phone 360/393/412dp và landscape. Dùng **dp sau insets**, font scale 1.0/1.3/1.5; không coi các số này là danh sách thiết bị duy nhất.
3. Chụp day/night và các trạng thái dài title/không artwork/no recent/recents/loading/error/live. Gắn ảnh với commit, APK, density, insets và font scale.
4. DHU hoặc màn xe thật: rotary/focus, reconnect, split, đổi day/night, resize và tốc độ phản hồi. Giả lập landscape trên phone không thay thế kiểm chứng AA.
5. Nâng cấp giữ dữ liệu trước; fresh install riêng. Đo frame time trên thiết bị mục tiêu và chạy phiên dài để phát hiện callback/lifecycle/network lặp.

**Điều kiện đi tiếp:** không còn P1 ở các luồng chính; P2 ảnh hưởng chạm/đọc/focus đã xử lý hoặc có phạm vi hỗ trợ rõ; có bằng chứng runtime AA. Khi đó mới đánh giá nhánh đủ dùng hằng ngày, không dựa riêng vào test suite xanh hay số màn đã tint.

## Giới hạn của audit

Chưa chạy bản HEAD trên DHU/màn xe, chưa đo frame time hoặc kiểm thử phiên dài. Chưa xác minh nguồn/license từng phần CarStream. Các màn phụ chưa được rà mọi tổ hợp dữ liệu và thiết bị; báo cáo không khẳng định đã kiểm chứng toàn bộ 101 file ở runtime.

## Bổ sung — Đồng bộ giao diện toàn app

**Nhận xét của người dùng được source xác nhận: nhiều điểm mới chỉ được trang trí Glass trên cấu trúc cũ.** Có một factory Glass không đồng nghĩa mọi luồng gọi đều đi qua factory; có nền Glass không đồng nghĩa typography, controls và states đã đồng bộ. Giữ lại component cũ là hợp lý nếu nó đáp ứng hợp đồng mới; vấn đề là giữ cả presentation cũ và bỏ sót những trạng thái phụ.

### Những khoảng hở đã xác nhận

| Khu vực | Bằng chứng source | Phần còn thiếu / xử lý |
|---|---|---|
| Chọn shortcut | `GlassShortcutPickerDialog.java:65` dùng `AlertDialog.Builder`, tiêu đề hard-code “Select Shortcut” | Chưa đi qua dialog factory Glass. Đồng bộ surface, typography, selection/focus và localization của picker. |
| Thay/xóa shortcut | `GlassDashboardController.java:228` tạo `android.app.AlertDialog.Builder` trực tiếp | Luồng phụ của chính dashboard mới vẫn dùng đường dialog khác. Chuyển qua cơ chế dialog chung, giữ nguyên hành vi Replace/Remove. |
| Equalizer: chọn virtualizer, nhập giá trị | `AudioEffectsScreenView.java:404,437` dùng AlertDialog trực tiếp, numeric editor tạo EditText native | Màn chính có Glass nhưng popup chưa được nối qua factory. Native widget không tự nó là lỗi; cần style thống nhất và bảo toàn validation/input/focus hiện có. |
| Car Finder: xác nhận và cài đặt phụ | `CarFinderFragment.java:1060,1078`, `CarFinderSettingsDialog.java:136` tạo AlertDialog trực tiếp | Radar/surface mới không bao phủ các modal. Rà cả confirm/cancel/destructive action và lựa chọn trong settings. |
| Danh sách media | `MediaItemView.java:518–533` đổi background/title/progress trong `setState`, vẫn dùng title/subtitle/checkbox của component hiện hữu | Đồng bộ đầy đủ typography, subtitle, selection, context actions và bounds; không đánh dấu xong chỉ vì row có nền kính. Đặc biệt title thường bị gán **trắng cố định** dù drawable chọn day/night: cần sửa sang semantic text token và kiểm chứng day mode. |
| Settings | `GlassSettingsDecorator.decorateRow` chỉ thêm minimum height, margin, padding, background | Decorator không chứng minh các loại preference, section header, summary, popup chọn và input đã đồng bộ. Factory switch/slider tồn tại nhưng phải kiểm chứng từng caller và nội dung con. |
| Dialog chung | `MainActivityDelegate.createDialogBuilder` trả Glass builder; `GlassDialogBuilder` chỉ style những ID title/message/button tìm được | Chỉ bao phủ caller sử dụng factory và view có ID khớp. Cần kiểm tra cấu trúc DialogView thật; không coi đây là giải pháp đã tự động bao phủ AlertDialog trực tiếp. |
| Player / EQ controls | ControlPanel tint SeekBar hiện có; EQ còn các SeekBar native | Không khớp tuyên bố thay toàn bộ bằng GlassSlider. Chọn một hợp đồng visual/interaction, có thể style native để giữ accessibility; không bắt buộc viết lại. |
| Phone shell | Các controller header/banner/bottom đã có Glass gate, ảnh vẫn có header và Settings trùng | Phải thống nhất composition toàn màn, không chỉ style từng component riêng. |

### Phạm vi cần kiểm chứng thêm

TV, Radio, Podcast và Audiobook có thể nhận Glass qua MediaLib/View chung; việc fragment không có `isGlassTheme` riêng **không chứng minh** màn đó bị bỏ sót. Phải theo chuỗi fragment → row/menu/dialog/widget và kiểm tra các màn thêm nguồn, tìm kiếm, lỗi, loading, empty và selection.

Với YouTube, TikTok, Stremio và Web, tách chrome do Fermata sở hữu khỏi nội dung website. Header, menu, loading/error và controls của app cần đồng bộ; không hứa biến giao diện bên thứ ba trong WebView thành Glass toàn bộ.

### Phương án xử lý đồng bộ

1. Lập inventory theo **screen + state + modal**, không theo tên class. Mỗi mục ghi phone/AA, day/night, component sở hữu presentation và bằng chứng runtime.
2. Chốt hợp đồng dùng chung cho text/icon/surface, spacing, radius, row/input/button, selected/disabled/error/loading và touch/focus. Cho phép kích thước phone và AA khác nhau; đồng bộ không có nghĩa mọi màn phải giống dashboard.
3. Vá các đường dialog bỏ qua factory trước. Dùng lại builder/decorator/controller hiện có và giữ nguyên validation/callback; tránh thêm lớp wrapper mới cho từng màn.
4. Hoàn thiện component tái sử dụng: media row, preference row, menu item, dialog và player controls. Sửa tại component chung sẽ bao phủ nhiều màn với ít thay đổi hơn migration từng fragment.
5. Kiểm chứng caller đặc thù ở từng addon; chỉ viết presentation riêng khi component chung thật sự không đáp ứng.
6. Chỉ đánh dấu hoàn tất khi **màn chính lẫn luồng phụ** dùng đúng token, đọc được day/night, thao tác được bằng touch/focus và không quay về presentation cũ ngoài những ngoại lệ đã ghi rõ.

**Ưu tiên bổ sung trước release:** lỗi text trắng day mode ở media row; dialog shortcut/EQ/Car Finder; preference và media row đủ trạng thái; shell phone thống nhất. Đây là một hạng mục hoàn thiện sản phẩm riêng, không nên gộp dưới checklist “đã thêm isGlassTheme”.
