# Phase 8E: Final Reconciliation And Release Smoke

## Result

**RELEASE READINESS: PARTIAL.** Web-only packaging and the physical universal
APK smoke pass; hosted capability and external transport gaps remain non-PASS.

## Evidence

- `:web:testMobileDebugUnitTest` and
  `:fermata:packageAutoReleaseUniversalApk` completed with `WEB_STREMIO=true`.
  The Mobile unit-test XML set contains 40 reports with zero failures.
- The universal APK signer SHA-256 is
  `A86D576FF1EC0E3245F5A6152C5D8D66B5DF8BB510820D75DC61110B19C3AEB4`.
- `fermata/lib/auto/aauto.aar` remains SHA-256
  `99337C3B591AC9670C12B508DA38886AEDBA61DD494F39F5F166F02580EC584B`.
- The signed universal APK installed on `15c36230`, launched FermataX and
  opened hosted `https://web.stremio.com/#/addons` without crash or ANR.
- The thin `auto-release.apk` surfaced Android Play SplitInstall
  `APP_NOT_OWNED` on this sideload device. It is not the sideload artifact;
  reinstalling the generated universal APK restored hosted Stremio entry.

## Final Matrix

| Boundary | State |
| --- | --- |
| Direct MP4/HLS, seek, subtitles, fullscreen/Back | PASS |
| Active control-only MediaSession claim | PASS (P8A) |
| Hosted audio-track selector | NOT OBSERVED (P8B reached a dual-audio HLS Player but no selector was rendered) |
| Episode `nexttrack` | CONDITIONAL_NOT_ADVERTISED (P8C reached Episode 1 Player but its native session did not register the action) |
| DHU/vehicle controls | PARTIAL (DHU transport connected but Android Auto remained `Waiting for phone`; no host input was delivered) |
| Streaming-server/torrent transport | BLOCKED (P8D; local-only retry did not pass server safety or metadata-transfer preflight) |
| Fermata native torrent/player | NOT APPLICABLE |

No phase listener remains on `7000`, `7001`, `7002`, `11470`, `42000`, `18080`
or `18081`; no phase reverse mapping remains. The seven-addon baseline contains
no P8 fixture. Existing forwards `9223` and `5277` remain intact. Fixture
processes and DHU are stopped; Temp directories are inert where deletion is
blocked by the execution environment.

Production LOC: `0`.

Test LOC: `0`.
