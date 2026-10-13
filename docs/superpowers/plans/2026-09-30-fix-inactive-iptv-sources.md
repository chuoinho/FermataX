# Fix Inactive IPTV Sources Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable users to view, edit, and delete inactive or inaccessible IPTV sources (M3U, Xtream Codes, Stalker) directly from the UI, resolving Issue #27.

**Architecture:** 
1. Introduce `UnavailableTvSourceItem` which implements `TvSourceItem`. It encapsulates a source that failed to load (network offline, host down, invalid credentials, HTTP error), displaying its configured name and an "Unavailable" subtitle.
2. Update `TvRootItem.listChildren()`: instead of dropping failed sources, map failed source IDs to `UnavailableTvSourceItem` and preserve them in the list in their original order.
3. Handle `UnavailableTvSourceItem` in `TvRootItem.itemRemoved(...)`: when deleted, purge its preferences via `sources.removeSource(id)`.
4. Update `TvFragment`:
   - When editing an `UnavailableTvSourceItem`, route to the appropriate provider editor (M3U, Xtream, or Stalker) based on `source.getSourceType()`.
   - Update `showFailedSources` messaging to let the user know failed sources can be edited or deleted from the list.

**Tech Stack:** Java, Android, JUnit 4, Fermata Media Library.

**Spec:** GitHub Issue #27 (`https://github.com/chuoinho/FermataX/issues/27`) - "cannot delete inactive iptv stream".

## Global Constraints
- Do not re-introduce Issue #25: available sources must still load and not be blocked if one source fails.
- All unit tests in `:tv:testMobileDebugUnitTest` must pass.

## Review Focus
1. An M3U source whose URL is offline/404 is still displayed in the TV list as an `UnavailableTvSourceItem` instead of disappearing.
2. An Xtream source whose credentials/host are invalid is still displayed in the TV list as an `UnavailableTvSourceItem`.
3. Long-pressing an `UnavailableTvSourceItem` provides "Delete" and "Edit" menu options.
4. Deleting an `UnavailableTvSourceItem` completely purges its entry from `tv.sources` preference store.
5. Editing an `UnavailableTvSourceItem` opens the proper edit screen allowing the user to correct the URL or credentials.

---

### Task 1: Create `UnavailableTvSourceItem` and Update `TvRootItem`

**Files:**
- Create: `modules/tv/src/main/java/me/aap/fermata/addon/tv/UnavailableTvSourceItem.java`
- Modify: `modules/tv/src/main/java/me/aap/fermata/addon/tv/TvRootItem.java:133-175`, `270-282`
- Modify: `modules/tv/src/main/res/values/strings.xml`
- Modify: `modules/tv/src/test/java/me/aap/fermata/addon/tv/TvRootItemTest.java`

**Interfaces:**
- `UnavailableTvSourceItem`: implements `TvSourceItem`.
  - `int getSourceId()`: returns source ID.
  - `String getSourceType()`: returns "m3u", "xtream", or "stalker".
  - `FutureSupplier<List<Item>> listChildren()`: returns empty list.
  - `FutureSupplier<String> buildSubtitle()`: returns localized "Unavailable" string.

- [ ] **Step 1: Write failing unit test in `TvRootItemTest.java`**

Add tests to verify:
1. `TvRootItem.createUnavailableSource(id, name, failure)` returns an `UnavailableTvSourceItem`.
2. `UnavailableTvSourceItem` exposes the correct source ID, source type, and empty children list.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :tv:testMobileDebugUnitTest --tests "me.aap.fermata.addon.tv.TvRootItemTest"`
Expected: FAIL due to missing `UnavailableTvSourceItem`.

- [ ] **Step 3: Implement `UnavailableTvSourceItem.java` and update `TvRootItem.java`**

1. Create `UnavailableTvSourceItem.java`.
2. In `TvRootItem.listChildren()`: When `loadAvailableSources` encounters a failure or null, record it in `unavailable` map as `UnavailableTvSourceItem`. Assemble `children` including both available and unavailable items in order of `ids`.
3. In `TvRootItem.itemRemoved(TvSourceItem i)`: if `i instanceof UnavailableTvSourceItem`, call `sources.removeSource(i.getSourceId())`.
4. Add string `tv_source_unavailable` to `modules/tv/src/main/res/values/strings.xml`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :tv:testMobileDebugUnitTest --tests "me.aap.fermata.addon.tv.TvRootItemTest"`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add modules/tv/src/main/java/me/aap/fermata/addon/tv/UnavailableTvSourceItem.java modules/tv/src/main/java/me/aap/fermata/addon/tv/TvRootItem.java modules/tv/src/main/res/values/strings.xml modules/tv/src/test/java/me/aap/fermata/addon/tv/TvRootItemTest.java
git commit -m "feat(tv): retain unavailable sources in list as manageable items"
```

---

### Task 2: Support Edit and Deletion of Unavailable Sources in `TvFragment`

**Files:**
- Modify: `modules/tv/src/main/java/me/aap/fermata/addon/tv/TvFragment.java:182-248`, `428-444`
- Modify: `modules/tv/src/main/res/values/strings.xml`
- Test: `modules/tv/src/test/java/me/aap/fermata/addon/tv/TvFragmentTest.java` or `TvSourceRepositoryTest.java`

**Interfaces:**
- `TvFragment.contextMenuItemSelected`: handles `UnavailableTvSourceItem` for `R.id.edit` based on `source.getSourceType()`.

- [ ] **Step 1: Write unit test in `TvSourceRepositoryTest.java` for removing unavailable source prefs**

Verify removing an unavailable source cleanly cleans up repository source IDs and its specific prefs.

- [ ] **Step 2: Run test to verify it passes / fails**

Run: `.\gradlew.bat :tv:testMobileDebugUnitTest --tests "me.aap.fermata.addon.tv.TvSourceRepositoryTest"`

- [ ] **Step 3: Update `TvFragment.java`**

1. In `contextMenuItemSelected`: if `source instanceof UnavailableTvSourceItem`, open edit dialog for M3U, Xtream, or Stalker according to `source.getSourceType()`.
2. In `showFailedSources`: clarify in the alert dialog that failed sources are marked as unavailable in the list and can be edited or deleted.

- [ ] **Step 4: Run all tv unit tests**

Run: `.\gradlew.bat :tv:testMobileDebugUnitTest`
Expected: ALL PASS.

- [ ] **Step 5: Commit changes**

```bash
git add modules/tv/src/main/java/me/aap/fermata/addon/tv/TvFragment.java modules/tv/src/main/res/values/strings.xml modules/tv/src/test/java/me/aap/fermata/addon/tv/TvSourceRepositoryTest.java
git commit -m "fix(tv): allow editing and deleting unavailable tv sources from fragment"
```

---

### Task 3: Full Regression and Verification

- [ ] **Step 1: Run full project test suite**

Run: `.\gradlew.bat :tv:testMobileDebugUnitTest :web:testMobileDebugUnitTest`
Expected: ALL PASS.

- [ ] **Step 2: Check git status and diff for code hygiene**

Run: `git diff`
Ensure clean diff with no unwanted changes.
