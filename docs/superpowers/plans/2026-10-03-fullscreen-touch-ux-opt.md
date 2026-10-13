# Fullscreen CarApp Touch UX Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide zero-friction touch UX in FermataX Fullscreen on Android Auto by adding dynamic tap-to-scroll buttons (`▲`/`▼`) on the Navigation Rail and Content areas, plus a first-run onboarding guide educating users on touch anchoring.

**Architecture:** 
1. `NavRailViewport` dynamically displays sleek, touchable `▲` and `▼` pill buttons whenever the rail has overflow items to scroll up/down, allowing one-tap scrolling without needing swipe gestures.
2. `CarScrollAffordanceView` attaches to `FullScreenCarPresentation` as a floating affordance that discovers active scrollable containers (`RecyclerView`, `ScrollView`, `WebView`) and renders `▲`/`▼` buttons reflecting `canScrollVertically(-1)` and `canScrollVertically(1)`.
3. `CarOnboardingOverlay` shows a non-intrusive guide card on the first run of Fullscreen mode ("Chạm vào vùng cần cuộn trước khi vuốt") and persists completion state via `SharedPreferences`.

**Tech Stack:** Android SDK, Android Auto CarApp Library, Java 17, Robolectric / JUnit 4.

**Spec:** User agreement on UX proposal: dynamic scroll buttons (NavRail & Content) + first-run touch onboarding overlay.

## Global Constraints

- Target min API: 26, compile SDK: 35/36.
- Maintain Android 16 (API 36) strict non-null safety (no null `ColorStateList` to `setTextColor`).
- Zero crashes: All touch events and scroll operations must run on MainLooper without blocking.
- Non-root compatible: Works entirely in-process within `FullScreenCarPresentation` and `VirtualDisplay`.
- DRY & YAGNI: Reuse existing scroll detection (`isScrollable`) and scroll policies (`NavRailScrollPolicy`).

## Review Focus

- Overflow tracking: `▲` and `▼` must vanish (`GONE`) when at the scroll boundaries (top / bottom).
- Click response: Tapping `▲` or `▼` must animate smoothly without jitter or interrupting concurrent touch events.
- First-run isolation: Onboarding overlay must only show once per install unless reset by preferences.
- Theme coherence: All newly added overlays and buttons must inherit `AppTheme.Classic` / Material You dark contrast.
- Performance: Scroll listeners and view hierarchies must not cause layout thrashing or FPS drops on Android Auto.

---

### Task 1: Dynamic `▲` / `▼` Tap-to-Scroll Buttons in `NavRailViewport`

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/view/NavRailViewport.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/view/NavRailViewportTest.java`

**Interfaces:**
- Consumes: `NavRailScrollPolicy.canScrollUp(scrollOffset)`, `NavRailScrollPolicy.canScrollDown(scrollOffset, max)`.
- Produces: `getScrollUpButton()`, `getScrollDownButton()` for accessibility and verification.

- [ ] **Step 1: Write unit tests in `NavRailViewportTest.java`**

```java
@Test
public void testDynamicScrollButtonsVisibilityAndClick() {
    NavRailViewport viewport = new NavRailViewport(context, Color.WHITE, false);
    // Add multiple child items to force overflow
    for (int i = 0; i < 15; i++) {
        View item = new View(context);
        item.layout(0, i * 80, 80, (i + 1) * 80);
        viewport.addNavigationItem(item);
    }
    viewport.refreshScrollState();
    // Initially at top: scrollUp should be GONE, scrollDown should be VISIBLE
    assertFalse(viewport.canScrollUp());
    assertTrue(viewport.canScrollDown());
    // Simulate clicking scroll down
    viewport.getScrollDownButton().performClick();
    assertTrue(viewport.getScrollOffset() > 0);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat fermata:testDebugUnitTest --tests me.aap.fermata.ui.view.NavRailViewportTest`
Expected: FAIL (missing methods / buttons)

- [ ] **Step 3: Implement dynamic buttons in `NavRailViewport.java`**

1. Create `scrollUpBtn` and `scrollDownBtn` with semi-transparent dark circle background and chevron icons (`▲` / `▼`).
2. Add them to viewport layout over `topFade` and `bottomFade`.
3. In `updateAffordances()`:
   - `scrollUpBtn.setVisibility(NavRailScrollPolicy.canScrollUp(scrollOffset) ? VISIBLE : GONE);`
   - `scrollDownBtn.setVisibility(NavRailScrollPolicy.canScrollDown(scrollOffset, max) ? VISIBLE : GONE);`
4. On click:
   - `scrollUpBtn`: `animateTo(scrollOffset - clip.getHeight() / 2);`
   - `scrollDownBtn`: `animateTo(scrollOffset + clip.getHeight() / 2);`

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat fermata:testDebugUnitTest --tests me.aap.fermata.ui.view.NavRailViewportTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/view/NavRailViewport.java fermata/src/test/java/me/aap/fermata/ui/view/NavRailViewportTest.java
git commit -m "feat(ui): add dynamic tap-to-scroll buttons to NavRailViewport"
```

---

### Task 2: Content Quick-Scroll Affordance (`CarScrollAffordanceView`) in `FullScreenCarPresentation`

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/CarScrollAffordanceView.java`
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/CarScrollAffordanceViewTest.java`

**Interfaces:**
- Consumes: `View.canScrollVertically(int direction)`, `RecyclerView.smoothScrollBy`, `ScrollView.smoothScrollBy`.
- Produces: Floating vertical scroll pill with `▲` and `▼` for scrollable content areas.

- [ ] **Step 1: Write unit tests in `CarScrollAffordanceViewTest.java`**

```java
@Test
public void testCarScrollAffordanceTriggersScroll() {
    RecyclerView rv = new RecyclerView(context);
    CarScrollAffordanceView affordance = new CarScrollAffordanceView(context);
    affordance.attachTo(rv);
    affordance.updateScrollState(true, true); // can scroll up and down
    assertTrue(affordance.getUpButton().isShown());
    assertTrue(affordance.getDownButton().isShown());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.CarScrollAffordanceViewTest`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement `CarScrollAffordanceView.java` and attach in `FullScreenCarPresentation.java`**

1. Create `CarScrollAffordanceView` extending `LinearLayoutCompat`:
   - Vertical floating pill positioned at `Gravity.END | Gravity.CENTER_VERTICAL`.
   - Semi-transparent dark rounded container `#C0121B27` with 2 icon buttons: `▲` (scroll up) and `▼` (scroll down).
   - `updateScrollState(boolean canUp, boolean canDown)`:
     - Updates button visibility/enabled state.
     - Hides entire pill if neither direction can scroll.
   - On click:
     - Up: calls `scrollBy(0, -pageHeight)` on active scrollable view.
     - Down: calls `scrollBy(0, pageHeight)` on active scrollable view.
2. In `FullScreenCarPresentation.java`:
   - Add `CarScrollAffordanceView` overlay to presentation window decor view.
   - Periodically / on layout / on touch release update affordance state.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.CarScrollAffordanceViewTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add fermata/src/auto/java/me/app/fermatax/auto/CarScrollAffordanceView.java fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java fermata/src/testAuto/java/me/app/fermatax/auto/CarScrollAffordanceViewTest.java
git commit -m "feat(auto): add CarScrollAffordanceView for content quick-scrolling"
```

---

### Task 3: First-Run Touch Onboarding Overlay (`CarOnboardingOverlay`)

**Files:**
- Create: `fermata/src/auto/java/me/app/fermatax/auto/CarOnboardingOverlay.java`
- Modify: `fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java`
- Test: `fermata/src/testAuto/java/me/app/fermatax/auto/CarOnboardingOverlayTest.java`

**Interfaces:**
- Consumes: `MainActivityPrefs.get().getBooleanPref(...)`, `applyBooleanPref(...)`.
- Produces: Modal onboarding guide card shown only on first launch of fullscreen mode.

- [ ] **Step 1: Write unit tests in `CarOnboardingOverlayTest.java`**

```java
@Test
public void testOnboardingDismissalSavesPref() {
    CarOnboardingOverlay overlay = new CarOnboardingOverlay(context, prefs);
    assertTrue(overlay.shouldShow());
    overlay.dismiss();
    assertFalse(overlay.shouldShow());
    assertTrue(prefs.getBooleanPref(CarOnboardingOverlay.PREF_ONBOARDING_SHOWN));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.CarOnboardingOverlayTest`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement `CarOnboardingOverlay.java` and bind in `FullScreenCarPresentation.java`**

1. Create `CarOnboardingOverlay`:
   - Dark modal card with title: "💡 Hướng dẫn thao tác FermataX Fullscreen".
   - Clear instructional items:
     - 👆 **Chạm trước khi vuốt**: Chạm nhẹ vào danh sách hoặc trang web để xác định vị trí trước khi vuốt.
     - 🔼🔽 **Nút cuộn nhanh**: Bấm các nút mũi tên `▲` / `▼` trên thanh điều hướng hoặc góc màn hình để cuộn nhanh.
     - 🏠 / ⬅️ **Điều hướng**: Dùng phím góc trên hoặc thanh công cụ để quay lại hoặc về Trang chủ.
   - Button "Đã hiểu" (Got it).
2. On click "Đã hiểu":
   - Save `PREF_ONBOARDING_SHOWN = true`.
   - Remove overlay view with smooth fade-out.
3. In `FullScreenCarPresentation.show()`:
   - If `!prefs.getBooleanPref(PREF_ONBOARDING_SHOWN)` -> attach and show `CarOnboardingOverlay`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.CarOnboardingOverlayTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add fermata/src/auto/java/me/app/fermatax/auto/CarOnboardingOverlay.java fermata/src/auto/java/me/app/fermatax/auto/FullScreenCarPresentation.java fermata/src/testAuto/java/me/app/fermatax/auto/CarOnboardingOverlayTest.java
git commit -m "feat(auto): add CarOnboardingOverlay for first-run touch guidance"
```

---

### Task 4: End-to-End Build and Live Verification on Hardware & DHU

**Files:**
- Test: Full physical device execution on Redmi Note 8 + DHU.

- [ ] **Step 1: Rebuild universal debug APK**

Run: `.\gradlew.bat :fermata:packageAutoDebugUniversalApk`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install APK and launch on DHU**

Run: `adb -s 15c36230 install -r -d ...`
Open FermataX on DHU.

- [ ] **Step 3: Verify Onboarding Overlay**

Capture screenshot: Confirm Onboarding dialog appears with readable Vietnamese text and dismisses cleanly upon tapping "Đã hiểu".

- [ ] **Step 4: Verify NavRail and Content Scroll Buttons**

1. On Dashboard / Addon list: Confirm `▲` and `▼` appear dynamically on NavRail.
2. Tap `▼`: verify NavRail smoothly scrolls down.
3. Tap `▲`: verify NavRail smoothly scrolls up.
4. On Settings / Web Browser: verify content scroll buttons allow paging through preferences / web page.

- [ ] **Step 5: Verify Logcat**

Run: `adb -s 15c36230 logcat -b crash -d`
Expected: Zero crashes.
