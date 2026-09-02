# SmartTop demo matrix

Bộ ảnh được render từ `design/smarttop-real-cases-demo.html`, dùng dữ liệu có thật trong repository.

Kích thước lấy từ các preset DHU trong `open-dhu.bat`: 800×480, 1280×720 và 1920×1080. Các preset khác lặp lại một trong ba độ phân giải này nên không tạo thêm ảnh trùng hình học.

| Trạng thái | 800×480 | 1280×720 | 1920×1080 |
| --- | --- | --- | --- |
| Artwork làm background, giữ icon TV | [PNG](smarttop-artwork-800x480.png) | [PNG](smarttop-artwork-1280x720.png) | [PNG](smarttop-artwork-1920x1080.png) |
| Audio không có cover: spectrum chỉ ở background, giữ icon Radio | [PNG](smarttop-audio-800x480.png) | [PNG](smarttop-audio-1280x720.png) | [PNG](smarttop-audio-1920x1080.png) |
| Wide artwork bị từ chối | [PNG](smarttop-wide-800x480.png) | [PNG](smarttop-wide-1280x720.png) | [PNG](smarttop-wide-1920x1080.png) |
| Empty | [PNG](smarttop-empty-800x480.png) | [PNG](smarttop-empty-1280x720.png) | [PNG](smarttop-empty-1920x1080.png) |

Ảnh tổng: [smarttop-all-cases-all-sizes.png](smarttop-all-cases-all-sizes.png).

## Tiêu chí chọn artwork cho background

1. Artwork phải thuộc item đang phát và do addon/provider cung cấp; không lấy source icon, logo addon hoặc ảnh tự suy đoán làm artwork.
2. URI phải ổn định, decode được và đã có trong cache/local state; Dashboard không chờ một network fetch mới chỉ để trang trí nền.
3. Ảnh tĩnh, không có animation; cạnh ngắn tối thiểu 256 px để tránh vỡ hình trên DHU 1920×1080.
4. Ưu tiên artwork gần vuông, tỷ lệ từ 0.8:1 đến 1.25:1. Thumbnail video 16:9, screenshot và ảnh quá dài/rộng không được nâng thành artwork nền.
5. Sau crop, vùng chữ và action phải giữ đủ tương phản với tint. Nếu không đạt thì dùng fallback nền theo addon.
6. Artwork chỉ thay `.backdrop-art`; source icon, metadata, timeline, action rail và Quick Recent không được đổi geometry hoặc bị thay thế.

## Spectrum audio

- Spectrum chỉ áp dụng cho addon audio khi item không có artwork hợp lệ.
- Bản hiện tại dùng spectrum tĩnh, opacity thấp, ở background-only.
- Chưa nên dùng FFT thật: repository chưa có pipeline Visualizer; việc thêm nó cần quyền thu âm, quản lý audio-session theo nhiều engine, callback định kỳ và release tài nguyên theo lifecycle.
- Chỉ mở lại FFT thật khi có prototype DHU chứng minh không gây xao nhãng, không thêm permission friction, không ảnh hưởng playback và tắt hoàn toàn khi pause/stop/off-screen.
