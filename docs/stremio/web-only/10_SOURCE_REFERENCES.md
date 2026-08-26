# 10 — Source references và audit evidence

## 1. FermataX

### Web infrastructure

- [`FermataWebView.java`](https://github.com/chuoinho/FermataX/blob/main/modules/web/src/main/java/me/aap/fermata/addon/web/FermataWebView.java): JavaScript/DOM storage/cookie, mobile UA, input/AA lifecycle, renderer recovery surface.
- [`FermataWebClient.java`](https://github.com/chuoinho/FermataX/blob/main/modules/web/src/main/java/me/aap/fermata/addon/web/FermataWebClient.java): navigation callback, SSL fail-closed, transient retry, renderer recovery và subclass replacement hook.
- [`WebBrowserFragment.java`](https://github.com/chuoinho/FermataX/blob/main/modules/web/src/main/java/me/aap/fermata/addon/web/WebBrowserFragment.java): WebView/client construction, back/fullscreen/refresh/voice shell.
- [`WebBrowserAddon.java`](https://github.com/chuoinho/FermataX/blob/main/modules/web/src/main/java/me/aap/fermata/addon/web/WebBrowserAddon.java): prefs, last URL, fragment và lifecycle.
- [`FermataJsInterface.java`](https://github.com/chuoinho/FermataX/blob/main/modules/web/src/main/java/me/aap/fermata/addon/web/FermataJsInterface.java): generic JavaScript interface cần bounded/rate/security review.
- [`modules/web/build.gradle`](https://github.com/chuoinho/FermataX/blob/main/modules/web/build.gradle): YouTube và Web Browser đã cùng được đăng ký trong một dynamic feature; AndroidX WebKit đã là dependency.

### Player and external handoff

- [`IntentPlayable.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/media/lib/IntentPlayable.java): playable wrapper cho external URI.
- [`MainActivityDelegate.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java): `playItem()` và MediaService access.
- [`ExternalNavigationPolicy.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/addon/external/ExternalNavigationPolicy.java): pattern policy-bound navigation hiện có.

### SmartTop and Quick Recent baseline

- [`SmartTopCoordinator.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopCoordinator.java): Current/Resume/Recent state và timeline ownership.
- [`SmartTopViewState.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopViewState.java): hiện dùng `List<PlayableItem>` cho Quick Recent, là điểm cần generic hóa.
- [`SmartTopProvider.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/addon/SmartTopProvider.java), [`SmartTopProviderLease.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/addon/SmartTopProviderLease.java), [`SmartTopProviderCoordinator.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/addon/SmartTopProviderCoordinator.java): pattern lease/timeout/isolation để tham khảo, không dùng Resume candidate giả cho OPEN-only.
- [`DashboardFragment.java`](https://github.com/chuoinho/FermataX/blob/main/fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java): Quick Recent click hiện gọi playable navigation; cần typed/provider branch.

### Build/CI and legacy

- [`modules/stremio/build.gradle`](https://github.com/chuoinho/FermataX/blob/main/modules/stremio/build.gradle): legacy module đang phụ thuộc các artifact `jlibtorrent` cho ARM/ARM64/x86_64.
- [`build.gradle`](https://github.com/chuoinho/FermataX/blob/main/build.gradle): dynamic feature discovery/addon metadata generation và architecture dependency direction.
- [`settings.gradle`](https://github.com/chuoinho/FermataX/blob/main/settings.gradle): modules auto-discovery và universal build environment.
- [`gradle/libs.versions.toml`](https://github.com/chuoinho/FermataX/blob/main/gradle/libs.versions.toml): audited SDK/NDK/WebKit/jlibtorrent versions.
- [`.github/workflows/ci.yml`](https://github.com/chuoinho/FermataX/blob/main/.github/workflows/ci.yml): mobile/auto unit, architecture, lint và exactly-one universal APK gates; legacy Core checkout/probe cần cleanup cuối migration.

## 2. Stremio Web

### Architecture/build

- [`README.md`](https://github.com/Stremio/stremio-web/blob/development/README.md): React UI, Core WebAssembly/Web Worker và `stremio-video` architecture.
- [`package.json`](https://github.com/Stremio/stremio-web/blob/development/package.json): `@stremio/stremio-core-web`, `@stremio/stremio-video`, Node/pnpm và GPL-2.0 metadata.
- [`webpack.config.js`](https://github.com/Stremio/stremio-web/blob/development/webpack.config.js): static build, worker/WASM/service-worker assets.
- [release workflow](https://github.com/Stremio/stremio-web/blob/development/.github/workflows/release.yml): official release artifact process.

### Platform/external player

- [`device.ts`](https://github.com/Stremio/stremio-web/blob/development/src/common/Platform/device.ts): Android detection từ user agent.
- [`CONSTANTS.js`](https://github.com/Stremio/stremio-web/blob/development/src/common/CONSTANTS.js): Android `Allow choosing`, external player list và default streaming server URL.
- [`usePlayerOptions.ts`](https://github.com/Stremio/stremio-web/blob/development/src/routes/Settings/Player/usePlayerOptions.ts): profile playerType update.
- [`Stream.js`](https://github.com/Stremio/stremio-web/blob/development/src/routes/MetaDetails/StreamsList/Stream/Stream.js): external-player deep-link selection và mark-watched-on-click behavior.

### Routes/server/player limitations

- [`routerPaths.tsx`](https://github.com/Stremio/stremio-web/blob/development/src/router/routerPaths.tsx): `/detail`, `/metadetails`, `/search` và `/player` routes.
- [`useSearch.js`](https://github.com/Stremio/stremio-web/blob/development/src/routes/Search/useSearch.js): `search`/`query` parameter handling.
- [`SearchParamsHandler.js`](https://github.com/Stremio/stremio-web/blob/development/src/App/SearchParamsHandler.js): optional `streamingServerUrl` handling.
- [`usePlayUrl.ts`](https://github.com/Stremio/stremio-web/blob/development/src/common/usePlayUrl.ts): magnet requires a Ready streaming server.
- [`OptionsMenu.js`](https://github.com/Stremio/stremio-web/blob/development/src/routes/Player/OptionsMenu/OptionsMenu.js): external streaming/download/magnet links.
- [`usePlayOnDevice.ts`](https://github.com/Stremio/stremio-web/blob/development/src/routes/Player/usePlayOnDevice.ts): playback device requires streaming URL/server action.

## 3. Evidence-derived decisions

| Evidence | Decision |
|---|---|
| Web repo đã mang Core Web + video dependencies | Không tích hợp Core repo thứ hai |
| Android đã có `Allow choosing` | Dùng external-player contract, không scrape Core state |
| Magnet cần Ready server | Không hứa local torrent trong Web-only APK |
| Fermata Web shell đã có lifecycle/recovery | Subclass/hook, không copy/fork |
| Architecture CI cấm sibling implementation imports | Đặt Stremio addon trong `modules/web` |
| SmartTop Quick Recent hiện là playable list | Thêm generic provider contract cho OPEN-only |
| CI bắt exactly one universal APK | Không tách phone/Auto release |

## 4. Verification rule

AI triển khai phải mở lại các source trên ở HEAD thực tế. Nếu source đã thay đổi, cập nhật phase 0A/0B evidence và tests trước khi sửa thiết kế. Không dùng tài liệu này để bỏ qua re-audit current code.
