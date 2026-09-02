# SmartTopCard background technical design

## 1. Purpose

Add contextual artwork to SmartTopCard without turning artwork into the source icon and without
changing the card's existing content, geometry, navigation or playback contracts. When eligible
artwork is unavailable, an explicitly proven audio addon may receive a static spectrum background;
other states use deterministic fallbacks.

The visual policy is defined in `design/smarttop-background-policy.md`. The real reference renders
are indexed by `design/smarttop-demo-matrix/README.md`.

## 2. Current production seam

`SmartTopViewState.icon()` correctly retains the source drawable. The defect is in
`SmartTopBinder.bindArtwork()`:

1. It assigns `state.icon()` to the foreground `ImageView`.
2. It resolves `presentedItem.getIconUri()`.
3. It replaces the same `ImageView` with the returned bitmap.

`PlayableItem.getIconUri()` is also unsafe for background provenance. Its default implementation
uses `METADATA_KEY_ALBUM_ART_URI` when present, then falls back to `parent.getIconUri()`. That fallback
can be a folder image, source logo or addon icon rather than artwork owned by the presented item.

`BitmapCache.getBitmap(...)` is unsafe for a decorative Dashboard lookup because HTTP(S) cache
misses call `loadHttpBitmap()` and `downloadImage()`. A distinct cache/local-only entry point is
required.

## 3. Target data flow

```text
Playback/Recent/provider evidence
              |
              v
SmartTopArtworkResolver ---- direct item metadata only
              |
              v
SmartTopBackgroundPolicy ---- artwork eligibility + audio-addon proof
              |
              v
SmartTopBackground descriptor in SmartTopViewState
              |
              v
SmartTopBinder
  |                         |
  v                         v
bindSourceIcon()      bindBackground()
dashboard_item_icon   root background drawable
```

The renderer never derives a different semantic state from view visibility. The coordinator owns
selection, the immutable view state transports the decision and the binder only renders it.

## 4. Background model

`SmartTopBackground` has four kinds:

| Kind | Payload | Renderer |
| --- | --- | --- |
| `ARTWORK` | stable URI string plus identity | cache/local bitmap with `centerCrop` and scrim |
| `AUDIO_SPECTRUM` | source identity | static drawable plus audio gradient |
| `SOURCE_FALLBACK` | source identity | deterministic gradient/watermark |
| `EMPTY` | no item payload | neutral gradient/grid watermark |

Rules:

- The descriptor never contains a `Bitmap`, `Drawable`, callback, provider response or secret.
- Identity must change when the semantic item or artwork URI changes.
- Identity must not change for progress, play/pause, favorite presentation or viewport changes.
- `SmartTopViewState.icon()` remains independent and always means foreground source icon.
- `withLayout`, `withTitle`, `withQuickRecent` and `withTimeline` preserve the descriptor.

## 5. Artwork provenance and eligibility

### 5.1 Accepted provenance

For a concrete `PlayableItem`, inspect only the direct
`MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI` returned by its media metadata. A missing value is a
normal no-art result. Do not call `getIconUri()` and do not walk the parent hierarchy.

Provider candidates deliberately contain no artwork URL. Until a provider contract offers a safe,
cached and transport-free artwork handle, provider-backed Resume renders its media-nature fallback.
This preserves the existing `SmartTopCandidate` security boundary.

### 5.2 Eligibility gates

Every gate must pass:

- Scheme is `file`, `content`, `android.resource`, or HTTP(S) already present in the Fermata bitmap
  cache.
- Decode succeeds to a non-recycled static bitmap.
- Short edge is at least 256 px; 512 px or greater is preferred.
- `width / height` is between 0.8 and 1.25 inclusive.
- Known animated MIME/extensions are rejected before decode.
- A 16:9 thumbnail, video frame, screenshot, folder icon or source logo is not promoted to artwork.
- The generation/item/background token is still current when decode completes.

The first release uses a deterministic scrim instead of per-image palette theming. This keeps visual
output stable across devices and makes contrast verification reproducible.

## 6. Audio-addon classification

Missing artwork alone never proves audio.

Accepted evidence in the first release:

- A concrete item whose stable root ID is `radio`, already recognized by
  `SmartTopCoordinator.sourceName()`.
- A candidate delivered through a valid `SmartTopProviderLease` whose owner-supplied media nature
  has `video=false`.

Not accepted:

- A generic local item with `isVideo()==false`.
- A missing or failed artwork URI.
- A source label inferred from title/subtitle text.
- Podcast or Audiobook without a characterized SmartTop provider/hint.

The spectrum is a static visual resource. It has no audio session, callback, timer, animation,
permission or engine lifecycle.

## 7. Cache/local-only bitmap path

The new API belongs in `BitmapCache` so URI normalization and disk layout stay single-owner.

### HTTP(S)

1. Normalize through `FermataContentProvider.getOrigUri()`.
2. Check the existing in-memory key.
3. Check the resized icon file and original `toImageFile(uri)` disk entry.
4. Decode a present file on the existing queue; optionally write the resized derivative.
5. Return empty immediately when neither file exists.
6. Never call `loadHttpBitmap()`, `downloadImage()`, `HttpFileDownloader` or `URL.openStream()`.

### Local schemes

`file`, `content` and `android.resource` may decode on the existing queue because they do not require
network. They may populate the in-memory/resized cache. Other schemes are rejected from this path;
the generic loader's VFS fallback may map them to HTTP.

### Failure behavior

Null, missing file, security error, decode error, invalid dimensions or stale token all produce the
already-selected fallback. Background failure must not hide the card or change foreground state.

## 8. Android background layering

Do not add, wrap, move or re-constrain a layout child. The V2 `ConstraintLayout` keeps its width,
height, margin, padding, click listener and focus behavior. A dedicated
`SmartTopCardBackgroundDrawable` replaces only the root background while the feature is enabled.

The drawable renders inside the root bounds, independent of content padding:

1. existing dark base gradient;
2. center-cropped artwork, static spectrum or deterministic fallback;
3. fixed readability scrim;
4. existing one-dp border;
5. the existing rounded ripple through an outer `RippleDrawable` and mask.

The factory resolves the same theme ripple color and 14dp corner radius used by
`dashboard_smart_top_bg`. With the gate disabled, the root continues to use that resource directly.
This design makes the requested ownership literal: only the SmartTopCard background changes, and no
foreground view gains layout, input or accessibility behavior.

## 9. Binder lifecycle

`SmartTopBinder.Views.artwork` is renamed to `sourceIcon`. The root itself owns the background
drawable, so no new interactive or semantic view is added.

Full bind order:

1. Store the new state and root bind token.
2. Restore the synchronous fallback root background and clear its bitmap/token.
3. Bind `sourceIcon` from `state.icon()` with the existing tint.
4. Apply the synchronous fallback for the selected background kind.
5. For `ARTWORK`, start cache/local-only decode and store a root token containing generation, item
   ID and background identity.
6. Apply the bitmap only if the root token still matches; invalidate only the root background
   drawable.
7. Bind the existing eyebrow, metadata, actions, timeline and Recent surfaces unchanged.

`bindTimelineUpdate()` may update the root state tag, timeline values and action presentation only.
It must not call background policy, metadata, cache or layout code.

## 10. Coordinator behavior

The coordinator selects a descriptor when it publishes a new semantic item. An artwork metadata
result may publish one same-generation state update when it changes fallback to eligible artwork.
Late metadata is rejected with the existing generation and item ownership checks.

`refreshCurrentInPlace()` includes background identity in metadata stability. A progress-only change
retains the same descriptor and continues through `onSmartTopTimeline()` rather than a full bind.

Mode mapping:

| Mode | Background decision |
| --- | --- |
| `CURRENT` | direct eligible artwork, proven audio spectrum, or source fallback |
| `RESUME` concrete item | same policy as Current |
| `RESUME` provider result | provider media-nature fallback; no artwork URL added to DTO |
| `RECENT` | direct eligible artwork or fallback |
| `RECOVERY` | source fallback, never stale failed artwork |
| `EMPTY` | neutral Empty background |

## 11. Performance, privacy and accessibility

- No Dashboard network request and no main-thread bitmap decode.
- At most one background decode for one semantic identity; timeline ticks reuse it.
- Decode uses an output target appropriate to the card instead of retaining a full camera-size
  bitmap after dimension validation.
- The custom background creates no focusable, clickable or announced child.
- Source icon content behavior and all existing focus/touch delegates remain unchanged.
- No URL, credential or cache path is written to user-facing strings or diagnostics.
- No live motion is introduced, so reduce-motion behavior remains deterministic.

## 12. Test design

### Pure JVM policy tests

- Four background kinds and strict fallback order.
- Short edge 255 rejected; 256 accepted.
- Aspect 0.8 and 1.25 accepted; just outside rejected.
- 16:9 rejected.
- Missing artwork does not classify a generic item as audio.
- Radio and leased provider audio classify as spectrum when art is unavailable.

### Cache tests

- HTTP memory hit, resized disk hit, original disk hit and cold miss.
- Cold miss records zero download/transport calls.
- Local file/content/resource decode succeeds on the queue.
- Unsupported/VFS schemes are rejected.
- Corrupt bitmap returns empty and keeps the foreground card visible.

### Binder/resource regression tests

- The V2 layout child list and every foreground constraint remain unchanged.
- `dashboard_item_icon` is still bound only from `state.icon()`.
- Artwork applies only to the root background drawable.
- A-to-B rebind rejects A's late callback.
- Timeline payload never invokes background loading.
- Existing dimensions, constraints, actions and Recent IDs remain unchanged.

### Runtime acceptance

Use the universal production package at 800x480, 1280x720 and 1920x1080 for:

1. eligible square artwork with TV source icon retained;
2. Radio without cover using static spectrum and Radio icon retained;
3. wide artwork rejected to source fallback;
4. Empty using the neutral background.

Capture twelve screenshots and verify focus, rotary/DPAD, touch, rapid item switching, pause/resume,
Dashboard leave/return and cold remote cache miss.

## 13. Rejected alternatives

- Reusing the foreground icon view for artwork: violates the requested component boundary.
- Calling the existing generic bitmap loader: can download on a cache miss.
- Live FFT/`Visualizer`: new permission/lifecycle/engine work for decorative motion.
- Runtime blur: reduces artwork visibility and adds bitmap/CPU cost; common RenderScript examples are
  deprecated on modern Android.
- Adding Glide, Coil, Picasso or a waveform dependency: duplicates the existing bitmap owner and
  increases the universal APK for a small deterministic renderer.
- Dynamic palette in the first release: makes screenshot output and contrast less deterministic;
  reconsider only after the fixed-scrim implementation passes DHU acceptance.
