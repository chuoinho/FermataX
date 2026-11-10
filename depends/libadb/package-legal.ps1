# Generates fermata/src/auto/res/raw/phone_apps_licenses.html (Auto About: Phone Apps licences) from the texts in
# this repository. Saved as UTF-8 with BOM so Windows PowerShell 5.1 keeps non-ASCII names intact.
# Run from anywhere: powershell -ExecutionPolicy Bypass -File depends/libadb/package-legal.ps1
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$builder = [Text.StringBuilder]::new()
[void]$builder.Append('<h2>Phone Apps: open source licenses</h2>')
[void]$builder.Append('<p>LibADB Android 3.1.1 by Muntashir Al-Islam. GPL-3.0-or-later OR Apache-2.0; BSD-3-Clause and MIT portions. Copyright 2013 Cameron Gutman; 2013 Google Inc.; 2020 Sam Palmer; 2020 南宫雪珊; 2022 Muntashir Al-Islam.</p>')
[void]$builder.Append('<p>SPAKE2 Android 2.2.1: Copyright 2021 Muntashir Al-Islam, LGPL-3.0. Native spake2-c at 0d15933e5ba3e662cb01245a7ac0dc9fca3eac31: LGPL-3.0-or-later; MIT/Apache-2.0 portions; glibc SHA512 LGPL-2.1-or-later.</p>')
[void]$builder.Append('<p>Conscrypt for Android 2.8.0 (org.conscrypt:conscrypt-android, every TLS session of the Phone Apps ADB: pairing and connection): Copyright 2016 The Android Open Source Project, Apache-2.0. Its LICENSE and NOTICE are reproduced below, and its licenses/LICENSE.netty.txt and LICENSE.harmony.txt (byte-identical Apache-2.0 texts, shown once). Its native library contains BoringSSL: Copyright Google Inc., Eric Young, The OpenSSL Project. The BoringSSL revision built into conscrypt-android 2.8.0 is not known, so both BoringSSL licence texts are reproduced: the current one (Apache-2.0) and the earlier one (OpenSSL License, original SSLeay License, ISC, MIT for third_party/fiat). Not reproduced: that exact revision&#39;s own LICENSE file, and licences of BoringSSL third_party code not named in these texts.</p>')
[void]$builder.Append('<p>Bouncy Castle bcprov/bcpkix/bcutil jdk18on 1.84: Bouncy Castle license (MIT-style). AndroidX annotation 1.10.0, existing Kotlin stdlib 2.2.10 and JetBrains annotations 23.0.0: Apache-2.0.</p>')
$paths = @('depends/libadb/UPSTREAM.md', 'depends/libadb/COPYING', 'LICENSE', 'depends/libadb/LICENSES/Apache-2.0', 'depends/libadb/LICENSES/BSD-3-Clause', 'depends/libadb/LICENSES/MIT', 'depends/libadb/third-party-licenses/LGPL-3.0.txt', 'depends/libadb/third-party-licenses/LGPL-2.1.txt', 'depends/libadb/third-party-licenses/Bouncy-Castle-1.84.html', 'depends/libadb/third-party-licenses/conscrypt-2.8.0-LICENSE.txt', 'depends/libadb/third-party-licenses/conscrypt-2.8.0-NOTICE.txt', 'depends/libadb/third-party-licenses/conscrypt-2.8.0-LICENSE.netty.txt', 'depends/libadb/third-party-licenses/boringssl-main-LICENSE.txt', 'depends/libadb/third-party-licenses/boringssl-fips-20220613-LICENSE.txt')
foreach ($path in $paths) {
    $content = [IO.File]::ReadAllText((Join-Path $root $path))
    if ($path.EndsWith('.html')) { $content = $content -replace '<[^>]+>', '' }
    [void]$builder.Append('<h3>' + [Net.WebUtility]::HtmlEncode($path) + '</h3><p>')
    [void]$builder.Append(([Net.WebUtility]::HtmlEncode($content) -replace '\r?\n', '<br>'))
    [void]$builder.Append('</p>')
}
$commit = 'spake2-c-0d15933e5ba3e662cb01245a7ac0dc9fca3eac31'
$zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $root "depends/libadb/third-party-sources/$commit.zip"))
foreach ($name in @('spake2.c','ed25519.h','curve25519_32.h','sha512.c','include/spake2/spake2.h')) {
    $reader = [IO.StreamReader]::new($zip.GetEntry("$commit/$name").Open(), [Text.Encoding]::UTF8)
    $content = $reader.ReadToEnd()
    $reader.Dispose()
    $header = [regex]::Match($content, '(?s)/\*.*?\*/').Value
    [void]$builder.Append('<h3>Native ' + [Net.WebUtility]::HtmlEncode($name) + '</h3><p>')
    [void]$builder.Append(([Net.WebUtility]::HtmlEncode($header) -replace '\r?\n', '<br>'))
    [void]$builder.Append('</p>')
}
$zip.Dispose()
[IO.File]::WriteAllText((Join-Path $root 'fermata/src/auto/res/raw/phone_apps_licenses.html'), $builder.ToString())
