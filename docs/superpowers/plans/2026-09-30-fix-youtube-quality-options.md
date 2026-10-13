# Fix YouTube Quality Options Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix YouTube quality selection in FermataX so that users can freely change resolution (or select "Auto") without having their choice overridden and reverted to highest quality.

**Architecture:** 
1. Convert `setHighestVideoQuality` into a one-shot operation: once the initial highest quality is applied, detach the `onStateChange` listener and clear pending timeouts so subsequent user-triggered bufferings/state changes are not overridden.
2. In `YoutubeQualityMenu`, call `clearHighestVideoQuality()` whenever the user makes a manual selection, ensuring any pending auto-highest listeners are cancelled.
3. In `YoutubeScripts`, always expose `'auto'` in the available quality list (`videoQualities`) with proper current-selection indication, and handle index selecting `'auto'` in `setVideoQuality` by resetting `setPlaybackQualityRange('default', 'default')` and `setPlaybackQuality('default')`.

**Tech Stack:** Java, Android WebView JavaScript bridge, YouTube HTML5 Player API, JUnit 4.

**Spec:** Issue #30 (`https://github.com/chuoinho/FermataX/issues/30`) - "Quality Options in Youtube Not Working".

## Global Constraints
- Preserve backward compatibility with existing YouTube playback controls and ad-skip/sponsor-block features.
- No regression on the "Auto-select highest video quality" preference: opening a new video should still default to the highest resolution if the setting is enabled.
- All unit tests in `:web:testMobileDebugUnitTest` must pass.

## Review Focus
1. User lowers quality to 480p/360p via YouTube web player UI: the video buffers and continues playing at 480p/360p without reverting to 1080p.
2. User selects a specific resolution from FermataX's quality menu: the choice is applied and not overridden by `setHighestVideoQuality`.
3. User selects "Auto" from FermataX's quality menu: playback quality range restriction is released (`default`), allowing YouTube adaptive bitrate to adapt to cellular connection.
4. Auto-select highest video quality is enabled: a newly opened video URL still initializes at highest available resolution.
5. Rapid video switching (SPA or playlist next): new video correctly initializes highest quality without leaving orphaned listeners.

---

### Task 1: Update YouTube Quality Scripts to Support "Auto" Option and Quality Reset

**Files:**
- Modify: `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeScripts.java:784-793`, `888-964`
- Modify: `modules/web/src/test/java/me/aap/fermata/addon/web/yt/YoutubeScriptsTest.java:28-47`

**Interfaces:**
- `YoutubeScripts.videoQualities(String eventFunction, int eventCode, String autoLabel)`: returns JS that populates `window.__fermataQualityLevels` including `'auto'` and sends semicolon-delimited list to the event callback.
- `YoutubeScripts.setVideoQuality(int index)`: returns JS that sets quality for `levels[index]`; if `level === 'auto'`, resets playback quality range and playback quality to `'default'`.

- [ ] **Step 1: Write failing unit tests in `YoutubeScriptsTest.java`**

Add tests ensuring:
1. `videoQualities()` script includes `'auto'` in `window.__fermataQualityLevels` and marks `'auto'` when active.
2. `setVideoQuality()` script handles `'auto'` by calling `setPlaybackQualityRange('default', 'default')` and `setPlaybackQuality('default')`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :web:testMobileDebugUnitTest --tests "me.aap.fermata.addon.web.yt.YoutubeScriptsTest"`
Expected: FAIL due to missing `'auto'` handling in scripts.

- [ ] **Step 3: Implement changes in `YoutubeScripts.java`**

1. In `YoutubeScripts.CLEAR_HIGHEST_VIDEO_QUALITY`: Ensure `window.__fermataManualQuality = true;` or detach hook resets cleanly.
2. In `YoutubeScripts.videoQualities()`:
   - Ensure `'auto'` is present in `unique` (e.g. `if (unique.indexOf('auto') < 0) unique.unshift('auto');`).
   - If `!window.__fermataQualityLocked` or `current === 'auto'`, mark `'auto'` with `*`.
3. In `YoutubeScripts.setVideoQuality(int index)`:
   - Check if `level === 'auto'`:
     ```javascript
     if (level === 'auto') {
       window.__fermataQualityLocked = false;
       try {
         if (typeof p.setPlaybackQualityRange === 'function')
           p.setPlaybackQualityRange('default', 'default');
         if (typeof p.setPlaybackQuality === 'function')
           p.setPlaybackQuality('default');
         return true;
       } catch (ignore) { return false; }
     }
     window.__fermataQualityLocked = true;
     ```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :web:testMobileDebugUnitTest --tests "me.aap.fermata.addon.web.yt.YoutubeScriptsTest"`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeScripts.java modules/web/src/test/java/me/aap/fermata/addon/web/yt/YoutubeScriptsTest.java
git commit -m "fix(web): support auto quality option and reset in youtube scripts"
```

---

### Task 2: Make `setHighestVideoQuality` Detach on Success & Update `YoutubeWebView`

**Files:**
- Modify: `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebView.java:1128-1175`
- Test: `modules/web/src/test/java/me/aap/fermata/addon/web/yt/YoutubeScriptsTest.java` (or new test in `modules/web/src/test/java/me/aap/fermata/addon/web/yt/YoutubeQualityScriptTest.java`)

**Interfaces:**
- `YoutubeWebView.setHighestVideoQuality()`: injects JS that finds player, applies highest quality, and promptly detaches `state.handler` once applied or confirmed.
- `YoutubeWebView.clearHighestVideoQuality()`: clears `window.__fermataQ` and removes any attached event listener.

- [ ] **Step 1: Write test for highest video quality script behavior**

Ensure that the script generated for `setHighestVideoQuality()` includes the `detach` logic (`removeEventListener('onStateChange', ...)`) once highest quality is applied or confirmed.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :web:testMobileDebugUnitTest --tests "me.aap.fermata.addon.web.yt.YoutubeScriptsTest"`
Expected: FAIL.

- [ ] **Step 3: Update `YoutubeWebView.java`**

In `setHighestVideoQuality()`:
- Define `detach()`:
  ```javascript
  function detach() {
    if (state.timeout) { clearTimeout(state.timeout); state.timeout = null; }
    if (state.player && state.handler) {
      try { state.player.removeEventListener('onStateChange', state.handler); } catch(e) {}
      state.handler = null;
    }
  }
  ```
- In `applyHighest(p)`:
  - If `p.getPlaybackQuality && p.getPlaybackQuality() === best`: call `detach()`, return `true`.
  - When `setPlaybackQualityRange(best, best)` / `setPlaybackQuality(best)` is invoked: call `detach()`, return `true`.
- Add an overall safety timeout (e.g. 15 seconds) so `state.handler` is guaranteed to be cleaned up even if video never transitions to state 1/3.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :web:testMobileDebugUnitTest --tests "me.aap.fermata.addon.web.yt.YoutubeScriptsTest"`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebView.java
git commit -m "fix(web): detach highest video quality listener upon successful application"
```

---

### Task 3: Update `YoutubeQualityMenu` to Clear Auto-Highest and Handle Selection

**Files:**
- Modify: `modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeQualityMenu.java:13-41`
- Test: `modules/web/src/test/java/me/aap/fermata/addon/web/yt/YoutubeQualityMenuTest.java` (create test)

**Interfaces:**
- `YoutubeQualityMenu.select(YoutubeWebView web, OverlayMenuItem item)`: clears highest video quality listener first, then calls `web.setVideoQuality(idx)`.

- [ ] **Step 1: Write test for `YoutubeQualityMenu`**

Verify that selecting an item invokes `clearHighestVideoQuality()` and `setVideoQuality()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :web:testMobileDebugUnitTest --tests "me.aap.fermata.addon.web.yt.YoutubeQualityMenuTest"`
Expected: FAIL.

- [ ] **Step 3: Implement changes in `YoutubeQualityMenu.java`**

In `select(YoutubeWebView web, OverlayMenuItem item)`:
```java
static boolean select(YoutubeWebView web, OverlayMenuItem item) {
    if (!(item.getData() instanceof Integer value) || ((value & QUALITY_MASK) == 0))
        return false;
    web.clearHighestVideoQuality();
    web.setVideoQuality(value & ~QUALITY_MASK);
    return true;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :web:testMobileDebugUnitTest --tests "me.aap.fermata.addon.web.yt.YoutubeQualityMenuTest"`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeQualityMenu.java
git commit -m "fix(web): clear highest video quality when manual quality is selected"
```

---

### Task 4: Full Regression and Verification

- [ ] **Step 1: Run all unit tests in `:web`**

Run: `.\gradlew.bat :web:testMobileDebugUnitTest`
Expected: ALL PASS.

- [ ] **Step 2: Check git status and diff for code hygiene**

Run: `git diff`
Ensure clean diff with no unwanted changes.
