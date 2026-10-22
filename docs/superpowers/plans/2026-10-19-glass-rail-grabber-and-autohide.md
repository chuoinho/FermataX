# Glass Navigation Rail Semicircle Grabber and Auto-Hide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Glass Navigation Rail grabber handle into an enlarged semicircle tab with an app grid/menu launcher icon, draggable vertical (Y-axis) positioning with persistent preference state, and a 5-second idle auto-hide mechanism.

**Architecture:** 
1. Modify `GlassDrawables.java` to support semicircular pill shapes with asymmetric corner radii (flat along screen edge, circular arc inward) adapting to left/right dock alignment (`isRight`).
2. Add vector drawable `ic_glass_rail_launcher.xml` (modern rounded 4-box app grid / menu icon) to replace the text chevron `>`.
3. In `GlassNavigationRailView.java`, enlarge grabber bounds (`hitWidth` 64-80dp, `visualWidth` 44dp, `visualHeight` 88dp), implement touch gesture detection separating tap (expand) from vertical drag (update Y-position and persist ratio to `MainActivityPrefs`), and add an idle timer `AUTO_HIDE_DELAY_MS = 5000L` with touch/focus reset.
4. Update `GlassNavigationRailViewTest.java` to test grabber layout, drag bounds, and auto-hide timer.

**Tech Stack:** Android View framework, `GradientDrawable`, `TouchSlop`, `ValueAnimator`, `MainActivityPrefs`, Robolectric.

**Spec:** User direction A:
- Enlarge handle size for screens $\le$ 800x480.
- Shape: semicircle (flat at screen bezel, curved inward).
- Icon: App grid / menu icon instead of text `>`.
- Drag & Drop: allow user to slide the handle vertically up/down, persisting position.
- Auto-hide: automatically collapse rail after 5 seconds of inactivity.

## Global Constraints

- Do not commit, push, or merge git.
- Do not uninstall app from device (always `install -r -d`).
- Keep `MainActivityDelegate.java` nonblank lines $\le 1291$.
- Ensure all tests pass (`:fermata:testAutoDebugUnitTest`, `:web:testAutoDebugUnitTest`).

## Review Focus

1. **Tap vs Drag Disambiguation**: Quick taps must trigger `expand()`, while vertical movements exceeding `touchSlop` must slide the handle without expanding.
2. **Boundary Clamping**: Vertical dragging must not move the handle off-screen or into system status bar / bottom navigation zones.
3. **Timer Inactivity Reset**: Any touch on the rail or scrim, as well as D-pad focus movement, must reset the 5s auto-hide timer; releasing touch re-arms the 5s countdown.
4. **Left/Right Dock Flip**: When user changes `NAV_BAR_POS` between Left and Right, the semicircle flat side must remain flush with the screen border (flat on left when docked left, flat on right when docked right).
5. **Screen Resolution Adaptability**: Layout must work seamlessly on both compact 800x480 head units and high-resolution phone/tablet screens without overlapping other controls.

---

### Task 1: Preference Storage for Rail Grabber Y-Position

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java`

**Interfaces:**
- Produces: `Pref<DoubleSupplier> GLASS_RAIL_Y_RATIO`, `float getGlassRailYRatioPref()`, `void setGlassRailYRatioPref(float ratio)`

- [x] **Step 1: Declare `GLASS_RAIL_Y_RATIO` preference in `MainActivityPrefs.java`**
  - Add `Pref<DoubleSupplier> GLASS_RAIL_Y_RATIO = Pref.f("GLASS_RAIL_Y_RATIO", 0.5f);`
  - Add default getters and setters:
    ```java
    default float getGlassRailYRatioPref() {
        return (float) getDoublePref(GLASS_RAIL_Y_RATIO);
    }
    default void setGlassRailYRatioPref(float ratio) {
        applyDoublePref(GLASS_RAIL_Y_RATIO, ratio);
    }
    ```

- [x] **Step 2: Verify compilation**
  - Run: `./gradlew.bat :fermata:compileAutoDebugJavaWithJavac "-PALLOW_LEGACY_TEST_SIGNING=true" "-PAPP_ID_SFX=.test"`
  - Expected: `BUILD SUCCESSFUL`

---

### Task 2: Semicircular Pill Drawable & Launcher Vector Asset

**Files:**
- Create: `fermata/src/main/res/drawable/ic_glass_rail_launcher.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/glass/theme/GlassDrawables.java`

**Interfaces:**
- Produces:
  - `Drawable createGlassGrabberSemicircleDrawable(Context context, float radiusDp, boolean isNight, boolean isRight)`
  - Vector drawable `@drawable/ic_glass_rail_launcher`

- [x] **Step 1: Create modern 4-tile / menu launcher vector drawable `ic_glass_rail_launcher.xml`**
  - 24x24dp viewport, rounded 2x2 grid tiles or rounded menu bars representing app launcher / navigation rail.
  - Fill color: `#FFFFFFFF` with tinting supported at runtime.

- [x] **Step 2: Implement `createGlassGrabberSemicircleDrawable` in `GlassDrawables.java`**
  - Create `GradientDrawable` with 8 corner radii:
    - If `!isRight` (docked left): top-left = 0, bottom-left = 0, top-right = `R`, bottom-right = `R`.
    - If `isRight` (docked right): top-left = `R`, bottom-left = `R`, top-right = 0, bottom-right = 0.
  - Background color: `isNight ? 0xFF14181B : 0xFFF4F3F1` (opaque glass color).
  - Stroke: 1dp border matching `colors.getCardBorder()`.

- [x] **Step 3: Verify compilation**
  - Run: `./gradlew.bat :fermata:compileAutoDebugJavaWithJavac "-PALLOW_LEGACY_TEST_SIGNING=true" "-PAPP_ID_SFX=.test"`
  - Expected: `BUILD SUCCESSFUL`

---

### Task 3: Semicircle Handle, Vertical Drag & Drop, and 5s Auto-Hide in `GlassNavigationRailView`

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailView.java`

**Interfaces:**
- Consumes: `createGlassGrabberSemicircleDrawable`, `ic_glass_rail_launcher`, `GLASS_RAIL_Y_RATIO`
- Produces: Semicircle grabber, draggable Y handle, 5-second inactivity auto-collapse.

- [x] **Step 1: Update Handle Geometry & Replace Icon**
  - Change dimensions:
    - `visualWidth`: `44dp`
    - `visualHeight`: `88dp`
    - `hitWidth`: `isCar ? 80dp : 64dp`
    - `hitHeight`: `104dp`
  - Replace `tvGrip` with `ImageView ivLauncher`:
    - Size: 24dp x 24dp, centered inside `innerGrabberPill`.
    - Source: `R.drawable.ic_glass_rail_launcher`.
    - Tint: `colors.getTextPrimary()`.
  - Apply `createGlassGrabberSemicircleDrawable(context, 44f, isNight, isRight)`.

- [x] **Step 2: Implement Vertical Drag & Drop with Position Persistence**
  - Read saved Y-ratio from `activity.getPrefs().getGlassRailYRatioPref()` on init/layout.
  - Position `grabberHandle` along Y according to the saved ratio (clamped within parent bounds with margin).
  - Implement `OnTouchListener` on `grabberHandle`:
    - Track `downRawY`, `initialTranslationY`.
    - Calculate distance: if $\Delta y > \text{touchSlop}$, mark `isDragging = true`.
    - Update `grabberHandle.setTranslationY(clampedY)`.
    - On `ACTION_UP`:
      - If `isDragging`: compute new ratio `y / (parentHeight - handleHeight)`, save to prefs via `activity.getPrefs().setGlassRailYRatioPref(ratio)`.
      - If NOT `isDragging`: trigger `expand()`.

- [x] **Step 3: Implement 5-Second Auto-Hide Idle Timer**
  - Define `private static final long AUTO_HIDE_DELAY_MS = 5000L;`
  - Add `private final Runnable autoHideRunnable = this::collapse;`
  - Implement `scheduleAutoHide()`, `cancelAutoHide()`, `resetAutoHideTimer()`.
  - In `expand()`: call `scheduleAutoHide()`.
  - In `collapse()`, `collapseImmediate()`, `onDetachedFromWindow()`: call `cancelAutoHide()`.
  - Override `dispatchTouchEvent(MotionEvent ev)`:
    - If `isOpen`:
      - On `ACTION_DOWN` / `ACTION_MOVE`: `cancelAutoHide()`.
      - On `ACTION_UP` / `ACTION_CANCEL`: `scheduleAutoHide()`.
  - In action buttons D-pad / key listener: reset timer on navigation.

- [x] **Step 4: Verify compilation**
  - Run: `./gradlew.bat :fermata:compileAutoDebugJavaWithJavac "-PALLOW_LEGACY_TEST_SIGNING=true" "-PAPP_ID_SFX=.test"`
  - Expected: `BUILD SUCCESSFUL`

---

### Task 4: Unit Testing & Robolectric Verification

**Files:**
- Modify: `fermata/src/test/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailViewTest.java`

- [x] **Step 1: Update existing dimension tests to new semicircle dimensions**
  - Verify `hitWidth` $\ge 64\text{dp}$, `visualWidth` = 44dp, `visualHeight` = 88dp.
  - Verify `ivLauncher` exists and displays launcher drawable.

- [x] **Step 2: Add test for Auto-Hide timer**
  - Test that calling `expand()` schedules auto-hide.
  - Fast-forward Robolectric scheduler by 5000ms: verify `railView.isOpen() == false`.
  - Test that dispatching touch event before 5000ms resets the timer.

- [x] **Step 3: Add test for Y-ratio clamping and drag updates**
  - Test that dragging updates translation Y and clamps within upper/lower screen boundaries.

- [x] **Step 4: Run unit tests**
  - Run: `./gradlew.bat :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.rail.GlassNavigationRailViewTest" "-PALLOW_LEGACY_TEST_SIGNING=true" "-PAPP_ID_SFX=.test"`
  - Run: `./gradlew.bat :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.architecture.ArchitectureBoundaryTest" "-PALLOW_LEGACY_TEST_SIGNING=true" "-PAPP_ID_SFX=.test"`
  - Expected: All tests PASS.

---

### Task 5: Live Device / Universal APK Verification

**Files:** None (build & device verification)

- [x] **Step 1: Package Universal Debug APK**
  - Run: `./gradlew.bat packageAutoDebugUniversalApk "-PALLOW_LEGACY_TEST_SIGNING=true" "-PAPP_ID_SFX=.test"`
  - Install to device: `adb -s 15c36230 install -r -d ...`

- [x] **Step 2: Verify visually on device**
  - Launch app, verify semicircle handle appearance and grid launcher icon.
  - Test dragging the handle up and down to different vertical positions, restart app to confirm position is preserved.
  - Test tapping to expand the menu, wait 5 seconds without touching to confirm it automatically collapses.
  - Capture screenshots and report back to user.
