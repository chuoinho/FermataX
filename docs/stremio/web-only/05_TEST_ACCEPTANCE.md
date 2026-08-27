# 05 - Acceptance Matrix

| Requirement | Evidence required | Current state |
|---|---|---|
| One Web-only addon, no legacy feature | Gradle projects/dependency graph | PASS |
| Addon is enabled and opens hosted origin | Physical device UI | PASS |
| Existing authenticated Web session remains | Physical device avatar/session UI | PASS |
| Catalog/search/library/settings are usable | Physical device manual test | PARTIAL (catalog/detail/trailer route observed) |
| Android WebView Media Session compatibility | Physical device DevTools and logcat | PASS |
| ADB play/pause/toggle control | Physical device trailer and `dumpsys media_session` | PASS |
| Next control | Physical device only when Stremio registers `nexttrack` | NOT OBSERVED (not advertised) |
| Inline HTTP MP4/HLS playback | Physical device with working server | PASS (P5B5 direct MP4 and HLS fixture) |
| Fullscreen without reload/position loss | Physical device video fixture | PARTIAL (custom-view entry/Back route observed; position not measured) |
| Back order | Physical device video fixture | PASS (P5B5 fullscreen exit preserves Player/playback) |
| Subtitle selection | Physical device video fixture | PASS (P5B5 explicit `OFF` -> `English`, rendered WebVTT) |
| Background/recovery/switching | Physical device lifecycle matrix | PARTIAL (P5B5 background/resume observed; switching not accepted) |
| No impact to other addons | Existing unit suite plus manual AA sweep | PARTIAL |

`PASS` requires observed evidence, not an inference from source code. A missing streaming server
is a test-environment limitation, not a reason to add a native playback fallback.

The previous blank-detail failure was attributable to Stremio Web calling an unavailable Android
WebView Media Session API. The compatibility shim is now exercised on-device. Direct-server
playback, seek, and explicit subtitle selection are physically observed; audio-track handling,
Automotive host controls, torrent transport, and renderer-loss recovery remain separately
unverified.
