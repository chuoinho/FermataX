# In-App TikTok Web Addon Design Specification

**Author:** Antigravity  
**Date:** 2026-10-02  
**Status:** Approved  
**Target:** `modules/web` in FermataX (`chuoinho/FermataX`)

---

## 1. Executive Summary & Intent

This specification defines the architectural design for porting, adapting, and integrating the complete **TikTok Web Addon** from `com.carstream.tech (4).apk` and the proven automotive scripts `tt_clean.js` & `tt_nav.js` into FermataX's modular web ecosystem (`modules/web`).

Unlike previous attempts with DRM-heavy services, TikTok Web uses standard HTML5 MP4/H.264 video, has no Widevine L1 encryption requirement, does not enforce Google OAuth embedded webview blocks, and features battle-tested automotive touch and navigation adaptations (`tt_clean.js` and `tt_nav.js`) specifically engineered and measured on automotive headunits (DHU Honda CR-V 860x530 viewport).

---

## 2. Source Assets & References

1. **APK Source**: `C:\Users\ttanh\Downloads\com.carstream.tech (4).apk`
   - Vector icon: `res/drawable/ic_app_tiktok.xml` (Official 48dp TikTok 3D-offset logo: Cyan `#25F4EE` + Magenta `#FE2C55` on black background).
   - Core entry URLs: `https://www.tiktok.com/foryou`, `https://www.tiktok.com/login`.
   - Core auth cookies: `sessionid`, `sessionid_ss`, `sid_tt`.
2. **Production Scripts**:
   - `C:\Users\ttanh\OneDrive\Desktop\vault2_scripts\js\tt_clean.js` (435 lines, 19.9 KB):
     - Edge-to-edge full-bleed video formatting (`100vh`, `object-fit: contain`, black background).
     - Removal of desktop sidebars, headers, banners, popups, and interaction clutter (like, comment, share, bookmark, follow).
     - Repositioning of video description and nickname to bottom without obstructing playback.
     - Protection of Photo Mode Swiper multi-image slides.
   - `C:\Users\ttanh\OneDrive\Desktop\vault2_scripts\js\tt_nav.js` (389 lines, 14.1 KB):
     - 5-tier resilient next/prev video navigation (Hover + Arrow click, synchronous `scrollTop` stepping, `scrollIntoView`, `WheelEvent`, `KeyboardEvent`).
     - Exposed bridge APIs: `window.__cntNext()`, `window.__cntPrev()`, `window.__cntVideo()`, `window.__cntTitle()`.
   - `C:\Users\ttanh\OneDrive\Desktop\vault2_scripts\js\playstate.js` (248 lines):
     - Playback reporting to `window.CntBridge.setPlaying(boolean)` and `window.CntBridge.webLog(String)`.

---

## 3. Architecture & Integration with FermataX

The TikTok addon is structured as a first-class citizen inside `modules/web`, following the exact pattern of YouTube (`me.aap.fermata.addon.web.yt`):

```
modules/web/
├── build.gradle (registers TIKTOK addon descriptor)
└── src/main/
    ├── java/me/aap/fermata/addon/web/tiktok/
    │   ├── TikTokAddon.java               # Lifecycle, preferences, capability registration
    │   ├── TikTokWebFragment.java         # Fragment shell extending WebBrowserFragment
    │   ├── TikTokWebView.java             # Specialized WebView extending FermataWebView
    │   ├── TikTokWebClient.java           # WebViewClient routing and injection trigger
    │   ├── TikTokWebSessionPolicy.java    # Route trust, login whitelist, back-navigation hierarchy
    │   ├── TikTokScripts.java             # Static asset holder for tt_clean.js and tt_nav.js
    │   ├── TikTokJsBridge.java            # CntBridge implementation (@JavascriptInterface)
    │   ├── TikTokMediaSessionBridge.java  # W3C / MediaSession bridge, steering wheel next/prev
    │   └── TikTokOpenOnCarBridge.java      # URL interception and phone-to-car handoff
    ├── res/
    │   ├── drawable/
    │   │   └── ic_app_tiktok.xml          # Vector icon from CarStream
    │   ├── layout/
    │   │   └── tiktok_browser.xml         # Layout container with AMOLED dark styling
    │   └── values/
    │       └── strings.xml                # Strings: addon_tiktok, tiktok_for_you, etc.
```

---

## 4. Detailed Component Design

### 4.1. Capability Registration & Addon Descriptor
- Add `TIKTOK` to `AddonCapability.java`.
- Update `modules/web/build.gradle` addon registration array to include:
  ```groovy
  [
      name: 'tiktok',
      className: 'me.aap.fermata.addon.web.tiktok.TikTokAddon',
      capability: 'TIKTOK',
      title: '@string/addon_tiktok',
      icon: '@drawable/ic_app_tiktok'
  ]
  ```
- Default URL: `https://www.tiktok.com/foryou`.

### 4.2. Route & Session Policy (`TikTokWebSessionPolicy`)
- **Trust Boundary**:
  - Hosted domains: `www.tiktok.com`, `tiktok.com`, `m.tiktok.com`.
  - Whitelisted authentication routes: `https://www.tiktok.com/login`, `https://www.tiktok.com/signup`.
  - Unbounded or external URLs are rejected or routed via standard external navigation policy.
- **Back Navigation**:
  - Pressing Back on `tiktok.com/foryou` returns to FermataX Dashboard.
  - Pressing Back inside sub-pages (profiles, search, login) navigates back to `/foryou`.

### 4.3. Scripts & DOM Injection Pipeline (`TikTokScripts`)
- Ingest `tt_clean.js` and `tt_nav.js` verbatim from `vault2_scripts`.
- On `onPageFinished` and on initial frame start:
  - Inject `tt_clean.js` to ensure the layout is immediately styled and clean.
  - Inject `tt_nav.js` to initialize the 5-tier navigation controller.
  - Set up a `MutationObserver` check to re-inject styles if TikTok's SPA resets the DOM.

### 4.4. Native Bridge & Steering Wheel Control (`TikTokJsBridge` & `TikTokMediaSessionBridge`)
- Attach `@JavascriptInterface` named `CntBridge`:
  ```java
  public class TikTokJsBridge {
      @JavascriptInterface
      public void setPlaying(boolean playing) { ... }
      @JavascriptInterface
      public void webLog(String msg) { ... }
  }
  ```
- **Steering Wheel Key Events**:
  - `KEYCODE_MEDIA_NEXT` -> Evaluate `window.__cntNext && window.__cntNext();`.
  - `KEYCODE_MEDIA_PREVIOUS` -> Evaluate `window.__cntPrev && window.__cntPrev();`.
  - `KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_MEDIA_PLAY` / `KEYCODE_MEDIA_PAUSE`:
    - Toggle playback via `window.__cntVideo && window.__cntVideo()`.
- **Media Metadata Sync**:
  - Periodically or on navigation, poll `window.__cntTitle()` to update `MediaMetadataCompat`:
    - Title: Video description or hashtag from active item.
    - Artist: "TikTok".
    - Album: "For You".

### 4.5. Multi-Display & Automotive UX Compatibility
- Fullscreen and Split-Screen (Android Auto Coolwalk):
  - Because `tt_clean.js` dynamically uses `100vh` and `%` rather than hardcoded pixel values, the layout automatically fills the split-screen window or full car display cleanly.
  - Background is hardcoded to pure black (`#000000`) for seamless OLED/AMOLED car dashboard integration.
  - Toolbar follows FermataX's standard styling with back button, reload button, and optional full-screen toggle.

---

## 5. Security & Stability Guardrails

1. **No DRM Restrictions**:
   - TikTok Web relies on HTML5 Video. No EME/Widevine L1 hardware decryption needed.
2. **Login Support**:
   - Supports QR Code login from mobile phone and username/password without embedded webview OAuth blocks.
3. **Audio Leases & Focus**:
   - Requests automotive `AUDIOFOCUS_GAIN` on playback.
   - Relinquishes audio focus cleanly when switching to YouTube or radio.
4. **Memory & Lifecycle**:
   - Suspends video rendering when the activity is stopped or covered by native navigation, resuming instantly upon return.
