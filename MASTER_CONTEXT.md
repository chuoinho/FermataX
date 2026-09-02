# FermataX Master Context

> Last updated: 2026-08-20
>
> This document is the primary project context for maintainers and coding agents. Read it
> before changing product behavior, navigation, playback, addon activation, packaging, or
> Android Auto UI. When this file conflicts with an older plan, screenshot, chat message, or
> implementation note, verify the current code and then update this file with the confirmed
> decision.

## 1. Project Identity

- Product name: **FermataX**
- Android display name: **fermataX**
- Repository: <https://github.com/chuoinho/FermataX>
- Donation page: <https://ko-fi.com/fermatax/>
- Upstream origin: <https://github.com/AndreyPavlenko/Fermata>
- License: inherited from upstream Fermata, currently documented as GPL-3.0
- Primary Android Auto package: `me.app.fermataX.auto`
- Base application ID configured by Gradle: `me.app.fermataX`
- Current version code: `301`
- Current version name: `2.0.1`
- Version source: `gradle/libs.versions.toml`

FermataX is a fork of Fermata. It is not intended to be a clone of Fermata Xtream and it is
not an IPTV-only product. Its product direction is a unified, car-friendly media hub for
Android Auto.

## 2. Product Direction

### Primary audience

Drivers and passengers who want one Android Auto interface for frequently used media:

- Live TV and IPTV
- Xtream Live, VOD, and Series
- Internet Radio
- YouTube and web media
- Local folders and playlists
- Favorites, recent items, and continue playback
- Optional media and utility addons

### Platform scope

- Android Auto is the primary platform and design target.
- FermataX ships as **one application / one universal APK** used on both phone Android and
  Android Auto/DHU. Mobile/Auto Gradle source sets may remain for implementation and testing, but
  they are internal only and must never become separate user-facing APKs, releases, or artifacts.
- Phone Android remains supported by the same universal package; SmartTop itself is intentionally
  hidden on phone while the ordinary Dashboard Recent tile remains available.
- Android TV is not a product priority.
- HUR may be useful for occasional reproduction, but final Android Auto UI validation should
  use Google Desktop Head Unit (DHU) unless a task explicitly says otherwise.

### Product priorities

1. Reliable operation and predictable navigation while driving.
2. Simple, glanceable UX with large targets and low interaction cost.
3. Addon isolation and stable media handoff between addons.
4. Correct playback state, back behavior, and restoration after leaving Android Auto.
5. Performance, source caching, and loading speed after UX and reliability are stable.

SmartTop V2 semantic/adaptive rollout and the horizontal-stability Phases 1-5 were completed,
validated, and merged to `main` through PR #18 on 2026-08-21. The accepted source keeps SmartTop
automotive-only, preserves a stable two-line title and 1-3 valid Quick Recent rows, and removes
Previous, Next, and Back/Open Context from SmartTop without changing those controls on full-player
or MediaSession surfaces. Podcast/Audiobook SmartTop providers remain deferred until their
cached-only ownership seams are characterized independently.

### Explicitly excluded or deferred

- No bundled or preconfigured IPTV/Xtream source.
- No broad adblock work for the YouTube addon.
- No animation-heavy redesign requirement.
- No Android TV-specific roadmap.
- Android Auto/HUR text-input keyboard work is currently deprioritized unless explicitly
  reopened.

## 3. Non-Negotiable Engineering Rules

### Behavior preservation

- Do not change working behavior during cleanup or refactoring.
- Do not change public APIs, preference keys, routes, manifest component names, provider
  authorities, package IDs, database schemas, or environment/property names without an
  explicit migration plan.
- Do not change visible text, layout order, CSS/style resources, or interaction rules as part
  of a cleanup-only task.
- If code may be used through Android manifests, reflection, JNI, generated addon registries,
  dynamic feature loading, or external integrations, do not delete it based only on a text
  reference count.
- Preserve user preferences and source data on update.

### Distribution contract

- FermataX has exactly one user-facing application package / universal APK for phone and Android
  Auto/DHU.
- Mobile/Auto are internal source sets and test targets only. Never publish, document, or offer
  separate Mobile and Auto APKs to users.
- Do not split the normal APK build by ABI. `build.sh` and CI must produce one neutral universal
  APK name without `mobile`, `auto`, `arm`, or `arm64` product suffixes.
- The canonical Gradle packaging task currently uses the internal Auto universal path because that
  source set contains both the phone launcher and Android Auto integration. The task name is an
  implementation detail, not a separate Auto product.
- CI may run Mobile and Auto unit/lint tasks independently for verification; that does not change
  the single-package distribution rule.

### Addon isolation

- Every addon must be independently loadable and removable where the architecture permits.
- Removing or disabling one addon must not break unrelated addons.
- Playback state from one addon must not leak into another addon.
- Switching from TV or another video addon to YouTube must transfer visual, audio, metadata,
  media-session, toolbar, and fullscreen ownership together.
- Shared navigation and playback policy may live in core code, but addon-specific browsing,
  source management, parsing, caching, and UI state should remain inside the addon module.
- Dynamic-feature modules may depend on `:fermata`, `:utils`, Android/JDK APIs, and external
  libraries, but must not import another feature module's implementation or declare a sibling
  feature-module Gradle dependency. `ArchitectureBoundaryTest` enforces this in CI.

### Secrets and test data

- Never commit IPTV/Xtream credentials, test accounts, signing passwords, access tokens, or
  user data.
- Do not place test account credentials in this context file, README, source comments, tests,
  screenshots, or release notes.
- `local.properties` and the signing keystore are local-machine assets and must not be exposed
  in repository documentation.

### TLS trust boundary

- HTTPS is strict by default and includes certificate-chain and hostname validation.
- Trust-all compatibility is restricted to the configured origin of IPTV/M3U, XMLTV/EPG, and
  Stremio sources. Same-origin redirects retain that policy; cross-origin redirects become strict.
- ChatGPT, Whisper/OpusMT downloads, generic artwork, and every other HTTPS path remain strict.
- Strict and user-source connections have separate cache identities. Do not introduce a global
  mutable trust switch or broaden the compatibility policy without an explicit product decision.

## 4. Confirmed UX Model

The current UI direction was developed with local mockup artifacts:

- `docs/ui-redesign/fermatax-aa-ui-refresh-mockup.html`
- `docs/ui-redesign/fermatax-aa-ui-refresh-mockup.png`

Those mockups are currently ignored local assets, not shared tracked documentation. The confirmed
rules below are canonical even when the local mockups are unavailable. Search was explicitly
excluded from this UI refresh.

### Dashboard

- Dashboard is always the startup screen.
- Dashboard is the home/root destination, not a marketing or explanatory page.
- The Home item remains available in the navigation rail.
- Dashboard cards use large recognizable icons. Titles are concise and subtitles are
  secondary.
- Card layout must adapt to different Android Auto display widths and aspect ratios.
- Dashboard cards can be reordered.
- Addon order in the navigation rail follows the configured Dashboard order.
- Addons can be disabled from Settings.
- No source is added automatically.

### Fresh install versus update

- On a true fresh install, supported bundled addons should be available and enabled by
  default.
- On an update, existing activation markers and backed-up preferences must be respected.
- An update must not behave like a fresh install and silently reset addon choices.
- Enabled addon state and actual Dashboard/navigation visibility must remain synchronized.

### SmartTopCard

SmartTopCard is the wide primary card at the top of the automotive Dashboard. The final SmartTop V2
semantic and adaptive presentation contracts are canonical:

- Visibility is host-gated only: phone never shows SmartTop; every automotive presentation host
  shows SmartTop regardless of width. When SmartTop is hidden on phone, the normal Dashboard Recent
  tile remains available.
- `CURRENT` semantic actions are Play/Pause and Favorite when supported. Previous, Next and
  Back/Open Context belong to full players and MediaSession surfaces, not SmartTop.
- `RESUME` and `RECENT`: Play and Favorite when supported; measured presentation may remove
  Favorite when required by the available budget.
- `EMPTY`: one labeled terminal CTA. The current safe label is **Settings** because the existing
  `OPEN_ADDONS` route opens Settings root; do not display a misleading **Add-ons** label until a
  Settings > Add-ons deep link exists.
- `RECOVERY`: one labeled Retry action.
- `RECOMMENDED`: compatibility only. Keep the enum, provider candidate kind, and compatibility
  action/API surface, but SmartTop selection and coordinator display flow must never select or
  publish a RECOMMENDED state.
- `SmartTopActionPolicy` owns semantic actions. `SmartTopAdaptivePolicy` is the pure measured
  composition/geometry authority. `SmartTopLayoutController` applies one resolved
  `SmartTopLayoutSpec`; it must not invent or pre-filter semantic state.
- Quick Recent semantic data retains up to three items. Whenever valid data exists, presentation
  shows 1-3 rows at every automotive width; width pressure must not drop the panel.
- Horizontal pressure compresses action gaps/terminal icon and then removes Favorite. Play or
  Play/Pause and valid Quick Recent are invariant. The action rail never wraps to a second row.
- Automotive action cells adapt to measured width/fontScale instead of being hard-fixed at 76dp.
  Current policy ranges from 56dp to 76dp with a minimum 64dp delegated touch target; primary and
  secondary glyphs shrink proportionally up to the 44dp/36dp caps. Touch presentation retains the
  48dp cell / 22dp glyph baseline.
- Automotive action spacing is deliberately compact to preserve metadata/title width: preferred
  inter-action gaps are 2dp on narrow AA viewports, 4dp from 820dp, and at most 6dp from 1000dp.
  Gap fitting may reduce them further when space is constrained.
- Title owns a stable two-line slot (`minLines=2`, `maxLines=2`) with end ellipsis. A long intrinsic
  title is budgeted as two lines and cannot steal the transport rail.
- SmartTop font scaling is bucketed/capped. Title, eyebrow/subtitle, progress time and Quick Recent
  text use bounded visual growth rather than multiplying every surface without limit at large
  system fontScale.
- Card height is a deterministic layout-class + fontScale-bucket value. Playback state and measured
  viewport height must not change the card height, preventing Play/Pause relayout flicker.
- For the same canonical CURRENT item, Play/Pause/timeline refresh reuses the current generation and
  publishes through the timeline payload path. It must not clear Quick Recent or full-rebind the
  card merely because playback state changed.
- The action rail is always horizontally laid out and vertically centered against the stable
  eyebrow-to-progress metadata block, not conditionally centered against the whole card.
- Add-on tiles retain their existing horizontal row geometry; Android Auto emphasizes add-on title
  text rather than shrinking the already-large add-on glyph.
- While media is playing, SmartTop acts as the mini player/Now Playing card and exposes the current
  item, source/category context, timeline when meaningful, and Play/Pause plus Favorite when
  supported. Transport navigation remains available only on the appropriate full player surfaces.
- Recent items can be deleted only after opening the Recent view, not directly from the collapsed
  SmartTopCard.
- Tapping current TV/video opens the playing content in the appropriate fullscreen or player
  destination.
- Tapping current Radio opens the corresponding station/source list with the radio playerbar
  visible; it must not jump to an older TV or YouTube item.
- SmartTop actions must avoid double-click and drag conflicts.

### Unified Recent and Favorites

- Unified Recent combines supported media history without replacing source-specific history.
- TV recent history is intentionally compact and should not grow into a noisy activity log.
- Unified Favorites only includes item types that implement favorite support correctly.
- Do not display a favorite action for unsupported or external items.

## 5. Navigation Rail Rules

- Navigation is a vertical rail, not a bottom navigation bar.
- The old bottom-position option must remain removed.
- A Settings option allows positioning the rail on the left or right for left-hand-drive and
  right-hand-drive ergonomics.
- The rail remains visible in ordinary Dashboard, list, settings, and split-view states.
- The rail hides in true fullscreen video.
- Tapping a fullscreen video surface shows player controls and navigation together, then they
  auto-hide together after the configured delay.
- YouTube must use the same visible/hidden timing model as other video addons.
- When returning from outside Android Auto, a non-fullscreen YouTube player state should show
  the navigation rail again.
- Inactive icons are visually subdued; only the active destination is prominent.
- Icons must remain large and easy to hit. Do not shrink all icons to force every addon into a
  single viewport.
- The rail scrolls vertically when required.
- Scroll affordance must visually belong to the rail, not appear attached to the app icon.
- A press that becomes a scroll gesture must not activate the initially touched addon.
- Do not show Move Up/Move Down actions in the rail overflow; they created accidental and
  confusing interactions while scrolling.

## 6. Back Navigation Contract

Back behavior is a product rule, not an implementation detail. Centralize decisions where
possible and avoid fragment-specific patches that conflict with these rules.

### TV and ordinary video

- From fullscreen TV/video, Back returns to split view containing the relevant channel/item
  list.
- From split view, another Back returns to the parent source/category/menu.
- Continue walking parent menus until no parent remains, then return to Dashboard.
- Opening another channel in split view must switch the actual playing video.
- Returning to TV from Dashboard must not restore split view without its navigation rail.

### YouTube and web-based addons

- First Back from fullscreen exits fullscreen.
- Next Back navigates web history when valid.
- When no useful history remains, return to the addon root or Dashboard according to the
  common navigation stack.
- YouTube fullscreen must not retain an empty top bar.
- Web Back resolution is one ordered transaction: `EXIT_FULLSCREEN`, then `WEB_HISTORY`, then
  the common parent/Dashboard fallback. Playerbar and activity Back must converge on it.
- `YoutubeMediaEngine` is the only owner of automatic YouTube fullscreen entry. Fragment,
  WebView-resume, metadata, and repeated JavaScript playback callbacks must not initiate a
  second automatic entry for the same video.
- Explicit user Back records `USER_EXIT` for the current YouTube video and invalidates every
  older fullscreen generation, including delayed 500 ms and 1,500 ms callbacks. Automatic
  fullscreen may be armed again only when a fresh WebView gesture is followed by a new stable
  YouTube video identity. A changed URL or playback callback alone is not user intent.
- Android Auto video mode and WebChromeClient custom fullscreen are asynchronous states. A
  YouTube playerbar/activity Back must first cancel the current video's automatic-entry
  generation, then leave whichever of those states is active. It must not depend only on
  `WebChromeClient.isFullScreen()`, because that value can still be false while auto-entry is
  pending.
- The playerbar Back path and the ordinary YouTube fragment Back path must converge on
  `YoutubeWebView.exitPlaybackFullScreenForBack()`. Do not add another callback that enters
  fullscreen outside `YoutubeMediaEngine` or another exit path that bypasses the fullscreen gate.
- Every app-initiated YouTube browser fullscreen request carries the gate generation into
  `YoutubeChromeClient`. A late `onShowCustomView()` callback must be rejected when Back has
  invalidated that generation. Generic home/blob transitions, a different video URL reached by
  WebView history, and rotating media URLs must never clear explicit-exit suppression by themselves.
- `USER_EXIT` also rejects untagged `onShowCustomView()` callbacks for the current video. This is
  required because the YouTube page can emit a second browser-owned callback after the tagged app
  request has already been cancelled. The same video may enter fullscreen again only through a
  fresh, one-shot WebView user-gesture permit; the permit expires after one second and cannot be
  reused by a late callback.
- `YoutubeChromeClientTest` is the wiring gate: every custom-view callback, including
  `NO_REQUEST`, must invoke fullscreen admission. Do not add a token-presence condition around
  this check; doing so bypasses `USER_EXIT` and recreates the playerbar Back regression.
- Stopping YouTube for WebView history invalidates the current fullscreen generation but does not
  reset its consumed video identity. Stale `playing` events from the page being left cannot re-arm
  fullscreen. A different stable YouTube video ID starts a new automatic-entry transaction only
  when paired with the fresh, one-shot WebView gesture permit. Playerbar/activity Back never grants
  that permit.

### Radio and audio

- Back must not stop audio.
- Back returns to the corresponding station/source/category list so the user can choose
  another station.
- The audio playerbar remains visible on the matching audio list route.

### Dashboard and roots

- Dashboard is the root destination.
- On Dashboard or another true root with no parent navigation, Back represents app exit.

### Back control placement

- When a playerbar is present, its Back button follows the media rules above.
- On list/menu screens without a playerbar, Back belongs in the top bar.
- Do not restore the old floating/overlay Back button on the video surface.

Relevant policy classes:

- `fermata/src/main/java/me/aap/fermata/ui/policy/BackNavigationPolicy.java`
- `fermata/src/main/java/me/aap/fermata/ui/policy/PlaybackUiPolicy.java`
- `fermata/src/main/java/me/aap/fermata/ui/policy/ItemRoutePolicy.java`
- `modules/web/src/main/java/me/aap/fermata/addon/web/WebBackNavigationPolicy.java`
- `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeFullscreenGate.java`

## 7. Playerbar and Playback Ownership

- Dashboard uses SmartTopCard instead of a duplicate bottom playerbar on automotive hosts; phone
  Dashboard intentionally omits SmartTop and retains its normal Dashboard composition.
- Radio lists show an audio playerbar while their matching station/source is active.
- Video playerbar behavior is tied to the active video/player route.
- Playerbar Back follows the navigation contract, not a generic fragment pop.
- Favorite is conditional on item support.
- The playback timer view is controlled separately from the main control panel.
- Top bar, playerbar, navigation rail, media session, metadata, and engine ownership must move
  together when switching addons.
- Do not solve YouTube handoff by bypassing the common media engine or by leaving the old
  engine active underneath the WebView.

Key implementation areas:

- `fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelView.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/PlaybackTimerController.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/PlayerFavoriteButtonController.java`
- `fermata/src/main/java/me/aap/fermata/media/service/MediaSessionCallback.java`
- `fermata/src/main/java/me/aap/fermata/media/service/FermataServiceUiBinder.java`
- `fermata/src/main/java/me/aap/fermata/ui/view/VideoView.java`

## 8. Addon Status and Expectations

### TV/IPTV/Xtream

Module: `modules/tv`

Implemented scope includes:

- M3U sources
- Xtream account sources
- Live TV
- VOD
- Series
- XMLTV and Xtream EPG support
- Now/Next information where source data permits
- Catch-up navigation
- Watch from beginning for compatible streams
- Source health checks and normalized connection/authentication errors
- Source reload through the existing long-press source menu
- Automatic source refresh when entering TV where supported

Requirements:

- Existing M3U source IDs and stored preferences must remain compatible.
- Xtream source handling must not break M3U/XMLTV behavior.
- Provider HTML responses masquerading as authentication JSON errors must fail clearly.
- Parser changes must remain streaming/bounded to avoid large-panel memory spikes.
- Account health information should distinguish DNS, authentication, expiry, no-slot, and dead
  stream conditions.
- TV source refresh is owned by `TvSourceRefreshCoordinator`, not `TvFragment`. Auto, manual and
  post-edit refreshes share source-ID deduplication; edit replaces work for the old account;
  cooldown begins only after success; repeated automatic network failures use capped exponential
  backoff while manual/edit refresh remains immediate; root replacement/uninstall cancels only
  TV-owned work.

### Internet Radio

Module: `modules/radio`

Implemented browsing includes:

- Popular
- Top voted
- Countries
- Tags

Requirements:

- Entering Radio refreshes data according to current source/cache policy.
- Playing a station shows the playerbar on the matching Radio list.
- Dashboard SmartTop Radio opens the correct list with playerbar.
- Radio Back never stops audio unless the user explicitly stops playback.
- Radio refresh is owned by `RadioRefreshCoordinator`. Cache clearing occurs only when a real
  operation starts, joined observers do not clear it again, and each browsable Radio item has an
  independent success-based cooldown plus capped automatic network-failure backoff. Manual refresh
  bypasses both cooldown and backoff.

### YouTube and Web

Module: `modules/web`

Current direction:

- Reliability and loading performance take priority over UI experiments or adblock.
- YouTube fullscreen visibility must follow the common video-control lifecycle.
- Avoid duplicate YouTube-only bar visibility systems when common video policy can be used.
- Do not remove all WebView-specific handling blindly; retain only the integration code needed
  to translate WebView/fullscreen events into common player state.
- Switching from another playing addon must replace old audio, metadata, toolbar title, and
  media-session state.
- Temporary network timeout recovery must not create infinite reload loops.

### Other bundled modules

Current Gradle settings discover every directory under `modules/` as a dynamic feature. The
current inventory is: `audiobook`, `cast`, `chat`, `exoplayer`, `gdrive`, `mlkit`, `opusmt`,
`podcast`, `radio`, `sftp`, `smb`, `stremio`, `tv`, `vlc`, `web`, and `whisper`.

`ArchitectureBoundaryTest.addonModulesDoNotImportSiblingImplementations` owns the corresponding
package map and fails if the map and physical module directories diverge, a module declares source
outside its owned package, or one feature imports/depends on another feature.

The universal APK must package all required modules for internal testing. Internal test builds
must not depend on Play on-demand delivery for core TV, Radio, Web, ExoPlayer, or VLC behavior.

## 9. Architecture Map

### Application and core UI

- Application: `fermata/src/main/java/me/aap/fermata/FermataApplication.java`
- Main activity: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivity.java`
- Main delegate: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java`
- Main preferences: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java`
- Dashboard: `fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java`
- Navigation mediator: `fermata/src/main/java/me/aap/fermata/ui/fragment/NavBarMediator.java`
- Navigation view: `fermata/src/main/java/me/aap/fermata/ui/view/FermataNavBarView.java`
- Toolbar: `fermata/src/main/java/me/aap/fermata/ui/view/FermataToolBarView.java`

### Dashboard responsibilities

Recent refactoring separates Dashboard responsibilities into:

- `DashboardCard`: immutable/view-facing Dashboard card model
- `DashboardModelBuilder`: constructs Dashboard and SmartTop models
- `DashboardPlayableNavigator`: routes SmartTop and playable item actions
- `DashboardItems`: configured addon/card order and persistence
- `DashboardPrefsBuilder`: Dashboard settings tree

Avoid moving addon-specific loading logic back into `DashboardFragment`.

### Settings responsibilities

`SettingsFragment` is now an orchestrator. Settings sections are separated into:

- `InterfacePrefsBuilder`
- `DashboardPrefsBuilder`
- `KeyBindingPrefsBuilder`
- `PlaybackPrefsBuilder`
- `VoicePrefsBuilder`
- `MediaEnginePrefsBuilder`
- `AddonPrefsBuilder`
- `SettingsBackupManager`

Keep preference keys, ordering, visibility conditions, value maps, and default-removal semantics
unchanged when editing these builders.

### Playback

- Media service: `fermata/src/main/java/me/aap/fermata/media/service/FermataMediaService.java`
- Session orchestration and engine-listener integration: `MediaSessionCallback`
- Engine-selection transaction and stale/reentrant rejection: `PlaybackEngineLease` and
  `PlaybackEngineLeaseController`
- Active/pending engine ownership and exact-token rollback: `PlaybackOwnership`
- Pending transition state: `PlaybackTransition`
- Pure prepared-item queue/position/publication decisions: `PlaybackPreparedItemDecisions`
- Sleep-timer deadline, replacement and exactly-once expiry: `PlaybackStopTimer`
- Duration-based cue-track auto-advance and stale async rejection: `PlaybackAdvanceWatchdog`
- UI binder: `FermataServiceUiBinder`
- Engine manager: `fermata/src/main/java/me/aap/fermata/media/engine/MediaEngineManager.java`
- ExoPlayer implementation: `modules/exoplayer`
- VLC implementation: `modules/vlc`

### TV provider internals

Xtream ownership is intentionally split as follows:

- `XtreamApi`: backward-compatible public facade only
- `XtreamHttpClient`: HTTP requests, compression, timeouts, user agent and stream probes
- `XtreamErrorMapper`: provider/network error classification and credential redaction
- `XtreamRepository`: streaming parser ownership, `FutureRef` caches and data loaders
- `XtreamHealthChecker`: authentication, account status, category counts and first-stream probe
- `XtreamJsonStreamParser`: OOM-safe streaming JSON parsing and provider-shape compatibility

XMLTV ownership is intentionally split as follows:

- `XmlTv`: public facade and SQLite/loader lifecycle
- `XmlTvLoader`: download/collect/parse orchestration only
- `XmlTvLoadPolicy`: pure startup/download/retry/max-age decisions
- `XmlTvUpdateScheduler`: one generation-scoped cancellable delayed update
- `XmlTvChannelMatcher`: recursive playlist traversal and normalized ID/name maps
- `XmlTvParser`: SAX parsing, transaction and statement writes
- `XmlTvSchema`: schema names, rebuild and programme-index detection
- `XmlTvDatabase`: track lookup/update and sorted EPG queries

Do not move HTTP, parsing, cache, scheduling or credential handling back into the public facades.
Xtream credentials remain encrypted and must never appear in logs, fixtures or source archives.

### Addon registration

- Addon metadata is assembled by Gradle from root and module `addons` definitions.
- Generated addon information is exposed through `BuildConfig.ADDONS`.
- Runtime registry: `fermata/src/main/java/me/aap/fermata/addon/AddonRegistry.java`
- Runtime manager: `fermata/src/main/java/me/aap/fermata/addon/AddonManager.java`
- `AddonPreferenceMigrator` owns backward-compatible fresh/update enable markers.
- `AddonDependencyResolver` owns dependency validation, cycle detection and install order.
- `AddonModuleController` owns the single global physical module-operation queue, shared-module
  deduplication, retries, restore and deferred uninstall. Each physical install/uninstall is
  bounded by a three-minute timeout; a timeout is surfaced as a failure and advances the FIFO
  queue so one stuck Play Core operation cannot block every addon indefinitely. The timeout is
  intentionally adjustable for delivery conditions.
- `AddonLoader` owns construction, install cleanup and transactional runtime registration.
- `AddonRuntimeState` owns registered, active, failed and installing state. Public lookups expose
  active addons only; declared `AddonInfo.addonId` is the authoritative runtime index.
- `AddonLifecycleCoordinator` serializes lifecycle replay and teardown. Addon readiness completes
  only after replay and the installed broadcast commit.
- Activation commit and failure marking are generation-token guarded under the Manager lock, so a
  canceled load cannot register into or poison a later re-enable generation.

Do not assume that a class with no direct Java reference is dead if it participates in this
generated/dynamic loading path.

## 10. Refactoring Status

The original stabilization/refactor phases and the subsequent reliability work are committed.
Behavior preservation remains the default for further decomposition.

Completed stabilization phases:

- Phase 0 captured the source/build/runtime baseline and rollback archive.
- Phase 1 added characterization coverage for navigation, playback presentation, Dashboard,
  addon state, TV sources, refresh policy and Web restoration/retry behavior.
- Phase 2 fixed the confirmed independent reliability defects and passed its automated,
  release-build, update-install and DHU gates. XMLTV now owns and cancels delayed updates;
  Settings owns exact addon preference listeners per view lifecycle; stream retries are bounded;
  Radio HTTP connections are closed on every exit; Web retries reject stale navigation; bitmap
  failures expire and concurrent loads are deduplicated; addon uninstall preserves shared modules;
  and delayed `Async.schedule` work is cancellable before and after execution starts.
- Phase 3 introduced `PlaybackSnapshot` as the media service's immutable revisioned view of
  current item, playback state and session metadata. The redundant
  `MediaSessionCallback.Listener.onPlaybackStateChanged` broadcast was retired after consumers
  migrated to snapshot state with normal-transition and initial-null-item parity coverage.
  `FermataServiceUiBinder` adapts item changes from the snapshot without a duplicate `currentItem`
  cache. The old `currentState` field was removed after parity tests proved initial-null and
  same/different-item behavior.
- Phase 4 introduced `PlaybackPresentationReducer` and `PlaybackPresentationCoordinator` as the
  Android Auto authority for video fullscreen/split presentation, top/navigation/playerbar
  visibility and timeout generations. TV and YouTube now share tap/show/timeout behavior; split
  mode remains persistent; stale delayed callbacks are rejected; and Radio visibility updates are
  synchronized into the coordinator so playerbar Back cannot apply stale video-exit state.
- Phase 5 decomposed addon loading into migration, dependency, runtime-state, lifecycle, loader and
  module-controller responsibilities. It adds dependency cycle/missing checks, late lifecycle
  replay before readiness, global physical-operation serialization, shared-module deduplication,
  deferred uninstall/restore, cancellation-isolated observers, active-only visibility and exact
  addon/module identity. Load commit and failure state are generation-safe across rapid
  disable/re-enable. Zero-ID implementation addons such as ML Kit and Opus-MT no longer collide
  through their inherited Translate UI ID. Independent adversarial review found no remaining P1/P2
  issue after the final fixes.
- Phase 6 replaced Dashboard, navigation and external-item routing heuristics with explicit addon
  capabilities. Generated addon metadata declares independent Dashboard/navigation surfaces and
  role capabilities; the legacy `AddonInfo` constructor preserves previous fragment behavior.
  Dashboard migration now preserves every persisted user position, appends only missing entries in
  canonical order, and resets its order/version marker atomically. External YouTube items carry an
  explicit `YOUTUBE` route capability instead of relying on root ID or class-name matching.
- The Phase 6 playback regression gate also made Web Back an ordered transaction and added a
  generation-scoped YouTube fullscreen gate. Explicit Back invalidates delayed fullscreen work for
  the current video while a newly selected video can arm a fresh fullscreen transaction only after
  a one-shot WebView gesture. URL/history changes alone remain suppressed.
- Phase 7 introduced generic keyed `RefreshCoordinator` mechanics and addon-owned TV/Radio
  coordinators. Concurrent auto/manual requests join without coupling observer cancellation to
  shared work; post-edit TV refresh replaces stale account work; successful keys alone enter
  cooldown; repeated automatic network failures use a one-minute exponential backoff capped at
  thirty minutes, while manual/edit requests and provider failures remain immediately retryable by
  default; root reset and addon uninstall cancel only owned tasks; failures are returned as typed
  results with normalized network/provider categories.
- Phase 8 split Xtream network, errors, cache/repository and health-check responsibilities while
  retaining the public `XtreamApi` contract, streaming/OOM-safe parser and EPG fallback order.
  XMLTV is split into facade, loader, pure load policy, cancellable scheduler, channel matcher,
  parser, schema and database layers. Existing-index startup remains non-blocking; initial startup
  waits for parse; unchanged files reuse the database; replacements wait 30 seconds; failures with
  an existing index retry after five minutes; initial failures close resources; and max-age timing
  remains unchanged. The YouTube fullscreen gate also now rejects both tagged and untagged late
  custom-view callbacks after explicit Back, with a wiring-level regression test.

Subsequent committed hardening:

- `MediaSessionCallback` engine selection now uses a lease transaction: capture pre-creation
  ownership, select a candidate, compare-before-install, bind the exact pending token, guard every
  engine-sensitive side effect with accepted-lease liveness, and roll failures back through the
  exact expected token. Queue/position/outgoing-state decisions are standalone pure predicates
  with normal-path and reentrant regression coverage. The callback still owns media-session and
  listener orchestration; the lease extraction is not permission for a broad rewrite.
- `AddonModuleController` applies a per-physical-operation three-minute timeout. Timeout failure
  releases the queue, preserves exactly-once advancement, ignores late completion, and allows the
  next install/uninstall to proceed.
- `RefreshCoordinator` applies one-minute exponential network-failure backoff capped at thirty
  minutes. Success resets backoff; manual/edit triggers remain immediate; stale replaced callbacks
  cannot overwrite current backoff state.
- Android Lint's 18 initial utils errors and the subsequently exposed 158 application errors were
  resolved without a baseline or blanket suppression. Both Mobile and Auto lint tasks are explicit
  CI gates.
- HTTPS trust is strict by default with hostname verification. Trust-all compatibility is scoped
  per connection to configured IPTV/M3U, XMLTV/EPG, and Stremio origins, with strict cross-origin
  redirect downgrade and policy-separated connection caching.
- GitHub Actions now gates Mobile and Auto unit suites, architecture boundaries, both lint flavors,
  and diff whitespace. Those Mobile/Auto tasks are internal verification only; CI then packages and
  uploads exactly one neutral `FermataX-debug-universal.apk` artifact.

Phase 4 source rollback archive:

```text
<local-backup-directory>/FermataX-phase4-source-20260711-183837.zip
Size: 3668829 bytes
SHA-256: FC9C287D79F218F8AA2563A76ECA6A06230C82DCD4BEE7FC691976222738B563
```

Phase 5 source rollback archive:

```text
<local-backup-directory>/FermataX-phase5-source-20260712-062923.zip
Files: 949
Size: 3622027 bytes
SHA-256: 4B134C0728A3373918FD5E2B4E4A5560A518E3723A48B69AB1887A8DE152C48E
```

The Phase 5 archive was reopened and verified entry-for-entry. It excludes build output, local
properties, signing material, APK/AAB/ZIP artifacts and diagnostic DHU screenshots.

Phase 6 source rollback archive:

```text
<local-backup-directory>/FermataX-phase6-source-20260712-090203.zip
Files: 958
Size: 3630236 bytes
SHA-256: 023F3E317B914AA26060F30E3D827A0B23A8087079A1BEE75B4AEF0DBD925523
```

The Phase 6 archive was reopened and verified with all required source/context files present and no
build output, local properties, signing material, APK/AAB/ZIP artifacts or DHU diagnostics.

Phase 7 source rollback archive:

```text
<local-backup-directory>/FermataX-phase7-source-20260712-133558.zip
Files: 962
Size: 3637578 bytes
SHA-256: 4042A851A2F76D61AF81E7F3A050288CBE105B732D7CD9E647FA1CAB11FFF7B0
```

The Phase 7 archive was reopened and verified with the core, TV and Radio coordinator sources
present. It excludes build output, local properties, signing material, APK/AAB/ZIP artifacts and
diagnostic DHU screenshots.

Phase 8 source rollback archive:

```text
<local-backup-directory>/FermataX-phase8-source-20260712-192156.zip
Files: 976
Size: 3722154 bytes
SHA-256: 1D4FB728FDC1FA47DBBBE09E00ED8B3F9AE0602FFC17D980C66E455232CA8AA5
```

The Phase 8 archive was reopened and verified with `MASTER_CONTEXT.md`, the XMLTV load policy and
the YouTube fullscreen gate present. It contains no build output, local properties, signing
material, APK/AAB artifacts or DHU diagnostics.

Final refactor source rollback archive:

```text
<local-backup-directory>/FermataX-final-refactor-source-20260713-015043.zip
Files: 978
Size: 3659337 bytes
SHA-256: CEC6BF94178DF6C62AA8BDE24963D18DE75C6908ACF0A6973B9F24F54A7EED67
```

The final archive was reopened and verified entry-for-entry. It includes the completion audit,
master context, YouTube fullscreen state machine and final source, with zero excluded-path
violations and no build output, diagnostics, local properties, signing material or APK/AAB/ZIP
artifacts.

Completed cleanup:

- Extracted Dashboard models and playable navigation.
- Centralized audio playerbar policy.
- Extracted favorite button handling.
- Extracted playback timer view handling.
- Split Settings into focused builders/managers.
- Removed `StreamItemWrapper`, an empty class with no references.
- Audited the version catalog; no dependency alias was proven unused.
- Extracted and tested playback ownership, transition, engine-lease, prepared-item decision, and
  progress-coordination responsibilities from `MediaSessionCallback`.

Current large/high-risk classes:

- `MediaSessionCallback`: still-large media-session/listener orchestration around extracted
  ownership, lease, transition, prepared-item decision, and progress collaborators
- `MainActivityDelegate`: navigation, activity state, fragments, Android Auto coordination
- `ControlPanelView`: gestures, visibility, player menu, speed/timer controls
- `MediaLibFragment`: async loading and list lifecycle
- `VideoView`: fullscreen/video surface and control interaction

Video rendering ownership (current contract):

- `BodyLayout` owns only FRAME/VIDEO/BOTH layout and commits its guideline before asking the
  active output to replan.
- `VideoOutputCoordinator` is the single owner of priority selection and decoder attachment.
  Adding/removing a non-winning phone/AA surface must not detach/re-attach a decoder; only a real
  selected-target or engine change may bind it. Its output/source generations reject stale first
  frame reveals.
- Value-based render-plan dedupe is safe only while the receiver identity is unchanged. A newly
  selected output or engine must receive the current plan even when its values equal the last plan
  delivered to the previous receiver.
- Engine playback reset and video-output ownership are separate lifecycles. `prepare()`, `stop()`,
  pause, completion, and recoverable engine fallback must not clear the bound `VideoView`.
  An engine may clear it only through `setVideoView(null)` when requested by
  `VideoOutputCoordinator`, or while the engine is permanently closed. Coordinator binding state
  and the engine's bound view must remain consistent.
- `VideoView` is a passive surface host: it applies the one pure `VideoRenderPlan` to video and
  subtitle surfaces only when it is the selected target. It does not delegate geometry ownership
  back to an engine. A shutter overlays decoder output during handoff instead of changing decoder
  Surface alpha or locking its canvas.
- `VideoRenderPlanner` is host-independent and preserves all five scale preferences from actual
  viewport + format data. `VideoFormatSnapshot` distinguishes coded and visible frame dimensions
  and pixel aspect ratio, so live/anamorphic VLC sources are not accidentally treated as 16:9.
  Before decoder metadata arrives it deliberately retains the measured viewport rather than
  guessing a 16:9 Surface; only a final plan may resize decoder output.
- `VideoRenderPlanner` is the sole owner of video geometry. A plan's `contentWidth/contentHeight`
  is the visible content box for subtitles and overlays; `surfaceWidth/surfaceHeight` is for the
  decoder Surface only and may retain coded-frame padding.
- Engine implementations own only decoder-native work after a plan is applied. They are pure
  plan-to-native mappings: they must not read a View, configuration, or preference, recalculate
  geometry, or ignore `plan.scale()`. VLC resets native scale/aspect deterministically and passes
  only `plan.surfaceWidth/surfaceHeight` to its vout window. Web/YouTube/Cast remain no-local-
  decoder output paths.
- The VLC fullscreen-to-split crop investigation ruled out source video classification
  (`pi.isVideo()`/`VideoSource`), buffer-versus-plan geometry mismatch, surface callbacks, stale
  coordinator cleanup/generation, and mismatched VLC instances.
  The measured cause was `prepare()` calling `MediaEngineBase.stopped(false)` after coordinator
  attachment and directly clearing the engine view. Do not reintroduce compensating rebinds or
  extra delayed retries for this lifecycle error.

Do not perform a broad rewrite of these classes without characterization tests and a staged
DHU regression plan. Prefer extracting one responsibility at a time.

## 11. Build Configuration

### Toolchain

- Java: JDK 17
- Gradle wrapper: `9.5.1`
- Android Gradle Plugin: `9.2.1`
- Compile SDK: `36`
- Target SDK: `36`
- Minimum SDK: `28`
- NDK: `29.0.14206865`

### Build commands

Preferred local universal APK build from Git Bash/WSL/macOS/Linux:

```sh
./build.sh
```

`build.sh` produces one neutral universal APK in `dist/` and deliberately does not apply an ABI
filter. `./build.sh -b` builds the AAB instead. There is no Mobile-versus-Auto product choice.

Windows PowerShell direct Gradle fallback:

```powershell
.\gradlew.bat :fermata:packageAutoReleaseUniversalApk --no-daemon --no-parallel --stacktrace
```

The `Auto` token in that Gradle task is the **internal canonical universal packaging source set**;
it is not a separate user-facing Auto build. Do not pair it with a Mobile APK build.

Useful internal verification commands:

```powershell
.\gradlew.bat testMobileDebugUnitTest --no-daemon --no-parallel --stacktrace
.\gradlew.bat testAutoDebugUnitTest --no-daemon --no-parallel --stacktrace
.\gradlew.bat :fermata:testMobileDebugUnitTest `
  --tests me.aap.fermata.architecture.ArchitectureBoundaryTest.hotspotClassesCannotGrowPastTheRecordedBaseline `
  --tests me.aap.fermata.architecture.ArchitectureBoundaryTest.addonModulesDoNotImportSiblingImplementations `
  --no-daemon --no-parallel --stacktrace
.\gradlew.bat :fermata:lintMobileDebug :fermata:lintAutoDebug `
  --no-daemon --no-parallel --stacktrace
git diff --check
```

`.github/workflows/ci.yml` runs these internal verification families on every pull request to
`main` and every push to `main`, then packages and uploads exactly one neutral
`FermataX-debug-universal.apk`. Release signing/publishing is not part of CI.

### Outputs

- Preferred local APK: `dist/FermataX-<version>.apk`
- Preferred local AAB: `dist/FermataX-<version>.aab`
- Raw internal release universal APK: `fermata/build/outputs/apk_from_bundle/autoRelease/`
- Raw AAB: `fermata/build/outputs/bundle/autoRelease/`
- CI universal APK staging path: `dist/FermataX-debug-universal.apk`

### Signing

- Local signing configuration is read from `local.properties`.
- The current local keystore path is machine-specific.
- Never commit signing passwords or expose them in reports.
- Release artifacts must be signed with the same certificate used by existing internal-test
  installations to allow update installs.

### Package composition

- Base `applicationId`: `me.app.fermataX`
- Internal canonical universal source-set suffix: `.auto`
- Current installed package from that internal path: `me.app.fermataX.auto`
- That one package includes the phone launcher and Android Auto integration and is the FermataX
  package used for both phone and Android Auto/DHU testing.
- Do not restore old `me.aap.fermata.auto` provider authorities or package values.
- Do not create a second user-facing Mobile package or ABI-specific APK product.

## 12. Testing and Validation

### Automated status at this snapshot

- SmartTop V2 completion source HEAD `e56c377be715f40a2fd6bfb5810fdf4a3c8e0038` passed CI
  run #313 before merging through PR #18 as `e8f30e3c310a3d965a44d4fc1e2a504a92feb1a3`.
- The accepted run passed Mobile and Auto unit suites, Web/TV UI-shell guards, the UI-shell
  single-writer guard, architecture boundary guards, Android Lint, whitespace validation, and
  single-universal-APK packaging/upload.
- Focused runtime validation passed on phone and Android Auto/DHU, including 800x480 and 1280x720
  automotive smoke checks. Previous, Next, and Back remained absent from SmartTop without ghost
  space or overlap; Play/Pause, Favorite, Quick Recent, full-player/YouTube transport, fullscreen
  Back, and phone Dashboard behavior remained intact.
- PR #19 and PR #20 were temporary diagnostic drafts and were intentionally closed without merge;
  their workflow-only commits are not part of the accepted source history.
- `DistributionContractTest` guards the single universal APK contract in `build.sh` and CI.
- This is immutable evidence for the named PR #18 source snapshot. Later commits must be evaluated
  from their own CI and runtime evidence rather than inheriting this result.

Historical release APK/AAB, R8, update-install, and DHU results below remain evidence for their
named snapshot only; they do not implicitly certify later commits.

The former `SubtitlesTest.testScheduler` hang was removed by replacing only its real-time test
harness with a deterministic clock/executor. Runtime subtitle behavior was not changed.

### ADB

Typical validation for the current universal debug package:

```powershell
adb devices -l
adb install -r "FermataX-debug-universal.apk"
adb shell dumpsys package me.app.fermataX.auto
adb logcat -c
```

Use `install -r` for update-path validation so existing preferences and addon markers remain.
Use a clean uninstall/install only when explicitly testing fresh-install behavior.

### Desktop Head Unit

Resolve DHU from the local Android SDK instead of documenting a developer-specific home path:

```powershell
$dhu = Join-Path $env:ANDROID_SDK_ROOT 'extras\google\auto\desktop-head-unit.exe'
if (-not (Test-Path $dhu)) {
  $dhu = Join-Path $env:LOCALAPPDATA 'Android\Sdk\extras\google\auto\desktop-head-unit.exe'
}
```

Required connection setup:

```powershell
adb forward tcp:5277 tcp:5277
& $dhu
```

The Android Auto Head Unit Server must already be enabled on the connected phone.

### Required manual regression checklist

1. Phone portrait/landscape shows no SmartTop and retains the ordinary Dashboard Recent tile.
2. Every automotive Dashboard shows SmartTop regardless of width; narrow hosts adapt by reducing
   gaps/Favorite rather than hiding the card or valid Quick Recent.
3. DHU 800x480 and 1280x720 at fontScale 1.0/1.3/1.5/2.0 keep SmartTop title, metadata and controls
   balanced with no overlap or vertical drift.
4. SmartTop short, one-line, two-line and very long titles keep a stable two-line title slot and a
   horizontal action rail.
5. Repeated SmartTop Play/Pause does not change card height/position, clear Quick Recent, or flicker
   the Dashboard; Quick Recent renders all 1-3 valid rows at every automotive width.
6. Navigation rail left/right setting persists after restart.
7. Rail scrolling does not activate the touched addon accidentally.
8. Settings sections render in the expected order and open without crash.
9. Enabled addons appear without disable/re-enable workarounds.
10. TV source list loads existing M3U and Xtream sources.
11. Live TV opens fullscreen; Back returns to split view with navigation visible.
12. Selecting another TV channel in split view changes the playing stream.
13. TV to Radio switches both UI and actual media engine/audio.
14. Radio station playback shows the audio playerbar on the station list.
15. Radio Back keeps audio playing and returns to the correct list.
16. Dashboard SmartTop Radio opens the active Radio list with playerbar.
17. Dashboard SmartTop TV/video opens the active video destination.
18. YouTube root does not show an empty top bar.
19. YouTube fullscreen hides bars; tap shows controls; timeout hides them again.
20. YouTube playerbar Back exits fullscreen and remains inline beyond the 1,500 ms delayed-entry
    window, including when Back is pressed during the app-video/custom-view transition; the next
    Back walks WebView history without auto-reentering fullscreen; selecting a different video by
    tapping it may fullscreen again.
21. Switching from another playing addon to YouTube clears old audio/title/metadata.
22. Leaving Android Auto and returning restores bars according to actual fullscreen state.
23. App update preserves sources, addon state, Dashboard order, language, and navigation side.

## 13. Historical Verified Runtime Snapshot (2026-07)

This section records the last named release-artifact/DHU campaign. It is historical evidence,
not the current source verification status; use CI in Section 12 for the current commit.

The final audited universal APK was installed successfully through ADB using `install -r`.

- Package: `me.app.fermataX.auto`
- Version: `294 / 2.0.1`
- Universal APK size: 329406378 bytes
- Universal APK SHA-256:
  `DC96916BA8ED631C1B869F602ABAAE1CCBBE41502089322F888553A6AFECB24D`
- AAB size: 174033096 bytes
- AAB SHA-256:
  `660DC222C672BE9BF9224008AD8DBBBC34D9046892562AA95B4994AFC2B64F47`
- Update install retained `firstInstallTime=2026-07-03 09:07:42`.
- Final automated gate: Core 99, TV 47, Radio 3 and Web 18 tests; 167 total with zero failures,
  errors or skipped tests. Core, TV, Radio and Web compile for Auto and Mobile; release R8,
  lint-vital, signed AAB and universal APK packaging all passed.

Phase 8 DHU checks completed after update installation:

- Dashboard restored without losing existing preferences.
- The existing TV source still exposed 12 groups; group/channel lists loaded normally.
- VTV1 opened and played fullscreen from the retained source.
- The isolated package log contained no XMLTV, Xtream, SQLite, linkage or runtime failure.

Phase 2 DHU checks completed after installation:

- Dashboard restored without crash.
- Settings was opened and destroyed repeatedly without stale callbacks, duplicate listener
  symptoms or logcat exceptions.
- Existing TV source groups/channels loaded and live video played fullscreen.
- Radio Popular loaded and RTL played with the audio playerbar visible.
- Switching from playing Radio to a YouTube video replaced the media-session metadata with the
  YouTube title and played the YouTube video fullscreen.
- No fatal exception, AndroidRuntime crash, XMLTV/SQLite failure or Web retry crash was present
  in the captured logcat smoke window.

Phase 3 DHU checks completed after a second update installation:

- Dashboard SmartTop restored the active TV item through the snapshot-backed binder.
- Radio Popular reloaded after a transient empty first route load; RTL then played with the
  correct playerbar and media-session metadata.
- Opening a YouTube video from playing Radio replaced state and metadata with the YouTube item
  and rendered fullscreen video.
- The isolated Phase 3 logcat window contained no fatal exception or AndroidRuntime crash.

Phase 4 DHU checks completed after installing the final clean universal APK:

- TV entered fullscreen with app chrome hidden; tapping video showed top/navigation/playerbar
  together and the common timeout hid them together.
- Playerbar Back returned TV fullscreen to the matching split channel list; navigation, top bar
  and playerbar remained visible after the fullscreen timeout window elapsed.
- Radio RTL played with `PLAYING` media-session state and its playerbar on the Popular list.
  Playerbar Back kept RTL playing, retained its metadata and kept the playerbar visible.
- Switching from playing Radio to YouTube replaced visual playback and media-session metadata
  with the selected YouTube item. YouTube tap and timeout followed the same chrome lifecycle as TV.
- Closing and restarting DHU restored Dashboard without a stale `FermataNavBarView` callback,
  fatal exception or AndroidRuntime crash in the isolated logcat window.

Phase 5 DHU checks completed after installing the final universal APK:

- Dashboard restored TV, YouTube, Radio, Web, Folders and Favorites with existing order and prefs.
- Existing TV source still exposed 12 groups; Radio, YouTube and Web roots opened successfully.
- Disabling Web removed only Web from navigation/Dashboard; TV, YouTube and Radio stayed active.
  Re-enabling Web restored it immediately, and DHU restart preserved activation state.
- The final runtime scan contained no fatal exception, addon load/install/uninstall failure,
  lifecycle dispatch failure, registration collision, `ClassNotFoundException` or `LinkageError`.

Phase 6 DHU checks completed after update-installing the final universal APK:

- Dashboard retained the existing TV, YouTube, Radio, Web, Folders and Favorites order after the
  capability migration; no addon required disable/re-enable to appear.
- YouTube opened from the navigation rail through generated capability metadata.
- Selecting a YouTube item transferred playback into the YouTube engine and rendered fullscreen
  video using the explicit external-root route capability.

Phase 7 DHU checks completed after update-installing the final universal APK:

- Dashboard and existing preferences restored after update; no addon required reactivation.
- Existing TV source `1` auto-refreshed and retained 12 groups; manual long-press Refresh completed,
  and the source still exposed its expected group/channel counts.
- Radio root retained Popular, Top voted, Countries and Tags; Popular loaded current stations.
- Long-press Refresh remained available for Radio browsable items, completed without an alert, and
  Popular loaded again afterward.
- The isolated post-update logcat window contained no fatal exception, AndroidRuntime crash,
  source-refresh failure, linkage error or missing class/method error.

YouTube Back regression verification completed on Google DHU after update-installing the release
universal APK:

- Playerbar Back left browser custom fullscreen and app video mode in one transaction. The video
  remained inline with top/navigation chrome after seven seconds, beyond every delayed-entry and
  control-timeout window.
- The next Back walked WebView history to YouTube Home and did not auto-enter the previous video's
  fullscreen state.
- Tapping a new video granted the one-shot WebView gesture permit and entered fullscreen normally,
  proving that explicit selection remains functional while callback/history re-entry is blocked.
- The final APK was rebuilt after replacing API-34-only `String.formatted()` calls in the YouTube
  runtime path. Release-bytecode inspection found zero remaining `String.formatted()` invokes.

This hash is a local verification detail, not a permanent release identifier. Update it only
when a new artifact is intentionally designated as the verified snapshot.

## 14. Git and Workspace State

- Primary branch: `main`
- Primary remote: `origin` pointing to `chuoinho/FermataX`
- No upstream Fermata remote should be reintroduced unless explicitly requested.
- Do not encode a current HEAD or worktree status in this shared document; those values become
  stale immediately. Inspect `git status` and `git log` at the start of each task.
- Preserve unrelated tracked and untracked workspace changes. Do not discard, reset, clean, or
  overwrite them without explicit approval.
- Do not commit build outputs, local secrets, temporary XML dumps, or bulk DHU screenshots
  unless the user explicitly selects them for documentation.
- Repository-wide source metrics and audits must enumerate tracked files (`git grep` or
  `git ls-files`) or walk explicit `src/` roots. Do not recursively scan the raw workspace: ignored
  Gradle/CMake output such as `.cxx/` can otherwise be miscounted as production source.

## 15. Known Risks and Technical Debt

### High priority

- Complete manual regression of TV fullscreen/split-view and YouTube handoff after every
  shared navigation/player change.
- Repeat the focused SmartTop matrix after future Dashboard, presentation, transport, typography,
  or host-detection changes: narrow/wide automotive viewports, representative font scales, long
  titles, repeated Play/Pause, Quick Recent 1-3, and confirmation that Previous/Next/Back remain
  absent only from SmartTop.
- VLC render plans may use Android layout sentinels while provisional. Native engine APIs must
  only receive positive final Surface pixels, or the measured viewport as the provisional
  fallback. A zero-sized libVLC layout callback during `detachViews()` is a lifecycle reset and
  must not erase the last known format for the current source; a newly prepared source owns fresh
  empty format state.
- Do not reopen the IPTV `pi.isVideo()`/`VideoSource` hypothesis for the fullscreen geometry issue.
  Runtime verification reached a final `1920x1088` VLC layout after the sentinel fix, proving the
  source passed `VideoSource` classification. The earlier persistent unknown format was caused by
  the invalid `setWindowSize(-1, -1)` lifecycle.
- Keep addon activation marker behavior distinct for update versus fresh install.
- Prevent multiple competing fullscreen/bar visibility controllers.
- Preserve media-engine ownership and accepted-lease liveness when switching addons.
- Preserve strict TLS and hostname verification outside explicitly configured IPTV/M3U,
  XMLTV/EPG, and Stremio source origins.

### Medium priority

- Continue reducing the remaining `MediaSessionCallback` and `MainActivityDelegate` orchestration
  only through characterized, responsibility-sized extractions. The engine-lease cutover is
  complete and must not be bypassed.
- Add a user-facing warning for trust-all user-source TLS and prefer fingerprint approval/pinning
  as a future replacement.
- Triage the remaining visible lint warnings without introducing a blanket baseline.
- Reduce `ControlPanelView` menu coupling in small, tested extractions.
- Organize or archive diagnostic screenshots outside normal source commits.

### Avoid as opportunistic cleanup

- `SplitCompatApp` appears unreferenced in ordinary Java search but is a public utility and may
  be used externally.
- `Xposed` and native loader classes may be reached through reflection, manifests, or JNI.
- Do not remove dynamic feature modules merely because a specific build/test did not open them.

## 16. Change Protocol for Future Agents

Before coding:

1. Read this file and the relevant module code.
2. Inspect `git status` and preserve unrelated user changes.
3. Identify the product invariant affected by the task.
4. Capture a reproducible baseline on DHU for navigation/player changes.

During coding:

1. Keep changes inside the owning module where possible.
2. Extract responsibilities without changing callbacks, ordering, preference keys, or routes.
3. Build after each coherent module-sized change.
4. Add characterization tests for pure decisions and high-risk policies.

Before reporting completion:

1. Run `git diff --check`.
2. Compile the internal Mobile/Auto verification targets affected by the change; never turn those
   test source sets into separate user-facing APKs.
3. Run relevant unit tests.
4. Build the single universal release when packaging/runtime integration is affected.
5. Sideload that same universal APK with `adb install -r` for update behavior.
6. Test the relevant workflow on Google DHU and, when shared phone code changed, on phone as well.
7. Review the final diff adversarially for lifecycle, short-circuit, route, layout-stability, and
   addon-isolation regressions.
8. Update this tracked file when a confirmed product rule, version, package, architecture boundary,
   toolchain, CI process, or verified snapshot changes. README remains the concise onboarding/build
   entry point; this file is the canonical product and architecture context.
