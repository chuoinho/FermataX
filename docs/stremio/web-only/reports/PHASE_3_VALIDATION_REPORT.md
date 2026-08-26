# Phase 3 Validation Report - Automotive, Renderer Recovery, and Server Playback

## Scope and Test Subject

- Input commit: `d925eb4a feat(stremio): bridge WebView media session controls`
- Worktree: `codex/stremio-web-only`
- Device: Redmi Note 8 (`15c36230`), Android 16
- Application: `me.app.fermataX.auto` 2.0.1 (304)
- WebView provider: Android System WebView 145.0.7632.109
- Automotive host: Android Auto 17.3.662854-release through DHU, `all_720p.ini`, touch input.

The hosted architecture was preserved throughout this validation:

```text
web.stremio.com -> HTML5/Chromium WebView
Android media controls -> Fermata control-only bridge -> hosted callbacks
```

No native player, stream URL extraction, Stremio Core, torrent/server dependency, external player,
DOM/router manipulation, or `aauto.aar` change was made.

## Automotive and Ownership Evidence

| Check | Result | Observed evidence |
| --- | --- | --- |
| `DHU_HOSTED_UI_PASS` | PASS | Stremio was visible in Dashboard, opened `https://web.stremio.com/#/`, retained the authenticated session, rendered the catalog and Obsession detail, and played the repeatable HTML5 trailer in DHU. No chooser, crash, ANR, or renderer-loss was observed. |
| Stremio control-only claim | PASS | During the trailer, `dumpsys media_session` reported Fermata active, `PLAYING`, with control-only actions (`518`). |
| Release when leaving Stremio | PASS | Switching to another addon released Stremio immediately: `active=false`, `state=NONE`, `actions=0`. |
| Cross-addon ownership | PASS | A YouTube video opened in DHU subsequently held Fermata's ordinary player session with the video engine actions (`2375551`). ADB `MEDIA_PAUSE` then `MEDIA_PLAY` changed that session from `PAUSED` to `PLAYING`. This proves the released Stremio bridge did not retain ownership. |
| `ADB_MEDIA_KEY_PASS` for Stremio | PASS (prior Phase 2 evidence) | Real trailer playback accepted ADB pause, play, and toggle with the expected one-step MediaSession state transitions. `NEXT` did nothing because the page did not register `nexttrack`. |
| `DHU_MEDIA_KEY_PASS` | NOT VERIFIED | The current DHU session did not expose a vehicle media transport control path. Windows `VK_MEDIA_PLAY_PAUSE` did not arrive as a DHU media event, so it is neither an app pass nor fail. ADB keyevents are intentionally recorded separately. |
| DHU reconnect | PARTIAL | Closing and reopening DHU was safe for the phone/app state, but the new DHU process showed its `Media Playback Status` window instead of reconnecting its projection surface. No application state, cookie, or Android Auto configuration was changed to force a reconnection. |

Touch navigation through the hosted UI was observed. A hardware/host D-pad and real vehicle media-button
matrix remains unverified because this DHU configuration did not supply those events.

## Renderer-Recovery Audit

`FermataWebClient.onRenderProcessGone()` completes the loading state and delegates to
`FermataWebView.recoverRenderProcess()`. That method obtains a non-script recovery URL, exits an old
custom view if needed, creates a replacement, initializes it with replacement web/chrome clients,
removes and destroys the old view, attaches the replacement at the same index and ID, then reloads
the recovery URL.

For Stremio, the replacement factory is covariant: `StremioWebView.createReplacementView()` returns
`StremioWebView`. Its `init()` constructs and installs a new document-start script and message
listener. Destruction closes the old bridge, resets its document state, removes the script/listener,
and releases any control-only claim. This makes stale page messages and old callbacks unable to
claim the current session.

| Check | Result | Evidence |
| --- | --- | --- |
| `UNIT_RECOVERY_PASS` | PARTIAL | Existing unit coverage validates recovery URL selection and the bridge's origin, bounded-message, state-reset, and handler semantics. There is no repository renderer-loss hook or Android instrumentation suite that can safely instantiate/kill a real WebView renderer. |
| Replacement subtype/lifecycle audit | PASS | Code audit confirms Stremio replacement and bridge lifecycle described above. |
| `PHYSICAL_RECOVERY_BLOCKED_SAFE_TEST_UNAVAILABLE` | BLOCKED | No safe, targeted physical renderer-loss trigger exists in this test environment. Killing Android System WebView or clearing its data was intentionally excluded. |

No production change is justified by this audit: no renderer-loss defect was reproduced and the
replacement path already preserves the Stremio-specific bridge boundary.

## Streaming Server and Real Stream Playback

Hosted Stremio Web still reports that its streaming server is unavailable. No server URL, token,
or endpoint was configured, logged, or stored during validation. The project must not use an
unknown public torrent server or fabricate a server URL.

| Check | Result |
| --- | --- |
| Streaming-server configuration | `BLOCKED_USER_CONFIGURATION` |
| HTTP/HLS or torrent-backed playback | `BLOCKED_USER_CONFIGURATION` |
| Duration/position/seek/resume | `BLOCKED_USER_CONFIGURATION` |
| Subtitle and audio-track selection | `BLOCKED_USER_CONFIGURATION` |
| Server failure/reconnect matrix | `BLOCKED_USER_CONFIGURATION` |
| Physical `nexttrack` episode transition | `BLOCKED_FIXTURE_UNAVAILABLE` |

The only observed playback is the hosted Obsession HTML5 trailer. It must not be treated as
evidence of server-backed content playback, subtitle support, seeking, audio-track switching,
or episode progression.

To run the blocked matrix, the user must provide or configure a valid streaming-server endpoint
reachable by the phone, whether it requires authentication, and a non-sensitive playable fixture
(movie, multi-episode series, subtitle fixture, and optionally multi-audio fixture). The endpoint
and credentials must remain out of source control and validation reports.

## Regression and Build Gates

The Phase 3 work changed documentation only. No production or test source, dependency, manifest,
or build configuration changed. The current release gates were nevertheless rerun against this
worktree with the approved local upload credential.

| Gate | Result |
| --- | --- |
| `git diff --check` before documentation | PASS |
| Stremio Web session/catalog/trailer route | PASS on device and DHU |
| No external-player handoff | PASS for hosted trailer |
| YouTube session after Stremio release | PASS |
| Web Browser and VLC/media-addon regression | NOT RE-RUN in this validation; no shared production code changed |
| `gradlew projects -PWEB_STREMIO=true` | PASS; `:stremio` is absent |
| `:web:dependencies` mobileDebug runtime classpath | PASS; no `jlibtorrent` or new player/torrent dependency |
| `:web:testMobileDebugUnitTest` | PASS |
| `:fermata:assembleAutoRelease` | PASS |
| `:fermata:packageAutoReleaseUniversalApk` | PASS |
| `aauto.aar` SHA-256 | PASS: `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B` |
| Universal APK signing certificate | PASS: SHA-256 `A8:6D:57:6F:F1:EC:0E:32:45:F5:A6:15:2C:5D:8D:66:B5:DF:8B:B5:10:82:0D:75:DC:61:11:0B:19:C3:AE:B4` |

The current unit/build gates must be rerun after any future source change.

## Phase 3 Status

**PARTIAL.** Automotive hosted UI, Stremio claim/release, and cross-addon ownership behavior are
observed. Physical renderer-loss, DHU-native vehicle transport controls, real server-backed
playback, seek, subtitles, audio tracks, and `nexttrack` are not accepted without their required
safe trigger, DHU input path, server configuration, and fixtures.

Known boundary: hosted Stremio Web plus Chromium remains the sole renderer and player. The
control-only bridge cannot and does not provide native playback, stream transport, or media
timeline ownership.
