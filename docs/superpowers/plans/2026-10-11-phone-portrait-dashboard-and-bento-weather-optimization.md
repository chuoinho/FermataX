# Phone Portrait Dashboard & Bento Weather Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Elevate Bento Weather Card into a first-class dashboard centerpiece on phone by retiring Hero Media Card on phone portrait mode, granting full vertical height (~500-550dp) to weather card so Tier 1 full details (Daily 7-day forecast, Hourly forecast, dual-row metrics) render naturally, and adding an interactive Weather Detail Sheet modal on tap.

**Architecture:** 
- In `DashboardLayoutHelper.java`, differentiate Portrait (phone) vs Landscape (car/DHU). In Portrait, set `home_hero_player_card` to `View.GONE` and rechain `home_weather_card` directly to `home_system_monitor_card` and `quick_stream_bar_container`, granting weather card a generous vertical weight (~1.65f) so it receives ~500-550dp of height on smartphones. In Landscape, keep Hero Media Card visible and unchanged at 52% width.
- With ~500-550dp on phone, `BentoWeatherCardView` naturally exceeds 460dp and operates in Tier 1 (Full Mode with Daily 7-day forecast, Hourly forecast, dual-row metrics, and road safety advisory) without clipping or crowding.
- Implement `WeatherDetailDialog.java` and `bento_weather_detail_sheet.xml` so tapping the weather card opens a complete modal showing the full 7-day forecast, hourly forecast, all environmental metrics, AQI, road safety advisory, and a city search / refresh action, complete with localization across all 6 locales.

**Tech Stack:** Android Java, AndroidX ConstraintLayout, Custom View lifecycle, Open-Meteo Weather API, JUnit 4, Robolectric.

**Spec:** Option B approved in discussion: Retire Hero Media Card on phone's Home page portrait mode to dedicate vertical space to Bento Weather Card, Hardware Monitor, and Quick Stream. Task 2 (micro-spacing tweaking) removed per user request.

## Global Constraints
- Preserve Landscape (Car/DHU) completely: `home_hero_player_card` must remain VISIBLE in landscape with 52% width and proper audio playback controls and recents list.
- Dynamic theme parity: Use `?attr/colorSecondary` and `?attr/boxStrokeColor` for cards; adhere to dark mode and light mode surfaces.
- 100% localization parity across all 6 locales (`values`, `values-vi`, `values-es`, `values-pt`, `values-ru`, `values-zh-rTW`).
- 100% test pass rate across unit suites `:fermata:testMobileDebugUnitTest` and `:fermata:testAutoDebugUnitTest`.

## Review Focus
- Portrait stacking verification: In portrait, `home_hero_player_card` must have visibility `GONE` and no hanging constraints; `home_weather_card` top connects to PARENT, bottom to `home_system_monitor_card`.
- Landscape regression verification: In landscape, `home_hero_player_card` must have visibility `VISIBLE` and maintain horizontal chain with `home_weather_card`.
- Weather card click behavior: When location is present, clicking card opens `WeatherDetailDialog`; when location is absent, requests location permission / city search.
- Zero regression on Android Auto DHU (800x400 and 800x480).

---

### Task 1: Phone Portrait Layout Stacking & Hero Media Retirement

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/DashboardLayoutHelper.java:226-340`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/AutomotiveModernPresentationTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/DashboardLayoutHelperTest.java`

- [ ] **Step 1.1: Write failing tests for portrait layout without Hero Media Card**
  In `AutomotiveModernPresentationTest.java` and `DashboardLayoutHelperTest.java`:
  - Update `testDashboardLayoutHelperAppliesSystemMonitorConstraintsInPortrait()` and `testApplyGapsPortraitStacking()`:
    - Assert that in portrait mode, `home_hero_player_card` has visibility `View.GONE` and is cleared from constraints.
    - Assert that `home_weather_card` has `topToTop == PARENT_ID` and `bottomToTop == R.id.home_system_monitor_card` (or `quick_stream_bar_container` if monitor absent).
    - Assert that `home_system_monitor_card` has `topToBottom == R.id.home_weather_card` with `topMargin == gaps.verticalGap` and `bottomToTop == R.id.quick_stream_bar_container`.
    - Assert that `quick_stream_bar_container` has `topToBottom == R.id.home_system_monitor_card` with `topMargin == gaps.verticalGap` and `bottomToBottom == PARENT_ID` with `bottomMargin == gaps.rootPadding`.
    - Assert that portrait weights are: weather `1.65f`, system monitor `0.35f`, quick stream `0.85f`.
  - Verify that tests fail with `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.AutomotiveModernPresentationTest" --tests "me.aap.fermata.ui.modern.DashboardLayoutHelperTest"`.

- [ ] **Step 1.2: Implement portrait layout in `DashboardLayoutHelper.java`**
  - In `DashboardLayoutHelper.apply(...)`:
    - In landscape mode (`isLandscape == true`):
      - Ensure `cs.setVisibility(R.id.home_hero_player_card, View.VISIBLE)` is explicitly set so switching from portrait to landscape restores hero card.
    - In portrait mode (`isLandscape == false`):
      - Set `cs.setVisibility(R.id.home_hero_player_card, View.GONE)`.
      - Clear all constraints on `R.id.home_hero_player_card`.
      - Connect `R.id.home_weather_card` top to `PARENT_ID.top`, bottom to `hasSystemMonitor ? R.id.home_system_monitor_card : (hasQuickStream ? R.id.quick_stream_bar_container : PARENT_ID.bottom)`. Set verticalWeight to `1.65f`.
      - If `hasSystemMonitor`:
        - Connect `R.id.home_system_monitor_card` top to `R.id.home_weather_card.bottom` with margin `gaps.verticalGap`.
        - Connect `R.id.home_system_monitor_card` bottom to `hasQuickStream ? R.id.quick_stream_bar_container.top : PARENT_ID.bottom`. Set verticalWeight to `0.35f`.
      - If `hasQuickStream`:
        - Connect `R.id.quick_stream_bar_container` top to `hasSystemMonitor ? R.id.home_system_monitor_card.bottom : R.id.home_weather_card.bottom` with margin `gaps.verticalGap`.
        - Connect `R.id.quick_stream_bar_container` bottom to `PARENT_ID.bottom` with margin `gaps.rootPadding`. Set verticalWeight to `0.85f`.

- [ ] **Step 1.3: Run tests and verify**
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.AutomotiveModernPresentationTest" --tests "me.aap.fermata.ui.modern.DashboardLayoutHelperTest"`.
  - Ensure all assertions pass 100%.

- [ ] **Step 1.4: Commit Task 1**
  - Git commit with message: `feat(layout): retire hero media card on phone portrait and rechain weather to system monitor`

---

### Task 2: Interactive Weather Detail Modal Sheet & Localization

**Files:**
- Create: `fermata/src/main/res/layout/bento_weather_detail_sheet.xml`
- Create: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/WeatherDetailDialog.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java:922-930`
- Modify: `fermata/src/main/res/values/strings.xml`
- Modify: `fermata/src/main/res/values-vi/strings.xml`
- Modify: `fermata/src/main/res/values-es/strings.xml`
- Modify: `fermata/src/main/res/values-pt/strings.xml`
- Modify: `fermata/src/main/res/values-ru/strings.xml`
- Modify: `fermata/src/main/res/values-zh-rTW/strings.xml`
- Test: `fermata/src/test/java/me/aap/fermata/ui/fragment/LocalizationCoverageTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/weather/WeatherDetailDialogTest.java`

- [ ] **Step 2.1: Add localized strings for Weather Detail Sheet**
  - Add string keys in all 6 locales:
    - `weather_details_title`: "Weather Forecast & Details" / "Dự báo & Chi tiết thời tiết" / "Pronóstico y detalles del tiempo" / "Previsão e detalhes do tempo" / "Прогноз и детали погоды" / "天氣預報與詳情"
    - `weather_details_7day_title`: "7-Day Outlook" / "Dự báo 7 ngày" / "Pronóstico a 7 días" / "Previsão para 7 dias" / "Прогноз на 7 дней" / "7 天預報"
    - `weather_details_24h_title`: "Hourly Forecast" / "Dự báo theo giờ" / "Pronóstico por horas" / "Previsão por hora" / "Почасовой прогноз" / "逐時預報"
    - `weather_details_refresh`: "Refresh" / "Làm mới" / "Actualizar" / "Atualizar" / "Обновить" / "重新整理"
    - `weather_details_pressure`: "Pressure" / "Áp suất" / "Presión" / "Pressão" / "Давление" / "氣壓"

- [ ] **Step 2.2: Create `bento_weather_detail_sheet.xml` layout**
  - Top header with city name, condition, current temperature, and close button.
  - Section 1: Hourly Forecast (horizontal scroll).
  - Section 2: 7-Day Daily Forecast (clean list with day, weather icon, condition, min/max temp bar).
  - Section 3: Environmental Driving Metrics Grid (Precipitation %, Wind Speed & direction, Humidity %, UV Index, AQI with color indicator, Pressure, Road Safety Advisory).
  - Action footer with "Search City" and "Refresh" buttons.

- [ ] **Step 2.3: Implement `WeatherDetailDialog.java`**
  - Extends `Dialog` or BottomSheetDialog with themed styling (`?attr/colorSecondary` background, rounded corners).
  - Binds live `WeatherData` from `OpenMeteoWeatherService.get()`.
  - Connects "Refresh" button to `weatherService.refresh(true)` and "Search City" to `bentoWeatherCardView.showCitySearchDialog()`.

- [ ] **Step 2.4: Integrate into `BentoWeatherCardView.java`**
  - In `onWeatherCardClicked()`:
    - If `!weatherService.hasLocation()`: call `requestLocationOrShowDialog()`.
    - Else: instantiate and show `WeatherDetailDialog(getContext(), this, cachedData)`.

- [ ] **Step 2.5: Run tests and verify**
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.fragment.LocalizationCoverageTest"`
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.weather.WeatherDetailDialogTest"`
  - Verify all unit tests pass 100%.

- [ ] **Step 2.6: Commit Task 2**
  - Git commit with message: `feat(weather): implement WeatherDetailDialog modal sheet with full 6-locale strings`

---

### Task 3: Whole-Branch Integration, Build, and Live Device Verification

- [ ] **Step 3.1: Run full unit test suites**
  - Run `./gradlew :fermata:testMobileDebugUnitTest`
  - Run `./gradlew :fermata:testAutoDebugUnitTest`
- [ ] **Step 3.2: Build Debug APKs**
  - Run `./gradlew :fermata:assembleMobileDebug`
  - Run `./gradlew :fermata:assembleAutoDebug`
- [ ] **Step 3.3: Deploy and verify live on Xiaomi Redmi Note 8 (`15c36230`)**
  - Install APK via `adb -s 15c36230 install -r ...`.
  - Launch FermataX Modern Home on phone.
  - Chụp ảnh màn hình điện thoại (`screencap`) và trích xuất `uiautomator dump` để xác nhận:
    - Thẻ Thời tiết nhận chiều cao ~500-550dp, tự động kích hoạt Tier 1 (Dự báo 7 ngày + Dự báo theo giờ + Đầy đủ 2 hàng chỉ số).
    - Không có bất kỳ chi tiết nào bị cắt cụt hay đè vách.
    - Thẻ Hardware Monitor và Quick Stream nằm cân đối, thoáng đãng.
    - Chạm vào thẻ thời tiết mở BottomSheet chi tiết hoạt động mượt mà.
- [ ] **Step 3.4: Subagent Whole-Branch Review**
  - Dispatch Whole-Branch Reviewer subagent to audit git diff, code safety, and spec compliance.
