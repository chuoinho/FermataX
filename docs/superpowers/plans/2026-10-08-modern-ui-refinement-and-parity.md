# Modern UI 2.0 Architectural Refinement and Parity Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Goals
Address all 13 reported issues through agent-driven development:
1. **Weather Architecture & Cache Integrity**:
   - Remove redundant `WEATHER_AUTO_LOCATION` boolean toggle in Settings; default to automatic whenever location is available.
   - Prevent forced network re-fetch and re-geocoding on dashboard recreation/rotation when 45-minute cache is valid.
   - Remove hardcoded default Hanoi coordinates/name. If location is unavailable/denied, prompt user to select location or grant permission instead of pretending to be Hanoi.
   - Scope cache to location/city to avoid stale cross-city data on errors.
   - Align WMO rain codes (61-67 classified as Rain, not Drizzle), remove hardcoded Vietnamese strings from service, map `getWeatherIconRes` to `ic_weather_*` drawables instead of returning 0.
   - Temperature unit "Auto" selects Fahrenheit when locale country is "US".
2. **QuickStream Persistence & Default Purge**:
   - Purge hardcoded Vietnamese default shortcuts (`getDefaultShortcuts()` returns empty list).
   - Migrate persistence from volatile runtime integer IDs (`fragment://<R.id>`, `R.drawable.<id>`) to stable string identifiers (`addon://<name>` / `fragment://<name>`, `iconName`) with backwards compatibility.
3. **Dead Code Purge & Theme Sync Stabilization**:
   - Delete `VoiceOverlayView.java`, `VoiceVisualizerView.java`, `VoiceOverlayViewTest.java`, and unused voice overlay XML layouts.
   - Delete unused `HomeFragment.java`, `home_modern_fragment.xml` (portrait & landscape), and `HomeFragmentTest.java`.
   - Prevent double `recreate()` / activity flicker during Auto theme and nav bar position mirroring.
   - Implement `THEME_MODERN` styling in `MainActivityDelegate.setTheme()` using `AppTheme.MaterialYou`.
   - Add missing `contentDescription` / accessibility tags across modern layouts.
4. **Settings Permissions Check & Test Coverage Expansion**:
   - Provide a clear, accessible permission check entry in Settings.
   - Add unit tests for Phone Modern root switching to Addons, Modern Back/topbar policies, weather caching integrity, and QuickStream stable IDs.

---

### Task 1: Weather Architecture & Cache Integrity
**Files:**
- `fermata/src/main/java/me/aap/fermata/ui/modern/weather/OpenMeteoWeatherService.java`
- `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
- `fermata/src/main/java/me/aap/fermata/ui/modern/weather/WeatherData.java`
- `fermata/src/main/java/me/aap/fermata/ui/fragment/InterfacePrefsBuilder.java`
- `fermata/src/test/java/me/aap/fermata/ui/modern/weather/OpenMeteoWeatherServiceTest.java`

- [ ] **Step 1.1**: Remove `WEATHER_AUTO_LOCATION` boolean toggle from `InterfacePrefsBuilder.java`.
- [ ] **Step 1.2**: Update `OpenMeteoWeatherService.java`:
  - Remove hardcoded default coordinates and city name (no automatic fallback to Hanoi). Provide `hasLocation()`.
  - Fix `updateLocationFromDevice`: Do NOT pass `forceRefresh=true` if existing cached weather for the resolved location is still within TTL.
  - Bind cache to location (city/coordinates); if requested location differs from cached data, do not return stale cached data.
  - Align WMO rain codes: 61, 63, 65, 66, 67 must map to Rain in both `getWeatherDescription` and `getWeatherDescriptionRes`.
  - Implement `getWeatherIconRes(weatherCode)` returning `R.drawable.ic_weather_*`.
  - Fix `getWeatherDescription(weatherCode)` to avoid hardcoded non-localized strings.
- [ ] **Step 1.3**: Update `BentoWeatherCardView.java`:
  - When constructed / recreated, if `weatherService.getCachedWeather()` is valid, display it immediately and do not force refresh over network.
  - If no location has been granted/set (`!weatherService.hasLocation()`), display prompt to grant permission or select location.
  - In `formatTemp()`, evaluate Fahrenheit when `Locale.getDefault().getCountry().equalsIgnoreCase("US")`.
- [ ] **Step 1.4**: Update `OpenMeteoWeatherServiceTest.java` to test:
  - Cache validity with matching vs non-matching location.
  - Weather icon resource mapping.
  - Rain code classification consistency.
  - Verification: `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.weather.*"`

---

### Task 2: QuickStream Stability, Custom Persistence & Default Purge
**Files:**
- `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamShortcut.java`
- `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamManager.java`
- `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/AddShortcutBottomSheet.java`
- `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java`
- `fermata/src/test/java/me/aap/fermata/ui/modern/quickstream/QuickStreamManagerTest.java`
- `fermata/src/test/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarViewTest.java`

- [ ] **Step 2.1**: In `QuickStreamManager.java`, set `getDefaultShortcuts()` to return `Collections.emptyList()` (purge hardcoded defaults).
- [ ] **Step 2.2**: Update `QuickStreamShortcut.java` to support stable string identifiers for target URIs (e.g. `addon://folders`, `addon://tv`, `addon://youtube`) and stable icon identifiers (`iconName`), while keeping backwards-compatible integer deserialization.
- [ ] **Step 2.3**: In `AddShortcutBottomSheet.java`, construct candidate items with stable `addon://<name>` target URIs and icon resource names instead of volatile compile-time integer IDs.
- [ ] **Step 2.4**: In `QuickStreamBarView.java`, resolve `addon://` URIs and icon resources stably via `DashboardItems.nameToId()`, ensuring shortcuts survive app updates and re-installations.
- [ ] **Step 2.5**: Update unit tests in `QuickStreamManagerTest.java` and `QuickStreamBarViewTest.java` to verify serialization and navigation with stable URIs.
  - Verification: `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.quickstream.*"`

---

### Task 3: Dead Code Removal & Theme Switching Stabilization
**Files:**
- Delete: `fermata/src/main/java/me/aap/fermata/ui/modern/voice/VoiceOverlayView.java`
- Delete: `fermata/src/main/java/me/aap/fermata/ui/modern/voice/VoiceVisualizerView.java`
- Delete: `fermata/src/test/java/me/aap/fermata/ui/modern/voice/VoiceOverlayViewTest.java`
- Delete: `fermata/src/main/res/layout/voice_overlay_view.xml`
- Delete: `fermata/src/main/res/layout/voice_suggestion_chip.xml`
- Delete: `fermata/src/main/java/me/aap/fermata/ui/modern/HomeFragment.java`
- Delete: `fermata/src/main/res/layout/home_modern_fragment.xml`
- Delete: `fermata/src/main/res/layout-land/home_modern_fragment.xml`
- Delete: `fermata/src/test/java/me/aap/fermata/ui/modern/HomeFragmentTest.java`
- Update: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java`
- Update: `fermata/src/main/res/values/ids.xml`
- Update: `fermata/src/main/res/layout/main_activity_modern_left.xml`, `main_activity_modern_bottom.xml`, `dashboard_modern_fragment.xml`, `bento_weather_card.xml`, `quick_stream_bar_view.xml`, `add_shortcut_item.xml`, `quick_stream_pill_item.xml`

- [ ] **Step 3.1**: Delete `VoiceOverlayView.java`, `VoiceVisualizerView.java`, `VoiceOverlayViewTest.java`, and layout files. Remove unused import and `showVoiceOverlay()` reference in `MainActivityDelegate.java`.
- [ ] **Step 3.2**: Delete `HomeFragment.java`, its portrait/landscape layouts, `HomeFragmentTest.java`, and clean up `home_modern_fragment` ID references in `ids.xml` and routing policies.
- [ ] **Step 3.3**: In `MainActivityDelegate.java`:
  - In `setTheme()`: Map `THEME_MODERN` to `R.style.AppTheme_MaterialYou`.
  - In `onPreferenceChanged()`: Prevent double `recreate()` when `THEME_MAIN` is mirrored to `THEME_AA` or when `NAV_BAR_POS_AA` is mirrored to `NAV_BAR_POS` by checking if the target already has the value before writing or suppressing redundant trigger.
- [ ] **Step 3.4**: Add missing `android:contentDescription` or `android:importantForAccessibility="no"` across modern layouts for full accessibility compliance.
- [ ] **Step 3.5**: Verify compilation and clean tests:
  - `./gradlew :fermata:testMobileDebugUnitTest`

---

### Task 4: Permissions System in Settings & Test Coverage Expansion
**Files:**
- `fermata/src/main/java/me/aap/fermata/ui/fragment/SettingsFragment.java`
- `fermata/src/main/java/me/aap/fermata/ui/fragment/ReadinessPrefsBuilder.java`
- `fermata/src/test/java/me/aap/fermata/ui/activity/ModernLayoutRoutingTest.java`
- `fermata/src/test/java/me/aap/fermata/ui/policy/BackNavigationPolicyTest.java`
- `fermata/src/test/java/me/aap/fermata/ui/policy/TopBarPolicyTest.java`

- [ ] **Step 4.1**: In `SettingsFragment.java`, make the system permissions & readiness check easily discoverable and accessible (ensure `ReadinessPrefsBuilder` or direct permission check is prominently available in Settings with clear localization: "Quyền ứng dụng & Trạng thái hệ thống" / "App Permissions & Readiness").
- [ ] **Step 4.2**: Write unit tests in `ModernLayoutRoutingTest.java` / `BackNavigationPolicyTest.java` / `TopBarPolicyTest.java`:
  - Test phone Modern UI root switching to `AddonsCatalogFragment` (`R.id.addons_catalog_fragment`).
  - Test back button from Addons / Settings returning to Home in Modern mode.
  - Test TopBar policy in Modern mode.
  - Test weather and QuickStream behavior on automotive presentation mode.
- [ ] **Step 4.3**: Run full unit test suite and both mobile and auto builds:
  - `./gradlew :fermata:testMobileDebugUnitTest`
  - `./gradlew :fermata:assembleMobileDebug`
  - `./gradlew :fermata:assembleAutoDebug`
