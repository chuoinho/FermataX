# Unified Playback Binding & Full Addon Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify playback card logic across all form factors into a lightweight PlaybackCardBinding helper, resolve root-cause YouTube auto-fullscreen/unmute and phone top player visibility bugs, ensure 100% addon parity between Classic and Modern 2.0 themes, and clean up duplicate legacy controller code.

**Architecture:** 
- A concise, zero-boilerplate `PlaybackCardBinding` helper in `me.aap.fermata.ui.control` directly binds UI elements (artwork, title, subtitle, prev, play/pause, next, seekbar, elapsed, duration) to `FermataServiceUiBinder` / `PlaybackSnapshot` / `PlaybackTimelineSnapshot`.
- Route-based media launching is unified via `DashboardPlayableNavigator.playAndGoToPlayable()`, ensuring YouTube and all other addons open their respective owner fragments and trigger auto-fullscreen and auto-unmute cleanly.
- Shell visibility logic in `PhonePlaybackCarHeaderController`, `ControlPresentation`, and `UiShellController` is fixed to preserve the phone top player whenever media is active, hiding it only during dedicated fullscreen video playback.
- Legacy duplicate player rendering and seekbar handling in `PhonePlaybackCarHeaderController` and `ModernDashboardController` / `HomeFragment` is completely excised.

**Tech Stack:** Android Java 17, Android Auto DHU, MediaSessionCompat, ExoPlayer/VLC, JUnit 4, Robolectric.

---

## Global Constraints
- Zero regression on Classic Theme and all existing unit tests.
- 100% parity across all addons (TV, Radio, YouTube, Web, Stremio, TikTok, Audiobook, Podcast, Folders, Favorites, Playlists, Recent).
- Touch targets >= 56dp for automotive safety.
- All supported locales (`values`, `values-vi`, `values-es`, `values-pt`, `values-ru`, `values-zh-rTW`) preserved without missing strings.
- Continuous clean compilation of `:fermata:assembleMobileDebug` and `:fermata:assembleAutoDebug`.

---

### Task 1: Root-Cause Addon Playback Routing & Shell Visibility Fixes

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/ModernDashboardController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/HomeFragment.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/control/ControlPresentation.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/view/PhonePlaybackCarHeaderController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/view/UiShellController.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/control/ControlPresentationTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/view/PhonePlaybackCarHeaderControllerTest.java`

- [x] Step 1: Write unit test in `ControlPresentationTest.java` verifying that `hasMedia == true` when media session is playing or has metadata even if `item == null` (e.g. YouTube web streams).
- [x] Step 2: Update `ControlPresentation.hasPresentableSession()` to accept active playback or non-empty metadata even when `item == null`.
- [x] Step 3: Update `ModernDashboardController.java`, `HomeFragment.java`, and `QuickStreamBarView.java` to route item playback through `DashboardPlayableNavigator.playAndGoToPlayable(activity, item)` instead of raw `activity.playItem(item)`.
- [x] Step 4: Update `UiShellController.onPlaybackPresentationChanged(activity)` to call `activity.refreshPhonePlaybackCarHeader()`.
- [x] Step 5: Update `PhonePlaybackCarHeaderController.resolveVisibility()` so the top header is visible on Phone whenever not in dedicated fullscreen video mode.
- [x] Step 6: Verify all unit tests pass: `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.control.ControlPresentationTest"`.
- [x] Step 7: Commit changes with message `fix(playback): unify playable routing and fix phone top header visibility`.

---

### Task 2: Unified PlaybackCardBinding Helper & Legacy Controller Cleanup

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/control/PlaybackCardBinding.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/view/PhonePlaybackCarHeaderController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/ModernDashboardController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/HomeFragment.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/control/PlaybackCardBindingTest.java`

- [x] Step 1: Write unit test `PlaybackCardBindingTest.java` testing state propagation, play/pause toggle, skip to previous/next, seek gestures, and artwork fallback.
- [x] Step 2: Implement `PlaybackCardBinding.java`:
  - Accepts nullable view references: `artwork`, `title`, `subtitle`, `prevBtn`, `playPauseBtn`, `nextBtn`, `seekBar`, `elapsedText`, `durationText`, `container`.
  - Attaches to `FermataServiceUiBinder.Listener`.
  - Reconciles state from `PlaybackSnapshot` and `PlaybackTimelineSnapshot`.
  - Wires transport click listeners and `ControlSeekGesture` on seekBar.
  - Implements clean lifecycle detachment (`close()`).
- [x] Step 3: Refactor `PhonePlaybackCarHeaderController.java`: delegate player summary, transport buttons, and progress bar to `PlaybackCardBinding`, removing ~140 lines of duplicate code.
- [x] Step 4: Refactor `HomeFragment.java` and `ModernDashboardController.java`: delegate hero player views and gestures to `PlaybackCardBinding`, removing ~160 lines of duplicate code.
- [x] Step 5: Run tests: `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.control.PlaybackCardBindingTest"`.
- [x] Step 6: Commit changes with message `refactor(ui): introduce PlaybackCardBinding and clean up duplicate player code`.

---

### Task 3: Adaptive Bento Weather Positioning & Responsive Gaps

**Files:**
- Modify: `fermata/src/main/res/layout/bento_weather_card.xml`
- Modify: `fermata/src/main/res/layout/home_modern_fragment.xml`
- Modify: `fermata/src/main/res/layout/dashboard_modern_fragment.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardViewTest.java`

- [x] Step 1: Update `bento_weather_card.xml` layout constraints so that expanded metrics (humidity, wind, UV, precipitation) sit immediately beneath the primary forecast section rather than anchoring to the card bottom.
- [x] Step 2: Update `home_modern_fragment.xml` and `dashboard_modern_fragment.xml` with responsive spacing between hero media, quick stream bar, and weather card for widescreen layouts.
- [x] Step 3: Run weather card unit tests: `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.weather.BentoWeatherCardViewTest"`.
- [x] Step 4: Commit changes with message `feat(weather): adjust detailed metrics positioning and responsive card gaps`.

---

### Task 4: Full Multi-Target Build Verification & End-to-End Sanity Check

**Files:**
- None (verification task)

- [x] Step 1: Run all unit tests: `./gradlew :fermata:testMobileDebugUnitTest`.
- [x] Step 2: Assemble mobile APK: `./gradlew :fermata:assembleMobileDebug`.
- [x] Step 3: Assemble automotive APK: `./gradlew :fermata:assembleAutoDebug`.
- [x] Step 4: Verify live execution on connected device via adb.
