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
| Hosted audio-track selector | NOT OBSERVED (P8B) |
| Episode `nexttrack` | NOT OBSERVED (P8C) |
| DHU/vehicle controls | PARTIAL |
| Streaming-server/torrent transport | BLOCKED (P8D) |
| Fermata native torrent/player | NOT APPLICABLE |

No phase listener remains on `7000`, `7001`, `7002`, `11470`, `42000`, `18080`
or `18081`; no phase reverse mapping remains. The seven-addon baseline contains
no P8 fixture. Existing forwards `9223` and `5277` remain intact. Fixture
processes are stopped; Temp directories are inert where deletion is blocked.

Production LOC: `0`.

Test LOC: `0`.
