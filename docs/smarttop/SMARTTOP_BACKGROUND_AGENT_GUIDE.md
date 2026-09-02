# SmartTopCard background agent guide

## Mission

Implement the production SmartTop V2 background described by:

1. `design/smarttop-background-policy.md`
2. `design/smarttop-demo-matrix/README.md`
3. `docs/smarttop/SMARTTOP_BACKGROUND_IMPLEMENTATION_PLAN.md`
4. `docs/smarttop/SMARTTOP_BACKGROUND_TECHNICAL_DESIGN.md`
5. `docs/smarttop/SMARTTOP_BACKGROUND_COPY_PASTE_SNIPPETS.md`

The task is complete only when production code, automated gates, a single universal APK and the real
DHU screenshot matrix all pass. The HTML demo is a visual reference, not the product implementation.

## Paste-ready task prompt

```text
Implement the SmartTop V2 production background using the repository documents under
docs/smarttop/SMARTTOP_BACKGROUND_*.md and the approved visual policy/demo under design/.

Only the SmartTopCard root background may change. Keep dashboard_item_icon as the foreground source
icon and do not modify metadata, timeline, actions, Quick Recent, card height, constraints, focus
order, click routing or provider selection. Use direct item album-art metadata only; never use
PlayableItem.getIconUri() for the background. Artwork loading must be cache/local-only and a remote
cache miss must produce zero network calls. Use a static spectrum only for explicitly proven audio
addons without eligible artwork. Do not add live FFT, Visualizer, RECORD_AUDIO, animation, blur or a
new image/waveform dependency.

Implement in small reviewable phases: policy/model, cache-only API, root background drawable,
coordinator/binder integration, tests, then DHU acceptance. Preserve unrelated and untracked user
changes. Run the exact Mobile/Auto/architecture/lint/package gates documented in the implementation
plan. Report changed files, test results, twelve screenshot paths, network-miss evidence and the
rollback switch.
```

## Read before editing

Inspect these production seams completely:

- `fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopViewState.java`
- `fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopBinder.java`
- `fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopCoordinator.java`
- `fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopLayoutController.java`
- `fermata/src/main/java/me/aap/fermata/media/engine/BitmapCache.java`
- `fermata/src/main/java/me/aap/fermata/media/lib/MediaLib.java`
- `fermata/src/main/java/me/aap/fermata/addon/SmartTopCandidate.java`
- `fermata/src/main/java/me/aap/fermata/addon/SmartTopProvider.java`
- `fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java`
- `fermata/src/main/res/layout/dashboard_smart_top_v2_item.xml`
- `fermata/src/main/res/drawable/dashboard_smart_top_bg.xml`
- the existing tests under `fermata/src/test/java/me/aap/fermata/ui/smarttop/`
- `.github/workflows/ci.yml` and `build.sh`

Before the first edit, run `git status --short`. Existing design artifacts may be untracked user work;
do not delete, move, regenerate or reformat them unless the task explicitly owns that file.

## Hard constraints

### Foreground ownership

- `SmartTopViewState.icon()` means foreground source icon only.
- `dashboard_item_icon` receives only the drawable resource and existing tint.
- No artwork bitmap may be assigned to `dashboard_item_icon` in any callback.
- Do not rename the Android resource ID; only rename the Java `Views.artwork` field to
  `sourceIcon` for clarity.

### Background ownership

- Change only the root background when the feature gate is enabled.
- Prefer a custom root drawable wrapped by a rounded `RippleDrawable`; do not add/wrap/re-constrain
  foreground children.
- Keep `dashboard_smart_top_bg` as the exact gate-off and failure fallback.
- Background failure must never hide the card, icon or foreground content.

### Artwork evidence

- Read only direct `METADATA_KEY_ALBUM_ART_URI` item metadata.
- Do not call `PlayableItem.getIconUri()` for background selection.
- Do not follow parents, infer artwork from screenshots or promote source/addon logos.
- Require cache/local availability, successful static decode, short edge at least 256 px and aspect
  0.8 through 1.25 inclusive.
- Reject 16:9 thumbnails and known animated inputs.

### Network and addon boundary

- A Dashboard background lookup must never initialize or refresh an addon.
- A remote cache miss must not call `loadHttpBitmap`, `downloadImage`, `HttpFileDownloader`,
  `URL.openStream`, DNS or a VFS HTTP bridge.
- Do not put artwork URL/path/credentials in `SmartTopCandidate`, logs or user-visible strings.
- Do not import concrete addon classes into core SmartTop UI.
- Keep provider lease/generation behavior unchanged.

### Spectrum

- Static bars only; no timer, animation, waveform capture or playback callback.
- Allowed first-release evidence: Radio root ID, or a valid provider candidate with owner-supplied
  `video=false`.
- Missing artwork by itself is not audio evidence.
- Do not apply spectrum to generic local audio, video, Empty or Recovery.

### Geometry and interaction

- Do not change card height, margins, adaptive padding, artwork/source-icon size, text lines, action
  sizes, progress geometry, Recent panel or its rows.
- Do not change click listeners, touch delegates, DPAD/rotary order or accessibility descriptions.
- `bindTimelineUpdate()` must not touch the background or layout.

## Implementation sequence

Commit-sized phases are recommended even if the final delivery is one commit.

### Phase 1: model and pure policy

1. Add `SmartTopBackground` and `SmartTopBackgroundPolicy`.
2. Add the background field to `SmartTopViewState`.
3. Update every constructor/copy method mechanically.
4. Add pure policy tests before binder work.

Exit gate: Mobile and Auto SmartTop unit tests compile and pass; renderer output is still unchanged.

### Phase 2: provenance and cache-only loading

1. Add direct metadata extraction without `getIconUri()` fallback.
2. Add `BitmapCache.getBitmapIfCached()` using the existing memory/disk layout.
3. Explicitly reject unsupported/VFS schemes.
4. Add cache-hit, cold-miss and zero-transport tests.

Exit gate: a cold HTTP(S) miss completes empty without reaching any downloader.

### Phase 3: root background renderer

1. Add the custom rounded drawable and ripple factory.
2. Render base, artwork/spectrum/fallback, scrim and border inside root bounds.
3. Keep the layout child list and every existing constraint unchanged.
4. Add resource/source guards proving foreground layout stability.

Exit gate: gate off renders the original drawable; gate on changes only background pixels.

### Phase 4: binder and coordinator integration

1. Rename `Views.artwork` to `Views.sourceIcon`.
2. Split `bindSourceIcon()` from `bindBackground()`.
3. Add generation + item + background identity to the async token.
4. Select/preserve background descriptors in the coordinator.
5. Confirm timeline payloads do not start background work.

Exit gate: source icon survives eligible artwork, invalid art, Radio spectrum, rapid rebind and
timeline updates.

### Phase 5: validation and rollout

1. Run focused SmartTop tests during iteration.
2. Run the full Mobile/Auto, architecture and lint gates.
3. Build exactly one universal debug APK.
4. Install/update without clearing app data.
5. Capture and inspect all twelve required DHU screenshots.
6. Enable the internal gate by default only after acceptance.

## Tests the agent must add

### Model/policy

- Valid square 512x512 artwork.
- Short edge 255 rejected and 256 accepted.
- Aspect boundaries 0.8 and 1.25 accepted.
- Wide 16:9 rejected.
- Animated or corrupt input rejected.
- Empty outranks every non-empty fallback input.
- Valid artwork outranks spectrum.
- Spectrum requires proven audio-addon evidence.

### Cache

- HTTP memory hit.
- HTTP resized disk hit.
- HTTP original disk hit with local decode.
- HTTP cold miss with zero downloader invocation.
- Local file/content/resource paths.
- Unsupported scheme rejection.

### Binder/state

- Source icon always calls `setImageResource(state.icon())`.
- No source icon callback calls `setImageBitmap()`.
- Artwork changes only the root background drawable.
- Binding item B invalidates item A's late result.
- `withLayout`, `withTitle`, `withQuickRecent` and `withTimeline` preserve background identity.
- Timeline payload does not call artwork metadata/cache APIs.
- Gate-off path restores `dashboard_smart_top_bg`.

### Layout/accessibility

- The V2 layout retains the same foreground IDs and child constraints.
- Card height and adaptive padding policy remain unchanged.
- No new focusable/clickable/accessibility child is introduced.
- Existing actions and Quick Recent touch targets remain unchanged.

## Required commands

Run focused tests first, then the full gates:

```powershell
.\gradlew.bat :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.smarttop.*" --no-daemon --no-parallel --stacktrace
.\gradlew.bat :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.smarttop.*" --no-daemon --no-parallel --stacktrace
.\gradlew.bat verifyWebOnlyProductionGraph --no-daemon --no-parallel --stacktrace
.\gradlew.bat testMobileDebugUnitTest testAutoDebugUnitTest --no-daemon --no-parallel --stacktrace
.\gradlew.bat :fermata:lintMobileDebug :fermata:lintAutoDebug --no-daemon --no-parallel --stacktrace
git diff --check
```

Package using a shell compatible with the repository script:

```sh
./build.sh -d
```

Do not publish separate Mobile and Auto APKs. The distribution contract requires exactly one
universal FermataX APK.

## DHU acceptance matrix

Use real cached/local items and the exact resolutions already represented by
`design/smarttop-demo-matrix/`:

| Case | 800x480 | 1280x720 | 1920x1080 |
| --- | --- | --- | --- |
| Eligible artwork, TV icon retained | required | required | required |
| Radio no-art, static spectrum | required | required | required |
| Wide artwork rejected | required | required | required |
| Empty neutral fallback | required | required | required |

For every image verify:

- source icon drawable, tint, size and position;
- title/subtitle/eyebrow bounds and truncation;
- timeline and action bounds;
- Quick Recent divider, rows and touch destination;
- unchanged card height and corner/ripple behavior;
- artwork visible enough to recognize while foreground remains readable.

Also test rapid A-to-B item changes, pause/play, Dashboard leave/return, process recreation, addon
handoff, DPAD/rotary traversal and a cold remote cache miss with transport diagnostics enabled.

## Forbidden shortcuts

- Do not solve the task by changing the foreground icon scale type or removing its tint.
- Do not call the current generic `getBitmap(uri, true, true)` from SmartTop.
- Do not load remote art and promise to cache it for the next Dashboard visit.
- Do not add Glide, Coil, Picasso, Blurry, a waveform library or a Palette dependency for v1.
- Do not copy UAMP's `AlbumArtContentProvider`; it downloads on cache miss.
- Do not copy a RenderScript blur implementation.
- Do not add FFT code behind an unused flag.
- Do not weaken `SmartTopCandidate` by adding a raw URL or resolved `PlayableItem`.
- Do not update the HTML demo to conceal a mismatch in the production APK.
- Do not rewrite unrelated dirty/untracked files.

## When external code is copied

The provided FermataX snippets are original scaffolds and need no third-party attribution. If the
agent chooses to copy external source anyway, it must stop before commit and:

1. state why the in-repository scaffold is insufficient;
2. identify source URL, exact commit, file and license;
3. retain copyright/license headers and mark modifications;
4. update `THIRD_PARTY_NOTICES.md`;
5. verify GPL-3.0 compatibility and distribution notice obligations.

Do not copy code from a repository with no clear license grant.

## Final report template

```text
Outcome:
- Background kinds implemented:
- Foreground invariants preserved:
- Feature gate and rollback value:

Owned files changed:
- ...

Automated verification:
- Focused SmartTop Mobile:
- Focused SmartTop Auto:
- Full Mobile:
- Full Auto:
- Architecture graph:
- Lint:
- git diff --check:
- Universal APK path:

Runtime evidence:
- 800x480 screenshots:
- 1280x720 screenshots:
- 1920x1080 screenshots:
- Cold remote cache-miss transport evidence:
- Rapid stale-callback evidence:
- DPAD/rotary/focus result:

Deferred by design:
- Live FFT/Visualizer
- Podcast/Audiobook provider artwork
- Dynamic palette/blur
```

Do not report completion if any required command, screenshot row or zero-network check is missing.
