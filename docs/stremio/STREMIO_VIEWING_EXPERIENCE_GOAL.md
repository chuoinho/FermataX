# FermataX Stremio Viewing Experience Goal

Status: active product direction

Created: 2026-07-22

Technical foundation: `STREMIO_ADDON_GOAL.md`

Reference UI: official [Stremio Web](https://github.com/Stremio/stremio-web) Board, Discover,
Meta Details and Library routes. FermataX adopts their content hierarchy and viewing flow, not a
pixel copy, embedded Web UI or Stremio account model.

## 1. Product Goal

Turn the native Stremio addon from a provider-oriented file list into a film-first experience:

```text
Stremio Home -> Discover/Search -> Movie or Series Details
             -> Season/Episode -> Direct Stream -> FermataX Player
```

The user should see posters, titles, progress and watch actions before provider implementation
details. Source management remains available, but it must not dominate normal browsing.

### Product priority

When requirements compete, implement them in this order:

1. Reach and resume a movie or episode with the fewest safe actions.
2. Keep title, artwork, progress and Back ownership attached to that exact content item.
3. Keep browsing readable and touchable on the smallest supported AA viewport.
4. Explain provider/stream details only where the user must choose or repair a source.

Provider management, protocol diagnostics and unsupported transports must never displace viewing
work from the active phase.

### Current execution boundary

Until this goal is accepted on DHU, implementation work is limited to the visible viewing path:

1. Home and Continue Watching.
2. Discover/Search and poster browsing.
3. Movie details or series season/episode selection.
4. Direct stream selection, playback and exact Back restoration.

Do not expand provider protocols, add another transport, add account sync, replace the player, or
redesign another FermataX addon while any step in this path is incomplete. Library may remain a
clear empty state until unified Favorites can supply stable Stremio media identities without a
second store.

## 2. Fixed Scope

Keep and reuse the current repository, protocol, provider-installation, session and playback
contracts for catalogs, metadata, direct HTTP/HLS/DASH playback, subtitles, progress, Recent,
Favorites, SmartTop, voice, security, cache and process restoration. Do not reuse the current
provider-root presentation or its internal navigation hierarchy.

The following are out of scope for this goal:

- YouTube or `ytId` playback inside Stremio. The parser may recognize `ytId` so mixed provider
  responses remain valid, but those entries are discarded before descriptor/UI creation.
- Torrent, `infoHash`, debrid, Stremio account sync, calendar and Trakt.
- Replacing FermataX player, playerbar, global Back, fullscreen or navigation rail policies.
- A WebView clone of Stremio Web or a new media engine.
- Broad changes to TV, Radio, Podcast, Audiobook, YouTube, Web or Dashboard.

## 3. Experience Contract

### 3.1 Stremio Home

- Opening Stremio lands on a content home, never directly on a provider/source list.
- First section is `Continue Watching` when resumable Stremio content exists.
- Remaining sections are horizontal content shelves such as Popular Movies and Popular Series.
  Results are merged and deduplicated by canonical content identity; provider priority resolves
  metadata/stream ownership but never creates a provider-branded Home shelf.
- Each media card uses a 2:3 poster, title and one useful secondary line only.
- Search, Discover, Library and Addons are compact top actions; Addons opens existing source
  management.
- Empty state has one clear `Add provider` action and no dead shelves.
- A provider is not a Home row, navigation level or poster subtitle. Provider identity is visible
  only in Addons and the stream picker.
- Home is bounded to six catalog shelves and twelve posters per shelf. More catalogs remain
  reachable through Discover instead of growing Home without limit.

### 3.2 Discover and Search

- Discover exposes three compact filters: type, catalog and genre.
- Results use an adaptive poster grid; pagination is an explicit final item and never an endless
  fetch under the pointer.
- Search reuses the proven AA keyboard and voice flow, then renders the same poster grid.
- Cached results appear immediately; loading, partial failure, empty and retry states are visible
  without replacing usable cached content.

### 3.3 Movie and Series Details

- Details are one unframed screen with backdrop, poster, title, year, runtime, rating when supplied,
  genres and a short overview.
- Primary action is `Watch` for movies and `Resume` when progress exists.
- Favorite is a single icon action and uses the existing unified Favorites contract.
- Series show a season selector followed by episode rows with thumbnail, episode number, title,
  duration and progress.
- Selecting an episode opens streams for that exact episode; title/progress ownership must never
  leak to another episode.
- Details must prioritize the primary Watch/Resume action and episode choice over exhaustive
  metadata. Missing optional metadata leaves space empty; it does not create placeholder rows.

### 3.4 Stream Picker

- Direct playable streams are grouped by provider and ranked in the current stable order.
- Each row shows provider, stream label and detected format/quality when trustworthy.
- Provider loading/failure is shown inline while healthy results remain selectable.
- The picker contains direct HTTP/HLS/DASH choices only. `ytId`, `externalUrl` and torrent choices
  are omitted from this goal instead of creating unavailable rows in the viewing flow.
- Selecting a stream starts playback once. Double tap, refresh and late provider results must not
  create a second playback request.
- The stream picker is the only normal viewing screen allowed to lead with provider identity.

### 3.5 Playback Return

- FermataX remains the sole player and MediaSession owner.
- Fullscreen Back returns to the exact stream picker; another Back returns to episode/details,
  then Discover/Home, then Dashboard.
- SmartTop, Recent, Favorites and Continue reopen the exact movie/episode and current progress.
- Top bar, playerbar and SmartTop show content title, never a URL or provider credential.

## 4. Responsive AA Rules

- Optimize first for 800x480, then verify 1024x600, 1280x720 and an ultra-wide AA viewport.
- Poster width is derived from available content width with bounded min/max values; aspect ratio is
  always 2:3 and card text cannot resize the grid.
- All primary touch targets are at least 48dp. Focus, touch and rotary states use the same selected
  item and never depend on hover.
- No nested cards, decorative panels, tiny metadata chips or horizontally clipped actions.
- Navigation rail remains visible on browse/details screens and hides only under the existing true
  fullscreen rule.
- Layout restoration after split/fullscreen always recomputes spans from current content width.

## 5. Architecture

Add a Stremio-owned presentation layer over existing immutable browse/session models:

```text
StremioFragment
  -> StremioScreenState (Home, Discover, Details, Streams, Library)
  -> StremioViewModel/Presenter
  -> existing StremioItemGateway + StremioSessionCoordinator
  -> existing repositories/protocol/playback
```

Rules:

- UI state contains stable IDs and immutable presentation models, never raw tokens or stream URLs.
- Routes use the full opaque catalog identity derived from source UUID, type and catalog ID; labels
  are never used as identity.
- Each visible model carries an explicit selection target. The renderer must not infer navigation
  from title text, key prefixes or concrete model classes.
- Fragment owns views only; repository/network callbacks are generation-checked and cancellable.
- Poster loading uses the existing image/cache path with fixed-size placeholders.
- The custom Stremio adapter/view holders stay inside `modules/stremio`; no Stremio special case is
  added to Dashboard, player, Back policy or another addon.
- Existing MediaLib items remain the compatibility boundary for MediaSession, Recent, Favorites,
  SmartTop, voice and process restoration.
- The existing MediaLib provider tree is a compatibility/resolution model only. It must not define
  the visible Home, Discover, Details, Episode or Stream layouts.

## 6. Delivery Phases

### Phase UX-0 - Baseline and Scope Lock

- Remove live `ytId` playback and its Stremio-specific Back route.
- Capture current DHU Home, catalog, details, episode and stream screens.
- Add characterization tests for stable IDs, Back destinations and direct playback ownership.

Exit: mixed direct/`ytId` fixture exposes direct streams only; all Auto/Mobile tests pass.

### Phase UX-1 - Presentation Foundation

- Introduce screen-state/presentation models and custom Stremio view types.
- Add responsive span/size policy with pure tests for target AA widths.
- Preserve existing data, playback identity and app-level Back behavior while replacing Stremio's
  provider-root internal navigation with the film-first route stack.
- Add a presentation gateway that maps existing browse/session data to stable, secret-free UI
  models and retains bounded identity registries for user navigation.

Exit: no provider/network/playback changes; loading/error/empty states render on mobile and DHU.

### Phase UX-2 - Home and Discover

- Build Continue shelf, catalog shelves, compact actions and adaptive poster grids.
- Add type/catalog/genre filters and explicit pagination.
- Keep source management under Addons.
- Remove provider-first rows from the normal Stremio entry flow; providers remain available through
  the single Addons action and in stream-group labels.

Exit: Home reaches a playable catalog item in at most three taps; Addons remains reachable in one
tap from Stremio Home.

### Phase UX-3 - Details, Seasons and Episodes

- Build movie/series details, favorite/resume actions, season selector and episode rows.
- Preserve exact parent context and progress ownership.

Exit: movie and series flows pass on all target viewports; ten randomized episode switches produce
zero wrong-title or wrong-progress updates.

### Phase UX-4 - Stream Selection and Playback Return

- Build grouped stream rows, trustworthy format/quality labels and inline provider states.
- Verify single playback dispatch, subtitle entry and exact Back hierarchy.

Exit: HLS, DASH and MP4 fixtures start through current engines; every Back moves exactly one level.

### Phase UX-5 - Library, Continue and Search Polish

- Add local Stremio Library view backed by unified Favorites.
- Complete Continue and Search poster flows, voice result handoff and stale-provider states.

Exit: restart/process-death restore opens the exact item; removed providers fail safely without
removing another addon's data.

### Phase UX-6 - DHU Hardening

- Run viewport, touch, D-pad/rotary, keyboard, cold-cache, warm-cache and addon-switch regression.
- Run full Auto/Mobile tests, release R8/lint and universal APK module inspection.
- Update acceptance evidence and screenshots only after device validation.

Exit: no P0/P1 issue, no overlap/clipping, no main-thread network/DB work and no regression in TV,
Radio, Podcast, Audiobook, YouTube, Web, Dashboard, SmartTop, playerbar, fullscreen or Back.

## 7. Measurable Acceptance

- `UX-ACC-001`: Stremio opens to content Home, not provider plumbing.
- `UX-ACC-001A`: No enabled provider is rendered as a Home row or an intermediate level before a
  catalog poster; provider names appear only in Addons and stream selection.
- `UX-ACC-002`: Home, Discover, Search and Library use stable poster layouts at all target widths.
- `UX-ACC-003`: Details expose enough metadata and one obvious Watch/Resume action without scroll
  on 800x480.
- `UX-ACC-004`: Series season/episode selection retains exact title, progress and parent context.
- `UX-ACC-005`: Stream picker omits `ytId`, groups direct streams and remains usable during partial
  provider failure.
- `UX-ACC-006`: One selection creates one playback request and exact MediaSession ownership.
- `UX-ACC-007`: Back traverses one level per action and never jumps to Dashboard early.
- `UX-ACC-008`: SmartTop/Recent/Favorites/Continue restore the exact Stremio item after restart.
- `UX-ACC-009`: Keyboard and voice search remain functional on DHU.
- `UX-ACC-010`: Full Auto/Mobile regression and release builds pass with no other-addon behavior
  change.
- `UX-ACC-011`: Home renders at most six content shelves and twelve posters per shelf, independent
  of the number of installed providers.
- `UX-ACC-012`: Back from Details or Discover restores the exact poster, shelf/grid position and
  query/filter state without re-entering provider/catalog plumbing.

No implementation is complete based on screenshots alone. Every phase requires automated tests
plus mobile and Google DHU evidence before the next phase begins.
