# TikTok Web Addon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port, adapt, and integrate a complete, in-app TikTok Web Addon into FermataX (`modules/web`) sourced from `com.carstream.tech (4).apk` and the battle-tested automotive scripts `tt_clean.js` & `tt_nav.js`, synchronized with FermataX's UI and architecture.

**Architecture:** A specialized web addon within `modules/web` featuring `TikTokAddon`, `TikTokWebView`, and `TikTokWebFragment`. It injects the proven 435-line `tt_clean.js` layout sanitizer and 389-line `tt_nav.js` 5-tier navigation engine, coupled with a native `CntBridge` (`TikTokJsBridge`) and `TikTokMediaSessionBridge` that routes steering wheel next/prev controls directly to video scrolling and SmartTop media cards.

**Tech Stack:** Java 17, Android WebView, Chromium DOM Manipulation, JavaScript (ES6), Android MediaSessionCompat, Gradle Android plugin.

**Spec:** `docs/superpowers/specs/2026-10-02-tiktok-addon-design.md`

## Global Constraints
- Target Android SDK 34 / Min SDK 23.
- All styles must use relative units (`%`, `vh`) and avoid hardcoded pixel constraints.
- AMOLED Dark background (`#000000` / `#121212`) across all views.
- MediaSession and steering wheel keys must control video without lagging when backgrounded.
- Gradle build task: `:web:testMobileDebugUnitTest` and `:fermata:assembleMobileDebug`.

## Review Focus
1. Video aspect ratio & swiper slides: Multi-image photo mode slides must not be hidden by video element sanitization rules.
2. Steering wheel next/prev: Pressing Next or Prev on car steering wheel must reliably transition to the next/prev video via the 5-tier fallback engine.
3. Audio playback during navigation: Audio must continue playing cleanly when Android Auto maps or overlays are active.
4. Back navigation trap: Pressing Back on the root `/foryou` route must return to FermataX Dashboard without getting trapped in SPA pushState history.
5. Guest & authenticated mode: The app must load and play videos immediately for anonymous users while supporting seamless QR/user login.

---

### Task 1: Addon Resources & Descriptor Registration

**Files:**
- Create: `modules/web/src/main/res/drawable/ic_app_tiktok.xml`
- Modify: `modules/web/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/me/aap/fermata/addon/AddonCapability.java`
- Modify: `modules/web/build.gradle`
- Test: `modules/web/src/test/java/me/aap/fermata/addon/web/tiktok/TikTokAddonRegistrationTest.java`

**Steps:**
- [ ] Extract and create `ic_app_tiktok.xml` with official 3D-offset TikTok vector graphic.
- [ ] Add `addon_tiktok` ("TikTok") and `tiktok_for_you` ("Dành cho bạn") string resources.
- [ ] Add `TIKTOK` enum value to `AddonCapability.java`.
- [ ] Register `TikTokAddon` descriptor in `modules/web/build.gradle`.
- [ ] Write unit test `TikTokAddonRegistrationTest` asserting capability and string identifiers exist.
- [ ] Run test: `cmd /c gradlew :web:testMobileDebugUnitTest --tests "*.TikTokAddonRegistrationTest"`
- [ ] Commit: `feat(web/tiktok): register addon descriptor, capability, and vector icon`

---

### Task 2: Session Policy & Navigation Hierarchy

**Files:**
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokWebSessionPolicy.java`
- Test: `modules/web/src/test/java/me/aap/fermata/addon/web/tiktok/TikTokWebSessionPolicyTest.java`

**Steps:**
- [ ] Define `HOME_URL = "https://www.tiktok.com/foryou"`.
- [ ] Implement `isHostedRoute(String url)` whitelisting `tiktok.com` subdomains and rejecting foreign hosts.
- [ ] Implement `isPersistableRoute(String url)` excluding transient auth endpoints (`/login`, `/signup`).
- [ ] Implement `backTarget(String currentUrl)` returning null or `HOME_URL` to avoid back navigation traps.
- [ ] Write unit test `TikTokWebSessionPolicyTest` verifying entry URL, whitelisting, and back logic.
- [ ] Run test: `cmd /c gradlew :web:testMobileDebugUnitTest --tests "*.TikTokWebSessionPolicyTest"`
- [ ] Commit: `feat(web/tiktok): implement session policy and navigation hierarchy`

---

### Task 3: Script Ingestion & DOM Cleaning Pipeline

**Files:**
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokScripts.java`
- Test: `modules/web/src/test/java/me/aap/fermata/addon/web/tiktok/TikTokScriptsTest.java`

**Steps:**
- [ ] Ingest `tt_clean.js` (435 lines) and `tt_nav.js` (389 lines) verbatim into `TikTokScripts.java`.
- [ ] Provide `injectCleanScript()` and `injectNavScript()` helper methods with `MutationObserver` safety.
- [ ] Provide helper `getScriptSource()` to compile the injection payload cleanly.
- [ ] Write unit test `TikTokScriptsTest` ensuring the full scripts are non-empty and retain core selector tokens (`feed-video`, `__cntNext`, `__cntPrev`, `CntBridge`).
- [ ] Run test: `cmd /c gradlew :web:testMobileDebugUnitTest --tests "*.TikTokScriptsTest"`
- [ ] Commit: `feat(web/tiktok): package automotive layout and navigation scripts`

---

### Task 4: Native JS Bridge & Steering Wheel MediaSession

**Files:**
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokJsBridge.java`
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokMediaSessionBridge.java`
- Test: `modules/web/src/test/java/me/aap/fermata/addon/web/tiktok/TikTokMediaSessionBridgeTest.java`

**Steps:**
- [ ] Implement `TikTokJsBridge` attaching `CntBridge` interface to WebView (`setPlaying`, `webLog`).
- [ ] Implement `TikTokMediaSessionBridge` binding Android `MediaSessionCompat` to TikTok DOM.
- [ ] Implement Next/Prev key hooks: dispatching `window.__cntNext()` and `window.__cntPrev()`.
- [ ] Implement Play/Pause hooks toggling `window.__cntVideo()` state.
- [ ] Implement periodic metadata polling (`window.__cntTitle()`) to update SmartTop Media Card.
- [ ] Write unit test `TikTokMediaSessionBridgeTest` asserting key event translation and metadata handling.
- [ ] Run test: `cmd /c gradlew :web:testMobileDebugUnitTest --tests "*.TikTokMediaSessionBridgeTest"`
- [ ] Commit: `feat(web/tiktok): implement native js bridge and steering wheel controls`

---

### Task 5: UI Shell, WebView & Fragment Implementation

**Files:**
- Create: `modules/web/src/main/res/layout/tiktok_browser.xml`
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokWebView.java`
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokWebClient.java`
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokWebFragment.java`
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokAddon.java`
- Create: `modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokOpenOnCarBridge.java`
- Test: `modules/web/src/test/java/me/aap/fermata/addon/web/tiktok/TikTokWebAddonBindingTest.java`

**Steps:**
- [ ] Create layout `tiktok_browser.xml` with pure black background `#000000` and `TikTokWebView`.
- [ ] Implement `TikTokWebView` and `TikTokWebClient` managing script injection on page load.
- [ ] Implement `TikTokWebFragment` extending `WebBrowserFragment` with navigation toolbar.
- [ ] Implement `TikTokAddon` extending `WebBrowserAddon` providing preferences and addon registration.
- [ ] Implement `TikTokOpenOnCarBridge` allowing phone-to-car handoff of TikTok links.
- [ ] Write unit test `TikTokWebAddonBindingTest` validating fragment and addon instantiation.
- [ ] Run test: `cmd /c gradlew :web:testMobileDebugUnitTest --tests "*.TikTok*"`
- [ ] Commit: `feat(web/tiktok): implement UI shell, webview, fragment, and addon binding`

---

### Task 6: Full Verification, Clean Build, and Device Sideload

**Files:**
- All modified & created files

**Steps:**
- [ ] Run complete test suite: `cmd /c gradlew :web:testMobileDebugUnitTest`
- [ ] Build clean mobile debug APK: `cmd /c gradlew :fermata:assembleMobileDebug`
- [ ] Sideload onto connected Pixel 8 Pro: `adb -s 15c36230 install -r fermata/build/outputs/apk/mobile/debug/fermata-2.0.1-me.app.fermataX.auto-mobile-debug.apk`
- [ ] Restart app and verify TikTok appears on dashboard and functions properly.
- [ ] Commit: `chore(web/tiktok): finalize build artifacts and sideload verification`
