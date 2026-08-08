# FermataX Demo TV

This package creates three local, rights-safe TV channels for FermataX promotional recording:

- `FermataX Showcase`
- `Scenic Drive`
- `Feature Highlights`

The media is generated from FermataX-owned branding and an original road image. It does not include third-party TV, YouTube or Stremio content.

## Build

From PowerShell:

```powershell
cd E:\Chatgpt\fermata\promo\tv-demo
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-demo.ps1
```

The default output is 1280x720 H.264/AAC HLS with 36-second channel loops.

## Start

Connect the recording phone over ADB, then run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-demo.ps1
```

Add the following M3U source in FermataX TV:

```text
http://127.0.0.1:8765/fermatax-demo.m3u
```

`adb reverse` routes this phone-local address to the HTTP server on the development computer.

## Stop

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-demo.ps1
```

## Recording Notes

- Start the demo server before opening the TV source.
- Open every channel once before recording to warm local caches.
- Play `FermataX Showcase` before returning to Dashboard so SmartTopCard contains only demo metadata.
- Keep YouTube and Stremio visible only as Dashboard/navigation icons.
- Do not show source URLs, device identifiers or personal account data in the final clip.
