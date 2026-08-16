# Unified UI Shell Readiness

## Status

Pre-device source and CI closure is complete. Runtime acceptance on physical PHONE and AA/DHU/mirror hosts remains intentionally pending.

Validated source checkpoint before this documentation closure:

- Branch: `agent/unify-ui-shell`
- Source HEAD: `0d8300d00bcc8f852315dd78378be593c5491fd2`
- GitHub Actions run: `31935440098` (CI #125)
- Job: `95136479944`
- Result: `success`

The documentation commit containing this checklist must also pass the full CI workflow before device testing starts. The exact final documentation HEAD/run is recorded on PR #12 after that workflow completes; this avoids a self-referential commit where writing its own final SHA would create another SHA.

## Source architecture closure

- [x] Top-bar title, Back target and Back visibility resolve through `TopBarPolicy` / `TopBarController`.
- [x] Toolbar structure is installed separately from semantic rendering; structural installers do not own route Back visibility or title text.
- [x] Default Fermata fragments use `TopBarMediator` instead of the generic competing `BackTitle` lifecycle writer.
- [x] Custom toolbars receive the canonical Back affordance and common activity Back action.
- [x] Player-bar presentation state remains reducer/coordinator owned and does not directly write top-bar or nav-bar views.
- [x] Player-bar video gestures emit video-presentation commands instead of directly mutating `BodyLayout`.
- [x] Nav selection is rendered only by `NavBarController` from authoritative destination state.
- [x] Programmatic top-level route changes synchronize destination state while non-nav pages preserve the previous top-level selection.
- [x] Nav reselection emits common navigation/Back-policy intents and does not own video-layout transitions.
- [x] System/hardware Back, toolbar Back and automotive/player Back converge on `BackNavigationPolicy`.
- [x] UI-triggered fullscreen/split/frame transitions use the common video-presentation command boundary; `BodyLayout` remains layout state owner.
- [x] Route exit from video mode has host-independent semantics; PHONE/AA differences remain rendering/input capabilities only.
- [x] Local-video playback preflights the committed fullscreen viewport before decoder playback begins, with stale-request and rejected-request guards.
- [x] Web toolbar Back structure/action/visibility use common top-bar authority; Web history remains fragment-specific Back behavior.
- [x] YouTube contributes playback-title context without owning canonical Back/title rendering.
- [x] TV continues through common `MediaLibFragment` Back/toolbar behavior and does not own core chrome or `BodyLayout` transitions.
- [x] Chat custom toolbar is covered by canonical top-bar reconciliation and does not own Back semantics.
- [x] Core UI contains no concrete `TvFragment`, `YoutubeFragment` or `WebBrowserFragment` chrome/navigation branches.
- [x] Architecture guards prevent cross-surface writers, addon Back forks and addon `BodyLayout` ownership from returning.

## Automated scenario coverage

- [x] Dashboard: no Back; Dashboard selected.
- [x] TV root: common Back target; TV selected.
- [x] TV nested: parent Back hierarchy; TV selected.
- [x] TV fullscreen: playback title and fullscreen-exit semantics.
- [x] TV split: common Back/control semantics.
- [x] YouTube/Web browse: common route Back semantics.
- [x] YouTube/Web fullscreen: fullscreen exits before route/history navigation and title remains on common policy.
- [x] Local video: common title and fullscreen/split Back semantics.
- [x] Audio playback while browsing another route: route chrome remains stable and player presentation remains independent.
- [x] Settings/non-nav pages: common Back hierarchy while previous top-level nav selection is preserved.
- [x] Common semantic matrix is exercised across PHONE, AA projection and mirror host modes.

## CI closure at source checkpoint

CI #125 / run `31935440098` on `0d8300d00bcc8f852315dd78378be593c5491fd2` passed every required gate:

- [x] Mobile unit suite.
- [x] Auto unit suite.
- [x] Web addon UI-shell guard.
- [x] TV addon UI-shell guard.
- [x] UI-shell single-writer guard.
- [x] Architecture boundary guards.
- [x] Mobile/Auto lint.
- [x] PR whitespace check.

## Final production-diff audit

- [x] Topbar diff audited for duplicate title/Back writers and host-semantic forks.
- [x] Playerbar diff audited for direct topbar/navbar/video-layout mutations.
- [x] Navbar diff audited for direct destination/view writers and video-layout ownership.
- [x] Video-presentation diff audited for host-semantic forks, first-frame viewport ordering and stale/rejected playback requests.
- [x] Web/YouTube production diff audited against common top-bar/Back ownership.
- [x] TV current integration audited; no PR production changes introduce a TV-specific core authority.
- [x] Chat current integration audited; custom toolbar relies on canonical toolbar reconciliation.
- [x] Remaining formatting-only hunks do not change runtime semantics and are covered by the whitespace gate; no risky large-file cosmetic rewrite is required before device QA.

## Device acceptance matrix — pending

### PHONE

- [ ] Dashboard title/nav/Back.
- [ ] TV root navigation and Back to Dashboard.
- [ ] TV nested navigation and Back to parent.
- [ ] TV playback title shows current channel.
- [ ] TV first frame opens in the intended fullscreen viewport without a small-viewport flash.
- [ ] TV fullscreen Back returns to split when supported.
- [ ] TV split controls and subsequent Back behavior.
- [ ] Local video first frame/fullscreen/split/Back flow.
- [ ] YouTube browse/fullscreen/title/Back flow.
- [ ] Web browse/fullscreen/history/Back flow.
- [ ] Audio playback while browsing another route keeps route chrome stable.
- [ ] Settings/non-nav page Back preserves and returns to the previous top-level destination.
- [ ] Chat custom toolbar shows the canonical Back affordance and common Back hierarchy.

### AA / DHU / MIRROR

- [ ] Dashboard/title/nav/Back semantics match PHONE for the same logical state.
- [ ] TV root/nested/fullscreen/split semantics match PHONE.
- [ ] Local video fullscreen/split semantics match PHONE.
- [ ] YouTube/Web browse/fullscreen/Back semantics match PHONE.
- [ ] Audio-while-browsing and Settings/non-nav behavior match PHONE.
- [ ] Player controls remain valid.
- [ ] Focus and rotary behavior remain functional.
- [ ] Edge-touch/automotive Back affordance uses common Back semantics.
- [ ] Host-specific nav placement/touch targets/system-bar integration remain correct.

## Exit condition

Do not move PR #12 out of draft or merge it until every device checkbox above is confirmed on real runtime hosts and any discovered regression has completed the implement → test → audit → fix → re-test loop.
