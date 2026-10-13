# Full Screen CarApp Architecture Fortification & All-Addon Verification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all architectural root causes preventing FermataX Full Screen (FS) mode from operating as reliably as standard FermataX, fix touch responsiveness (60ms tap), resolve Context/AppActivity unwrapping for all addons, prevent WindowManager BadTokenException dialog crashes and listener leaks, and verify all 12 addons operate natively on Android Auto.

**Architecture:** 
1. Deterministic Human-Emulated Touch Pipeline: Update `CarTouchDispatcher` to dispatch `ACTION_UP` with a 60ms gesture timestamp delta (`now + 60ms`), matching CarStream's reliable gesture model and satisfying both Android View `PFLAG_PREPRESSED` tap timeout and Chromium's `FilteredGestureProvider`.
2. Context Architecture: Create `PresentationContext extends ContextThemeWrapper implements FermataActivity` so that any Fragment/View calling `ActivityDelegate.getActivityDelegate(getContext())` resolves directly to `FullScreenCarPresentation` without relying on fragile static singletons.
3. Safe In-Layout Dialogs: Refactor `QuickStreamBarView` and any Presentation dialogs to use in-window `OverlayMenu` / `DialogBuilder` instead of window-manager-bound `AlertDialog.Builder`, eliminating `WindowManager$BadTokenException`.
4. Automotive-Safe In-App File Browsing: Ensure `FoldersFragment` invokes `FilePickerFragment` rather than external SAF `startActivityForResult(ACTION_OPEN_DOCUMENT_TREE)` when on the car.
5. Lifecycle Memory Hygiene: Fix preference listener leaks in `WebBrowserAddon` to eliminate `IllegalStateException: Listener leaks detected!`.

**Tech Stack:** Java 17, Android SDK (API 26-36), Android Auto Car App Library, Android Jetpack Fragment/Lifecycle, Chromium WebView.

---

## Global Constraints
- Target min API: 26, compile SDK: 35/36.
- Maintain Android 16 (API 36) strict non-null safety.
- Zero crashes: All touch events and view operations must run safely on MainLooper.
- Non-root compatible: Works entirely in-process within FullScreenCarPresentation.
- Zero regressions on Mobile (`MainActivity`) and standard Android Auto (`MainCarActivity`).
- No hacky band-aids; implement idiomatic Android and Fermata architecture.

## Review Focus
1. `CarTouchDispatcher` must dispatch `downTime = now` and `eventTime = now + 60` for `ACTION_UP` to satisfy Chromium WebView and Android View tap detection.
2. `FullScreenCarPresentation`'s context must implement `AppActivity`/`FermataActivity` so `MainActivityDelegate.getActivityDelegate(getContext())` succeeds without static state.
3. Dialogs in `QuickStreamBarView` must not throw `WindowManager$BadTokenException`.
4. `WebBrowserAddon.contributeSettings()` must not leak listeners into `SharedPreferenceStore`.
5. `ModernLayoutRoutingTest` must pass with `THEME_MODERN` set on car.

---

### Task 1: Fix Core Architectural Foundation in FullScreenCarPresentation & CarTouchDispatcher

**Files:**
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/CarTouchDispatcher.java`
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java`
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/CarOnboardingOverlay.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/CarTouchDispatcherTest.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/FullScreenCarPresentationTest.java`

**Interfaces:**
- `CarTouchDispatcher`:
  - `void handleClick(float x, float y)`: dispatches `ACTION_DOWN` (downTime=now, eventTime=now) and `ACTION_UP` (downTime=now, eventTime=now + 60ms) to ensure human tap compatibility.
- `FullScreenCarPresentation`:
  - `PresentationContext extends ContextThemeWrapper implements FermataActivity`: wraps presentation context so that `ActivityDelegate.getActivityDelegate(ctx)` resolves directly to presentation.
  - Window flags: configure `FLAG_NOT_TOUCH_MODAL`, `FLAG_HARDWARE_ACCELERATED`.
- `CarOnboardingOverlay`:
  - Ensures outside touches pass through or dismiss cleanly without blocking entire DecorView.

- [x] **Step 1: Update `CarTouchDispatcherTest` to assert 60ms tap duration**
  In `fermata/src/testAuto/java/me/app/fermatax/auto/CarTouchDispatcherTest.java`:
  Verify that when `handleClick(x, y)` is called, the generated `ACTION_UP` has `eventTime - downTime >= 60`.

- [x] **Step 2: Run test to verify failure**
  Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.CarTouchDispatcherTest`

- [x] **Step 3: Implement 60ms tap in `CarTouchDispatcher.java`**
  Update `handleClick(float x, float y)`:
  ```java
  long now = SystemClock.uptimeMillis();
  dispatchEvent(now, now, MotionEvent.ACTION_DOWN, x, y);
  dispatchEvent(now, now + 60, MotionEvent.ACTION_UP, x, y);
  ```

- [x] **Step 4: Implement `PresentationContext` implementing `FermataActivity` in `FullScreenCarPresentation.java`**
  Make the context returned by `FullScreenCarPresentation.getContext()` implement `FermataActivity` delegating to `FullScreenCarPresentation.this`. This guarantees `ActivityDelegate.getActivityDelegate(getContext())` immediately succeeds without static singletons.
  Add window flags `FLAG_NOT_TOUCH_MODAL`, `FLAG_HARDWARE_ACCELERATED`.

- [x] **Step 5: Make `CarOnboardingOverlay` non-blocking for outside touches**
  In `CarOnboardingOverlay.java`:
  Only handle clicks on the dismiss button and card container. Outside touches must not block underlying views.

- [x] **Step 6: Run tests to verify they pass**
  Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests "me.app.fermatax.auto.*"`

- [x] **Step 7: Commit**
  ```bash
  git add fermata/src/auto/java/me/app/fermatax/auto/CarTouchDispatcher.java fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java fermata/src/auto/java/me/app/fermatax/auto/CarOnboardingOverlay.java fermata/src/testAuto/java/me/app/fermatax/auto/CarTouchDispatcherTest.java fermata/src/testAuto/java/me/app/fermatax/auto/FullScreenCarPresentationTest.java
  git commit -m "fix(auto): fortify touch dispatching with 60ms tap and PresentationContext AppActivity binding"
  ```

---

### Task 2: Fix Addon Dialogs, BadTokenException, Listener Leaks & SAF on Car

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/fragment/FoldersFragment.java`
- Modify: `modules/web/src/main/java/me/aap/fermata/addon/web/WebBrowserAddon.java`
- Modify: `fermata/src/test/java/me/aap/fermata/ui/activity/ModernLayoutRoutingTest.java`

**Interfaces:**
- `QuickStreamBarView`:
  - `showAddShortcutDialog(Context context)`: replaces raw `AlertDialog.Builder` with in-layout `OverlayMenu` or `DialogBuilder` from `MainActivityDelegate`.
- `FoldersFragment`:
  - When `getMainActivity().isCarActivity()` is true, "Add folder" directly calls `addFolderPicker()` (`FilePickerFragment`) instead of system SAF.
- `WebBrowserAddon`:
  - `contributeSettings()`: registers preference listeners safely using stored references or lifecycle unregistration so no listeners leak into `SharedPreferenceStore`.
- `ModernLayoutRoutingTest`:
  - Fix test assertion to set `prefs.setThemePref(true, MainActivityPrefs.THEME_MODERN)`.

- [x] **Step 1: Fix `QuickStreamBarView.java` dialog creation**
  Use `MainActivityDelegate.get(context).createDialogBuilder()` or `OverlayMenu` to display shortcut selection safely without triggering `BadTokenException`.

- [x] **Step 2: Fix `FoldersFragment.java` automotive folder addition**
  In `addFolder(OverlayMenuItem item)`:
  If `getMainActivity().isCarActivity()` is true and item is `vfs_content`, route to `addFolderPicker()` with local filesystem or alert user rather than throwing `UnsupportedOperationException`.

- [x] **Step 3: Fix `WebBrowserAddon.java` preference listener leak**
  Ensure listener is registered only once or properly tracked so `onActivityDestroy` does not detect unreleased listeners.

- [x] **Step 4: Fix `ModernLayoutRoutingTest.java`**
  Update line 52 to `prefs.setThemePref(true, MainActivityPrefs.THEME_MODERN);`.

- [x] **Step 5: Run unit tests across all modules**
  Run: `.\gradlew.bat testAutoDebugUnitTest testDebugUnitTest`
  Expected: ALL TESTS PASS (100% green).

- [x] **Step 6: Commit**
  ```bash
  git add fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java fermata/src/main/java/me/aap/fermata/ui/fragment/FoldersFragment.java modules/web/src/main/java/me/aap/fermata/addon/web/WebBrowserAddon.java fermata/src/test/java/me/aap/fermata/ui/activity/ModernLayoutRoutingTest.java
  git commit -m "fix(ui): eliminate BadTokenException dialog crashes, SAF car limitation, and listener leaks"
  ```

---

### Task 3: Assemble, Deploy & Comprehensive Addon Verification on Device / DHU

**Files:**
- All build outputs and test artifacts.

- [x] **Step 1: Build debug APK**
  Run: `.\gradlew.bat assembleAutoDebug`
  Expected: BUILD SUCCESSFUL.

- [x] **Step 2: Install to test device**
  Run: `adb -s 15c36230 install -r fermata/build/outputs/apk/auto/debug/fermata-auto-debug.apk`

- [x] **Step 3: Launch Android Auto DHU and open Full Screen CarApp**
  - Verify `MirrorServiceFS` starts and connects without crashes.
  - Verify `dumpsys display` and take screenshots of the live interface.

- [x] **Step 4: Verify all 12 addons and functions**
  1. Dashboard & NavRail: tap buttons, verify instant page transition.
  2. TV: verify channel list loads and video playback starts.
  3. YouTube: verify WebView loads, search and video playback respond to taps.
  4. Web Browser: verify web navigation and link clicking.
  5. Radio: verify station directory and audio stream.
  6. Podcasts: verify show list and episode playback.
  7. Folders: verify local file browser opens and navigates directories.
  8. Favorites: verify item listing and playback.
  9. Playlists: verify queue management.
  10. Audiobooks: verify chapter playback.
  11. Recent: verify recently played items.
  12. Settings: verify opening, modifying preferences, and returning without `Listener leaks detected!`.

- [x] **Step 5: Final commit & report**
  ```bash
  git commit --allow-empty -m "chore(auto): verify all addons and full-screen interactions on device"
  ```
