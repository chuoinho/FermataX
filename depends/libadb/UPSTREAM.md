# LibADB Android: pinned original source

This module contains all 19 original production Java files and the manifest from
LibADB Android **3.1.1**, commit `c849886ebc6d48e7b46d967e78a6bb65c90c3b74`:
https://github.com/MuntashirAkon/libadb-android/tree/3.1.1/libadb/src/main

Original archive SHA-256:
`8cdb9b909991ab96da211b293175357345c9d727a22221f36b8d573da7bb1de8`.
Original `COPYING`, `LICENSES/`, copyright and SPDX headers are retained.
The GPL-3.0-or-later option is used with FermataX's GPL-3.0 distribution;
Apache-2.0, BSD-3-Clause and MIT texts/notices are also preserved.

FermataX modifications, 2026-11-09, are limited to these five Java files:

- `AbsAdbConnectionManager`: protected transport factories permit caller-owned sockets;
  omit credential-related exception stack traces from the unused default close path.
- `AdbStream`: `read()` returns EOF (-1) once the peer has closed the stream and its data is
  drained, instead of throwing "Stream closed."; only a local/transport close still throws.
- `AdbConnection`: accept an externally owned socket, bounded socket connect/read,
  close/interrupt without an unbounded thread join, generic transport failure log.
- `PairingConnectionCtx`: accept an externally owned socket, bounded connect/TLS/read,
  null-safe/idempotent cleanup before or after partial initialization, omit peer-info logging,
  throw `AdbAuthenticationFailedException` when peer info fails authenticated decryption (wrong code).
- `SslUtils`: create the TLS context with the current identity for every session,
  rather than retaining the first identity in a static context; drop the stdout provider line.

The ADB/pairing protocol is unchanged. The manager's existing locks remain in place;
LocalAdb's deadline owns and closes the raw socket without acquiring those locks.
No OPEN notification race change has been made without a reproducing regression.
`UPSTREAM.patch` records the complete five-file delta; `SOURCE_MANIFEST.json`
records original/current SHA-256 values for all 19 Java files.

Only Auto depends on this module. No binary libadb dependency is also included.
SPAKE2 Android is pinned to its published JitPack coordinate
`com.github.MuntashirAkon.spake2-java:spake2-android:2.2.1`.
The repository filter permits only that exact group. Existing Bouncy Castle
`bcprov-jdk18on`, `bcpkix-jdk18on`, `bcutil-jdk18on` **1.84** are reused;
the upstream `bcprov-jdk15to18:1.81` binary is not added.
AndroidX annotations resolve to the existing **1.10.0** catalog version.
The existing graph's Kotlin stdlib **2.2.10** and JetBrains annotations **23.0.0**
are not new dependencies; Apache-2.0 notices apply to these and AndroidX.

## Corresponding source and recombination (LGPL)

The original SPAKE2 Java source archive and the exact native submodule source are
included in `third-party-sources/`, not downloaded from mutable branches at build time:

- `spake2-2.2.1.zip`: https://github.com/MuntashirAkon/spake2-java/tree/2.2.1
- `spake2-c-0d15933e5ba3e662cb01245a7ac0dc9fca3eac31.zip`:
  https://github.com/MuntashirAkon/spake2-c/tree/0d15933e5ba3e662cb01245a7ac0dc9fca3eac31

Archive SHA-256 values, respectively:
`c3f46fe17d5078613ab319d431fcb7a03b88b64d7cd377f6c0ed14f925053e53`,
`0f1ae5da428e4f8ddea49e0e33dcfe07d2ac2114443793b8508bdc4dd48e1eec`.

SPAKE2 Java is LGPL-3.0; native SPAKE2 is LGPL-3.0-or-later, with glibc SHA512
LGPL-2.1-or-later and MIT/Apache-2.0 portions. Original headers in the archives
retain Google, Muntashir Al-Islam, fiat-crypto and FSF notices. Full license texts
and these notices are also packaged in Auto's existing About screen.

To modify/rebuild the library, extract the Java archive and extract the C archive
into its `android/src/main/cpp/spake2-c` directory (the ZIP lacks git submodules).
Use the included upstream Gradle wrapper and Android SDK/NDK toolchain to build
the Android release AAR. The upstream Java archive's `android/build.gradle`
declares version 2.2.0 despite tag/publication 2.2.1; use the pinned tag contents.
Replace the `spake2Android` dependency in this module with the resulting AAR,
then build FermataX from its complete source using the repository's build instructions
and your own signing key. No original signing key is needed to install a separately
signed build after removing the installed original (which removes its private data).
The dependency contains native `libspake2.so` for arm64-v8a, armeabi-v7a, x86 and
x86_64; the published 2.2.1 AAR has 16 KiB PT_LOAD alignment for all four.

**Release distribution obligations remain:** accompany a distributed binary with
the GPL/LGPL license texts, copyright notices, and complete corresponding source
for the exact app/library build (including patches, dependency sources and build
scripts), in a form allowing users to rebuild/recombine with modified LGPL code.
Supply installation information when required by GPL/LGPL section 6/4(e), and do
not restrict reverse engineering for debugging library modifications. Public
upstream links and this development checkout alone are not a claim that a future
binary distribution's source-delivery obligations have already been fulfilled.
No binary release is being distributed by this Task2 change.
