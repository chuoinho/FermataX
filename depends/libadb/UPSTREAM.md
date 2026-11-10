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

## Conscrypt (TLS for pairing and the ADB connection)

No vendored Java file changed for this. Stock Android 13-15 blocks the platform Conscrypt's hidden
`exportKeyingMaterial`, which the pairing key exchange needs, so Auto bundles
`org.conscrypt:conscrypt-android:2.8.0` (Maven Central; Apache-2.0, Copyright 2016 The Android Open Source
Project), as upstream libadb documents. `SslUtils` (patched above; its provider choice is upstream code) creates
every session's `SSLContext`, for pairing and for the loopback ADB connection, from its
`org.conscrypt.OpenSSLProvider`, and `PairingConnectionCtx` calls its `org.conscrypt.Conscrypt`; the provider is
not installed process-wide, so the rest of the app's TLS is unaffected, and no `hidden_api_policy` or reflection
exemption is used. The AAR's consumer rules keep `org.conscrypt.**`. It adds native `libconscrypt_jni.so`
(containing BoringSSL) for arm64-v8a, armeabi-v7a, x86 and x86_64, all with 16 KiB PT_LOAD
alignment. Mobile does not include it. JVM unit tests run on Robolectric's `conscrypt-openjdk-uber`
(the Android artifact is excluded from unit-test classpaths: its JNI library cannot load on a JVM).

Licence texts packaged in Auto's About screen, copied unmodified into `third-party-licenses/`
(downloaded 2026-11-10; SHA-256):

- `conscrypt-2.8.0-LICENSE.txt` (Apache-2.0, plus the Chromium BSD and NIST terms of its generated/test files),
  https://raw.githubusercontent.com/google/conscrypt/2.8.0/LICENSE,
  `eebc1e08d5edeea3b0866243385c0ec20bda335a1e4adaed3874525ede3d6e0c`
- `conscrypt-2.8.0-NOTICE.txt` (Netty and Apache Harmony portions),
  https://raw.githubusercontent.com/google/conscrypt/2.8.0/NOTICE,
  `378a858f72823759ec0cff19b32e70b7d355dbf8f5810df484dc5e3d60ea8dbd`
- `conscrypt-2.8.0-LICENSE.netty.txt` and `conscrypt-2.8.0-LICENSE.harmony.txt` (Apache-2.0, byte-identical,
  so About shows the text once), https://raw.githubusercontent.com/google/conscrypt/2.8.0/licenses/LICENSE.netty.txt
  and .../licenses/LICENSE.harmony.txt, both
  `58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd`
- `boringssl-main-LICENSE.txt` (current BoringSSL: Apache-2.0),
  https://raw.githubusercontent.com/google/boringssl/main/LICENSE,
  `827c8d8fc207c2392794eef9e00fe246f9f61fdcc132556c275be3dd8c3cd97f`
- `boringssl-fips-20220613-LICENSE.txt` (earlier BoringSSL: OpenSSL License, original SSLeay License, ISC, and MIT
  for third_party/fiat), https://raw.githubusercontent.com/google/boringssl/fips-20220613/LICENSE,
  `60bd7c54856bf9387221bde5ab55d516d7cea15870d0fed69406bcd1c8ec7c9d`

The BoringSSL revision built into conscrypt-android 2.8.0 is not known, so both BoringSSL licence texts are
packaged. Not packaged: the LICENSE file of that exact BoringSSL revision, and licences of BoringSSL
`third_party/` code other than those named in these two texts. The AAR's own `META-INF/LICENSE`/`NOTICE` are
excluded from the APK by `packagingOptions`; the copies above replace them.

The About page (`fermata/src/auto/res/raw/phone_apps_licenses.html`) is generated from these files, `COPYING`,
`LICENSES/`, this file and the native headers in `third-party-sources/`: run
`powershell -ExecutionPolicy Bypass -File depends/libadb/package-legal.ps1` after changing any of them. The script
is saved as UTF-8 with BOM; without it Windows PowerShell 5.1 would corrupt non-ASCII author names.

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
