# Design Spec: Android Auto Glassmorphism Theme (Theme Kính)

- **Date:** 2026-10-13
- **Branch:** `feat/aa-glass-theme`
- **Target Platform:** Android Auto / DHU (Desktop Head Unit)
- **Status:** Draft / Proposed

---

## 1. Overview & Goals

### 1.1 Context
FermataX currently supports `Classic` (`THEME_CLASSIC = 6`) and `Material You` (`THEME_MATERIAL_YOU = 7`) on `main`. A new, modern visual style—the **Glassmorphism Theme (Theme Kính)**—is being introduced specifically for Android Auto / DHU, adapting and modernizing the dark translucent glass architecture from `carstream` (`scratch/carstream/src`).

### 1.2 Core Goals
1. **Isolated Automotive Theme**: Build `THEME_GLASS = 9` exclusively for Android Auto / DHU (`THEME_AA`). The phone UI (`THEME_MAIN`) remains 100% untouched and operates under Classic or Material You.
2. **Glass Dashboard**:
   - **Header**: Digital clock, international Gregorian date (`Locale`-driven), Open-Meteo weather chip, battery & network/GPS chips, and a top-right frosted glass Settings button.
   - **Speedo & Trip Card**: GPS speedometer with speed limit badge and trip computer (trip distance, average speed, drive time) replacing Vietnam-specific fuel prices.
   - **Turn-by-Turn Nav & Weather Fallback Card**: Real-time turn-by-turn guidance from Google Maps, Waze, etc. via `NotificationListenerService` (zero paid API dependencies). When no active navigation is running, automatically displays Open-Meteo 24-hour detailed weather forecast.
   - **Media Player Card**: Frosted glass media card bound directly to `FermataServiceUiBinder` / Fermata media engines.
   - **6-Slot Quick Navigation Bar**: 
     - Slots 1–4: User-customizable shortcuts (QuickStream style). Default state: **Empty** (`[+] Add Shortcut`).
     - Slot 5: Fixed system shortcut to **Addons Catalog**.
     - Slot 6: Fixed system shortcut to **Exit**.
3. **Glass Addons Catalog**:
   - A dedicated fullscreen responsive grid of glass cards showing all active FermataX addons/destinations (TV, YouTube, Radio, Folders, Playlists, Web, Drive, SMB).
   - Category filter tabs omitted for rapid, single-tap automotive navigation.
4. **Glass Settings**:
   - When `THEME_AA == THEME_GLASS`, `SettingsFragment` presents a unified frosted glass card layout ($64\text{ dp}$ height touch targets, glowing toggles, unit segment switches).
   - Integrates notification listener readiness check into `ReadinessPrefsBuilder`.
5. **Internationalization & Localization**:
   - Supports Metric (`km/h`, `km`, `°C`) and Imperial (`mph`, `miles`, `°F`).
   - Standard Gregorian calendar formatting; locale-agnostic navigation parsing.

### 1.3 Non-Goals
- Modifying phone UI or introducing phone-specific glass layouts.
- Rewriting core media engines (ExoPlayer/VLC) or existing addon registration pipelines.
- Using paid third-party navigation or weather APIs.

---

## 2. Architecture & Component Decomposition

All new glass theme components reside in a dedicated package to ensure complete isolation:
`me.aap.fermata.ui.glass.*`

```
me.aap.fermata.ui.glass/
├── theme/
│   ├── GlassThemeColors.java          # Day/Night color palettes adapted from CoDienMau
│   ├── GlassDrawables.java             # Frosted glass GradientDrawable & outline shaders
│   └── GlassDimensions.java            # Standard radii (28dp card, 20dp chip, 16dp icon)
├── dashboard/
│   ├── GlassDashboardController.java   # Orchestrates dashboard lifecycle, binder, and cards
│   ├── GlassDashboardLayout.java       # Custom responsive ViewGroup (adapting CoDienBoCuc)
│   ├── GlassSpeedoTripView.java        # GPS speedometer + Trip computer (distance, avg speed, time)
│   ├── GlassNavWeatherView.java        # Turn-by-turn listener with hourly weather fallback
│   ├── GlassMediaPlayerView.java       # Direct binding to FermataServiceUiBinder
│   └── GlassQuickBarView.java          # 6-slot navigation bar (4 customizable + 2 fixed)
├── nav/
│   ├── AutomotiveNavListenerService.java # NotificationListenerService for Maps/Waze
│   └── NavTurnEvent.java               # Turn direction, distance, ETA, next street model
├── weather/
│   └── GlassOpenMeteoClient.java       # Lightweight global Open-Meteo HTTP client
├── catalog/
│   ├── GlassAddonsCatalogFragment.java # Fullscreen glass grid for all addons
│   └── GlassAddonCardHolder.java       # Frosted glass card viewholder
└── settings/
    └── GlassSettingsDecorator.java     # Decorates preference items with glass cards on AA
```

---

## 3. Detailed Component Specifications

### 3.1 Glass Theme Tokens (`GlassThemeColors` & `GlassDrawables`)
Adapted from `CoDienMau.java`:
- **Card Background (`the`)**:
  - Dark mode: `0x1A181C1E` with subtle blur fallback.
  - Light mode: `0xE6F2F1EF`.
- **Card Border (`vien`)**:
  - Dark mode: `0x18FFFFFF` (1dp stroke).
  - Light mode: `0x14000000`.
- **Corner Radii**:
  - Main cards: $28\text{ dp}$.
  - Pill buttons / chips: $20\text{ dp}$.
  - Icons: $16\text{ dp}$.
- **Accent Palette**:
  - Speed / Nav / Progress: Cyan `#00E5FF` & Blue `#2979FF`.
  - Favorites: Rose `#FF1744`.
  - Exit / Alert: Crimson `#D50000`.

### 3.2 Glass Dashboard Layout (`GlassDashboardLayout`)
Adapted from `CoDienBoCuc.java`:
- Calculates layout mode based on screen dimensions and aspect ratio $R = \frac{W}{H}$:
  - `WIDE` ($R \ge 1.25$): 3-column card arrangement (Left: Speedo/Trip, Center: Nav/Weather, Right: Media Player), header top, quick bar bottom.
  - `SQUARE` ($1.0 \le R < 1.25$): 2-row adaptive grid.
  - `PORTRAIT` ($R < 1.0$): Vertically stacked cards for tall automotive displays (Tesla/Volvo AA screens).
  - `SPLIT`: Hides secondary widgets to prioritize media player and navigation.

### 3.3 Speedo & Trip Computer (`GlassSpeedoTripView`)
Replaces Vietnam fuel prices (`GiaXang.java`):
- **Inputs**: `LocationManager.GPS_PROVIDER` location updates.
- **Metrics**:
  - Current Speed: `location.getSpeed() * 3.6f` (km/h) or `* 2.23694f` (mph).
  - Trip Distance: Accumulated distance in meters $\ge 3\text{ km/h}$, converted to km / miles.
  - Driving Time: Accumulated seconds while moving.
  - Average Speed: $\frac{\text{Trip Distance}}{\text{Driving Time}}$.
- **Interactions**: Long-press (2s) on trip stats triggers Trip Reset dialog.

### 3.4 Turn-by-Turn Navigation & Weather Fallback (`GlassNavWeatherView`)
- **Active Navigation Mode**:
  - Monitored packages: `com.google.android.apps.maps`, `com.waze`, `com.here.app.maps`, `net.osmand`, `com.tomtom.speedcams.android.map` or notifications with `category == CATEGORY_NAVIGATION`.
  - Extracts turn icon (turn right/left, keep right, U-turn, roundabout), next street name, distance to turn, ETA, remaining distance.
  - Exit button dispatches the notification's action intent without hardcoded language matching.
- **Fallback Weather Mode**:
  - Triggered when no navigation notification is active or when permission is not yet granted.
  - Queries Open-Meteo for 24-hour hourly temperature, weather code, and conditions.
  - Renders ambient sky graphics (sun/clouds by day, stars/moon by night) and hourly timeline chips.

### 3.5 Media Player Card (`GlassMediaPlayerView`)
- Natively connects to `FermataServiceUiBinder`:
  - Listens to `PlaybackSnapshot` updates.
  - Displays album artwork with $16\text{ dp}$ rounded corners.
  - Displays title, artist/album, live progress bar, and formatted duration.
  - Transport buttons: Previous, Play/Pause (circular glowing button), Next, Favorite toggle (synced with Fermata media library).

### 3.6 6-Slot Quick Navigation Bar (`GlassQuickBarView`)
- **Structure**: Fixed 6 slots across bottom of screen:
  - **Slots 1–4 (User Customizable)**:
    - Default state on clean install: **Empty** (`[+] Add Shortcut`).
    - Tap on empty slot: Opens `GlassShortcutPickerDialog` listing all available addons, playlists, and folders.
    - Tap on assigned slot: Launches destination.
    - Long-press on assigned slot: Opens management popup (Remove / Replace).
    - Persisted in `PreferenceStore` as JSON under `PREF_GLASS_QUICK_SHORTCUTS`.
  - **Slot 5 (Fixed - Addons Catalog)**:
    - Icon: 4-square grid. Title: *Addons*.
    - Action: Opens `GlassAddonsCatalogFragment`.
  - **Slot 6 (Fixed - Exit)**:
    - Icon: Power symbol in soft red glass. Title: *Exit*.
    - Action: Closes automotive projection session safely.

### 3.7 Top Status Bar & Settings Access
- **Left**: Digital clock (12h/24h based on system settings), Gregorian date formatted via `SimpleDateFormat("EEEE, MMM d", Locale.getDefault())`.
- **Center**: Status chips for weather summary, battery level, network/GPS lock.
- **Top-Right**: $44\text{ dp}$ circular frosted glass button with gear icon (`ic_settings`).
  - Action: Directly opens `R.id.settings_fragment`.

### 3.8 Glass Addons Catalog (`GlassAddonsCatalogFragment`)
- Replaces legacy list with a full-screen frosted glass grid.
- **Header**: `[< Dashboard]` pill back button, title `ADDONS CATALOG (N)`, top-right Settings button.
- **Grid**:
  - Dynamically computes column count (3 to 6 columns) using the screen-width formula from `LuoiApp.java`.
  - No category filter tab row; all active addons appear immediately.
  - Each item is a rounded glass card displaying icon with brand tint, bold title, and subtitle.

### 3.9 Glass Settings & Readiness Integration
- When `THEME_AA == THEME_GLASS`:
  - Applies ambient depth gradient background.
  - Transforms preference list rows into $64\text{ dp}$ frosted glass cards with 20dp corner radii.
  - Segmented toggle for Units: `[ KM/H | MPH ]`.
- **Readiness Integration**:
  - Extends `PhoneReadiness.java` and `ReadinessPrefsBuilder.java` with a new item:
    `READINESS_NOTIFICATION_LISTENER` ("Notification Access for Navigation").
  - Verifies whether `enabled_notification_listeners` contains the package name.
  - Provides a single-tap intent button: `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` on the phone.

---

## 4. Preference Model & Backward Compatibility

- **Preferences in `MainActivityPrefs`**:
  ```java
  int THEME_GLASS = 9;
  Pref<Supplier<String>> PREF_GLASS_QUICK_SHORTCUTS = Pref.s("GLASS_QUICK_SHORTCUTS", "[]");
  Pref<IntSupplier> GLASS_SPEED_UNIT = Pref.i("GLASS_SPEED_UNIT", 0); // 0: km/h, 1: mph
  ```
- **Phone UI Invariant**:
  `getThemePref(false)` strictly ignores `THEME_GLASS` and resolves to `THEME_CLASSIC` or `THEME_MATERIAL_YOU`.
- **Automotive Invariant**:
  `getThemePref(true)` returns `THEME_GLASS` only when explicitly configured for Android Auto.

---

## 5. Verification & Testing Strategy

### 5.1 Unit Tests
- `GlassLayoutGeometryTest`: Validates WIDE, SQUARE, PORTRAIT, and SPLIT aspect ratio resolutions across screen sizes ($800\times480$, $1280\times720$, $1920\times720$, $1080\times1920$).
- `GlassQuickBarTest`: Validates 4 empty slots on fresh install, shortcut assignment, serialization/deserialization, and immutability of slots 5 and 6.
- `NavNotificationParserTest`: Validates extraction of turn icons, distance, and ETA from sample Google Maps and Waze status bar notifications without language hardcoding.
- `GlassThemePreferencesTest`: Confirms phone UI preferences are isolated from AA glass theme settings.

### 5.2 DHU & Automotive Verification
1. Launch Desktop Head Unit (DHU) with standard landscape ($1280\times720$), ultrawide ($1920\times720$), and portrait ($1080\times1920$) profiles.
2. Verify:
   - Dashboard renders smoothly at 60 fps without frame drops.
   - Speedometer updates cleanly via mock GPS locations.
   - Trigger navigation in Google Maps; verify center card transitions to turn-by-turn guidance with cyan arrow and ETA.
   - Stop navigation; verify center card smoothly falls back to Open-Meteo hourly weather.
   - Click Slot 5; verify Addon Catalog opens with unified glass cards and no category filter row.
   - Click top-right Settings icon; verify Settings renders with $64\text{ dp}$ glass preference cards.
   - Disconnect DHU; verify phone UI remains in Classic or Material You without visual alteration.
