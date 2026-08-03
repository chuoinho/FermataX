# TLS trust audit and lint delivery evidence — 2026-08-03

## Verdict

Removing `SecurityUtils.InsecureTrustManager` was not behavior-neutral. The class has no
direct production call site by name, but it was installed in the singleton
`ClientContextHolder` used by `SecurityUtils.createClientSslEngine()`. `SelectorHandler`
selects that engine whenever an SSL connection does not provide a custom engine, and
`HttpConnection` enables SSL automatically for every HTTPS URL. Consequently, the old
trust-all behavior was the production default for the project's asynchronous HTTP stack.

The change in commit `b4d5dcb6cd22cbae29ad6d2eedc2fa5f027514df` makes that stack use
the platform trust store. This closes a real certificate-chain validation vulnerability,
but it can also reject self-signed endpoints that worked before. Treat this as a security
and compatibility migration, not merely lint cleanup.

## Production call chain

1. `HttpConnection.connect()` derives `ssl=true` for an HTTPS URL.
2. `SelectorHandler.connect()` sees `ssl=true` and, when the caller did not supply
   `ConnectOpts.sslEngine`, selects `SecurityUtils::createClientSslEngine`.
3. Before the lint commit, `ClientContextHolder` initialized its `SSLContext` with
   `InsecureTrustManager.instance`, whose client/server validation methods were empty.
4. No production caller found in the repository supplies an alternative client
   `sslEngine`, and there is no preference or UI option for self-signed certificates.

The only other direct `createClientSslEngine()` call is `NetThread`, which creates an
engine to size reusable TLS buffers and does not itself connect to a peer.

## Affected production consumers

| Consumer | User-visible impact of strict certificate validation |
| --- | --- |
| `M3uFileSystem` via `HttpFileDownloader` | A user-configured HTTPS M3U playlist on a self-signed server can no longer refresh. A cached playlist may mask the failure because this flow can return an existing cache on download failure. |
| `TvM3uFile.downloadEpg()` | A self-signed XMLTV/EPG endpoint can no longer refresh. |
| Stremio `ProjectHttpTransport` | HTTPS manifest/catalog/meta/stream/subtitle/config requests to a self-hosted Stremio addon can fail certificate validation. Existing `allowLan` and `allowCleartext` consent flags do not grant self-signed trust. |
| `BitmapCache` and `FermataContentProvider` | Artwork on self-signed HTTPS origins can fail to load/materialize. |
| `Utils.createDownloader()` consumers | Model/package downloads through the project downloader now require a platform-trusted chain. Current built-in public endpoints should satisfy this; custom/replaced endpoints might not. |
| `ChatGpt` | Uses the same client stack, but its fixed public OpenAI endpoint should have a platform-trusted certificate. |

Actual media playback may use VLC, ExoPlayer, WebView, or platform networking instead of
this stack. Therefore the trust change does not uniformly govern every media segment,
but it can block playlist, EPG, Stremio metadata, subtitle, or artwork acquisition before
playback begins.

## Additional security finding

The new client context validates certificate chains, but
`createClientSslEngine(peerHost, peerPort)` does not set the SSL endpoint-identification
algorithm to `HTTPS`. The repository therefore does not explicitly enable hostname
verification for this `SSLEngine` path. A follow-up TLS design must cover both chain trust
and hostname identity; simply retaining the current context is not a complete HTTPS
hardening story.

## Recommended follow-up

Do not restore a global trust-all manager. Keep strict validation as the default and add
an explicit per-source TLS policy for the user-configurable M3U/EPG and Stremio sources.
The safer compatibility mode is certificate fingerprint approval/pinning (TOFU with a
visible fingerprint), not silently accepting any certificate. At minimum the design must:

- scope approval to one source/origin and never propagate it across redirects;
- include TLS policy/pin identity in `HttpConnection`'s connection-cache key so strict and
  approved connections cannot be reused across policies;
- preserve hostname verification for normal platform-trusted certificates;
- test strict rejection, approved-pin acceptance, wrong/rotated certificate rejection,
  redirect isolation, and connection-cache isolation;
- expose a clear warning wherever the user approves a self-signed certificate.

Until that decision is implemented or the compatibility break is explicitly accepted,
the lint gate is operationally complete but the TLS behavior change should not be called
fully closed.

## Hosted-runner delivery evidence

- Commit: `b4d5dcb6cd22cbae29ad6d2eedc2fa5f027514df`
- Push target: `origin/main`
- GitHub Actions run: <https://github.com/chuoinho/FermataX/actions/runs/30828588995>
- Workflow job: `Verify Mobile, Auto, lint, hotspot, and whitespace`
- Run status/conclusion: `completed / success`
- Run time: 2026-08-03 15:40:19Z to 15:46:31Z

The hosted job recorded every relevant step as successful:

1. Mobile unit suite
2. Auto unit suite
3. Architecture hotspot guard
4. Lint — the workflow now invokes both `:fermata:lintMobileDebug` and
   `:fermata:lintAutoDebug` explicitly
5. Pull/push diff whitespace check

The isolated local snapshot of the same commit also reported zero lint errors for both
variants (352 warnings Auto and 351 warnings Mobile). Warnings remain visible; no lint
baseline was introduced.

## Scoped trust-policy follow-up

### Product decision

The compatibility exception is now deliberate and limited to three user-configured
source families: IPTV/M3U playlist downloads, XMLTV/EPG downloads, and Stremio HTTP
requests (manifest, catalog, metadata, stream/subtitle metadata, and configuration).
These requests use `TRUST_ALL_USER_SOURCE`; all other traffic defaults to `STRICT`.
This preserves compatibility with self-hosted sources using self-signed certificates
while removing the former application-wide trust-all behavior.

`STRICT` uses the platform trust store and explicitly enables HTTPS endpoint
identification on every client `SSLEngine`. Thus both certificate-chain validation and
hostname verification apply to ChatGPT, Whisper/OpusMT model downloads, generic
downloaders, artwork, and every other unmarked HTTPS request.

### Authoritative call-site mapping

| Traffic | Concrete entry path | Policy |
| --- | --- | --- |
| M3U playlist | `M3uFileSystem.load()` -> `Utils.createUserSourceDownloader()` -> `HttpFileDownloader` -> `HttpConnection` | `TRUST_ALL_USER_SOURCE` for the configured origin |
| XMLTV/EPG | `TvM3uFile.downloadEpg()` -> `HttpFileDownloader.forUserSource()` -> `HttpConnection` | `TRUST_ALL_USER_SOURCE` for the configured origin |
| Stremio | `StremioRuntimeFactory`/`LifecycleHttpTransport` -> `StremioHttpClient.execute()` -> `ProjectHttpTransport.execute()` -> `HttpConnection` | Explicit policy on each `TransportRequest` |
| ChatGPT | `ChatGpt.sendRequest()` -> `HttpConnection.connect()` | Explicit `STRICT` |
| Whisper | `Whisper` -> `Utils.createStrictDownloader()` -> `HttpFileDownloader` | `STRICT` |
| OpusMT | `OpusMtTranslateAddon` -> `Utils.createStrictDownloader()` -> `HttpFileDownloader` | `STRICT` |
| Artwork | `BitmapCache` -> default `HttpFileDownloader`; `FermataContentProvider` -> `HttpConnection` | `STRICT` |

`BitmapCache` and `FermataContentProvider` remain strict. They are shared artwork paths
and do not carry trustworthy provenance tying an image URL to a configured IPTV or
Stremio source. Automatically applying trust-all based only on the artwork URL would
expand the exception beyond the three authorized source families. Supporting
self-signed artwork later requires explicit source-origin provenance, not URL guessing.

### Redirect and isolation rules

The trust exception is scoped to the original logical request origin (scheme, host, and
effective port). A same-origin redirect retains `TRUST_ALL_USER_SOURCE`; every
cross-origin redirect becomes `STRICT`, even if another independently configured source
uses that destination. This avoids one source granting TLS trust to another request.
Stremio applies this rule before constructing each redirected `TransportRequest`.
`HttpConnection` recalculates the rule after every M3U/EPG redirect, including cached
permanent redirects.

The `HttpConnection` pool/cache key now contains `TlsTrustPolicy`, so strict and
trust-all connections are never shared for the same host and port. Policy is carried as
an explicit connection option; there is no global mutable selector and no `ThreadLocal`.
The two SSL contexts are immutable holders, while each connection independently selects
which context it is authorized to use.

### Verification coverage

- A real local TLS handshake proves `STRICT` rejects the generated self-signed server.
- A real local TLS handshake with a chain explicitly trusted by the test proves
  `STRICT` still rejects the wrong hostname. This exercises HTTPS endpoint
  identification rather than merely inspecting an enum or SSL parameter.
- A real local TLS handshake proves `TRUST_ALL_USER_SOURCE` accepts the self-signed
  server.
- Unit tests prove source-origin/same-origin redirect retention, cross-origin downgrade
  to strict, and connection-cache identity separation by policy.
- Downloader tests prove the default/fixed model-download path is strict and the
  user-source factory is the only downloader path that authorizes trust-all.
- Stremio tests prove the initial and same-origin redirected requests carry trust-all,
  while a cross-origin redirect carries strict and strips sensitive headers.

### Explicitly not done

No user-facing warning was added in this change. A future source editor should warn that
IPTV/EPG/Stremio compatibility mode accepts any certificate for the configured origin
and therefore does not protect that source against certificate impersonation. A
fingerprint approval/pinning UI remains the preferred long-term replacement.

### Scoped-policy hosted-runner evidence

- Implementation and test commit: `9fae0fe6c68a1f5294ddcc0fed64dcac4b831eaa`
- Push target: `origin/main`
- GitHub Actions run: <https://github.com/chuoinho/FermataX/actions/runs/30832277716>
- Job: `Verify Mobile, Auto, lint, hotspot, and whitespace`
- Result: `success` in 6m 4s

The hosted job completed the Mobile unit suite, Auto unit suite, architecture hotspot
guard, both explicit lint variants, and push-diff whitespace check successfully for the
exact scoped-policy implementation commit.
