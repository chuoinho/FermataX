# Changelog

## v318 (draft) — kể từ v305

**Cơ sở so sánh:** release [`v305`](https://github.com/chuoinho/FermataX/releases/tag/v305),
commit `eab2a7e`. Bản này giữ `versionName` 2.0.1 và nâng `versionCode` từ 305 lên
318. Phạm vi gồm 40 commit, với 449 file thay đổi; đây là release note cho người
dùng, không phải danh sách từng file nguồn.

### Mới

#### Điều khiển từ điện thoại và Mở trên xe

- Bổ sung dải điều khiển trên đầu giao diện điện thoại: ảnh bìa, trạng thái phát,
  nút Previous/Play-Pause/Next, trạng thái Android Auto và công tắc **Mở trên xe**.
- Bổ sung luồng **Mở trên xe** có kiểu dữ liệu rõ ràng và chỉ hoạt động khi host
  Android Auto đang sẵn sàng. Công tắc bị vô hiệu khi xe chưa kết nối.
- Nội dung cụ thể được mở trên xe thay vì mở lại lệnh cũ sau khi kết nối lại.
  Các yêu cầu quá hạn, nguồn WebView đã mất, hoặc host xe cũ đều bị loại bỏ.
- Add-on Web tổng quát có thể chuyển URL mới từ điện thoại sang xe. Việc duyệt trang
  thông thường không tự chuyển dữ liệu cho đến khi có một URL nội dung hợp lệ.
- YouTube nhận tuyến chọn/phát nội dung riêng; Stremio có cầu nối control-only để
  điện thoại tiếp tục là nơi duyệt/chọn, còn route player được mở ở xe khi phù hợp.

#### Giao diện điện thoại

- Khôi phục theme **Material You / Bento Grid** và vẫn giữ theme Classic.
- Dashboard trên điện thoại ẩn rail chiếm diện tích; bottom menu điện thoại trải hết
  chiều ngang; dải đầu trống thu gọn thành capsule.
- SmartTop và các biểu tượng điều khiển được cập nhật để nhất quán giữa Classic và
  Material You.
- Settings được gộp thành một trang lớn, thống nhất các tuỳ chọn Phone/Android Auto.
  Chỉ còn hai lựa chọn theme được hỗ trợ chính thức: Classic và Material You.

#### Car Finder

- Thêm Car Finder để lưu vị trí đỗ xe, xem bản đồ, la bàn/radar, ảnh chỗ đỗ và mở
  điều hướng đến vị trí đã lưu.
- Thêm làm mượt vị trí, lọc hướng, debounce sự kiện xe ngắt kết nối và yêu cầu quyền
  vị trí khi dùng tính năng.
- Cache tile bản đồ có giới hạn; FileProvider riêng hỗ trợ chia sẻ ảnh đỗ xe an toàn.

### Cải thiện và sửa lỗi

#### Android Auto và phát media

- Thay cơ chế theo dõi kết nối cũ bằng điều phối session Android Auto có generation.
  Bind lặp không tạo lại session; teardown generation cũ không thể đóng session mới.
- Ngắt AA khi đang phát media không còn coi playback là host còn sống. Teardown có
  retry hữu hạn và bị huỷ khi một session mới bắt đầu.
- Cứng hoá việc không replay lệnh cũ, admission của host xe và vòng đời mirror.
- Khôi phục các media intent filter phổ biến để mở file media từ ứng dụng khác tốt hơn.
- Bổ sung kiểm tra biến thể Mirror FS trong build và mở rộng tương thích hook với một
  số trình phát phổ biến.

#### Web, YouTube và Stremio

- Cải thiện điều khiển WebView, theo dõi URL, quyền sở hữu nguồn/đích và cleanup khi
  WebView bị detach để không có hành động đến muộn.
- Ổn định luồng YouTube: phát hiện/quét quảng cáo, thumbnail, điều khiển phát, route
  selection, fullscreen coordinator và WebAudio/EQ bridge.
- Cải thiện vòng đời Stremio, navigation, MediaSession và cầu nối WebAudio/EQ.
- Thêm Generic WebAudio bridge với kiểm tra ứng viên media trước khi can thiệp.

#### Ngôn ngữ và tuỳ chọn

- Giữ ordinal lịch sử của `Lang` để dữ liệu locale cũ không bị đổi nhầm sang ngôn
  ngữ khác.
- Settings hiện lưu đúng ordinal của ngôn ngữ được chọn, không còn lưu chỉ số hiển
  thị của danh sách.
- Giảm các bản dịch được duy trì chính thức xuống: English, Tiếng Việt, Русский,
  Español, Português và 繁體中文. Locale cũ không còn hỗ trợ được chuẩn hoá về English.
- Chuẩn hoá hiển thị option/preference và xác nhận coverage của các chuỗi còn hỗ trợ.

### Đã loại bỏ

- Toàn bộ speech-to-text/offline subtitle và dịch offline: Whisper, Opus-MT, ML Kit,
  SubGen và Translate add-on, gồm native library/model tương ứng.
- Chat add-on và Google Drive VFS add-on.
- Các bản dịch chưa còn duy trì cho 11 locale; code, manifest, tài nguyên và rule
  build liên quan cũng được bỏ khỏi Gradle graph.

### Tương thích khi nâng cấp

- Người dùng đang chọn một ngôn ngữ đã bị loại bỏ sẽ được chuyển về English thay vì
  bị ánh xạ sang nhầm ngôn ngữ khác.
- Chat, Google Drive, subtitle bằng Whisper và dịch offline không còn xuất hiện sau
  khi nâng cấp; dữ liệu hoặc shortcut của các add-on đó không còn được sử dụng.
- **Mở trên xe** cần một kết nối Android Auto hoạt động. Khi xe ngắt kết nối, lệnh
  đang chờ sẽ không được phát lại tự động ở lần kết nối sau.
- Car Finder chỉ lưu/cập nhật vị trí sau khi người dùng cấp quyền vị trí cần thiết.

### Kiểm tra phát hành

- Mobile unit tests, Android Auto unit tests và Web unit tests được chạy với biến thể
  `.test`.
- Script kiểm tra xác nhận Whisper, Opus-MT, ML Kit, Chat và GDrive không còn nằm
  trong Gradle project graph.
- Cần kiểm tra thủ công trên DHU/xe thật cho chuỗi ngắt/kết nối AA nhanh, Mở trên xe,
  fullscreen YouTube và Car Finder vì các hành vi này phụ thuộc host/hardware.
