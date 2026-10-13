# Dashboard Adaptive Weather & Responsive Card Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix automotive dashboard card clustering on large screens by introducing responsive gaps/margins, and refine the Bento Weather card so extra environmental metrics flow directly below weather (not pinned to bottom), automatically hiding on compact displays and expanding gracefully on large displays without empty voids.

**Architecture:** 
1. Make `BentoWeatherCardView` dynamically adaptive: remove bottom-pinning spacer, attach the metrics container directly below the hourly forecast, and auto-toggle its visibility (`View.GONE`) on compact heights (< 460dp) while expanding internal element sizing and spacing on large displays.
2. Introduce a responsive gap calculation engine in `ModernDashboardController` and `HomeFragment` that determines `rootPadding`, `columnGap`, and `verticalGap` from display metrics, fixing the `ConstraintSet.connect()` bug that wiped out card margins to 0dp.
3. Validate across Robolectric unit tests, package the Universal APK, and verify live on Desktop Head Unit (DHU) across compact and large screen viewports.

**Tech Stack:** Android Java 17, AndroidX ConstraintLayout 2.1+, Open-Meteo REST Client, Robolectric, Gradle, Android Auto DHU.

**Spec:** In-chat bounded design agreed with user:
1. Small screens: hide humidity/wind/UV metrics (`GONE`) to avoid clipping/overflow.
2. Large screens: extra weather info flows directly below current weather (no bottom pinning), with expanded content spacing to eliminate dead void.
3. Adaptive spacing: card gaps scale with screen dimensions (10dp on compact, 16dp on standard, 22dp on large/ultra-wide) so cards never stick together.

## Global Constraints

- Automotive ergonomics: All touch targets must remain >= 48-56dp.
- Zero regression on Classic UI mode (`MainActivityPrefs.UI_STYLE_CLASSIC`).
- 100% localization coverage across 6 supported locales (`values`, `values-vi`, `values-es`, `values-pt`, `values-ru`, `values-zh-rTW`).
- Single Universal APK build pipeline (`packageAutoDebugUniversalApk`).
- Safe UI thread execution for all layout updates (`Looper.getMainLooper()`).

## Review Focus

1. **Compact display height (< 460dp)**: Verify metrics card is completely `GONE` and no text or hourly pills are clipped.
2. **Tall / Ultra-wide display (>= 560dp height or >= 900dp width)**: Verify metrics card stays attached directly below hourly forecast with zero black void in the middle.
3. **Nav rail side swap (Left vs Right)**: Verify card gaps and margins remain intact on both left-nav and right-nav configurations without collapsing to 0dp.
4. **Dynamic configuration changes**: Verify screen rotation or multi-window resize re-evaluates gaps and weather metrics visibility cleanly without memory leaks.
5. **Localization integrity**: Verify any modified layout attributes or strings pass `LocalizationCoverageTest`.

---

### Task 1: Weather Card Adaptive Visibility & Direct Flow

**Files:**
- Modify: `fermata/src/main/res/layout/bento_weather_card.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
- Modify: `fermata/src/test/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardViewTest.java`

**Interfaces:**
- Consumes: `WeatherData`, `OpenMeteoWeatherService`
- Produces: `BentoWeatherCardView.setCompactMode(boolean)`, `BentoWeatherCardView.getMetricsContainer()`

- [x] **Step 1: Write the failing Robolectric test in `BentoWeatherCardViewTest.java`**

Add Robolectric runner and tests asserting:
1. When view height / display height is compact (< 460dp or `isCompactMode`), `metricsContainer` visibility is `View.GONE`.
2. When view height is standard/large (>= 460dp), `metricsContainer` visibility is `View.VISIBLE`.
3. Metrics container is placed directly after hourly container (not bottom-pinned).

```java
@RunWith(RobolectricTestRunner.class)
public class BentoWeatherCardViewTest {
    @Test
    public void testCompactModeHidesMetricsContainer() {
        Context context = RuntimeEnvironment.getApplication();
        BentoWeatherCardView view = new BentoWeatherCardView(context);
        view.setCompactMode(true);
        assertEquals(View.GONE, view.getMetricsContainer().getVisibility());

        view.setCompactMode(false);
        assertEquals(View.VISIBLE, view.getMetricsContainer().getVisibility());
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.weather.BentoWeatherCardViewTest"`
Expected: FAIL (method `setCompactMode` or `getMetricsContainer` not defined).

- [x] **Step 3: Implement adaptive flow and compact mode in `bento_weather_card.xml` & `BentoWeatherCardView.java`**

1. In `bento_weather_card.xml`: Remove the `<View android:layout_height="0dp" android:layout_weight="1" />` spacer preceding `bento_metrics_container`.
2. In `BentoWeatherCardView.java`:
   - Bind `metricsContainer = findViewById(R.id.bento_metrics_container)`.
   - Add `isCompactHeight(int heightPx)` checking if available height in dp is < 460dp.
   - In `onSizeChanged(w, h, oldw, oldh)`: evaluate height and set `setCompactMode(isCompactHeight(h))`.
   - Expose `getMetricsContainer()` and `setCompactMode(boolean compact)`.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.weather.BentoWeatherCardViewTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add fermata/src/main/res/layout/bento_weather_card.xml fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java fermata/src/test/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardViewTest.java
git commit -m "feat(weather): attach metrics directly below forecast and hide on compact screens"
```

---

### Task 2: Weather Card Responsive Content Scaling & Spacing

**Files:**
- Modify: `fermata/src/main/res/layout/bento_weather_card.xml`
- Modify: `fermata/src/main/res/layout/bento_hourly_slot_item.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardViewTest.java`

**Interfaces:**
- Consumes: `bento_weather_card.xml`, `bento_hourly_slot_item.xml`
- Produces: `BentoWeatherCardView.applyResponsiveContentSpacing(boolean isLargeScreen)`

- [x] **Step 1: Write the failing test for content spacing adjustments**

Add test in `BentoWeatherCardViewTest.java` verifying that when large screen mode is detected or set, `applyResponsiveContentSpacing(true)` increases vertical margins between sections and padding without crashing or exceeding parent bounds.

- [x] **Step 2: Run test to verify it fails**

Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.weather.BentoWeatherCardViewTest"`
Expected: FAIL (`applyResponsiveContentSpacing` not defined).

- [x] **Step 3: Implement responsive spacing in `bento_weather_card.xml` & `BentoWeatherCardView.java`**

1. In `bento_weather_card.xml`:
   - Set base padding to 16dp.
   - Use comfortable margins: 12dp between header and divider, 12dp before weather condition, 14dp before hourly forecast, 12dp before metrics card.
   - In `bento_metrics_container`: increase padding to 12dp (top/bottom) and 12dp (sides) with 8dp divider margin.
2. In `bento_hourly_slot_item.xml`:
   - Increase vertical padding from 6dp to 8dp, icon size to 24dp for better visual weight.
3. In `BentoWeatherCardView.java`:
   - Implement `applyResponsiveContentSpacing(boolean isLarge)`: adjust padding and text sizes (clock 36sp, temp 34sp on large screens) dynamically if ample space is available.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.weather.BentoWeatherCardViewTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add fermata/src/main/res/layout/bento_weather_card.xml fermata/src/main/res/layout/bento_hourly_slot_item.xml fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java fermata/src/test/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardViewTest.java
git commit -m "feat(weather): scale weather card typography and spacing on large displays"
```

---

### Task 3: Adaptive Card Gaps & Margins Across Screen Dimensions

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/DashboardLayoutHelper.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/ModernDashboardController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/HomeFragment.java`
- Create: `fermata/src/test/java/me/aap/fermata/ui/modern/DashboardLayoutHelperTest.java`

**Interfaces:**
- Produces: 
  - `DashboardLayoutHelper.Gaps`: record/class holding `rootPadding`, `columnGap`, `verticalGap` in pixels.
  - `DashboardLayoutHelper.calculateGaps(Context context)`: determines gaps based on screen width/height dp.
  - `DashboardLayoutHelper.applyGaps(ConstraintLayout layout, Gaps gaps, boolean isNavRight)`: applies margins and padding to `ConstraintSet` without resetting to 0.

- [x] **Step 1: Write the failing unit test in `DashboardLayoutHelperTest.java`**

```java
@RunWith(RobolectricTestRunner.class)
public class DashboardLayoutHelperTest {
    @Test
    public void testGapsCalculationByScreenSize() {
        // Compact screen (< 700dp width or < 460dp height): 10dp padding, 10dp column gap, 8dp vertical gap
        DashboardLayoutHelper.Gaps compact = DashboardLayoutHelper.calculateGaps(640, 400, 1.0f);
        assertEquals(10, compact.rootPadding);
        assertEquals(10, compact.columnGap);
        assertEquals(8, compact.verticalGap);

        // Standard screen (800x480): 14dp padding, 16dp column gap, 12dp vertical gap
        DashboardLayoutHelper.Gaps standard = DashboardLayoutHelper.calculateGaps(800, 480, 1.0f);
        assertEquals(14, standard.rootPadding);
        assertEquals(16, standard.columnGap);
        assertEquals(12, standard.verticalGap);

        // Large / Ultra-wide (> 900dp width or > 560dp height): 20dp padding, 22dp column gap, 16dp vertical gap
        DashboardLayoutHelper.Gaps large = DashboardLayoutHelper.calculateGaps(1024, 600, 1.0f);
        assertEquals(20, large.rootPadding);
        assertEquals(22, large.columnGap);
        assertEquals(16, large.verticalGap);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.DashboardLayoutHelperTest"`
Expected: FAIL (`DashboardLayoutHelper` class not found).

- [x] **Step 3: Implement `DashboardLayoutHelper.java` and integrate into `ModernDashboardController` and `HomeFragment`**

1. Create `DashboardLayoutHelper.java` with `Gaps` data structure, responsive math, and `applyGaps(ConstraintLayout, Gaps, boolean isNavRight)`.
2. In `ModernDashboardController.applyNavBarSideConstraints()`:
   - Compute `gaps = DashboardLayoutHelper.calculateGaps(context)`.
   - Apply `root.setPadding(gaps.rootPadding, gaps.rootPadding, gaps.rootPadding, gaps.rootPadding)`.
   - When connecting `home_hero_player_card`, `quick_stream_bar_container`, and `home_weather_card`, pass `gaps.columnGap` and `gaps.verticalGap` into `cs.connect(...)` and `cs.setMargin(...)`.
3. In `HomeFragment.applyNavBarSideConstraints()`:
   - Apply the exact same `DashboardLayoutHelper.applyGaps` logic.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.DashboardLayoutHelperTest"`
Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.activity.ModernLayoutRoutingTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/modern/DashboardLayoutHelper.java fermata/src/main/java/me/aap/fermata/ui/modern/ModernDashboardController.java fermata/src/main/java/me/aap/fermata/ui/modern/HomeFragment.java fermata/src/test/java/me/aap/fermata/ui/modern/DashboardLayoutHelperTest.java
git commit -m "feat(ui): add responsive card gaps and margins engine for automotive dashboard"
```

---

### Task 4: Multi-Resolution Testing, Build & Live DHU Verification

**Files:**
- Verification only

- [x] **Step 1: Run complete test suite and localization check**

Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.fragment.LocalizationCoverageTest"`
Run: `.\gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.*"`
Expected: ALL PASS.

- [x] **Step 2: Build Universal APK**

Run: `.\gradlew :fermata:packageAutoDebugUniversalApk`
Expected: BUILD SUCCESSFUL producing `fermata-2.0.1-me.app.fermataX.auto-auto-debug-universal.apk`.

- [x] **Step 3: Sideload Universal APK to Redmi Note 8 (`15c36230`)**

Commands:
```bash
adb -s 15c36230 push "fermata/build/outputs/apk_from_bundle/autoDebug/fermata-2.0.1-me.app.fermataX.auto-auto-debug-universal.apk" /data/local/tmp/fermata-universal.apk
adb -s 15c36230 shell "su -c 'pm install -i com.android.vending -r -d -g /data/local/tmp/fermata-universal.apk'"
```
Expected: `Success`.

- [x] **Step 4: Test live on Desktop Head Unit (DHU)**

1. Forward port 5277 and launch DHU.
2. Launch FermataX from CarService icon (`tap 540 255`).
3. Capture screenshots and verify:
   - Weather metrics card is attached directly below Hourly Forecast (no central void).
   - Distinct, comfortable gaps between Hero Media Card, Quick Stream Bar, and Weather Card (no touching/gluing).
   - Quick stream buttons remain fully visible and clickable without clipping.
4. Save verification artifact screenshot `dhu_screen_responsive_gaps_verified.png`.

- [x] **Step 5: Commit final verification changes**

```bash
git commit --allow-empty -m "test(dhu): verify responsive weather card flow and adaptive card gaps on DHU"
```
