# Android Auto Glassmorphism Theme (Theme Kính) Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and port an isolated, internationalized dark Glassmorphism Theme (`THEME_GLASS = 9`) for FermataX on Android Auto / DHU, comprising a Glass Dashboard (Speedo/Trip, Nav/Weather, Media Player, 6-slot Quick Bar), Glass Addon Catalog, Glass Settings, and notification-based turn-by-turn navigation without paid APIs, while preserving the phone UI 100% untouched.

**Architecture:** Encapsulate all new theme components inside `me.aap.fermata.ui.glass.*`. Adapt the responsive layout mathematics (`WIDE`/`SQUARE`/`PORTRAIT`/`SPLIT`) from `CoDienBoCuc.java` and design tokens from `CoDienMau.java` into clean Java View components. Connect the media card directly to `FermataServiceUiBinder`. Capture Google Maps and Waze navigation instructions via `NotificationListenerService` with fallback to Open-Meteo hourly weather.

**Tech Stack:** Java 17, Android SDK (API 28–36), Android Auto CarActivity SDK, AndroidX, Robolectric & JUnit 4, Open-Meteo REST API, `NotificationListenerService`.

**Spec:** [`docs/superpowers/specs/2026-10-13-aa-glass-theme-design.md`](file:///E:/antigravity/fermata/docs/superpowers/specs/2026-10-13-aa-glass-theme-design.md)

## Global Constraints

- **Phone UI Invariant:** Phone UI (`THEME_MAIN`) must never use `THEME_GLASS` and must remain strictly on Classic or Material You.
- **Theme Isolation:** Do not modify or couple with Bento/Modern (`me.aap.fermata.ui.modern`) code or tests; keep all glass code in `me.aap.fermata.ui.glass.*`.
- **Zero Paid APIs:** Weather uses Open-Meteo (no API key); Turn-by-Turn navigation uses Android `NotificationListenerService` (no Google Cloud API keys).
- **Single Universal Package:** Distribution remains one universal APK for both phone and car; do not split source sets or packaging flavors.
- **Automotive Ergonomics:** Minimum touch targets on car displays are 56dp–64dp; high-contrast Day/Night mode support.

## Review Focus

- **No Active Navigation Fallback:** Center card must seamlessly display 24-hour Open-Meteo forecast when no turn-by-turn notification exists, without crashing or freezing.
- **Clean Install Quick Bar State:** Slots 1–4 must start completely empty (`[+] Add Shortcut`), while slots 5 (Addons) and 6 (Exit) remain permanent.
- **Notification Access Denied:** When user has not granted notification listener access, nav card shows weather forecast without nagging; Settings readiness check offers one-tap intent to grant permission on phone.
- **Display Aspect Ratio Extremes:** Layout engine must correctly handle widescreen (16:9, 21:9), square (1:1), and portrait (Tesla/Volvo AA) displays.
- **Locale Independence:** Date and speed units must adapt dynamically between Metric (`km/h`, `km`, `°C`) and Imperial (`mph`, `miles`, `°F`).

---

### Task 1: Theme Constants & Preference Isolation

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java`
- Modify: `fermata/src/main/res/values/strings.xml`
- Modify: `fermata/src/main/res/values-vi/strings.xml`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/GlassThemePreferencesTest.java`

**Interfaces:**
- Consumes: Existing `MainActivityPrefs` preference store.
- Produces: `MainActivityPrefs.THEME_GLASS = 9`, `PREF_GLASS_QUICK_SHORTCUTS`, `GLASS_SPEED_UNIT`, `isGlassTheme(boolean auto)`.

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/GlassThemePreferencesTest.java
package me.aap.fermata.ui.glass;

import static org.junit.Assert.*;
import org.junit.Test;
import me.aap.fermata.ui.activity.MainActivityPrefs;

public class GlassThemePreferencesTest {
    @Test
    public void testGlassThemeConstantAndPhoneIsolation() {
        assertEquals(9, MainActivityPrefs.THEME_GLASS);
        // Verify phone UI never falls back to THEME_GLASS
        // and getThemePref(false) clamps to classic if glass was set to phone
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.GlassThemePreferencesTest"`
Expected: FAIL (cannot find symbol `THEME_GLASS`).

- [ ] **Step 3: Implement `THEME_GLASS` in `MainActivityPrefs.java`**

Add `int THEME_GLASS = 9;`, string resources `theme_glass`, ensure `getThemePref(boolean auto)` allows `THEME_GLASS` only when `auto == true`, while `auto == false` strictly maps to `THEME_CLASSIC` or `THEME_MATERIAL_YOU`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.GlassThemePreferencesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java fermata/src/main/res/values*/strings.xml fermata/src/test/java/me/aap/fermata/ui/glass/GlassThemePreferencesTest.java
git commit -m "feat(glass): add THEME_GLASS constant and isolate phone theme preference"
```

---

### Task 2: Design Tokens & Glass Drawable Engine

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/theme/GlassThemeColors.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/theme/GlassDrawables.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/theme/GlassDimensions.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/theme/GlassDrawablesTest.java`

**Interfaces:**
- Consumes: Android `Context`, `Configuration.uiMode`.
- Produces:
  - `GlassThemeColors.get(Context)` -> immutable theme palette (`cardBg`, `cardBorder`, `accentCyan`, `textPrimary`, `textSecondary`, etc.).
  - `GlassDrawables.createGlassCardDrawable(float cornerRadiusDp, boolean isNight)` -> `Drawable`.
  - `GlassDrawables.createGlassPillDrawable(float cornerRadiusDp, boolean isNight)` -> `Drawable`.
  - `GlassDrawables.createBackgroundGradient(int width, int height, boolean isNight)` -> `Drawable`.

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/theme/GlassDrawablesTest.java
package me.aap.fermata.ui.glass.theme;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;

@RunWith(RobolectricTestRunner.class)
public class GlassDrawablesTest {
    @Test
    public void testCardDrawableCornerRadiiAndColors() {
        Context context = ApplicationProvider.getApplicationContext();
        GlassThemeColors colors = GlassThemeColors.get(context, true);
        assertNotNull(colors);
        GradientDrawable card = (GradientDrawable) GlassDrawables.createGlassCardDrawable(context, 28f, true);
        assertNotNull(card);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.theme.GlassDrawablesTest"`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement `GlassThemeColors`, `GlassDrawables`, and `GlassDimensions`**

Port and adapt the color math and gradient/border construction from `CoDienMau.java`:
- Dark: `cardBg = 0x1A181C1E`, `border = 0x18FFFFFF`, text white / translucent white.
- Light: `cardBg = 0xE6F2F1EF`, `border = 0x14000000`, text dark slate.
- Radii: Card $28\text{ dp}$, Pill $20\text{ dp}$, Icon $16\text{ dp}$.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.theme.GlassDrawablesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/theme/ fermata/src/test/java/me/aap/fermata/ui/glass/theme/
git commit -m "feat(glass): implement glassmorphism colors, dimensions, and drawable shaders"
```

---

### Task 3: GPS Speedometer & Trip Computer Engine

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripView.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripControllerTest.java`

**Interfaces:**
- Consumes: Android `LocationManager`, GPS coordinates.
- Produces:
  - `GlassSpeedoTripController(Context, Listener)`
  - Metrics: `currentSpeed`, `tripDistanceMeters`, `driveTimeSeconds`, `averageSpeedKmh`
  - Unit support: `formatSpeed(float, boolean isMetric)`, `formatDistance(float, boolean isMetric)`
  - Action: `resetTrip()`

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripControllerTest.java
package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.*;
import org.junit.Test;
import android.location.Location;

public class GlassSpeedoTripControllerTest {
    @Test
    public void testDistanceAndAverageSpeedComputation() {
        GlassSpeedoTripController.TripState state = new GlassSpeedoTripController.TripState();
        state.addDistance(10000f, 600); // 10 km in 10 minutes (600s)
        assertEquals(10.0f, state.getDistanceKm(), 0.01f);
        assertEquals(60.0f, state.getAverageSpeedKmh(), 0.1f);
        assertEquals("00h 10m", state.getFormattedDriveTime());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.speedo.GlassSpeedoTripControllerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `GlassSpeedoTripController` and `GlassSpeedoTripView`**

Implement trip accumulators, metric/imperial unit formatting, GPS speed filter ($<3\text{ km/h}$ stationary threshold to avoid GPS drift), and UI view with digital speed, radial arc gauge, speed limit badge, and 3D car avatar.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.speedo.GlassSpeedoTripControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/speedo/ fermata/src/test/java/me/aap/fermata/ui/glass/speedo/
git commit -m "feat(glass): implement GPS speedometer and trip computer replacing fuel prices"
```

---

### Task 4: Turn-by-Turn Navigation Reader & Open-Meteo Weather Fallback

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/nav/AutomotiveNavNotificationService.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/nav/NavTurnInfo.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/weather/GlassOpenMeteoClient.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/dashboard/GlassNavWeatherView.java`
- Modify: `fermata/src/main/AndroidManifest.xml`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/nav/NavNotificationParserTest.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/weather/GlassOpenMeteoClientTest.java`

**Interfaces:**
- Consumes: `StatusBarNotification` from Google Maps, Waze; Open-Meteo REST endpoint (`api.open-meteo.com`).
- Produces:
  - `NavTurnInfo` (turn direction enum, distance text, ETA, next street, cancel `PendingIntent`).
  - `GlassOpenMeteoClient.fetchHourly(lat, lon, isMetric)` -> `HourlyForecast`.
  - `GlassNavWeatherView` seamlessly switches between active Nav state and hourly weather fallback.

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/nav/NavNotificationParserTest.java
package me.aap.fermata.ui.glass.nav;

import static org.junit.Assert.*;
import org.junit.Test;
import android.os.Bundle;

public class NavNotificationParserTest {
    @Test
    public void testNavigationNotificationExtractionWithoutLanguageHardcoding() {
        Bundle extras = new Bundle();
        extras.putString("android.title", "In 300m Turn Right onto Broadway");
        extras.putString("android.subText", "11:15");
        NavTurnInfo info = NavTurnInfo.fromNotification("com.google.android.apps.maps", extras, null);
        assertTrue(info.isActive());
        assertEquals("11:15", info.getEta());
        assertTrue(info.getInstruction().contains("Broadway"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.nav.NavNotificationParserTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `NavTurnInfo`, `AutomotiveNavNotificationService`, and `GlassOpenMeteoClient`**

- Register `AutomotiveNavNotificationService` in `AndroidManifest.xml` with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`.
- Parse turn direction, ETA, and progress bar without restrictive Vietnamese-only regex.
- Implement Open-Meteo REST query without API keys; cache responses for 20 minutes.
- In `GlassNavWeatherView`, observe navigation state: show turn arrow + street if nav active; show 24h hourly weather sky view if idle.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.nav.NavNotificationParserTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/nav/ fermata/src/main/java/me/aap/fermata/ui/glass/weather/ fermata/src/main/java/me/aap/fermata/ui/glass/dashboard/GlassNavWeatherView.java fermata/src/main/AndroidManifest.xml fermata/src/test/java/me/aap/fermata/ui/glass/nav/
git commit -m "feat(glass): implement turn-by-turn nav listener with hourly weather fallback"
```

---

### Task 5: Native Glass Media Player Card

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/media/GlassMediaPlayerView.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/media/GlassMediaPlayerController.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/media/GlassMediaPlayerControllerTest.java`

**Interfaces:**
- Consumes: `FermataServiceUiBinder`, `PlaybackSnapshot`.
- Produces: `GlassMediaPlayerView` displaying album art, title, artist, live progress slider, transport controls (`Previous`, `Play/Pause`, `Next`, `Favorite`).

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/media/GlassMediaPlayerControllerTest.java
package me.aap.fermata.ui.glass.media;

import static org.junit.Assert.*;
import org.junit.Test;
import me.aap.fermata.media.service.PlaybackSnapshot;

public class GlassMediaPlayerControllerTest {
    @Test
    public void testMediaCardBindsSnapshotCorrectly() {
        GlassMediaPlayerController controller = new GlassMediaPlayerController();
        PlaybackSnapshot snapshot = new PlaybackSnapshot(null, "Midnight Drive", "Synthwave City", 120000L, 250000L, true, false);
        controller.applySnapshot(snapshot);
        assertEquals("Midnight Drive", controller.getDisplayedTitle());
        assertEquals("Synthwave City", controller.getDisplayedArtist());
        assertTrue(controller.isPlaying());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.media.GlassMediaPlayerControllerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `GlassMediaPlayerController` and `GlassMediaPlayerView`**

Wire `PlaybackSnapshot` item changes, update $16\text{ dp}$ rounded artwork, timeline scrubber with current time / total duration, play/pause state synchronization, and favorite toggle dispatching to Fermata media library.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.media.GlassMediaPlayerControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/media/ fermata/src/test/java/me/aap/fermata/ui/glass/media/
git commit -m "feat(glass): implement native glass media player card bound to Fermata service"
```

---

### Task 6: 6-Slot Quick Navigation Bar & Shortcut Management

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/quickbar/GlassQuickShortcut.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/quickbar/GlassQuickBarManager.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/quickbar/GlassQuickBarView.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/quickbar/GlassShortcutPickerDialog.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/quickbar/GlassQuickBarManagerTest.java`

**Interfaces:**
- Consumes: `PreferenceStore`, `PREF_GLASS_QUICK_SHORTCUTS`.
- Produces:
  - 6 fixed slots: Slots 1–4 customizable (default: Empty), Slot 5: Addon Catalog, Slot 6: Exit.
  - `addOrUpdateShortcut(int slotIndex, GlassQuickShortcut)`
  - `removeShortcut(int slotIndex)`

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/quickbar/GlassQuickBarManagerTest.java
package me.aap.fermata.ui.glass.quickbar;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import me.aap.utils.pref.SharedPreferenceStore;

@RunWith(RobolectricTestRunner.class)
public class GlassQuickBarManagerTest {
    @Test
    public void testCleanInstallSlotsAreEmptyAndFixedSlotsExist() {
        var store = SharedPreferenceStore.create(ApplicationProvider.getApplicationContext());
        GlassQuickBarManager manager = new GlassQuickBarManager(store);
        // On clean install, customizable slots 1-4 must be empty
        for (int i = 0; i < 4; i++) {
            assertNull(manager.getCustomShortcut(i));
        }
        assertEquals("ADDON_CATALOG", manager.getFixedSlot(4).getId());
        assertEquals("EXIT", manager.getFixedSlot(5).getId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.quickbar.GlassQuickBarManagerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `GlassQuickBarManager`, `GlassQuickBarView`, and `GlassShortcutPickerDialog`**

- Provide 6-slot layout: slots 1–4 render `[+] Add Shortcut` when null; assigned shortcuts render frosted glass pill with icon and title.
- Long-press triggers remove/replace popup.
- Slot 5 fixed to Addons Catalog; Slot 6 fixed to Exit.
- Picker dialog presents all available addons and playable destinations with search and preview.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.quickbar.GlassQuickBarManagerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/quickbar/ fermata/src/test/java/me/aap/fermata/ui/glass/quickbar/
git commit -m "feat(glass): implement 6-slot quick navigation bar with customizable empty defaults"
```

---

### Task 7: Glass Dashboard Layout & Orchestration

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/dashboard/GlassDashboardLayout.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/dashboard/GlassDashboardController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/dashboard/GlassDashboardLayoutTest.java`

**Interfaces:**
- Consumes: `GlassSpeedoTripView`, `GlassNavWeatherView`, `GlassMediaPlayerView`, `GlassQuickBarView`.
- Produces: Fully orchestrated Glass Dashboard with header, 3-card body, bottom quick bar, and top-right Settings button. Hooked into `DashboardFragment`.

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/dashboard/GlassDashboardLayoutTest.java
package me.aap.fermata.ui.glass.dashboard;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import android.content.Context;

@RunWith(RobolectricTestRunner.class)
public class GlassDashboardLayoutTest {
    @Test
    public void testResponsiveLayoutModeResolution() {
        Context ctx = ApplicationProvider.getApplicationContext();
        GlassDashboardLayout layout = new GlassDashboardLayout(ctx);
        assertEquals(GlassDashboardLayout.Mode.WIDE, layout.computeMode(1920, 720)); // Ultrawide
        assertEquals(GlassDashboardLayout.Mode.WIDE, layout.computeMode(1280, 720)); // Standard 16:9
        assertEquals(GlassDashboardLayout.Mode.SQUARE, layout.computeMode(800, 800)); // 1:1
        assertEquals(GlassDashboardLayout.Mode.PORTRAIT, layout.computeMode(1080, 1920)); // Tall display
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.dashboard.GlassDashboardLayoutTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `GlassDashboardLayout` and `GlassDashboardController`**

- Port layout mathematics from `CoDienBoCuc.java`.
- Assemble Top Header: clock, Gregorian date (`SimpleDateFormat("EEEE, MMM d", Locale.getDefault())`), weather chip, battery/network chips, top-right Settings gear button.
- Integrate 3 cards (Speedo/Trip, Nav/Weather, Media Player) and bottom 6-slot Quick Bar.
- In `DashboardFragment.onCreateView` / `onViewCreated`:
  ```java
  if (activity.getRuntimeHostMode().usesAutomotivePresentation() && prefs.getIntPref(THEME_AA) == THEME_GLASS) {
      glassDashboardController = new GlassDashboardController(activity, view, binder);
      return;
  }
  ```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.dashboard.GlassDashboardLayoutTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/dashboard/ fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java fermata/src/test/java/me/aap/fermata/ui/glass/dashboard/
git commit -m "feat(glass): implement responsive Glass Dashboard layout and integrate into DashboardFragment"
```

---

### Task 8: Glass Addons Catalog Screen

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/catalog/GlassAddonsCatalogFragment.java`
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/catalog/GlassAddonCardHolder.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/catalog/GlassAddonsCatalogFragmentTest.java`

**Interfaces:**
- Consumes: `AddonManager.getActiveAddons()`, core media destinations (TV, YouTube, Radio, Folders, Playlists, Web, Drive, SMB).
- Produces: `GlassAddonsCatalogFragment` with header (`< Dashboard`, `ADDONS CATALOG (N)`, Settings gear) and responsive grid of frosted glass cards (no filter row).

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/catalog/GlassAddonsCatalogFragmentTest.java
package me.aap.fermata.ui.glass.catalog;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GlassAddonsCatalogFragmentTest {
    @Test
    public void testColumnCountComputation() {
        // Computes 3 to 6 columns based on width DP
        assertEquals(4, GlassAddonsCatalogFragment.computeColumnCount(1280, 2.0f));
        assertEquals(6, GlassAddonsCatalogFragment.computeColumnCount(1920, 2.0f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.catalog.GlassAddonsCatalogFragmentTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `GlassAddonsCatalogFragment` and `GlassAddonCardHolder`**

- Responsive column grid layout adapted from `LuoiApp.java`.
- Each addon renders as a $20\text{ dp}$ rounded frosted glass card with glowing brand-colored icon, bold title, and concise description.
- Single tap launches destination via `activity.openFragment(...)` or appropriate route.
- Header contains `[< Dashboard]` pill button and Settings gear.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.catalog.GlassAddonsCatalogFragmentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/catalog/ fermata/src/test/java/me/aap/fermata/ui/glass/catalog/
git commit -m "feat(glass): implement Glass Addons Catalog screen without category filter row"
```

---

### Task 9: Glass Settings & Notification Readiness Check

**Files:**
- Create: `fermata/src/main/java/me/aap/fermata/ui/glass/settings/GlassSettingsDecorator.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/fragment/SettingsFragment.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilder.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/control/PhoneReadiness.java`
- Create/Test: `fermata/src/test/java/me/aap/fermata/ui/glass/settings/GlassSettingsDecoratorTest.java`
- Modify: `fermata/src/test/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilderTest.java`

**Interfaces:**
- Consumes: `SettingsFragment`, `ReadinessPrefsBuilder`.
- Produces:
  - When on AA and `THEME_AA == THEME_GLASS`, decorates preference rows as $64\text{ dp}$ glass cards with $20\text{ dp}$ corner radii and glowing toggles.
  - Adds Notification Listener access verification and intent button to existing Readiness checks.

- [ ] **Step 1: Write the failing test**

```java
// fermata/src/test/java/me/aap/fermata/ui/glass/settings/GlassSettingsDecoratorTest.java
package me.aap.fermata.ui.glass.settings;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import android.content.Context;
import me.aap.fermata.ui.control.PhoneReadiness;

@RunWith(RobolectricTestRunner.class)
public class GlassSettingsDecoratorTest {
    @Test
    public void testNotificationListenerReadinessRegistered() {
        Context ctx = ApplicationProvider.getApplicationContext();
        boolean hasCheck = PhoneReadiness.hasNotificationListenerCheck(ctx);
        // Verify readiness check method exists and returns valid boolean
        assertNotNull(PhoneReadiness.createNotificationListenerSettingsIntent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.settings.GlassSettingsDecoratorTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `GlassSettingsDecorator` and extend `ReadinessPrefsBuilder`**

- In `ReadinessPrefsBuilder` / `PhoneReadiness`, add Notification Listener check (`Settings.Secure.getString(contentResolver, "enabled_notification_listeners")`).
- Add one-tap action on phone to launch `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- In `SettingsFragment`, apply `GlassSettingsDecorator` when `activity.isCarMode() && prefs.getIntPref(THEME_AA) == THEME_GLASS`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.ui.glass.settings.GlassSettingsDecoratorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fermata/src/main/java/me/aap/fermata/ui/glass/settings/ fermata/src/main/java/me/aap/fermata/ui/fragment/SettingsFragment.java fermata/src/main/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilder.java fermata/src/main/java/me/aap/fermata/ui/control/PhoneReadiness.java fermata/src/test/java/me/aap/fermata/ui/glass/settings/ fermata/src/test/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilderTest.java
git commit -m "feat(glass): style AA settings as glass cards and add notification listener readiness check"
```

---

### Task 10: Complete Verification, Universal Build & Regression Gate

**Files:**
- Test: All created test suites in `fermata/src/test/java/me/aap/fermata/ui/glass/`
- Build: Root project universal APK

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew :fermata:testAutoDebugUnitTest`
Expected: 100% tests PASS, zero failures.

- [ ] **Step 2: Run architecture boundary check**

Run: `./gradlew :fermata:testAutoDebugUnitTest --tests "me.aap.fermata.architecture.ArchitectureBoundaryTest"`
Expected: PASS (proves no illegal module coupling introduced).

- [ ] **Step 3: Build universal debug APK**

Run: `./gradlew :fermata:packageAutoDebugUniversalApk --no-daemon`
Expected: BUILD SUCCESSFUL, produces universal APK in `fermata/build/outputs/apk/auto/debug/`.

- [ ] **Step 4: Verify phone UI isolation**

Run unit tests confirming phone launcher activities never load `THEME_GLASS` or alter Classic/Material You views.

- [ ] **Step 5: Commit & Finalize**

```bash
git commit --allow-empty -m "chore(glass): finalize and verify complete Android Auto glass theme port"
```
