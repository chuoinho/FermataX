# Third Party Notices

This file records included Phone Apps components and external Xtream Codes references.

The Xtream reference entries below describe preparatory research rather than copied code. If future Xtream work ports implementation code, update those entries with the copied components, copyright notices, and required license text.

## Project License

Fermata is licensed under GPL-3.0. See `LICENSE`.

## Phone Apps: local ADB (Auto only)

- LibADB Android 3.1.1, commit `c849886ebc6d48e7b46d967e78a6bb65c90c3b74`:
  original production Java source is in `depends/libadb`, under
  GPL-3.0-or-later OR Apache-2.0, with BSD-3-Clause/MIT portions.
  Original SPDX/copyright headers, `COPYING` and all `LICENSES` are preserved.
- SPAKE2 Android 2.2.1, Copyright 2021 Muntashir Al-Islam, LGPL-3.0;
  native submodule `0d15933e5ba3e662cb01245a7ac0dc9fca3eac31`, LGPL-3.0-or-later,
  includes LGPL-2.1-or-later glibc SHA512 and MIT/Apache-2.0 portions.
  Exact original source archives and full LGPL texts accompany this source tree.
- Existing Bouncy Castle jdk18on provider/PKIX/util 1.84 retain their MIT-style
  license; AndroidX annotation 1.10.0, existing Kotlin stdlib 2.2.10 and JetBrains
  annotations 23.0.0 are Apache-2.0. No duplicate jdk15to18 provider is included.
- Conscrypt for Android 2.8.0 (`org.conscrypt:conscrypt-android`), Copyright 2016 The Android Open Source
  Project, Apache-2.0, with its native BoringSSL (Copyright Google Inc., Eric Young, The OpenSSL Project).
  Used for every TLS session of the app's own ADB (pairing and the loopback connection); not in the Mobile
  flavor. Conscrypt's LICENSE, NOTICE and Netty/Harmony licence texts are packaged. The BoringSSL revision it
  contains is not known, so both BoringSSL licence texts are packaged (current: Apache-2.0; earlier: OpenSSL,
  original SSLeay, ISC and MIT); the exact revision's own LICENSE is not. Sources and SHA-256 are in UPSTREAM.md.
- Preserved credits include Muntashir Al-Islam, Cameron Gutman, Google Inc.,
  Sam Palmer, 南宫雪珊, the fiat-crypto authors and the Free Software Foundation.

See [`depends/libadb/UPSTREAM.md`](depends/libadb/UPSTREAM.md) for exact sources,
the five-file lifecycle/credential patch, redistribution obligations and instructions
for rebuilding/recombining with modified LGPL code. The notices, the license texts in
`depends/libadb/LICENSES` and `depends/libadb/third-party-licenses`, and source provenance
are packaged in Auto's existing About screen. A future binary
release must supply its complete corresponding app/dependency source and required
installation information; these notices do not claim that future distribution is
already compliant.

## Xtream References

### zGrav/xtream-iptv-player

- URL: https://github.com/zGrav/xtream-iptv-player
- License observed on 2026-06-28: AGPL-3.0
- Planned use: high-level reference for Xtream Codes endpoint flow and response fields.
- Notice: do not copy implementation code from this project without a specific AGPL/GPL compatibility review.

### ahXN00/OwnTV

- URL: https://github.com/ahXN00/OwnTV
- License observed on 2026-06-28: MIT
- Planned use: reference for streaming, low-memory parsing techniques around large IPTV/Xtream datasets.
- Notice: if implementation code is ported from this project, preserve the MIT copyright and permission notice.

### zaclimon/xipl

- URL: https://github.com/zaclimon/xipl
- Wiki: https://github.com/zaclimon/xipl/wiki
- License observed on 2026-06-28: Apache-2.0
- Planned use: reference documentation for Xtream Codes API endpoints and stream output formats.
- Notice: if implementation code or documentation text is copied from this project, preserve the required Apache-2.0 notices.

## Upstream Discussions

- Fermata discussion #434: https://github.com/AndreyPavlenko/Fermata/discussions/434
- Fermata discussion #727: https://github.com/AndreyPavlenko/Fermata/discussions/727

These discussions were reviewed for prior Xtream work and potential upstream coordination. Discussion #727 mentions an external Fermata Xtream mod, but that discussion does not provide source code to reuse.

