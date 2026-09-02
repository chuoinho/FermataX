# SmartTopCard background policy

## Phạm vi bất biến

Artwork, spectrum và fallback chỉ được render trong lớp background của SmartTopCard. Không được thay đổi hoặc thay thế source icon, metadata, title slot, timeline, action rail, Quick Recent, card height hay vị trí của các thành phần này.

## Tiêu chí chọn artwork

Artwork chỉ được chọn khi đạt toàn bộ tiêu chí bắt buộc:

| Nhóm | Tiêu chí bắt buộc |
| --- | --- |
| Nguồn | Là artwork của item đang phát do addon/provider cung cấp. Không dùng source icon, logo addon, screenshot hoặc ảnh tự suy đoán. |
| Ngữ nghĩa | Là cover/poster của nội dung. Thumbnail video 16:9 và frame trích từ video không được nâng thành artwork. |
| Khả dụng | URI ổn định, decode thành công và đã có trong cache/local state. Dashboard không chờ network chỉ để lấy background. |
| Chất lượng | Ảnh tĩnh; cạnh ngắn tối thiểu 256 px, ưu tiên từ 512 px. Không dùng ảnh hỏng, animated image hoặc ảnh bị upscale rõ rệt. |
| Tỷ lệ | Ưu tiên gần vuông; chấp nhận 0.8:1–1.25:1. Ngoài dải này dùng fallback để tránh crop mất chủ thể. |
| Khả năng đọc | Sau crop và tint, title, subtitle, progress, actions và Quick Recent vẫn đạt tương phản rõ ở 800×480, 1280×720 và 1920×1080. |
| Tính ổn định | Artwork đổi theo item, không đổi theo progress/play-pause và không làm card re-layout. |

Thứ tự fallback:

1. Artwork hợp lệ → artwork làm background.
2. Audio addon không có artwork hợp lệ → spectrum tĩnh làm background.
3. Non-audio không có artwork hợp lệ → gradient/watermark tĩnh theo source.
4. Empty → nền neutral và watermark grid; không dùng spectrum.

## Phạm vi spectrum

- Spectrum chỉ áp dụng cho audio addon. Hiện tại ca có bằng chứng là Radio; Podcast/Audiobook chỉ dùng khi provider tương lai xuất bản rõ media kind là audio.
- Không suy ra audio chỉ từ việc item thiếu artwork.
- Spectrum nằm dưới foreground, không thay source icon.
- Spectrum dừng ở texture tĩnh, opacity thấp; không mô phỏng rằng dữ liệu đang được capture theo thời gian thực.

## Đánh giá FFT thật

Quyết định hiện tại: **không thay spectrum tĩnh bằng spectrum FFT thật**.

| Tiêu chí | Spectrum tĩnh | FFT thật |
| --- | --- | --- |
| Giá trị cho thao tác lái xe | Đủ phân biệt audio/no-art | Không thêm thông tin cần thiết cho thao tác |
| Phân tâm | Không chuyển động | Chuyển động liên tục trong Dashboard |
| Quyền | Không thêm quyền | Android `Visualizer` yêu cầu `RECORD_AUDIO`; output mix còn có yêu cầu audio-settings |
| Engine | Không phụ thuộc engine | Phải thống nhất audio-session của MediaPlayer, ExoPlayer và VLC |
| Lifecycle | Không có tài nguyên runtime | Phải enable/disable/release theo playback, fragment, Auto disconnect và engine switch |
| Hiệu năng | Không có callback/frame invalidation | FFT callback và redraw liên tục |
| Độ tin cậy | Giống nhau trên mọi DHU | Có thể fail/unsupported theo thiết bị hoặc audio route |

Repository hiện chỉ dùng audio-session để gắn `AudioEffects`; chưa có common spectrum/FFT pipeline. Android mô tả `Visualizer` là API capture waveform/FFT chất lượng thấp, yêu cầu quyền thu âm và phải disable/release khi không dùng: [Visualizer API](https://developer.android.com/reference/android/media/audiofx/Visualizer.html). Hướng dẫn Android for Cars đặt ưu tiên cao nhất vào giảm phân tâm và giao diện thực dụng, dễ đoán: [Media apps for cars](https://developer.android.com/design/ui/cars/guides/app-types/media-apps).

Chỉ xem xét FFT thật trong tương lai nếu đồng thời đạt các gate sau:

1. Không xin quyền `RECORD_AUDIO` chỉ để trang trí Dashboard.
2. Có abstraction audio-session chung và fallback an toàn cho mọi engine.
3. Tắt capture khi pause, stop, off-screen, đổi engine hoặc Android Auto disconnect; luôn release native resource.
4. DHU chứng minh không frame drop, không ảnh hưởng playback và không gây layout shift ở cả ba độ phân giải.
5. Motion giảm tối đa, hỗ trợ reduce-motion và vượt qua review phân tâm riêng.
