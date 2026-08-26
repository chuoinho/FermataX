# 02 — Thiết kế component và API

## 1. File layout dự kiến

```text
modules/web/
  build.gradle
  src/main/java/me/aap/fermata/addon/web/
    WebBrowserAddon.java                 # thêm constructor/hook mỏng
    WebBrowserFragment.java              # thêm client factory/hook mỏng
    stremio/
      StremioWebAddon.java
      StremioWebFragment.java
      StremioWebClient.java
      StremioIntentPlayable.java
      StremioRouteStore.java
  src/test/java/me/aap/fermata/addon/web/stremio/

fermata/src/main/java/me/aap/fermata/addon/
  QuickRecentProvider.java
  QuickRecentProviderLease.java
  QuickRecentCandidate.java

fermata/src/main/java/me/aap/fermata/ui/smarttop/
  SmartTopQuickRecent.java
  SmartTopCoordinator.java               # merge provider candidates
  SmartTopViewState.java                 # typed Quick Recent list
```

Tên cuối có thể điều chỉnh theo convention hiện hữu, nhưng dependency direction và semantics không được thay đổi.

## 2. Thay đổi `modules/web/build.gradle`

Thêm addon entry cạnh YouTube và Web Browser:

```groovy
[
    name : 'stremio',
    id   : 'stremio_fragment',
    icon : 'stremio',
    class: 'me.aap.fermata.addon.web.stremio.StremioWebAddon',
    fragment: true,
    capabilities: 'dashboard,navigation,stremio,voice_search',
    voiceTarget: 'stremio',
    resolverSchemes: 'stremio',
    order: 7
]
```

Không thêm dependency Stremio mới. `androidx.webkit` đã có trong `modules/web`.

## 3. Web shell hooks

### `WebBrowserAddon`

Thêm protected constructor để dùng prefs và initial URL riêng:

```java
protected WebBrowserAddon(String preferenceFile, String initialUrl)
```

Constructor public hiện tại delegate về `("web", "http://google.com")` để không đổi hành vi Web Browser.

Yêu cầu:

- `LAST_URL` trở thành instance preference hoặc getter dùng initial URL của instance.
- `getLastUrl()`/`setLastUrl()` có visibility đủ cho subclass nhưng không public rộng hơn cần thiết.
- `getInfo()`, `getAddonId()`, `createFragment()` vẫn override được.
- Không làm thay đổi prefs namespace của Web Browser/YouTube hiện có.

### `WebBrowserFragment`

Thêm factory hooks:

```java
protected FermataWebClient createWebClient()
protected FermataChromeClient createChromeClient(FermataWebView web, ViewGroup fullScreen)
```

Sửa nhánh lưu URL khi view chưa tồn tại để gọi `getAddon()` thay vì lookup cứng `WebBrowserAddon.class`.

Không fork/copy toàn bộ Fragment.

## 4. Stremio Web classes

### `StremioWebAddon`

Trách nhiệm:

- addon ID/info/fragment;
- prefs namespace `stremio_web`;
- initial URL `https://web.stremio.com/#/`;
- voice target `stremio`;
- implement `QuickRecentProvider`;
- mở exact canonical route khi provider action hợp lệ.

Không implement MediaLib addon, source manager, protocol client, torrent engine hoặc backup contributor cho auth/Web storage.

### `StremioWebFragment`

Trách nhiệm:

- trả `R.id.stremio_fragment`;
- resolve đúng `StremioWebAddon`;
- tạo `StremioWebClient`;
- disable desktop-mode toggle;
- voice search mở `#/search?search={encoded}`;
- hiển thị onboarding external player đúng một lần;
- cung cấp callback mở canonical route cho Quick Recent.

Không inject/click DOM để chỉnh player setting. User chọn một lần trong Stremio Web: Settings → Player → Play in external player → Allow choosing.

### `StremioWebClient`

Trách nhiệm:

- intercept main-frame `intent://`;
- yêu cầu `request.hasGesture()`;
- xác nhận current top-level WebView URL thuộc HTTPS origin `web.stremio.com`;
- parse bằng `Intent.parseUri(..., Intent.URI_INTENT_SCHEME)`;
- bỏ package/component/selector/clipData/fallback/extras;
- chỉ nhận final data URI scheme `http` hoặc `https`;
- giới hạn URL length;
- debounce duplicate handoff;
- dispatch `StremioIntentPlayable` qua `MainActivityDelegate.playItem()`;
- giữ trang Web hiện tại;
- override `newReplacement()` để renderer recovery giữ subtype.

Không gọi `startActivity()` với parsed Intent.

### `StremioIntentPlayable`

Subclass/wrapper mỏng quanh `IntentPlayable` để giữ metadata an toàn:

- canonical detail route key nếu biết;
- safe title fallback;
- đánh dấu external/video/seekable theo Fermata contract;
- không giữ token, intent raw hoặc stream URL trong `toString()`/diagnostics.

SmartTop Current vẫn lấy dữ liệu từ `PlaybackSnapshot`; wrapper chỉ cải thiện fallback metadata.

### `StremioRouteStore`

Canonical route grammar:

```text
#/detail/{type}/{id}[/{videoId}]
#/metadetails/{type}/{id}[/{videoId}]
```

API gợi ý:

```java
Optional<Route> acceptVisitedUrl(String value, @Nullable String safeTitle, long nowMillis)
Optional<Route> current()
boolean matchesOpaqueId(String opaqueId)
void clear()
```

Validation:

- scheme `https`;
- host exact `web.stremio.com`;
- path `/`;
- chỉ hai hash route trên;
- giới hạn từng segment và tổng length;
- reject control characters, nested URL, `?streamingServerUrl`, token hoặc raw media URL;
- persist raw encoded route sau khi canonicalize, không decode/re-encode mơ hồ.

Route được cập nhật từ `doUpdateVisitedHistory()`, `onPageFinished()` và snapshot `webView.getUrl()` khi pause; không polling DOM.

## 5. Quick Recent generic contract

### Provider interface

```java
public interface QuickRecentProvider {
    FutureSupplier<List<QuickRecentCandidate>> loadQuickRecent(
            QuickRecentProviderLease lease, Context context);

    FutureSupplier<OpenResult> openQuickRecent(
            MainActivityDelegate activity,
            QuickRecentProviderLease lease,
            Context context,
            String opaqueId);

    enum OpenResult { HANDLED, NOT_HANDLED }

    record Context(
            @Nullable String activeCanonicalId,
            RuntimeHostMode hostMode,
            int maxItems) {}
}
```

Tên `Context` nên đổi nếu va chạm `android.content.Context`.

### Candidate

```java
public record QuickRecentCandidate(
        String addonClass,
        long lifecycleGeneration,
        String opaqueId,
        String title,
        String subtitle,
        long lastInteractionMillis) {}
```

Candidate không chứa URL, token, stream, provider hoặc PlayableItem. `opaqueId` là route key nội bộ có giới hạn length.

### Lease rules

- Chỉ `AddonManager` tạo lease.
- Candidate phải khớp addon class và lifecycle generation.
- Load/open đều timeout và fail independently.
- Click sau disable/reload addon trả `NOT_HANDLED`.
- `HANDLED` chỉ trả sau khi exact route được chấp nhận để mở.
- Không fallback từ provider candidate sang `playItem()`.

### Presentation model

`SmartTopViewState.quickRecent` đổi từ `List<PlayableItem>` sang `List<SmartTopQuickRecent>` có hai dạng:

- default MediaLib playable;
- provider-bound candidate + lease.

Dashboard click:

- playable → giữ behavior hiện tại;
- provider-bound → coordinator gọi provider open;
- `NOT_HANDLED`/failure/stale lease → refresh Quick Recent, không play.

Tối đa ba dòng, không phụ thuộc width AA, không làm full rebind khi chỉ timeline thay đổi.

## 6. External-player handoff contract

Input hợp lệ phải thỏa tất cả:

1. Main-frame request.
2. Có user gesture.
3. Current page origin là `https://web.stremio.com`.
4. Request scheme là `intent`.
5. Parsed final data URI là HTTP(S).
6. URL nằm trong length/resource limits.
7. Handoff không trùng token gần nhất.

Output:

- exactly one `playItem()`;
- WebView vẫn ở details/episode page;
- playback ownership chuyển sang Fermata;
- không mở package ngoài;
- không mark native Recent bằng media URL.

Invalid input:

- consume request;
- ghi privacy-safe reason enum;
- hiện lỗi ngắn nếu do user click;
- không log raw URL;
- không thử scheme/fallback khác.

## 7. Voice contract

`handleVoiceSearch(query, play)` luôn mở search route. Tham số `play=true` không được autoplay trong Stremio Web addon vì yêu cầu OPEN/select-only.

```text
https://web.stremio.com/#/search?search={UTF-8 encoded query}
```

## 8. Line budget guard

Thêm architecture test đo hotspot và cấm tăng không kiểm soát:

- Stremio-specific production ≤500 dòng, trừ generated/resource.
- Shared Web hooks ≤80 dòng.
- Quick Recent shared contract ≤320 dòng trước khi có justification trong phase report.

Không tối ưu bằng cách dồn code khó đọc vào một dòng hoặc bỏ validation/test.
