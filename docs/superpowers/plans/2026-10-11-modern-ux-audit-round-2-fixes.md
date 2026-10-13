# Modern UX Audit Round 2 Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all 12 findings (Issues 16–27) from `fermata-modern-ux-audit-round-2.md`: unify primary root Back navigation policy and implementation; ensure URL shortcuts actually load their designated URLs; guard against async race conditions in picker navigation and shortcut launching; provide clear loading, empty, and retry states; eliminate shortcut ID collisions; validate URL forms with immediate field errors; restrict catalog dragging to active edit mode; localize all hardcoded Quick Stream Vietnamese strings across 6 locales; distinguish city search network outages from empty search results; resolve narrow-grid catalog edit action overlaps; and cancel pending support banner animations on route changes.

**Tech Stack:** Android Java, AndroidX ConstraintLayout & RecyclerView, Material Components, JUnit 4, Robolectric.

**Spec:** `c:\Users\ttanh\Documents\Codex\2026-10-11\e-antigravity-fermata\outputs\fermata-modern-ux-audit-round-2.md`.

## Global Constraints
- 100% test pass rate across unit suites `:fermata:testMobileDebugUnitTest` and `:fermata:testAutoDebugUnitTest`.
- 100% string localization parity across all 6 locales (en, es, pt, ru, vi, zh-rTW).
- Preserve Android Auto DHU and Phone behavior cleanly.
- No regression on Boost RAM removal or previously fixed audit round 1 items.

---

### Task 1: Navigation Root Alignment, URL Fallback & Shortcut ID Collision (Audit Issues 16, 17, 20, 27)

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/policy/PhoneRootPolicy.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/policy/BackNavigationPolicy.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/view/TopBarController.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/AddShortcutBottomSheet.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamManager.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/policy/ModernPhoneRootAndBackTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/quickstream/QuickStreamManagerTest.java`

- [ ] **Step 1.1: Unify Primary Root Policy across Back navigation, TopBar, and Delegate (Issue 16)**
  - In `PhoneRootPolicy.java`: add `getPrimaryRootId(RuntimeHostMode mode, boolean isModern)`:
    - If `mode == RuntimeHostMode.PHONE && isModern`: return `R.id.addons_catalog_fragment`.
    - Else: return `R.id.dashboard_fragment`.
  - In `MainActivityDelegate.java:showPrimaryRoot()`: navigate to `PhoneRootPolicy.getPrimaryRootId(getRuntimeHostMode(), getPrefs().isModernTheme(this))`. When on Home/Settings root and Back is pressed, actually transition to the primary root (Addons) or exit cleanly, eliminating infinite loops.
  - In `TopBarController.java`: use `PhoneRootPolicy.getPrimaryRootId` to determine `primaryRoot` for TopBar back button visibility.
  - Update `ModernPhoneRootAndBackTest.java` to test Home -> Back -> Primary Root transition and Settings root -> Back -> Primary Root.
- [ ] **Step 1.2: Pass and load designated URLs in fallback navigation (Issue 17)**
  - In `QuickStreamBarView.java:fallbackNavigate`:
    - For `web://`, `http://`, `https://`: strip `web://` prefix if present, call `delegate.showFragment(R.id.web_browser_fragment, url)`.
    - For `yt://`, `youtube`: call `delegate.showFragment(R.id.youtube_fragment, url)`.
    - For media stream schemes (`rtsp://`, `rtmp://`): resolve via media engine or `DashboardPlayableNavigator`.
- [ ] **Step 1.3: Eliminate Shortcut ID Collisions with 64-bit/128-bit SHA-256 Hash (Issue 20)**
  - In `AddShortcutBottomSheet.java:createCustomUrlShortcut`: replace `Math.abs(url.hashCode())` with SHA-256 hex string (e.g., `"custom_url_" + sha256Hex(normalizedUrl)`).
  - In `QuickStreamManager.java:addShortcut`: match items by ID or matching URI to prevent collisions while properly updating existing URLs.
  - Add test in `QuickStreamManagerTest.java` adding `https://example.com/Aa` and `https://example.com/BB` verifying 2 separate shortcuts exist.
- [ ] **Step 1.4: Invalidate Out-of-Order Async Shortcut Clicks (Issue 27)**
  - In `QuickStreamBarView.java`: add `long shortcutClickGeneration` counter.
  - Capture `final long token = ++shortcutClickGeneration;` in `onShortcutClicked`.
  - In `onSuccess` and `onFailure`, verify `if (token != shortcutClickGeneration) return;`. Advance counter in `onDetachedFromWindow()`.
- [ ] **Step 1.5: Run tests and commit Task 1**
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.policy.*" --tests "me.aap.fermata.ui.modern.quickstream.*"`
  - Git commit: `fix(nav,quickstream): unify primary root back navigation, forward shortcut urls, eliminate id collision, and cancel stale clicks`

---

### Task 2: Picker Async Safety & States, Form Validation (Audit Issues 18, 19, 21)

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/AddShortcutBottomSheet.java`
- Modify: `fermata/src/main/res/layout/add_shortcut_bottom_sheet.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/quickstream/AddShortcutBottomSheetTest.java`

- [ ] **Step 2.1: Add generation token to `AddShortcutBottomSheet` navigation (Issue 18)**
  - Add `long drillDownGeneration` counter.
  - Increment token on `drillDown`, `navigateBack`, and `dismiss`.
  - In async `candidate.browsable.getChildren()` callbacks, check `if (!isAdded() || token != drillDownGeneration) return;`.
- [ ] **Step 2.2: Implement Loading, Empty, and Error/Retry States in Picker (Issue 19)**
  - In `add_shortcut_bottom_sheet.xml`: add loading indicator (ProgressBar), empty view, and error view with retry button.
  - In `AddShortcutBottomSheet.java`:
    - Show loading during drill down.
    - If `children.isEmpty()`, display "Chưa có nội dung" / Empty state.
    - If `onFailure`, display "Không tải được" + Retry button that re-invokes drill down for current candidate.
- [ ] **Step 2.3: In-Place URL Validation and Error Feedback (Issue 21)**
  - In `AddShortcutBottomSheet.java:submitCustomUrl`:
    - If input is empty: display error on `customUrlInput` (`customUrlInput.setError(...)`) and keep sheet open.
    - Parse URL: ensure host is non-null, non-empty, contains no spaces, and scheme is supported (`http`, `https`, `rtsp`, `rtmp`, `m3u`, `iptv`, etc.).
    - If malformed: show descriptive error message and keep sheet open with text intact for user correction.
- [ ] **Step 2.4: Run tests and commit Task 2**
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.quickstream.AddShortcutBottomSheetTest"`
  - Git commit: `fix(quickstream): add picker async generation token, loading/empty/error states, and in-place url validation`

---

### Task 3: Catalog Edit Drag Protection, City Search Network Resilience, Support Banner Timing (Audit Issues 22, 24, 25, 26)

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/AddonsCatalogFragment.java`
- Modify: `fermata/src/main/res/layout/addon_bento_card_item.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/OpenMeteoWeatherService.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/weather/BentoWeatherCardView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/view/PhoneSupportBannerController.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/AddonsCatalogFragmentTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/modern/weather/OpenMeteoWeatherServiceTest.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/view/PhoneSupportBannerControllerTest.java`

- [ ] **Step 3.1: Restrict Catalog Item Dragging to Active Edit Mode (Issue 22)**
  - In `AddonsCatalogFragment.java`: in `ItemTouchHelper.Callback`, return `editMode` for `isLongPressDragEnabled()`.
  - In `getMovementFlags()`, return `makeMovementFlags(0, 0)` when `!editMode`.
- [ ] **Step 3.2: Prevent Edit Actions from Overlapping Icon on Narrow Grids (Issue 25)**
  - In `addon_bento_card_item.xml`: add horizontal barrier or constraint `app:layout_constraintStart_toEndOf="@id/addon_icon"` on `addon_edit_actions` with `app:layout_constraintHorizontal_bias="1.0"`.
  - In `AddonsCatalogFragment.java`: ensure column span count adjusts when in edit mode on narrow widths (< 600dp) so card width never drops below minimum needed for icon + edit buttons.
- [ ] **Step 3.3: Differentiate Network Failures from Empty City Search Results (Issue 24)**
  - In `OpenMeteoWeatherService.java`: differentiate between network exception (pass null or error callback) and successful empty results (empty list).
  - In `BentoWeatherCardView.java`: when search fails due to network error, display "Không thể kết nối / Kiểm tra mạng" with Retry action; only display "Không tìm thấy" when server successfully returns 0 results.
- [ ] **Step 3.4: Cancel Pending Support Banner Animations on Route/Fragment Changes (Issue 26)**
  - In `PhoneSupportBannerController.java`: retain `showRunnable`.
  - In `onActivityEvent(FRAGMENT_CHANGED)` and `dismissInstant()`, remove callbacks for `showRunnable`.
  - In `showAnimated()`, verify active fragment is still valid root before animating.
- [ ] **Step 3.5: Run tests and commit Task 3**
  - Run `./gradlew :fermata:testMobileDebugUnitTest --tests "me.aap.fermata.ui.modern.*" --tests "me.aap.fermata.ui.view.*"`
  - Git commit: `fix(catalog,weather,banner): restrict dragging to edit mode, prevent icon overlap, separate network errors in city search, and cancel pending banner`

---

### Task 4: Quick Stream String Localization & Whole-Branch Integration (Audit Issue 23 & Build/Verify)

**Files:**
- Modify: `fermata/src/main/res/values/strings.xml`
- Modify: `fermata/src/main/res/values-vi/strings.xml`
- Modify: `fermata/src/main/res/values-es/strings.xml`
- Modify: `fermata/src/main/res/values-pt/strings.xml`
- Modify: `fermata/src/main/res/values-ru/strings.xml`
- Modify: `fermata/src/main/res/values-zh-rTW/strings.xml`
- Modify: `fermata/src/main/res/layout/add_shortcut_bottom_sheet.xml`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/QuickStreamBarView.java`
- Modify: `fermata/src/main/java/me/aap/fermata/ui/modern/quickstream/AddShortcutBottomSheet.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/fragment/LocalizationCoverageTest.java`

- [ ] **Step 4.1: Localize All Hardcoded Quick Stream Strings across All 6 Locales (Issue 23)**
  - Add string keys:
    - `quick_stream_empty_prompt`: "Tap + to pin your favorite channels, playlists, or apps" / "Chạm + để ghim kênh, danh sách phát hoặc ứng dụng yêu thích"
    - `quick_stream_custom_url_hint`: "Enter Web link / video stream URL" / "Nhập liên kết Web / Luồng video tùy ý"
    - `quick_stream_paste_clipboard`: "Paste from Clipboard" / "Dán từ Clipboard"
    - `quick_stream_pin_link`: "+ Pin Link" / "+ Ghim Link"
    - `quick_stream_empty_url_error`: "Please enter a valid URL" / "Vui lòng nhập liên kết hợp lệ"
    - `quick_stream_invalid_url_error`: "Invalid URL or unsupported stream format" / "Liên kết không hợp lệ hoặc định dạng chưa hỗ trợ"
    - `quick_stream_loading`: "Loading..." / "Đang tải..."
    - `quick_stream_no_items`: "No items found" / "Chưa có nội dung"
    - `quick_stream_load_failed`: "Failed to load content" / "Không tải được nội dung"
    - `quick_stream_retry`: "Retry" / "Thử lại"
  - Translate across all 6 locales (`values`, `values-vi`, `values-es`, `values-pt`, `values-ru`, `values-zh-rTW`).
  - Update `add_shortcut_bottom_sheet.xml` and `QuickStreamBarView.java` to use these strings.
  - Verify 100% key parity with `LocalizationCoverageTest`.
- [ ] **Step 4.2: Run Full Unit Test Suites**
  - Run `./gradlew :fermata:testMobileDebugUnitTest`
  - Run `./gradlew :fermata:testAutoDebugUnitTest`
  - Ensure 100% pass rate.
- [ ] **Step 4.3: Build Debug APKs & Deploy Live to Xiaomi Redmi Note 8 (`15c36230`)**
  - Run `./gradlew :fermata:assembleMobileDebug`
  - Run `./gradlew :fermata:assembleAutoDebug`
  - Install to `15c36230` and verify live UI with screencaps.
- [ ] **Step 4.4: Whole-Branch Review**
  - Dispatch Whole-Branch Reviewer subagent to audit git diff and ensure all 12 issues from round 2 are cleanly resolved.
