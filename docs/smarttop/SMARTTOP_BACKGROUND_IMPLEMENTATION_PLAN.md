# SmartTopCard background implementation plan

Status: ready for implementation

This plan moves artwork and audio spectrum into a dedicated SmartTopCard background layer. The
foreground source icon and every existing semantic or geometric contract remain unchanged.

## Scope

- In: SmartTop V2 production layout, eligible item artwork, static spectrum for proven audio
  addons, source/empty fallbacks, cache-only bitmap access, stale-bind protection, automated tests,
  the universal debug APK and the three real DHU resolutions.
- Out: legacy SmartTop layouts, live FFT or `Visualizer`, `RECORD_AUDIO`, new network requests,
  source icon replacement, provider ranking, Podcast/Audiobook provider implementation, changes to
  metadata, timeline, actions, Quick Recent, card height or Dashboard navigation.

## Non-negotiable acceptance contract

1. `dashboard_item_icon` always renders `SmartTopViewState.icon()` and never receives an artwork
   bitmap.
2. Artwork, spectrum, source fallback and empty texture render behind every existing child.
3. A timeline-only payload never resolves, decodes, reloads or relays out the background.
4. A remote artwork cache miss never starts HTTP, DNS, a provider refresh or addon initialization.
5. An async result for item A can never appear after the card has rebound to item B.
6. Card height, constraints, action policy, click routing, focus order and Quick Recent stay stable.
7. Spectrum is static and appears only for an explicitly proven audio addon without eligible
   artwork.

## Deliverables

- `SmartTopBackground`: immutable renderer input with `ARTWORK`, `AUDIO_SPECTRUM`,
  `SOURCE_FALLBACK` and `EMPTY` kinds.
- `SmartTopBackgroundPolicy`: pure selection and artwork-dimension policy.
- `SmartTopArtworkResolver`: extracts direct item artwork provenance without following the
  `PlayableItem.getIconUri()` parent fallback.
- A cache/local-only bitmap API in `BitmapCache`.
- A full-card `SmartTopCardBackgroundDrawable` that preserves the existing ripple and rounded mask.
- Static spectrum, source fallback, empty and scrim resources.
- Split foreground-icon and background binding in `SmartTopBinder`.
- Unit, resource and stale-callback regression tests.
- Twelve production screenshots: four visual branches at 800x480, 1280x720 and 1920x1080.

## Action items

[ ] Add a temporary internal `smart_top/background_enabled` preference next to
`SMART_TOP_V2_ENABLED`. When disabled, the V2 card must render the current
`dashboard_smart_top_bg` path exactly; do not add a user-facing setting before DHU acceptance.

[ ] Add `SmartTopBackground`, `SmartTopBackgroundPolicy` and `SmartTopArtworkResolver` under
`fermata/src/main/java/me/aap/fermata/ui/smarttop/`. Keep bitmap objects and network addresses out of
`SmartTopViewState`; the state carries only a bounded descriptor and stable identity.

[ ] Extend `SmartTopViewState` with a background field and preserve it in `withLayout`, `withTitle`,
`withQuickRecent` and `withTimeline`. Keep the existing `icon` field and document it as foreground
source identity only.

[ ] Resolve artwork provenance only from the item's direct
`MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI`. Never use `PlayableItem.getIconUri()` for the
background because its default implementation falls back to the parent/source icon. Do not add an
artwork URL to the transport-free `SmartTopCandidate` DTO.

[ ] Add `BitmapCache.getBitmapIfCached(...)`. For HTTP(S), inspect the current memory/disk cache and
return empty on a miss; for `file`, `content` and `android.resource`, allow background decoding and
optional resized-cache writes. Reject every other scheme in the SmartTop path so a VFS adapter
cannot turn the operation into network I/O.

[ ] Add `SmartTopCardBackgroundDrawable` plus a small factory that wraps it in the same rounded
`RippleDrawable` contract as `dashboard_smart_top_bg`. Draw against the root bounds so card padding
cannot inset the artwork. Do not insert, wrap, move or re-constrain any existing layout child.

[ ] Add static drawables for the spectrum, source fallback, empty texture and artwork scrim. Match
the real demo's visual hierarchy: visible artwork with no blur, a darker text/action side, a low
opacity spectrum occupying only part of the card, and a neutral Empty background.

[ ] Rename the ambiguous `SmartTopBinder.Views.artwork` field to `sourceIcon`. Replace the current
`bindArtwork()` seam with `bindSourceIcon()` and `bindBackground()`; the latter changes only the root
background drawable. Clear the previous bitmap and bind token before each full bind; validate
generation, item identity and background identity before applying an async result.

[ ] Make `SmartTopCoordinator` select one background descriptor when semantic content changes.
Classify current/Recent Radio items by the stable root ID already recognized by `sourceName()`;
classify a provider Resume candidate as audio only when its owner-supplied media nature says
`video=false`. Defer Podcast/Audiobook spectrum until their providers publish equivalent evidence.

[ ] Add pure policy tests for all boundaries: 255/256 px, aspect 0.8/1.25, 16:9 rejection, invalid
decode, remote cache hit/miss, local URI, Radio no-art, non-addon audio, provider audio/video,
source fallback, Empty and Recovery. Add binder/resource guards for foreground icon ownership,
layer order, unchanged geometry, stale A-to-B callbacks and timeline-only stability.

[ ] Run the repository gates in this order:

```text
.\gradlew.bat verifyWebOnlyProductionGraph --no-daemon --no-parallel --stacktrace
.\gradlew.bat testMobileDebugUnitTest testAutoDebugUnitTest --no-daemon --no-parallel --stacktrace
.\gradlew.bat :fermata:lintMobileDebug :fermata:lintAutoDebug --no-daemon --no-parallel --stacktrace
git diff --check
./build.sh -d
```

[ ] Install the single universal debug APK and capture the real DHU matrix using cached/local
repository assets. Compare before/after geometry and focus behavior, verify a cold remote cache miss
produces no transport, then enable the background gate by default in a separate final change.

## Rollout and rollback

1. Land model, policy and tests with the background gate disabled.
2. Land the V2 background renderer and pass Mobile/Auto units plus lint.
3. Produce exactly one universal debug APK and complete the twelve-image DHU matrix.
4. Switch the internal gate default to enabled only after every acceptance row passes.
5. Roll back by disabling only `smart_top/background_enabled`; do not disable SmartTop V2 or alter
   saved user data.

## Definition of done

- All automated commands above pass.
- The universal APK is the only packaged artifact.
- The four visual branches match `design/smarttop-demo-matrix/` at all three resolutions.
- Foreground components are byte-for-byte/resource-contract unchanged except for the `Views`
  wiring needed to expose the new background surface.
- No new permission, dependency, network path, live animation or addon contract is introduced.
- The implementation report records screenshots, transport evidence and the exact rollback switch.

## Open questions

There are no blocking product questions. The plan assumes a temporary internal gate and a fixed
scrim for the first release; dynamic palette extraction remains a later, separately reviewed option.
