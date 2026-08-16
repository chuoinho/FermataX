# Unified UI Shell Goal

## Objective

Unify FermataX chrome so PHONE, AA projection and DHU/mirror hosts share the same semantic mechanisms for top bar, player bar, navigation bar, Back navigation and video presentation. Host-specific code may change rendering, focus, rotary, edge-touch and responsive geometry, but must not fork navigation/title/playback semantics.

## Required authorities

- One top-bar authority for visibility, title, Back target and actions.
- One player-bar authority for playback controls, visibility, seek state and timeout.
- One nav-bar authority for destinations, selected destination and visibility.
- One Back navigation policy used by toolbar Back, system/hardware Back and automotive Back affordances.
- One video-presentation authority for FRAME/BOTH/VIDEO transitions.

No surface may directly mutate another surface. Addons may contribute generic capabilities/actions but core must not depend on addon fragment classes.

## Acceptance criteria

1. Same logical route/playback/presentation state produces the same title, Back destination, selected navigation destination and playback-control semantics on PHONE and automotive hosts.
2. PHONE/automotive differences are limited to host capabilities such as nav placement, touch target size, focus/rotary behavior, edge touch and system-bar integration.
3. Top-bar Back visibility is derived from the resolved Back target, not from a second independent visibility rule.
4. Top-bar title has one resolver. TV/video playback title behavior is the same on PHONE and automotive hosts.
5. Player-bar state is reducer/coordinator owned and does not directly write top-bar or nav-bar views.
6. Nav-bar selection is derived from navigation state and nav rendering does not directly own video-layout transitions.
7. FRAME/BOTH/VIDEO decisions remain in video presentation/layout policy; chrome surfaces only observe presentation state.
8. Fragment, playback, mode and configuration events update authoritative state and trigger recomputation; they are not independent state writers.
9. Core contains no TvFragment/YoutubeFragment/WebBrowserFragment-specific chrome/navigation branches.
10. Existing YouTube/Web fullscreen behavior, TV/local split/fullscreen behavior, audio playback, settings/dashboard navigation and AA/DHU focus/rotary behavior remain functional.
11. Tests cover the common state matrices and architecture guards prevent cross-surface mutations from returning.

## Required scenario matrix

- Dashboard: no Back; Dashboard selected.
- TV root: Back to Dashboard; TV selected.
- TV nested: Back to parent; TV selected.
- TV fullscreen: current channel title; Back leaves fullscreen to split when supported; TV selected.
- TV split: normal app Back; video controls remain valid; TV selected.
- YouTube/Web browse: route Back semantics remain valid.
- YouTube/Web fullscreen: playback/page title according to common title policy; Back exits fullscreen before route navigation.
- Local video: item title and valid fullscreen/split Back semantics.
- Audio playback while browsing another route: route chrome remains stable and player bar remains independent.
- Settings/non-nav pages: Back returns through common navigation hierarchy while previous top-level nav selection is preserved.

## Execution loop

For every phase:

1. Re-fetch branch HEAD and relevant source before editing.
2. Implement one authority migration only; do not mix unrelated UI behavior.
3. Add/update behavioral tests and architecture guards.
4. Run available validation. If local Android tooling is unavailable, push the phase and inspect GitHub status/workflow results; never claim unrun tests passed.
5. Audit the diff for duplicate writers, host semantic forks, addon dependencies and stale lifecycle paths.
6. Fix every finding from the audit and re-run validation.
7. Repeat audit/fix/validation until the phase acceptance criteria pass.
8. Commit the phase separately.

## Completion status

**COMPLETE.** All acceptance criteria and scenario-matrix rows have passed automated and real-device validation.

Runtime-tested implementation checkpoint:

- Branch: `agent/unify-ui-shell`
- Source HEAD: `8c9ba6c7c07c9dccb8d374ca0731f82856669bfb`
- GitHub Actions: CI #152 / run `31946865623`
- Job: `95164168055`
- Result: `success`
- Gates passed: Mobile unit suite, Auto unit suite, Web addon UI-shell guard, TV addon UI-shell guard, UI-shell single-writer guard, architecture boundary guards, Mobile/Auto lint and PR whitespace check.
- Real-device matrix: user-confirmed `PASS` for every checklist row on the tested PHONE and AA/DHU/mirror paths.

The device cycle also closed the discovered runtime regressions: rotation/fullscreen reflow, responsive playerbar geometry, removal of the legacy PHONE round Back overlay, removal of the PHONE far-left hide-playerbar action, and shared video-scaling persistence/application across the tested video/addon paths.

`UI_SHELL_READINESS.md` is the final acceptance record. A documentation-only closing commit must pass full CI before PR #12 leaves draft. Merge remains a separate explicit action.
