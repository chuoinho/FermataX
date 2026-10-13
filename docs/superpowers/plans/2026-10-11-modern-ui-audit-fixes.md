# Modern UI Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all P1 and P2 findings from `fermata-modern-ui-audit-2026-10-11.md`: completely retire the Boost RAM feature and redesign the vehicle monitor card with balanced metrics; remove unused/unsafe `BlurUtils`; allow landscape Addons scrolling; correct focus/pressed state selectors in 11 ripple drawables; guard against stale road safety claims and fix badge contrast; prevent race condition overwrites in artwork callbacks; and fix `SystemHardwareMonitor` multi-card observer lifecycle.

**Architecture:**
- **System & Vehicle Monitor (Issues 1 & 4)**: Completely remove `CarStreamOptimizerEngine` (eliminating `WebStorage.deleteAllData()` and fake RAM reclaimed numbers). Remove `system_boost_btn` and `system_clean_toast` from `system_monitor_card.xml`, `SystemMonitorCardView.java`, and `SystemMonitorDetailDialog.java`. Redesign `system_monitor_card.xml` to distribute RAM, Battery/Thermal, and Network metrics across the entire width in 3 balanced, legible columns.
- **Blur Cleanup (Issues 2 & 10)**: Delete `BlurUtils.java`. Remove all references to `hero_blurred_bg`, `hero_dark_scrim`, and `fastBlur` in `ModernDashboardController.java`.
- **Addons Catalog Landscape Scrolling (Issue 3)**: In `AddonsCatalogFragment.java`, remove unconditional `canScrollVertically() == false` in landscape. Allow vertical scrolling whenever total items exceed viewport height, and synchronize landscape detection with viewport dimensions.
- **Focus Drawables (Issue 5)**: Update the 11 ripple drawables (`add_shortcut_item_bg.xml`, `addon_bento_card_bg.xml`, `bento_weather_card_bg.xml`, `dashboard_settings_button_modern_bg.xml`, `hero_button_play_pause_bg.xml`, `hero_button_transport_bg.xml`, `hero_player_card_bg.xml`, `quick_stream_add_btn_bg.xml`, `quick_stream_add_slot_bg.xml`, `quick_stream_pill_bg.xml`, `system_monitor_card_bg.xml`) to nest `<selector>` inside the non-mask `<item>` of `<ripple>`, ensuring rotary and D-pad focus outlines work reliably.
- **Weather Road Safety Stale Guard & Contrast (Issues 6 & 13)**: In `BentoWeatherCardView.java` and `RoadSafetyRisk.java`, do not claim "ROAD SURFACE SAFE" when weather data is stale (> 1 hour) or missing essential road metrics. Adjust text color on caution/safe badges to achieve WCAG accessible contrast (black text on amber `#F59E0B`, darker green background for safe).
- **Artwork Callback Generation Token (Issue 7)**: In `PlaybackCardBinding.java`, add an atomic/monotonically increasing `artworkGeneration` counter so stale asynchronous image fetch callbacks are dropped if track/media changed.
- **Hardware Monitor Observer Lifecycle (Issue 8)**: In `SystemHardwareMonitor.java`, change `stopObserving` to accept the specific listener to remove, and only unregister system broadcast receivers when `listeners.isEmpty()`.

**Tech Stack:** Android Java, AndroidX ConstraintLayout & RecyclerView, JUnit 4, Robolectric.

**Spec:** `c:\Users\ttanh\Documents\Codex\2026-10-11\e-antigravity-fermata\outputs\fermata-modern-ui-audit-2026-10-11.md`.

## Global Constraints
- Remove Boost RAM button and functionality completely per user instruction; do not retain `WebStorage.deleteAllData()`.
- Preserve Android Auto DHU and Phone behavior cleanly.
- 100% test pass rate across unit suites `:fermata:testMobileDebugUnitTest` and `:fermata:testAutoDebugUnitTest`.
- 100% string localization parity across all 6 locales.

---

### Task 1: Retire Boost RAM, Delete BlurUtils & Redesign Vehicle Monitor Card (Audit Issues 1, 2, 4, 10)

**Files:**
- Delete: `fermata/src/main/java/me/aap/fermata/ui/modern/system/CarStreamOptimizerEngine.java`
- Delete: `fermata/src/test/java/me/aap/fermata/ui/modern/system/CarStreamOptimizerEngineTest.java`
- Delete: `fermata/src/main/java/me/aap/fermata/ui/modern/BlurUtils.java`
- Delete: `fermata/src/main/res/drawable/system_clean_boost_btn_bg.xml`
- Delete: `fermata/src/main/res/drawable/ic_system_clean_rocket.xml`
- Modify: `fermata/src/main/res/layout/system_monitor_card.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/system/SystemMonitorCardView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/system/SystemMonitorDetailDialog.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/ModernDashboardController.java`
- Modify: `fermata/src/test/java/me/aap/fermata/ui/modern/system/SystemMonitorCardViewTest.java`

- [x] **Step 1.1: Delete `CarStreamOptimizerEngine` and remove Boost RAM from `SystemMonitorCardView` & `SystemMonitorDetailDialog`**
  - Delete `CarStreamOptimizerEngine.java` and `CarStreamOptimizerEngineTest.java`.
  - Remove `system_boost_btn` and `system_clean_toast` from `system_monitor_card.xml`.
  - In `system_monitor_card.xml`, redesign the layout so the 3 metric sections (RAM, Battery/Thermal, Network) span 100% of the card width with balanced weights (`1.2`, `1.0`, `1.0`), legible typography (RAM % 13sp bold, Battery % 13sp bold, Operator/SSID 13sp bold), and comfortable padding.
  - In `SystemMonitorCardView.java`: remove `boostBtn`, `cleanToastView`, `startCleanOptimization()`. Clicking the card opens `SystemMonitorDetailDialog`.
  - In `SystemMonitorDetailDialog.java`: remove the boost/clean button and references to `CarStreamOptimizerEngine`.
- [x] **Step 1.2: Remove unused `BlurUtils` and blur logic from `ModernDashboardController.java`**
  - Delete `BlurUtils.java`.
  - In `ModernDashboardController.java`: remove `heroBlurredBg`, `heroDarkScrim`, `applyBlurredArtwork`, `clearBlurredBackground`, and `currentBlurredBitmap`. Directly apply bitmap to `heroAlbumArt`.
- [x] **Step 1.3: Update unit tests in `SystemMonitorCardViewTest.java`**
  - Remove boost click test; verify card inflation, metric updates, and detail dialog opening on card click.
- [x] **Step 1.4: Run tests and verify**
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.system.*"`
  - Verify 100% pass.
- [x] **Step 1.5: Commit Task 1**
  - Git commit: `refactor(system): retire boost ram and blur utils, redesign vehicle monitor card`

---

### Task 2: Fix Addons Landscape Scroll & Correct 11 Ripple Focus Drawables (Audit Issues 3, 5)

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/AddonsCatalogFragment.java`
- Modify: `fermata/src/main/res/drawable/add_shortcut_item_bg.xml`
- Modify: `fermata/src/main/res/drawable/addon_bento_card_bg.xml`
- Modify: `fermata/src/main/res/drawable/bento_weather_card_bg.xml`
- Modify: `fermata/src/main/res/drawable/dashboard_settings_button_modern_bg.xml`
- Modify: `fermata/src/main/res/drawable/hero_button_play_pause_bg.xml`
- Modify: `fermata/src/main/res/drawable/hero_button_transport_bg.xml`
- Modify: `fermata/src/main/res/drawable/hero_player_card_bg.xml`
- Modify: `fermata/src/main/res/drawable/quick_stream_add_btn_bg.xml`
- Modify: `fermata/src/main/res/drawable/quick_stream_add_slot_bg.xml`
- Modify: `fermata/src/main/res/drawable/quick_stream_pill_bg.xml`
- Modify: `fermata/src/main/res/drawable/system_monitor_card_bg.xml`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/AddonsCatalogFragmentTest.java`

- [x] **Step 2.1: Allow scrolling in `AddonsCatalogFragment.java` when items exceed viewport**
  - In `AddonsCatalogFragment.java`: remove `canScrollVertically() { return !isLandscape; }`.
  - Let `GridLayoutManager.canScrollVertically()` return `true` whenever item count and card height exceed the available RecyclerView viewport height, so all addon rows are accessible on landscape and low-height screens.
- [x] **Step 2.2: Correct focus/pressed state structure in 11 ripple drawables**
  - In all 11 XML drawables, nest `<selector>` under the content `<item>` of `<ripple>`:
    ```xml
    <ripple ...>
        <item android:id="@android:id/mask">...</item>
        <item>
            <selector>
                <item android:state_focused="true">...</item>
                <item android:state_pressed="true">...</item>
                <item>...</item>
            </selector>
        </item>
    </ripple>
    ```
    This ensures that D-pad and rotary focus outlines are properly rendered.
- [x] **Step 2.3: Test and verify**
  - Run `./gradlew :fermata:testMobileDebugUnitTest`
  - Verify all drawables parse without inflation errors and test suites pass.
- [x] **Step 2.4: Commit Task 2**
  - Git commit: `fix(ui): allow landscape addons scrolling and fix focus state in ripple drawables`

---

### Task 3: Stale Weather Guard, Artwork Callback Generation Token & Monitor Observer Lifecycle (Audit Issues 6, 7, 8, 13)

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/RoadSafetyRisk.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/control/PlaybackCardBinding.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/system/SystemHardwareMonitor.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardViewTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/system/SystemHardwareMonitorTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/control/PlaybackCardBindingTest.java`

- [x] **Step 3.1: Stale weather guard and badge accessible contrast**
  - In `BentoWeatherCardView.java`: if weather data is older than 60 minutes or fetch failed, hide or adjust the road safety banner so it does not falsely display "ROAD SURFACE SAFE".
  - In `RoadSafetyRisk.java` & `BentoWeatherCardView.java`: update badge colors:
    - CAUTION: use black text (`#000000`) on amber (`#F59E0B`) for > 8:1 contrast.
    - SAFE: use `#065F46` or `#047857` (darker green) with white text, or black text on light green for >= 4.5:1 contrast.
- [x] **Step 3.2: Artwork generation token in `PlaybackCardBinding.java`**
  - Add `long artworkGeneration` counter.
  - In `updateArtwork` and `loadItemArtwork`, capture `final long token = ++artworkGeneration;`.
  - In asynchronous callbacks, verify `if (closed || token != artworkGeneration) return;` before applying image.
- [x] **Step 3.3: Specific listener removal in `SystemHardwareMonitor.java`**
  - Change `stopObserving(Context context)` to support `stopObserving(Context context, Consumer<SystemHardwareMonitor> listener)`.
  - Remove only the specified listener from `listeners`. Unregister system broadcast receiver only when `listeners.isEmpty()`.
- [x] **Step 3.4: Test and verify**
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.system.SystemHardwareMonitorTest" --tests "me.aap.fermata.ui.modern.weather.*"`
- [x] **Step 3.5: Commit Task 3**
  - Git commit: `fix(weather,playback,system): add stale weather guard, artwork generation token, and per-listener unregistration`

---

### Task 4: Whole-Branch Integration, Build, and Live Device Verification

- [ ] **Step 4.1: Run full unit test suites**
  - Run `./gradlew :fermata:testMobileDebugUnitTest`
  - Run `./gradlew :fermata:testAutoDebugUnitTest`
- [ ] **Step 4.2: Build Debug APKs**
  - Run `./gradlew :fermata:assembleMobileDebug`
  - Run `./gradlew :fermata:assembleAutoDebug`
- [ ] **Step 4.3: Deploy and verify live on Xiaomi Redmi Note 8 (`15c36230`)**
  - Install APK and verify the redesigned vehicle monitor card without boost button and balanced metrics.
- [ ] **Step 4.4: Subagent Whole-Branch Review**
  - Dispatch Whole-Branch Reviewer subagent to audit git diff, code safety, and spec compliance.
