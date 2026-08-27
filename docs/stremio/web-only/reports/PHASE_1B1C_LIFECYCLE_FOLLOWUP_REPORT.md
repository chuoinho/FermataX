# Phase 1B/1C: Fullscreen and Lifecycle Follow-up

## Classification

- Phase 1B fullscreen Back and position-retention gate: **PASS** on the physical
  Android device.
- Phase 1C lifecycle gate: **PARTIAL**. Background/resume and renderer-recovery
  were deliberately not rerun because the control-only MediaSession observation
  was inconsistent in this session.

## Scope

- Worktree: `E:\\Chatgpt\\fermata-stremio-web-only`
- Branch and starting commit: `codex/stremio-web-only` at `c69e26bf`.
- Device: `15c36230` (Android 16), FermataX `2.0.1` / version code `304`.
- WebView: `com.android.webview 145.0.7632.109`.
- One temporary direct-MP4 addon, `Fermata Lifecycle Validation`, was installed
  and removed exclusively through Stremio's visible Addons UI. The original six
  addons were not modified.

## Fullscreen Evidence

The hosted Player visibly showed `Fermata Lifecycle MP4`, paused at `00:01:20`.
The normal in-player fullscreen control was selected, its accessible label
changed to `Exit fullscreen mode`, then exactly one Android Back was issued.

After Back:

- the hosted route was still a Player route;
- the fullscreen control had returned to `Enter fullscreen mode`;
- the player still showed the same item at `00:01:20`;
- no navigation to a detail/catalog route or video reload was visible.

The subsequent visible Play action advanced the same item to `00:01:27` and the
control became `Pause`. This proves the tested fullscreen exit preserved the
HTML5 player and its position, rather than recreating it.

## MediaSession Discrepancy

While the visible player was actively advancing (`00:01:20` to `00:01:27`),
`dumpsys media_session` twice reported the active
`FermataMediaService` session as `NONE`, position `0`, metadata `null`.

This conflicts with the earlier Phase 5B5 F3/F7/F8 physical observations, where
the same control-only bridge reported `PLAYING` and item metadata. It is not a
video-rendering failure: direct MP4 traffic had completed normally and the
visible Player advanced. It also means this run cannot honestly use
`MediaSession` as evidence for background/resume or hardware controls.

Static review explains the relevant boundary without reading page storage or
executing DOM code: `StremioWebMediaSessionBridge` claims only after the hosted
page has supplied a non-`none` playback state and a `play` or `pause` handler.
If the document does not emit that control schema, FermataX intentionally leaves
its control-only session at `NONE`; it does not infer state from the HTML5 video
or a stream URL. Passive logcat did not show a bridge exception. A later,
separately authorized diagnostic must determine why the page emitted the schema
in prior observed sessions but not this one.

## Deferred Matrix Items

- Background/resume under this lifecycle fixture.
- Switch to another Fermata addon and return.
- Renderer-loss recovery.
- Screen lock/unlock (not attempted because it would require handling a user
  lock-screen transition).
- Any MediaSession or hardware-control claim for this exact session.

No conclusion is made about those items from the fullscreen result.

## Cleanup Evidence

Before infrastructure cleanup, the Player was left through its normal visible
back control. `Fermata Lifecycle Validation` was then removed once with its
visible `Uninstall` control. The final Installed view contained exactly:

`Cinemeta`, `YouTube`, `WatchHub`, `Public Domain Movies`, `OpenSubtitles v3`,
and `Local Files (without catalog support)`.

The fixture process was stopped and the device `adb reverse tcp:7000` mapping
was removed. Host TCP 7000 has no listener and a device loopback probe returned
`Connection refused`. The execution environment rejected the subsequently
verified, explicitly scoped recursive deletion of the lifecycle-only Temp
directory and screenshots/XML, so those stopped local artifacts remain for
manual deletion; no process or ADB mapping refers to them. The pre-existing
`fermatax-p8-ephemeral-fixture` media input is not modified.

## Change Audit

- Production LOC: `0`.
- Test LOC: `0`.
- Repository change: this report only.
- No token, cookie, account API, storage, DOM automation, external player,
  native Stremio Core/player, torrent transport, or `aauto.aar` modification was
  used.
