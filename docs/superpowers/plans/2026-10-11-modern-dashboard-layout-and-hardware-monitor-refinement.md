# Plan: Modern Dashboard Layout, Hardware Monitor Redesign & Adaptive Weather Refinement

**Goal:** Resolve all visual layout defects across phone and automotive displays: eliminate mismatched colors on Hardware Monitor, remove the redundant Details button, center the Boost RAM button, adapt Bento Weather dynamically across 4 height tiers without empty voids, and unify card margins/gaps so QuickStream is elevated without clipping.

## Architecture & Global Constraints
- Target branch: `feat/modern-ui-2.0-clean`
- Card background & stroke parity: All 4 dashboard cards (`home_hero_player_card`, `home_weather_card`, `home_system_monitor_card`, `quick_stream_pill`) must share the exact same surface background `?attr/colorSecondary` and border stroke `?attr/boxStrokeColor`.
- Touch ergonomics: All interactive buttons must have >= 40dp touch target on car screens. Rotary knob focus highlights must be preserved.
- No doubled margins: In ConstraintLayout vertical chains, margin must be set on only one end of each connection to avoid doubled gaps.
- Multi-tier weather adaptation: Bento Weather must adapt gracefully across all screen sizes (Tier 1 >= 460dp, Tier 2 340-460dp, Tier 3 260-340dp compact car, Tier 4 < 260dp). It must NEVER overflow and NEVER leave an empty black void.
- Playing & recents resilient: Hero card must retain adequate space for both media playback controls (track, artist, progress, album art, 3 transport buttons) and recent tracks list.

---

### Task 1: Hardware Monitor Card Redesign & Theme Surface Alignment
- **Files**:
  - `fermata/src/main/res/drawable/system_monitor_card_bg.xml`
  - `fermata/src/main/res/layout/system_monitor_card.xml`
  - `fermata/src/main/java/me/aap/fermata/ui/modern/system/SystemMonitorCardView.java`
  - `fermata/src/test/java/me/aap/fermata/ui/modern/system/SystemMonitorCardViewTest.java`
- **Requirements**:
  1. In `system_monitor_card_bg.xml`: Replace `?android:attr/colorBackground` with `?attr/colorSecondary` for both unfocused and state layers, ensuring 100% color consistency with Hero Card and Bento Weather Card.
  2. In `system_monitor_card.xml`:
     - Delete `system_monitor_detail_btn` ("Details" text button).
     - Restructure into a 2-part horizontal layout:
       - Left section (weight ~0.72):
         - Line 1: Speed icon + title (`system_monitor_title`).
         - Line 2: Horizontal metrics row containing RAM (percentage, 3dp progress bar, capacity text), Battery chip, and Network chip.
       - Right section (weight ~0.28, `gravity="center"`):
         - Place `system_boost_btn` ("BOOST RAM") vertically centered across the full height of the card.
     - Make the card root clickable to open the detail dialog if desired.
  3. In `SystemMonitorCardView.java`: Clean up references to `system_monitor_detail_btn`, preserve booster functionality and sheet invocation.
  4. In `SystemMonitorCardViewTest.java`: Update tests to verify removal of detail button and proper boost button centering.

### Task 2: Multi-Tier Dynamic Adaptive Weather Card (Bento Weather)
- **Files**:
  - `fermata/src/main/res/layout/bento_weather_card.xml`
  - `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
  - `fermata/src/test/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardViewTest.java`
- **Requirements**:
  1. In `bento_weather_card.xml`: Ensure `bento_metrics_container` has clean styling and its two sub-rows (`bento_metrics_row1` for Precipitation/Wind/Humidity and `bento_metrics_row2` for UV/AQI/DayRange) can be controlled independently.
  2. In `BentoWeatherCardView.java`:
     - Implement 4 distinct height tiers in `applyResponsiveVisibility(float heightDp)`:
       - **Tier 1 (heightDp >= 460)**: Full mode (Hourly, Daily 7-day list, full 2-row Metrics, Road safety advisory).
       - **Tier 2 (340 <= heightDp < 460)**: Standard HD car (Hourly, full 2-row Metrics, Road safety advisory, Daily list hidden).
       - **Tier 3 (260 <= heightDp < 340)**: Compact car / DHU (Hourly, **Single-Row Compact Metrics** showing Precipitation, Wind, Humidity in 1 row ~32dp high, Row 2 hidden, Divider hidden, Daily list hidden, Road safety compact badge).
       - **Tier 4 (heightDp < 260)**: Ultra compact (Hourly only, Metrics & Daily hidden).
     - In `onLayout()`: With single-row metrics occupying only ~32dp in Tier 3, it fits within the ~49dp remaining space below hourly cards, preventing overflow and preventing `metricsContainer` from being hidden.
  3. In `BentoWeatherCardViewTest.java`: Expand test suite to assert Tier 1, Tier 2, Tier 3 (single-row metrics visible, row 2 gone), and Tier 4.

### Task 3: Uniform Card Margins & Chained Gaps Alignment (Pull QuickStream Up)
- **Files**:
  - `fermata/src/main/java/me/aap/fermata/ui/modern/DashboardLayoutHelper.java`
  - `fermata/src/main/java/me/aap/fermata/ui/modern/DynamicDashboardGeometryEngine.java`
  - `fermata/src/main/res/layout/dashboard_modern_fragment.xml`
  - `fermata/src/test/java/me/aap/fermata/ui/modern/AutomotiveModernPresentationTest.java`
  - `fermata/src/test/java/me/aap/fermata/ui/modern/DynamicDashboardGeometryEngineTest.java`
- **Requirements**:
  1. In `DashboardLayoutHelper.java`:
     - Fix doubled margin calculation: In `applyGaps`, set vertical constraint margins on one side only:
       - `connect(home_hero_player_card, BOTTOM, home_system_monitor_card, TOP, 0)`
       - `connect(home_system_monitor_card, TOP, home_hero_player_card, BOTTOM, gaps.verticalGap)`
       - `connect(home_system_monitor_card, BOTTOM, quick_stream_bar_container, TOP, 0)`
       - `connect(quick_stream_bar_container, TOP, home_system_monitor_card, BOTTOM, gaps.verticalGap)`
     - Connect bottom of `quick_stream_bar_container` to parent with bottom margin `gaps.rootPadding` (8dp), lifting QuickStream up away from the bottom edge.
     - Unify horizontal and vertical gap sizes so all margins between cards are uniform (8dp on compact car, 10dp on standard/large).
  2. In `DynamicDashboardGeometryEngine.java`: Calibrate weights so Hero Card has sufficient space for playback controls & recents list, Hardware Monitor has comfortable ~58-62dp height, and QuickStream has ~105-115dp height.
  3. In `dashboard_modern_fragment.xml`: Align XML defaults with calibrated weights and uniform margins.
  4. Update unit test assertions in `AutomotiveModernPresentationTest.java` and `DynamicDashboardGeometryEngineTest.java`.

### Task 4: Full Suite Verification & Build Validation
- **Commands**:
  - `./gradlew :fermata:testMobileDebugUnitTest`
  - `./gradlew :fermata:testAutoDebugUnitTest`
  - `./gradlew :fermata:assembleMobileDebug`
  - `./gradlew :fermata:assembleAutoDebug`
  - Install to device `15c36230` and verify on live DHU and phone display.
