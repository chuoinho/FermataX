<div align="center">
  <img src="extracted_logo/fermatax_app_logo.png" width="120" alt="FermataX logo">

  <h1>FermataX</h1>

  <p>
    A free, open source audio, video, IPTV, radio, and web media player
    customized as a car-friendly media hub for Android Auto.
  </p>

  <p>
    <img alt="Android" src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white">
    <img alt="Android Auto" src="https://img.shields.io/badge/Android%20Auto-supported-4285F4?logo=androidauto&logoColor=white">
    <img alt="Open Source" src="https://img.shields.io/badge/open%20source-yes-brightgreen">
    <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-blue">
  </p>
</div>

> [!IMPORTANT]
> FermataX is free and open source. If you paid for this app from an unofficial website or seller, you may have been scammed.

## About

If you love [Fermata](https://github.com/AndreyPavlenko/Fermata), welcome to **FermataX**: a free, open source audio, video, TV player,... with a simple and intuitive interface.

FermataX is a fork of Fermata, customized as a media hub for Android Auto. It brings local media, IPTV/Xtream, Internet Radio, YouTube/Web media, Favorites, Recent, and playlists together in one car-friendly experience.

## Highlights

- **Dashboard-first navigation** for quick access on Android Auto screens.
- **Customizable Dashboard and bottom navigation**: rearrange cards and nav items to match your habits.
- **Xtream Codes support** with Live TV, Movies, and Series.
- **IPTV source support** for M3U playlists, XMLTV EPG, and Catchup.
- **Watch from beginning** and Catchup support for compatible Xtream sources.
- **Internet Radio** with popular stations, top voted stations, countries, and tags.
- **More media services** access inside the same media hub.
- **Favorites, Recent, playlists, and local folders** available from the main screen.
- **Car-friendly UI** designed for fast navigation and repeated daily use.

## Screenshots

| Dashboard | IPTV Sources |
| --- | --- |
| <img src="product_screenshots/Dashboard.png" alt="FermataX dashboard"> | <img src="product_screenshots/iptv.png" alt="FermataX IPTV source picker"> |

| Podcasts | Internet Radio |
| --- | --- |
| <img src="product_screenshots/podcasts.png" alt="FermataX podcasts screen"> | <img src="product_screenshots/radio.png" alt="FermataX radio screen"> |

## Disclaimer

FermataX is a media player only. It does not provide, host, sell, or distribute any media content, playlists, TV channels, IPTV services, or Xtream accounts.

Users are responsible for the sources they add.

## Building the project

### Requirements

- Install the latest Android SDK or Android Studio from https://developer.android.com/studio/
- Set `ANDROID_SDK_ROOT` to your Android SDK directory.

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
```

On Windows PowerShell:

```powershell
$env:ANDROID_SDK_ROOT="C:\Users\<user>\AppData\Local\Android\Sdk"
```

### Clone

```bash
git clone https://github.com/chuoinho/FermataX.git
cd FermataX
```

### Build AAB

Release builds require all four signing values in `local.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or through the matching
`FERMATAX_*` environment variables. The build fails instead of generating an unsigned
or unapproved release artifact when signing is missing or invalid.

```bash
./gradlew :fermata:bundleAutoRelease
```

Output:

```text
fermata/build/outputs/bundle/autoRelease/
```

### Build Universal APK

```bash
./gradlew :fermata:packageAutoReleaseUniversalApk
```

Output:

```text
fermata/build/outputs/apk_from_bundle/autoRelease/
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.


## Donate

If you enjoy this app or want to try my app installation service, feel free to buy me a coffee:

<p>
  <a href="https://ko-fi.com/fermatax">
    <img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support FermataX on Ko-fi">
  </a>
</p>

## Credits

FermataX is based on [Fermata Media Player](https://github.com/AndreyPavlenko/Fermata) by Andrey Pavlenko.

## License

This project follows the upstream Fermata license. Please respect the original license and attribution requirements.
