# Phase 5B5: FermataX Hosted Playback Matrix

## Final Classification

**`PHASE_5B5_PARTIAL`**.

The physical Android device proved that the hosted Stremio WebView can play the
temporary direct MP4 and HLS fixtures through FermataX's normal visible UI.
MP4 range seeking, the control-only MediaSession bridge, and background/resume
were also observed. At the time of this initial matrix run, the fullscreen Back
contract failed and no explicit normal subtitle-selection action could be
observed. The fullscreen failure is subsequently fixed and physically
revalidated in `PHASE_5B5_FULLSCREEN_BACK_REMEDIATION_REPORT.md`; the phase
remains partial until explicit subtitle selection is observed. No production or
test code was changed during this initial matrix run.

## Environment and Safety Boundary

- Worktree: `E:\\Chatgpt\\fermata-stremio-web-only`, branch
  `codex/stremio-web-only`.
- Device: physical ADB device `15c36230`, Android 16 (SDK 36).
- Application: `me.app.fermataX.auto`, version `2.0.1` (version code 304).
- The user-synced Installed list began with exactly six addons: Cinemeta,
  YouTube, WatchHub, Public Domain Movies, OpenSubtitles v3, and Local Files.
- One temporary `Fermata Local Validation` addon was installed through the
  visible Stremio UI. No existing addon was changed, reordered, or removed.
- Fixture transport was host loopback plus temporary
  `adb reverse tcp:7000 tcp:7000`. No torrent, Core dispatch, DOM script,
  storage inspection, account API, external player, or production-code change
  was used.

## Observed Results

| ID | Result | Physical-device evidence |
| --- | --- | --- |
| F0 | PASS | FermataX opened the hosted Stremio session and retained the normal user-facing catalog/addon UI. No legacy/native Stremio player surface appeared. |
| F1 | PASS | The standard Addons UI showed one `Fermata Local Validation` fixture; Discover exposed the `fermata-local` catalog and both MP4/HLS cards. |
| F2 | PASS | Normal catalog -> detail -> `Local MP4` stream selection reached `#/player/...`; the visible control changed to `Pause`. Fixture evidence recorded a preflight `HEAD`, video `GET` with `Range`, and `206` response. |
| F3 | PASS | While MP4 was active, `dumpsys media_session` showed FermataMediaService active and `PLAYING`, with metadata `Fermata Local MP4`. Leaving the player later returned it to `NONE` with empty metadata. |
| F4 | PASS | A visible seek moved playback from about `00:00:22` to `00:00:48`; the fixture recorded a later video `GET` carrying a new byte range and `206`. Playback remained `PLAYING`. |
| F5 | PARTIAL | The fixture VTT was fetched and the rendered text `Subtitle timing test` was visible in the player. However, no separate visible subtitle-menu selection was available/observed, so the explicit selection-path criterion is not claimed. |
| F6 | FAIL at initial run; later PASS | The initial run returned to the detail route and released the session. The root cause, narrow repair, and physical PASS evidence are recorded in `PHASE_5B5_FULLSCREEN_BACK_REMEDIATION_REPORT.md`. |
| F7 | PASS | With MP4 active, Home moved focus to Launcher while FermataMediaService remained `PLAYING`. Reopening FermataX restored the hosted Player at about `00:00:33`, with `Pause`, correct metadata, and `PLAYING` state. No crash or ANR occurred. |
| F8 | PASS | Normal `Local HLS` selection reached the visible player (`Pause`, about `00:00:10`). The fixture recorded HLS manifest and media-segment `GET` responses; the bridge was `PLAYING` with metadata `Fermata Local HLS`. |
| F9 | PASS | Fixture was removed through the canonical visible Addons `Uninstall` control. The post-action list showed the original six addons and no fixture. |

## Safe Network Evidence

All identifiers below are path classes only; no account data, media URLs,
query strings, cookies, or route payloads are recorded.

| Scenario | Observed request/result |
| --- | --- |
| MP4 catalog/detail/stream | `GET /catalog/...`, `GET /meta/...`, `GET /stream/...` -> `200` |
| MP4 handoff | `HEAD /media/<redacted>.mp4` -> `200`; `GET /media/<redacted>.mp4` with `Range` -> `206` |
| MP4 subtitles | `GET /subtitles/...` and `GET /media/<redacted>.vtt` -> `200` |
| MP4 seek | a later MP4 `GET` carried a different byte range -> `206` |
| HLS handoff | `GET /hls/<manifest>` and successive `GET /hls/<segment>` -> `200` |

## Fullscreen Failure Detail

The failure is bounded to the Back contract, not fullscreen entry or rendering:

1. The player advertised `Enter fullscreen mode`.
2. The normal visible fullscreen control was selected.
3. Accessibility state showed `browserFullScreenView` and the control changed
   to `Exit fullscreen mode`; video and fixture subtitle text remained visible.
4. One normal Android Back left the player route for the detail route and the
   Fermata MediaSession became `NONE`.

This must be diagnosed before claiming fullscreen navigation parity. It was
not masked by replaying the item or changing any application state.

## Cleanup Evidence

- The player was exited before cleanup and the temporary addon was removed once
  through the normal Installed/Addons UI.
- The original six addons were visible after removal; `Fermata Local
  Validation` was absent.
- The fixture Node PID `50156` was stopped.
- `adb reverse --list` contains no TCP 7000 mapping.
- Host TCP port 7000 has no listener.
- Device loopback probe to TCP 7000 returned `Connection refused`.
- The fixture directory under the local Temp folder was removed.
- No B5 ADB forward was created. Existing unrelated forwards
  (`tcp:9223` and `tcp:5277`) were left untouched.
- Device rotation was restored to `accelerometer_rotation=1` and
  `user_rotation=0`.

## Change Audit and Remaining Work

- Production/test LOC: `0`.
- Repository change: this report only.
- No crash, ANR, unexpected external-app launch, or account/addon mutation was
  observed outside the single temporary fixture lifecycle.
- Do not claim completion for explicit subtitle selection, fullscreen Back
  behavior, torrent transport, `nexttrack`, renderer-loss recovery, AA/DHU
  hardware-button behavior, HLS quality selection, audio-track switching, or
  persistent resume semantics.

## Checkpoint

Phase 5B5 remains **PARTIAL**. The next work must first diagnose the visible
fullscreen Back regression and separately obtain explicit subtitle-selection
evidence; it must not infer either from the successful MP4/HLS transport runs.
