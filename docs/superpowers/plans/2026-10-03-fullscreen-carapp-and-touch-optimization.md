# Full Screen CarApp, Touch Optimization & Legacy Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform FermataX's Android Auto `MirrorServiceFS` from screen mirroring into a native Full Screen CarApp using `VirtualDisplay` + `Presentation`, optimize touch latency from >200ms to <16ms with 0ms clicks, `onFling` momentum, and 40ms adaptive scroll release, strengthen background playback with an explicit CPU `WakeLock` and watchdog in `FermataMediaService`, and purge all legacy screen mirroring code (`MirrorDisplay`, `MirrorActivity`, `MirrorService`, accessibility gestures) to keep the codebase clean and maintainable.

**Architecture:** Instead of mirroring the phone display via `MediaProjection` and injecting slow gestures through `AccessibilityService`, `MirrorServiceFS` creates a native Android `VirtualDisplay` on the car's `SurfaceContainer` and attaches a `FullScreenCarPresentation` (implementing `FermataActivity`). The presentation hosts `MainActivityDelegate` directly in-process. Touch events (`onClick`, `onScroll`, `onFling`) from `SurfaceCallback` are directly converted and dispatched into the presentation's `DecorView` in real time with zero IPC. In the background, `FermataMediaService` maintains an active CPU `WakeLock` and a periodic liveness watchdog. All legacy screen mirroring classes and assets (~1100 lines) are completely deleted.

**Tech Stack:** Android Car App Library (`androidx.car.app`), Android Presentation & VirtualDisplay (`android.app.Presentation`, `android.hardware.display.VirtualDisplay`), Java 17 / Android SDK, JUnit 4, Robolectric.

**Spec:** Architectural design confirmed in chat (repurposing `MirrorServiceFS` into native Full Screen Presentation, eliminating `MediaProjection` screen mirroring, high-performance in-process direct touch dispatch, `FermataMediaService` WakeLock/Watchdog, and complete legacy code cleanup).

## Global Constraints
- Target min API: 26, compile SDK: 35/36.
- Preserve backward compatibility for existing Android Auto permissions and manifest entries.
- Must not require root, KingInstaller, or phone screen to remain on during car playback.
- Preserve all existing playback engines (ExoPlayer, VLC, local files, IPTV, network drives).
- Maintain code simplicity (DRY / YAGNI): no redundant services or duplicated media sessions.
- Clean code: delete unused files, classes, drawable assets, and manifest declarations related to phone screen mirroring.

## Review Focus
- Re-surface / resize resilience: when Android Auto resizes the surface or toggles split-screen/Coolwalk, the `VirtualDisplay` is resized without destroying or recreating the active player and UI.
- Single tap responsiveness: `onClick` dispatches `ACTION_DOWN` and `ACTION_UP` with 0ms artificial delay directly to `DecorView`.
- Gesture continuity: `onScroll` anchor points track the active container and release cleanly after 40ms instead of hanging for 220ms.
- Fling momentum: `onFling` immediately terminates active drag state and invokes `fling()` on the active scroll container.
- Background playback survival: locking the phone screen or switching apps on the phone does not throttle or kill the car audio/video stream.
- Zero leftover dead code: no compilation errors or runtime references remaining to deleted `MirrorDisplay`, `MirrorActivity`, or `MirrorService`.

---

### Task 1: Create `FullScreenCarPresentation` Implementing `FermataActivity`

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/FullScreenCarPresentationTest.java`

**Interfaces:**
- Consumes: `android.app.Presentation`, `me.aap.fermata.ui.activity.FermataActivity`, `me.aap.fermata.ui.activity.MainActivityDelegate`
- Produces: `FullScreenCarPresentation` extending `Presentation` and implementing `FermataActivity`

- [ ] **Step 1: Write the failing contract test**

```java
package me.app.fermatax.auto;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FullScreenCarPresentationTest {
    @Test
    public void testPresentationImplementsFermataActivity() {
        assertTrue(me.aap.fermata.ui.activity.FermataActivity.class.isAssignableFrom(FullScreenCarPresentation.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.FullScreenCarPresentationTest`
Expected: Compilation failure or FAIL (class not found).

- [ ] **Step 3: Implement `FullScreenCarPresentation`**

Implement `FullScreenCarPresentation` extending `android.app.Presentation` and implementing `FermataActivity`:
- Constructor: `FullScreenCarPresentation(Context outerContext, Display display)`
- Sets `isCarActivity() -> true`
- Implement `setContentView`, `findViewById`, `getWindow`, `getContext`
- Integrates with `MainActivityDelegate` via `getActivityDelegate()`
- Manages lifecycle (`onCreate`, `onStart`, `onStop`, `dismiss`) cleanly without memory leaks.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.FullScreenCarPresentationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java fermata/src/testAuto/java/me/app/fermatax/auto/FullScreenCarPresentationTest.java
git commit -m "feat(auto): add FullScreenCarPresentation implementing FermataActivity"
```

---

### Task 2: Implement High-Performance `CarTouchDispatcher`

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/CarTouchDispatcher.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/CarTouchDispatcherTest.java`

**Interfaces:**
- Consumes: `android.view.View`, `android.view.MotionEvent`
- Produces: `CarTouchDispatcher`:
  - `void setTargetView(@Nullable View target)`
  - `void handleClick(float x, float y)`
  - `void handleScroll(float distanceX, float distanceY)`
  - `void handleFling(float velocityX, float velocityY)`
  - `void cancelPending()`

- [ ] **Step 1: Write the failing tests for click, scroll, and fling**

```java
package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CarTouchDispatcherTest {
    @Test
    public void testClickGeneratesDownAndUpImmediately() {
        List<Integer> actions = new ArrayList<>();
        CarTouchDispatcher dispatcher = new CarTouchDispatcher(event -> actions.add(event.getActionMasked()));
        dispatcher.handleClick(100f, 200f);
        assertEquals(2, actions.size());
        assertEquals(Integer.valueOf(MotionEvent.ACTION_DOWN), actions.get(0));
        assertEquals(Integer.valueOf(MotionEvent.ACTION_UP), actions.get(1));
    }

    @Test
    public void testFlingTriggersInstantUpAndCancelsPending() {
        List<Integer> actions = new ArrayList<>();
        CarTouchDispatcher dispatcher = new CarTouchDispatcher(event -> actions.add(event.getActionMasked()));
        dispatcher.handleScroll(10f, 15f);
        dispatcher.handleFling(-500f, -800f);
        assertTrue(actions.contains(MotionEvent.ACTION_UP));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.CarTouchDispatcherTest`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement `CarTouchDispatcher`**

Implement `CarTouchDispatcher`:
- `handleClick(float x, float y)`: dispatch `ACTION_DOWN` followed immediately by `ACTION_UP` with 0ms artificial delay.
- `handleScroll(float distanceX, float distanceY)`: maintain active drag state, dispatch `ACTION_DOWN` on first movement, followed by `ACTION_MOVE`, and schedule `ACTION_UP` with an adaptive 40ms timer.
- `handleFling(float velocityX, float velocityY)`: immediately cancel the 40ms timer, dispatch `ACTION_UP`, and invoke `fling` on target view if scrollable.
- Ensure thread safety on main looper and recycle all `MotionEvent` instances properly.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.CarTouchDispatcherTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add fermata/src/auto/java/me/app/fermatax/auto/CarTouchDispatcher.java fermata/src/testAuto/java/me/app/fermatax/auto/CarTouchDispatcherTest.java
git commit -m "feat(auto): add CarTouchDispatcher with zero-delay tap and fling momentum"
```

---

### Task 3: Refactor `MirrorServiceFS` into Native Full Screen CarApp Service

**Files:**
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/MirrorServiceFS.java`
- Modify: `fermata/src/main/res/values/strings.xml` (update/clarify service label)
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/MirrorServiceFSTest.java`

**Interfaces:**
- Consumes: `FullScreenCarPresentation`, `CarTouchDispatcher`, `SurfaceContainer`
- Produces: Native full screen CarApp execution in `MirrorServiceFS` with Surface re-use and zero phone screen dependency.

- [ ] **Step 1: Write test for Surface Lifecycle & Surface Re-use**

```java
package me.app.fermatax.auto;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MirrorServiceFSTest {
    @Test
    public void testServiceInheritsCarAppService() {
        assertTrue(androidx.car.app.CarAppService.class.isAssignableFrom(MirrorServiceFS.class));
    }
}
```

- [ ] **Step 2: Run test to verify current behavior**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.MirrorServiceFSTest`
Expected: PASS/FAIL depending on current structure.

- [ ] **Step 3: Refactor `MirrorServiceFS`**

- Remove dependency on `MirrorDisplay`.
- In `MirrorScreen`:
  - Initialize `CarTouchDispatcher`.
  - In `onSurfaceAvailable(SurfaceContainer sc)`:
    - If `VirtualDisplay` already exists and is valid, invoke `vd.setSurface(sc.getSurface())` and `vd.resize(sc.getWidth(), sc.getHeight(), sc.getDpi())` without destroying the active Presentation or resetting playback!
    - If creating new: create `VirtualDisplay` with `VIRTUAL_DISPLAY_FLAG_PRESENTATION | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY`, instantiate `FullScreenCarPresentation`, attach to `MainActivityDelegate`, and call `show()`.
    - Set target decor view on `CarTouchDispatcher`.
  - In `onClick(float x, float y)`: delegate to `touchDispatcher.handleClick(x, y)`.
  - In `onScroll(float distanceX, float distanceY)`: delegate to `touchDispatcher.handleScroll(distanceX, distanceY)`.
  - Implement `@Override public void onFling(float velocityX, float velocityY)`: delegate to `touchDispatcher.handleFling(velocityX, velocityY)`.
  - In `onSurfaceDestroyed(SurfaceContainer sc)`: clean up touch state and detach presentation safely.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.MirrorServiceFSTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add fermata/src/auto/java/me/app/fermatax/auto/MirrorServiceFS.java fermata/src/testAuto/java/me/app/fermatax/auto/MirrorServiceFSTest.java
git commit -m "refactor(auto): transform MirrorServiceFS to native Full Screen CarApp"
```

---

### Task 4: Fortify `FermataMediaService` with CPU `WakeLock` & Watchdog

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/media/service/FermataMediaService.java`
- Test: `fermata/src/test/java/me/aap/fermata/media/service/FermataMediaServiceWakeLockTest.java`

**Interfaces:**
- Consumes: `android.os.PowerManager`, `PowerManager.WakeLock`
- Produces: Persistent background execution protection during active car playback.

- [ ] **Step 1: Write unit test for WakeLock acquisition on play and release on pause/stop**

```java
package me.aap.fermata.media.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FermataMediaServiceWakeLockTest {
    @Test
    public void testWakeLockPolicy() {
        // Test that wakeLock is held only while state is PLAYING or BUFFERING
        assertTrue(FermataMediaService.shouldHoldWakeLock(true, false));
        assertFalse(FermataMediaService.shouldHoldWakeLock(false, false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat fermata:testDebugUnitTest --tests me.aap.fermata.media.service.FermataMediaServiceWakeLockTest`
Expected: FAIL (method not defined).

- [ ] **Step 3: Implement WakeLock and periodic liveness check in `FermataMediaService`**

- In `FermataMediaService`:
  - Acquire `PowerManager.PARTIAL_WAKE_LOCK` named `"Fermata:MediaPlayback"` when playback begins.
  - Release WakeLock when playback pauses, completes, or service is destroyed.
  - Add lightweight Watchdog (scheduled task every 10s) to verify `MediaSessionCompat` is active and update metadata/playbackState if needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat fermata:testDebugUnitTest --tests me.aap.fermata.media.service.FermataMediaServiceWakeLockTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/media/service/FermataMediaService.java fermata/src/test/java/me/aap/fermata/media/service/FermataMediaServiceWakeLockTest.java
git commit -m "feat(media): add WakeLock and liveness watchdog to FermataMediaService"
```

---

### Task 5: Clean Up Legacy Screen Mirroring Subsystem & Dead Code (Code Hygiene)

**Files:**
- Delete: `fermata/src/auto/java/me/app/fermatax/auto/MirrorDisplay.java`
- Delete: `fermata/src/auto/java/me/app/fermatax/auto/MirrorActivity.java`
- Delete: `fermata/src/auto/java/me/app/fermatax/auto/MirrorService.java`
- Delete: `fermata/src/auto/res/drawable/mirror.xml`
- Delete: `fermata/src/auto/res/drawable/mirror_bg.xml`
- Delete: `fermata/src/auto/res/drawable-anydpi-v26/mirror.xml`
- Modify: `fermata/src/auto/AndroidManifest.xml` (remove `MirrorService` declaration)
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/CarSessionCoordinator.java` (remove MirrorDisplay/MirrorActivity/MirrorService steps)
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/LauncherActivity.java` (remove `MirrorDisplay.disableAccelRotation()`)
- Modify: `fermata/src/testAuto/java/me/app/fermatax/auto/AutoExitLifecycleTest.java` (remove MirrorActivity tests)
- Modify: `fermata/src/testAuto/java/me/app/fermatax/auto/AutoManifestPolicyTest.java` (clean manifest assertions)
- Modify / Replace: `fermata/src/testAuto/java/me/app/fermatax/auto/MirrorShutdownPolicyTest.java`

- [ ] **Step 1: Write test verifying zero mirror references in Auto manifest and shutdown sequence**

```java
package me.app.fermatax.auto;

import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class CleanAutoArchitectureTest {
    @Test
    public void testNoMirrorDisplayReferences() {
        // Verify code cleanliness: no references to removed MirrorDisplay
        assertFalse(CarSessionCoordinator.hasLegacyMirrorSteps());
    }
}
```

- [ ] **Step 2: Delete legacy mirror files and update coordinators**

- Delete `MirrorDisplay.java`, `MirrorActivity.java`, `MirrorService.java`, and unused drawables.
- In `CarSessionCoordinator.java`:
  - Remove `MirrorDisplay.projectionSessionStarted()`.
  - Remove `MirrorService.isActive()`.
  - Replace shutdown steps `"mirror_display"` and `"mirror_ui"` with `"fullscreen_presentation"`.
- In `LauncherActivity.java`: Remove `MirrorDisplay.disableAccelRotation()` call.
- In `fermata/src/auto/AndroidManifest.xml`: Remove `<service android:name=".MirrorService" ... />`.
- Clean up test files (`AutoExitLifecycleTest`, `AutoManifestPolicyTest`).

- [ ] **Step 3: Run compilation and test suite to ensure clean build with no dangling references**

Run: `.\gradlew.bat testAutoDebugUnitTest`
Expected: PASS (all tests green, 0 compiler errors or warnings about missing Mirror classes).

- [ ] **Step 4: Commit**

```bash
git rm fermata/src/auto/java/me/app/fermatax/auto/MirrorDisplay.java fermata/src/auto/java/me/app/fermatax/auto/MirrorActivity.java fermata/src/auto/java/me/app/fermatax/auto/MirrorService.java
git add fermata/src/auto/ fermata/src/testAuto/
git commit -m "chore(auto): purge legacy screen mirroring subsystem and clean up code"
```

---

### Task 6: End-to-End Build, Integration & Device Verification

**Files:**
- All touched files above.

- [ ] **Step 1: Run full unit test suite**

Run: `.\gradlew.bat testAutoDebugUnitTest testDebugUnitTest`
Expected: All tests pass.

- [ ] **Step 2: Assemble full APK**

Run: `.\gradlew.bat assembleAutoDebug`
Expected: BUILD SUCCESSFUL with output APK generated.

- [ ] **Step 3: Deploy to test device and verify on Desktop Head Unit (DHU)**

- Install updated APK to phone via ADB: `adb -s 15c36230 install -r fermata/build/outputs/apk/auto/debug/fermata-auto-debug.apk`
- Launch Android Auto DHU session (`adb forward tcp:5277 tcp:5277` and `desktop-head-unit.exe`).
- Open Full Screen CarApp:
  - Verify app UI renders full screen natively on car display.
  - Verify phone screen can be turned off while playback continues on DHU.
  - Verify touch speed: tap items (instant 0ms response), scroll lists, and fling.
  - Verify steering wheel / media notification buttons control playback smoothly without freezing.

- [ ] **Step 4: Final commit and cleanup**

```bash
git commit --allow-empty -m "chore(auto): complete Full Screen CarApp, touch optimization and cleanup verification"
```
