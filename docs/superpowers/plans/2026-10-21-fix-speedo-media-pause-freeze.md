# Fix GPS Speedometer Freezing When Pausing Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the Glass Speedometer & Trip computer from freezing at its standing value (`≈ 25 KM/H`, `GPS signal lost`, `Missing data`) when media playback is paused on Android Auto.

**Architecture:** Maintain the app's `ForegroundService` status with `foregroundServiceType="mediaPlayback|location"` during active Android Auto projection sessions, ensuring Android OS location services (`LocationManager`) never throttle or cut off GPS fixes to the car dashboard when media is paused.

**Tech Stack:** Android Services, Android Auto SDK, Android 14/15/16 Foreground Service Types, LocationManager, JUnit.

## Global Constraints
- Do NOT commit, push, or merge git.
- Do NOT uninstall the app from the connected test phone `15c36230` (always use `adb install -r -d`).
- `MainActivityDelegate.java` non-blank line count must remain $\le 1291$ (currently 1270) — do not touch this file.
- All unit tests in `:fermata:testAutoDebugUnitTest` must pass 100%.

---

## Tasks

### Task 1: Manifest Permissions & Foreground Service Location Type
**Files:**
- Modify: `fermata/src/main/AndroidManifest.xml`
- Modify: `fermata/src/auto/AndroidManifest.xml`
- Modify: `fermata/src/testAuto/java/me/app/fermatax/auto/AutoManifestPolicyTest.java`

**Steps:**
- [ ] Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />` in `fermata/src/main/AndroidManifest.xml`.
- [ ] Update `android:foregroundServiceType="mediaPlayback|location"` for `FermataMediaService` in `fermata/src/main/AndroidManifest.xml`.
- [ ] Update `android:foregroundServiceType="mediaPlayback|location"` for `AutoFermataMediaService` in `fermata/src/auto/AndroidManifest.xml`.
- [ ] In `AutoManifestPolicyTest.java`, add unit test assertion verifying `AutoFermataMediaService` specifies `mediaPlayback|location`.
- [ ] Run test:
  `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.app.fermatax.auto.AutoManifestPolicyTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`

---

### Task 2: Preserve Foreground Service During Active Automotive Projection
**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/media/service/FermataMediaService.java`
- Modify: `fermata/src/test/java/me/aap/fermata/media/service/FermataMediaServiceNotificationTest.java`

**Steps:**
- [ ] In `FermataMediaService.java`, add helper method `isAutomotiveSessionActive()` checking:
  - `AutomotiveRuntimeGate.isProjectionActive()`
  - `AutomotiveConnectionState.get().isProjectionConnected()`
  - Or `BuildConfig.AUTO && me.app.fermatax.auto.CarService.isActive()`
- [ ] In `FermataMediaService.java:promote()`:
  - If SDK $\ge 29$, pass `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK | FOREGROUND_SERVICE_TYPE_LOCATION` (guarded by `hasLocationPermission()`).
- [ ] In `FermataMediaService.java:updateNotification()`:
  - In `if (st == STATE_PAUSED)` block:
    If `isAutomotiveSessionActive()` is true, call `playbackForeground.keepForeground()` instead of `leaveForeground(false)`, keeping the foreground service alive with the paused media notification so GPS is not severed.
- [ ] In `FermataMediaServiceNotificationTest.java`:
  - Add test verifying that in an active automotive session, paused state retains foreground playback/service status.
- [ ] Run test:
  `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.media.service.FermataMediaServiceNotificationTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`

---

### Task 3: Watchdog Responsiveness & Host Resume Recovery in Speedo Controller
**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripController.java`
- Modify: `fermata/src/test/java/me/aap/fermata/ui/glass/speedo/GlassSpeedoTripControllerTest.java`
- Modify: `build.gradle` (bump `appVersionCode = "326"`)

**Steps:**
- [ ] In `GlassSpeedoTripController.java`:
  - When `onHostResumed()` is invoked, reset `retryDelay = 5_000` and `nextRetry = 0` so location registration immediately re-probes without waiting for the exponential backoff.
  - In `heartbeat`: if `time - lastCallback > 8_000` and `retryDelay > 15_000`, reset `retryDelay = 5_000` to quickly re-probe when returning from brief signal dropouts.
- [ ] In `GlassSpeedoTripControllerTest.java`:
  - Add unit test verifying that `onHostResumed()` resets backoff delay.
- [ ] Run all unit tests:
  `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
- [ ] Bump version code to `326` in `build.gradle`.
- [ ] Build and install on device `15c36230`:
  `cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:assembleAutoDebug -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
  `adb -s 15c36230 install -r -d fermata\build\outputs\apk\auto\debug\fermata-auto-debug.apk`
