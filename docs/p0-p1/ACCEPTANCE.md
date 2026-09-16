# FermataX P0/P1 — Final acceptance record

Date closed: 2026-09-16
Branch: `codex/phone-control-p0`
Status: **ACCEPTED**

This is the retained delivery record for Phone Control P0 and the approved
P1 scope. It replaces the implementation prompts, intermediate plans,
reference notes, and per-step evidence ledgers that were used while building
the work.

## Accepted P0 scope

- Phone roots use the approved separation: **Control**, the original
  Dashboard with its full addon navigation, and **Settings**. The dedicated
  bottom menu is limited to the phone roots; addon/dashboard child screens
  retain the normal addon navigation.
- Control presents both normal media and supported Web control-only sessions
  without manufacturing a playable item. It uses the existing binder as the
  only transport boundary.
- Addon rows refresh after Settings changes and restore the previous `OPEN`
  row when a newer request replaces it. A phone-to-car route is guarded by
  its current host, connection epoch, addon state, and view/request lifetime;
  stale work is cancelled rather than replayed after reconnect.
- The compact car-state badge distinguishes checking, disconnected, connected,
  and visible-on-car states without starting a car host from the phone.
- Readiness in phone Settings reports six independent read-only checks:
  connection, Control channel, notifications, overlay, screen capture, and
  battery optimization. System Settings can open only after an explicit tap.
  These checks do not request permissions, start projection/services, or gate
  playback.

## Accepted retained P1 scope

- YouTube has a persisted automatic-fullscreen preference. Disabling it
  cancels pending automatic entry but preserves manual fullscreen.
- YouTube's internal fullscreen uses its dedicated host path. The competing
  generic custom-view geometry path was removed from that route, resolving
  the observed fullscreen flicker.
- Control-only session ownership is lease-based and stale commands, metadata,
  artwork, and callbacks cannot take over a newer owner.
- Automotive SmartTop supports the retained Web presentation safely: only
  Play/Pause is exposed, a card/action is revalidated before use, and the
  layout preserves the source-icon anchor so thumbnail and text do not
  overlap.
- Stremio releases its control-only claim when its active fragment ends; the
  Dashboard then returns to the normal RECENT state with no stale Stremio
  title, transport, artwork, or CURRENT_WEB card.

## Runtime acceptance completed

- Phone Control, Dashboard and Settings chrome were rechecked, including
  addon navigation and the absence of the legacy duplicated player surface on
  phone roots.
- One Play/Pause, Next, or Previous input produced one action; no double
  toggle was observed.
- A stale phone-to-car request did not open after disconnect/reconnect. The
  car host and Phone were reopened manually and remained controllable.
- Readiness was hidden/restored across an Android Auto/DHU reconnect with no
  stale Settings update or crash.
- YouTube automatic/manual fullscreen and its internal fullscreen were tested
  on DHU; the reported flicker was resolved.
- SmartTop was checked at DHU 800x480, and Stremio's active-fragment release
  was checked at DHU 1200x720.

## Deliberately excluded

- TikTok was cancelled and all TikTok production code was removed. It is not
  a deferred P1 gate and must not be restored without a separately approved
  scope.
- GPS, accounts, VTVGo, games, permission automation, a companion APK,
  license/activation changes, voice changes, and a global cross-addon
  reconnect/replay policy are outside P0/P1.
- Device-wide font-scale permutations remain optional visual-regression
  coverage, not a P0/P1 blocker.

## Invariants retained

- One universal package continues to serve phone and Android Auto; no
  companion app, package/signing migration, or production-install mutation is
  part of this delivery.
- No new playback owner, MediaSession service, watchdog, permission framework,
  Android Auto shutdown behavior, or audio-focus policy was introduced.
- Existing MediaSession, engine, shutdown, and addon ownership paths remain
  authoritative; UI code observes or dispatches through their existing seams.

## Final verification

Immediately before the merge, the branch is verified with:

```powershell
.\gradlew.bat :fermata:testMobileDebugUnitTest :fermata:testAutoDebugUnitTest :web:testMobileDebugUnitTest :web:testAutoDebugUnitTest --rerun-tasks --console=plain
git diff --check
```

The exact command exit status and merge commit are recorded in the merge
handoff rather than duplicating volatile build output in this document.

## Documentation retained

This file is the only P0/P1 documentation retained in version control. The
removed planning files were intentionally non-authoritative working material;
they included superseded TikTok scope and are not required to operate or
extend the accepted implementation.
