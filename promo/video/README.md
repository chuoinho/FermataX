# FermataX Introduction Video

This folder contains rights-safe DHU captures and a reproducible FFmpeg build for the FermataX introduction video.

## Build

```powershell
cd E:\Chatgpt\fermata\promo\video
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-promo.ps1
```

The final video is written to:

```text
E:\Chatgpt\fermata\dist\promo\FermataX-introduction-v300.mp4
```

The video is 1920x1080 H.264/AAC and uses only FermataX-owned branding, original demo TV media, DHU captures and locally generated ambient audio.
