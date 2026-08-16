# Unified UI Shell Readiness

## Status

Source, CI and real-device runtime acceptance are complete. The Unified UI Shell acceptance matrix has passed on PHONE and AA/DHU/mirror hosts.

Validated runtime-tested checkpoint:

- Branch: `agent/unify-ui-shell`
- Runtime-tested source HEAD: `8c9ba6c7c07c9dccb8d374ca0731f82856669bfb`
- GitHub Actions: CI #152 / run `31946865623`
- Job: `95164168055`
- Result: `success`
- Device matrix: user-confirmed `PASS` for every checklist row.

## Source architecture closure

- [x] Top-bar title, Back target and Back visibility resolve through `TopBarPolicy` / `TopBarController`.
- [x] Toolbar structure is separate from semantic rendering; structural installers do not own route Back visibility or title text.
- [x] Default and custom Fermata toolbars use common Back/title authority without a competing generic writer.
- [x] Player-bar presentation is reducer/coordinator owned and does not directly write top-bar or nav-bar state.
- [x] Player-bar video gestures emit video-presentation commands instead of directly mutating `BodyLayout`.
- [x] Nav selection is rendered by `NavBarController` from authoritative destination state.
- [x] System/hardware Back, toolbar Back and automotive/player Back converge on `BackNavigationPolicy`.
- [x] UI-triggered fullscreen/split/frame transitions use the common video-presentation boundary; `BodyLayout` remains layout owner.
- [x] Route-exit video semantics are host-independent; PHONE/AA differences remain rendering/input capabilities.
- [x] Local-video playback preflights the committed viewport before decoder playback, with stale/rejected-request guards.
- [x] Web/YouTube, TV and Chat integration use common chrome/Back authority without core addon-specific semantic forks.
- [x] Architecture guards prevent cross-surface writers, addon Back forks and addon `BodyLayout` ownership from returning.

## Automated scenario and CI closure

- [x] Dashboard, TV root/nested/fullscreen/split, YouTube/Web browse/fullscreen, local video, audio-while-browsing and Settings/non-nav matrices are covered.
- [x] Common semantic matrix is exercised across PHONE, AA projection and mirror host modes.
- [x] Mobile unit suite.
- [x] Auto unit suite.
- [x] Web addon UI-shell guard.
- [x] TV addon UI-shell guard.
- [x] UI-shell single-writer guard.
- [x] Architecture boundary guards.
- [x] Mobile/Auto lint.
- [x] PR whitespace check.

CI #152 / run `31946865623` on `8c9ba6c7c07c9dccb8d374ca0731f82856669bfb` passed all required automated gates before this documentation-closing commit.

## Final production-diff audit

- [x] Topbar audited for duplicate title/Back writers and host-semantic forks.
- [x] Playerbar audited for direct topbar/navbar/video-layout mutations.
- [x] Navbar audited for direct destination/view writers and video-layout ownership.
- [x] Video presentation audited for host-semantic forks, first-frame ordering and stale/rejected playback requests.
- [x] Web/YouTube/TV/Chat integration boundaries audited.
- [x] Rotation/playerbar geometry regression completed the implement → test → audit → re-test loop.
- [x] PHONE legacy round Back overlay removed while common toolbar Back remains authoritative.
- [x] PHONE far-left hide-playerbar action removed without altering automotive Back/edge behavior.
- [x] Video scaling uses shared persistence across channel changes and is applied across supported addon video outputs.

## Device acceptance matrix — complete

### PHONE

- [x] Dashboard title/nav/Back.
- [x] TV root navigation and Back to Dashboard.
- [x] TV nested navigation and Back to parent.
- [x] TV playback title shows current channel.
- [x] TV first frame opens in the intended fullscreen viewport without a small-viewport flash.
- [x] TV fullscreen Back returns to split when supported.
- [x] TV split controls and subsequent Back behavior.
- [x] Local video first frame/fullscreen/split/Back flow.
- [x] YouTube browse/fullscreen/title/Back flow.
- [x] Web browse/fullscreen/history/Back flow.
- [x] Audio playback while browsing another route keeps route chrome stable.
- [x] Settings/non-nav page Back preserves and returns to the previous top-level destination.
- [x] Chat custom toolbar uses the common Back hierarchy.
- [x] Rotation/configuration-change regression retest.
- [x] No legacy round floating Back overlay.
- [x] No far-left hide-playerbar action.
- [x] Video scaling persists across channel changes and works on the tested addon video paths.

### AA / DHU / MIRROR

- [x] Dashboard/title/nav/Back semantics match PHONE for the same logical state.
- [x] TV root/nested/fullscreen/split semantics match PHONE.
- [x] Local-video fullscreen/split semantics match PHONE.
- [x] YouTube/Web browse/fullscreen/Back semantics match PHONE.
- [x] Audio-while-browsing and Settings/non-nav behavior match PHONE.
- [x] Player controls remain valid.
- [x] Focus and rotary behavior remain functional.
- [x] Edge-touch/automotive Back uses common Back semantics.
- [x] Host-specific nav placement/touch targets/system-bar integration remain correct.
- [x] Video-scaling behavior remains valid on the tested automotive/mirror paths.

## Exit condition

All source/CI and real-device acceptance gates are satisfied. PR #12 is eligible to leave draft after this documentation-closing commit passes the full CI workflow. Merge remains a separate explicit repository action.
