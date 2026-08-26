# 05 — Test và acceptance plan

## 1. Test pyramid

| Level | Trọng tâm |
|---|---|
| Pure unit | Route parser, intent parser, candidate validation, debounce |
| Coordinator unit | Provider lease, timeout, current context, HANDLED/NOT_HANDLED |
| Robolectric | Web fragment/addon factory, prefs, lifecycle |
| Instrumentation | Real WebView callbacks, cookie, hash route, renderer recovery |
| E2E | Production Stremio Web, direct playback, phone/AA/DHU |
| Build/architecture | Dependency boundaries, APK count, native libs, line budget |

## 2. Unit test inventory

### Intent parser

- valid HTTP and HTTPS.
- mixed-case scheme.
- malformed intent.
- missing data.
- file/content/javascript/data/magnet.
- package/component/fallback ignored.
- user-info/control chars/oversize.
- non-main-frame/no gesture/non-Stremio origin.
- duplicate within debounce window.
- different URL outside/inside window.

### Route store

- `/detail` and `/metadetails` movie/series/episode.
- percent-encoded IDs.
- missing/extra segments.
- other host/scheme/path.
- nested URL/control characters/oversize.
- query with server/token rejected from persistence.
- last valid route survives invalid navigation.
- schema migration/clear.

### Quick Recent

- provider timeout/failure isolated.
- max candidate count and ordering.
- wrong addonClass/generation rejected.
- stale lease returns `NOT_HANDLED`.
- active current route excluded.
- click `HANDLED` opens exact route once.
- `NOT_HANDLED` refreshes without playback.
- default MediaLib playable entries retain old behavior.
- maximum three rows.
- no candidate enters `DefaultRecent`.

### Web shell regression

- default Web Browser prefs/home unchanged.
- YouTube factory/client subtype unchanged.
- Stremio uses its own prefs namespace.
- renderer replacement remains `StremioWebClient`.
- voice `play=true` still search-only.

## 3. Instrumentation matrix

| Scenario | Phone | DHU/AA compact | DHU/AA wide |
|---|---:|---:|---:|
| Cold start/login/guest | Required | Required | Required |
| Cookie/session restore | Required | Required | Required |
| Discover/search/details | Required | Required | Required |
| Keyboard/focus/back | Required | Required | Required |
| Direct MP4 handoff | Required | Required | Required |
| HLS handoff | Required | At least one | Required |
| SmartTop Current | N/A where hidden | Required | Required |
| Quick Recent OPEN-only | N/A where hidden | Required | Required |
| Background/foreground | Required | Required | Required |
| Renderer recovery | Required | Required | One DHU profile |
| Network loss/recovery | Required | Required | Required |

Mobile/Auto là test/source-set nội bộ; output phát hành vẫn là một APK.

## 4. Stremio functional smoke

1. Open home/Board.
2. Guest hoặc email login.
3. Open Addons; install/config/remove một test addon hợp lệ.
4. Discover và search.
5. Open movie details.
6. Open series, season và exact episode.
7. Library add/remove.
8. Calendar/Continue Watching page.
9. Set Android external player to Allow choosing.
10. Direct media handoff.
11. Back returns to exact details/episode context.
12. Quick Recent reopens exact context without stream selection.

Không dùng nội dung vi phạm quyền để test; dùng stream sample hợp pháp/kiểm soát.

## 5. Server-dependent smoke

### No server

- Select magnet/source requiring server.
- Web reports server unavailable.
- Fermata receives no `magnet:` and starts no player.
- No retry loop/service start/background process.

### External test server

- Configure endpoint entirely in Stremio Web Settings.
- Source resolves to HTTP(S).
- Handoff uses same validator/player path.
- Stop server mid-flow; user sees network/playback failure without crash or secret log.

## 6. Playback ownership acceptance

- A valid click creates exactly one Fermata playable.
- Stremio Web audio/video does not continue underneath after handoff.
- Fragment recreation does not duplicate playback.
- MediaSession actions control Fermata player.
- SmartTop title/play-pause/progress track Fermata snapshot.
- Timeline update does not trigger full card rebind/flicker.
- Current playable is never replaced by Quick Recent navigation.

## 7. CI commands

Giữ và chạy ít nhất:

```bash
./gradlew testMobileDebugUnitTest :whisper:testMobileDebugUnitTest \
  --no-daemon --no-parallel --stacktrace

./gradlew testAutoDebugUnitTest \
  --no-daemon --no-parallel --stacktrace

./gradlew :fermata:lintMobileDebug :fermata:lintAutoDebug \
  --no-daemon --no-parallel --stacktrace

./gradlew :fermata:packageAutoDebugUniversalApk \
  --no-daemon --no-parallel --stacktrace
```

Thêm targeted suites:

```bash
./gradlew :web:testMobileDebugUnitTest :web:testAutoDebugUnitTest \
  --no-daemon --no-parallel --stacktrace

./gradlew :fermata:testMobileDebugUnitTest \
  --tests '*QuickRecent*' --tests '*Architecture*' --tests '*SmartTop*' \
  --no-daemon --no-parallel --stacktrace
```

Task/name phải được xác nhận bằng Gradle ở HEAD; không đoán nếu variant thay đổi.

## 8. Build artifact gates

- Exactly one file `FermataX-debug-universal.apk` hoặc release equivalent.
- Không upload Mobile/Auto APK riêng.
- Sau cutover, APK không chứa libtorrent native libraries/classes.
- Không chứa static Stremio Web bundle ngoài quyết định hosted model.
- Không chứa secret/test server URL.
- APK size delta được ghi so với Phase 0A.
- `git diff --check` sạch.

## 9. Exit acceptance

Release candidate chỉ PASS khi:

1. All mandatory tests xanh ở exact HEAD.
2. Production Web contract smoke xanh.
3. Direct MP4/HLS phone và DHU xanh.
4. No-server path đúng semantics.
5. Quick Recent OPEN-only được chứng minh bằng test spy: không resolve/play.
6. One playback owner, one MediaSession, one APK.
7. Security/log/secret/native/size audits xanh.
8. Rollback drill hoàn tất.
