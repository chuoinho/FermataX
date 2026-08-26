# Stremio Reference Register

> Status: HISTORICAL REFERENCE. It does not authorize the retired native Stremio architecture;
> use [`web-only/README.md`](web-only/README.md) for current references.

This file freezes the Phase 0 research inputs for the FermataX Stremio addon. It is a provenance
register, not permission to copy an upstream implementation. Protocol behavior must be verified
against the primary Stremio specifications before third-party code is consulted.

## Primary Specifications

The following official documents are semantic authorities. They are web documentation rather than
repository snapshots, so implementation changes that rely on them must record the access date and
the relevant protocol fixture or contract test.

| Specification | Scope |
| --- | --- |
| [Stremio Addon Protocol](https://stremio.github.io/stremio-addon-sdk/protocol.html) | HTTP resource paths, transport and extra arguments |
| [Manifest format](https://stremio.github.io/stremio-addon-sdk/api/responses/manifest.html) | Manifest resources, types, `idPrefixes`, catalogs and behavior hints |
| [Resource model](https://stremio.github.io/stremio-addon-sdk/api/) | Catalog, meta, stream and subtitle response flow |
| [Stream object](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md) | Direct URL, `ytId`, `externalUrl`, torrent and behavior hints |
| [Subtitles object](https://stremio.github.io/stremio-addon-sdk/api/responses/subtitles.html) | Subtitle response fields |
| [Deep links](https://stremio.github.io/stremio-addon-sdk/deep-links.html) | `stremio://` normalization |
| [Official addon descriptors](https://github.com/Stremio/stremio-official-addons/blob/master/index.json) | Candidate verified default descriptors |

Official descriptor endpoints are mutable. Cinemeta and OpenSubtitles availability must be
verified at build/release time; no descriptor URL or community stream provider is frozen into a
test fixture in this repository.

Release verification on 2026-07-22 confirmed the official descriptor still lists Cinemeta at
`https://v3-cinemeta.strem.io/manifest.json` and OpenSubtitles v3 at
`https://opensubtitles-v3.strem.io/manifest.json`.

## Pinned Open-Source Snapshots

### stremio-addon-client

- Repository: <https://github.com/Stremio/stremio-addon-client>
- Pinned commit: [`7c66830cfc1a8e749373d9df0bb105c7dad33bfd`](https://github.com/Stremio/stremio-addon-client/tree/7c66830cfc1a8e749373d9df0bb105c7dad33bfd)
- License at the pinned commit: [MIT](https://github.com/Stremio/stremio-addon-client/blob/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/LICENSE.md)
- Intended use: protocol semantics and compatibility-test reference. FermataX ports behavior into
  typed Java and does not embed Node.js.

Exact files reviewed at this revision:

| Upstream file | Behavior to characterize | Excluded implementation detail |
| --- | --- | --- |
| [`lib/AddonClient.js`](https://github.com/Stremio/stremio-addon-client/blob/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/lib/AddonClient.js) | Immutable descriptor boundary and generic resource request | JavaScript callback/promisify layer |
| [`lib/AddonCollection.js`](https://github.com/Stremio/stremio-addon-client/blob/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/lib/AddonCollection.js) | Descriptor add/remove/deduplication semantics | Mutable collection as persistent truth |
| [`lib/stringifyRequest.js`](https://github.com/Stremio/stremio-addon-client/blob/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/lib/stringifyRequest.js) | Resource/type/id/extra path construction | Node encoding without Android Unicode tests |
| [`lib/util/isSupported.js`](https://github.com/Stremio/stremio-addon-client/blob/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/lib/util/isSupported.js) | Resource/type/`idPrefixes` matching and catalog special case | Trusting malformed resource arrays |
| [`lib/util/mapURL.js`](https://github.com/Stremio/stremio-addon-client/blob/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/lib/util/mapURL.js) | Recognition of transport URL forms | Blind scheme rewriting and browser-localhost assumptions |
| [`lib/transports/http.js`](https://github.com/Stremio/stremio-addon-client/blob/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/lib/transports/http.js) | Manifest URL replacement and response cache metadata | Unbounded fetch and browser CORS assumptions |
| [`test/`](https://github.com/Stremio/stremio-addon-client/tree/7c66830cfc1a8e749373d9df0bb105c7dad33bfd/test) | Edge cases and compatibility expectations | Node runtime/test dependencies |

### NuvioMobile

- Repository: <https://github.com/NuvioMedia/NuvioMobile>
- Pinned commit: [`b1c9d08435a5b7d7487b30bbf181cb48830c2458`](https://github.com/NuvioMedia/NuvioMobile/tree/b1c9d08435a5b7d7487b30bbf181cb48830c2458)
- License at the pinned commit: [GPL-3.0](https://github.com/NuvioMedia/NuvioMobile/blob/b1c9d08435a5b7d7487b30bbf181cb48830c2458/LICENSE)
- Intended use: Android failure handling, typed normalization, repository boundaries, stream
  parsing and partial-provider failure behavior.

Exact files reviewed at this revision:

```text
composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/AddonManifestParser.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/AddonTransportUrls.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/AddonRepository.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogRepository.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsRepository.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/search/SearchRepository.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamModels.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamParser.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt
composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamFetchSupport.kt
```

Compose UI, Media3 ownership, account/cloud assumptions and Nuvio product navigation are outside
the reference scope.

## Additional Pinned Semantic References

These references were pinned for the reliability implementation guide. Cross-language code must be
ported behind FermataX contracts; pinning a revision is not permission to copy incompatible UI or
runtime layers.

| Project | Pinned commit | License | Allowed use |
| --- | --- | --- | --- |
| [stremio-core](https://github.com/Stremio/stremio-core) | `eeb89ff8c7f401b50c435933dab399daa956dc35` | MIT | Immutable models, request filtering, aggregation and loadable-state semantics |
| [jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent) | `169b7a8f09ba99a683536a77de1978cc014e6b09` | MIT | Alert APIs, torrent status and streaming behavior for the existing jlibtorrent engine |
| [Harbor](https://github.com/harborstremio/harbor) | `cfdafb95528315a8bd37997abbfbed9ff27dab35` | MIT | Stream probe, provider aggregation, subtitle and bounded recovery algorithms |
| [Stremio Web](https://github.com/Stremio/stremio-web) | `daf74b0ec973054c94de9f0f8271b3234bd26c43` | GPL-2.0 | Observable UI/state behavior only; no source copying into FermataX |

## Behavioral APK Reference

The following binary was inspected only for observable behavior:

```text
URL: https://github.com/malebuffy/Fermata-Xtream/releases/download/v2.4.18/
     fermata-rec-2.4.18-auto-release-universal.apk
Package: me.aap.fermatamod.dear.google.why.bingo2.rec
Version: 2.4.18 (412)
SHA-256: BCA83F847F84CE577A594F92F78D96C2C8CA010AB3074E40ABB67E383F753C8E
```

Permitted observations include native catalog/detail/season navigation, provider chips,
configuration flow, subtitle aggregation and optional torrent UX. The binary is not a source-code
dependency.

## Operational Stremio Web Baseline

The user-supplied `stremio-web-source.zip` was extracted from a working Stremio addon in Fermata
Xtream. Its Home, Discover, Library, Calendar, Search, Details and Player state flows are therefore
treated as an operational behavior baseline rather than generic upstream UI code. The archive
contains Web assets/WASM and a customized `window.FermataStremio` transport call site, but not the
native Android implementation of `init`, `getState`, `dispatch` or `onCoreEvent`.

No Web bundle code is embedded in FermataX. The comparison is used to characterize observable
pagination, next-video and navigation behavior while FermataX retains its native Java runtime,
bounded networking and existing AA lifecycle contracts.

## No Decompiled-Code Copying Policy

1. Decompiled classes, resources, strings, algorithms and control flow from the behavioral APK
   must not be copied, translated, mechanically rewritten or used as a patch source.
2. Engineers may document only externally observable input/output behavior and reproduce that
   behavior from the official protocol plus independently designed FermataX contracts.
3. Every adapted open-source behavior must cite project, commit and exact file in the implementing
   commit or review note, and all license/notice obligations must be preserved.
4. If provenance cannot be established, stop implementation of that portion and create an
   independent contract test from the official specification.
5. Test fixtures must contain only fictional/public-domain metadata, reserved `example.invalid`
   endpoints, and no credentials, tokens, cookies or community-provider configuration.

## FermataX Contracts To Reuse

Stremio must integrate with, rather than duplicate, these local contracts:

```text
fermata/src/main/java/me/aap/fermata/addon/VoiceSearchAddon.java
fermata/src/main/java/me/aap/fermata/addon/MediaItemResolverAddon.java
fermata/src/main/java/me/aap/fermata/media/lib/PlaybackProgressItem.java
fermata/src/main/java/me/aap/fermata/security/SecurePreferenceStore.java
fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardPlayableNavigator.java
fermata/src/main/java/me/aap/fermata/ui/policy/ItemRoutePolicy.java
fermata/src/main/java/me/aap/fermata/ui/policy/BackNavigationPolicy.java
fermata/src/main/java/me/aap/fermata/ui/policy/PlaybackLayoutPolicy.java
```

The controlling product contract is `MASTER_CONTEXT.md`; the implementation and acceptance plan is
`docs/stremio/STREMIO_ADDON_GOAL.md`.
