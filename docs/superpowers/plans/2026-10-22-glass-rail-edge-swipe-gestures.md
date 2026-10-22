# Glass Theme Rail & Edge Swipe Gestures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement context-aware edge swipe gestures on Android Auto/DHU in Glass Theme: swipe inward on the collapsed rail grabber handle to open the navigation rail, and swipe inward along the screen edge outside the handle to trigger Back (or exit app if at root Dashboard), with no haptic feedback.

**Architecture:** `GlassNavigationRailView` sits as an overlay over the automotive screen. We enhance `grabberHandle`'s touch listener to arbitrate between vertical drag (repositioning) and horizontal swipe-in (open rail). Additionally, `GlassNavigationRailView` intercepts edge touches along the rail's active edge (left or right) outside the handle's dynamic vertical bounds, dispatching `onBackPressed()` (which handles Back or app exit at root) while remaining 100% transparent to normal touches elsewhere.

**Tech Stack:** Android SDK, Android Auto Car SDK, View touch event system (`onInterceptTouchEvent`, `onTouchEvent`), Robolectric, JUnit 4.

**Spec:** User requirements conversation 508ab038-20ce-42b4-8bdf-b29fe28b8938:
1. Glass Theme on Android Auto/DHU only (`isCar() && isGlassTheme()`).
2. Swipe inward on the collapsed rail grabber handle opens the rail (`expand()`).
3. Vertical drag on the grabber handle continues to reposition the handle up/down along the edge (`translationY`).
4. Swipe inward from the rail's screen edge outside the grabber handle triggers Back as if tapping the topbar Back button (`activity.onBackPressed()`).
5. If at root Dashboard, edge swipe exits the app (`activity.finish()`).
6. Active edge tracks rail position (`isRight`: right edge when rail on right, left edge when rail on left).
7. No haptic feedback ("bỏ phản hồi rung haptic").

## Global Constraints

- Do NOT execute `git commit`, `git push`, or `git merge`.
- Do NOT touch `MainActivityDelegate.java` (line count must remain $\le 1291$, currently 1270).
- Do NOT uninstall the app from test phone `15c36230` (always use `adb -s 15c36230 install -r -d -g`).
- All tests in `:fermata:testAutoDebugUnitTest` must pass 100%.

## Review Focus

1. **Touch conflict with vertical handle drag**: Swiping inward on the handle must not falsely trigger vertical repositioning, and dragging vertically must not falsely open the rail.
2. **Dynamic handle boundary exclusion**: Moving the handle up/down dynamically updates the exclusion zone so edge Back gestures never swallow handle drags, and handle touches never trigger Back.
3. **Touch transparency for underlying content**: Swipes in the middle of the screen (e.g. browsing media, scrolling lists, swiping cards) must pass through to children without being consumed.
4. **Direction vector discriminator**: Diagonal swipes must be classified by dominant axis ($|\Delta X| > 1.3 \times |\Delta Y|$ for horizontal Back) to avoid hijacking vertical scroll.
5. **Rail position awareness (`isRight`)**: Left vs Right rail orientation must mirror gesture direction thresholds ($\Delta X > 0$ for left rail, $\Delta X < 0$ for right rail).

---

### Task 1: Swipe-to-Open on Collapsed Rail Grabber Handle

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailView.java:195-245`
- Test: `fermata/src/test/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailViewTest.java`

**Interfaces:**
- Consumes: `GlassNavigationRailView.expand()`, `grabberHandle.getTranslationY()`, `touchSlop`
- Produces: Vector gesture arbitration in `grabberHandle.setOnTouchListener` supporting both vertical repositioning and horizontal swipe-in.

- [ ] **Step 1: Write the failing test for handle swipe-to-open and vertical drag preservation**

In `fermata/src/test/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailViewTest.java`, add tests:
```java
@Test
public void testSwipeInwardOnGrabberHandleExpandsRail() {
    railView.collapse();
    assertFalse(railView.isOpen());

    FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
    long downTime = SystemClock.uptimeMillis();

    // Down on grabber handle
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 20f, 50f, 0);
    grabber.dispatchTouchEvent(down);

    // Swipe horizontally inward by 50px (rail is on left)
    MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 70f, 52f, 0);
    grabber.dispatchTouchEvent(move);

    MotionEvent up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 70f, 52f, 0);
    grabber.dispatchTouchEvent(up);

    assertTrue("Inward horizontal swipe on grabber handle must expand rail", railView.isOpen());
}

@Test
public void testVerticalDragOnGrabberHandleRepositionsWithoutExpanding() {
    railView.collapse();
    FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
    grabber.layout(0, 0, 80, 104);
    railView.layout(0, 0, 800, 480);
    float initialY = grabber.getTranslationY();

    long downTime = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 20f, 50f, 0);
    grabber.dispatchTouchEvent(down);

    // Drag vertically down by 80px
    MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 22f, 130f, 0);
    grabber.dispatchTouchEvent(move);

    MotionEvent up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 22f, 130f, 0);
    grabber.dispatchTouchEvent(up);

    assertFalse("Vertical drag must not expand rail", railView.isOpen());
    assertNotEquals("Vertical drag must update grabber translationY", initialY, grabber.getTranslationY(), 0.1f);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
`cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.ui.glass.rail.GlassNavigationRailViewTest.testSwipeInwardOnGrabberHandleExpandsRail -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
Expected: FAIL with assertion error (`railView.isOpen()` was false).

- [ ] **Step 3: Implement vector gesture arbitration in `GlassNavigationRailView.java`**

In `GlassNavigationRailView.java`:
1. Add tracking variables: `private float downRawX = 0f;`, `private boolean isHorizontalSwipe = false;`.
2. In `grabberHandle.setOnTouchListener`:
   - `ACTION_DOWN`: record `downRawX = event.getRawX()`, `downRawY = event.getRawY()`, `isHorizontalSwipe = false`, `isDragging = false`.
   - `ACTION_MOVE`:
     - Calculate `deltaX = event.getRawX() - downRawX;`, `deltaY = event.getRawY() - downRawY;`.
     - Inward delta threshold $S = \text{GlassDimensions.dpToPx}(context, 30)$.
     - If neither `isDragging` nor `isHorizontalSwipe`:
       - If `Math.abs(deltaY) > touchSlop && Math.abs(deltaY) > Math.abs(deltaX)`: set `isDragging = true`.
       - Else if `(!isRight && deltaX >= S) || (isRight && deltaX <= -S)` and `Math.abs(deltaX) > Math.abs(deltaY)`: set `isHorizontalSwipe = true`.
     - If `isDragging`: execute existing vertical handle translation logic.
     - If `isHorizontalSwipe`: call `expand()`, return `true`.
   - `ACTION_UP`:
     - If `isHorizontalSwipe`: return `true`.
     - If `isDragging`: execute existing ratio save logic.
     - Else: `v.performClick()` -> `expand()`.
   - `ACTION_CANCEL`: reset `isDragging = false; isHorizontalSwipe = false;`.

- [ ] **Step 4: Run tests to verify they pass**

Run:
`cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.ui.glass.rail.GlassNavigationRailViewTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
Expected: PASS.

---

### Task 2: Edge Swipe-to-Back and Exit App along Screen Edge

**Files:**
- Modify: `fermata/src/main/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailView.java`
- Test: `fermata/src/test/java/me/aap/fermata/ui/glass/rail/GlassNavigationRailViewTest.java`

**Interfaces:**
- Consumes: `activity.onBackPressed()`, `grabberHandle.getTranslationY()`, `grabberHandle.getHeight()`
- Produces: Edge touch interception via `onInterceptTouchEvent` and `onTouchEvent` in `GlassNavigationRailView`.

- [ ] **Step 1: Write failing tests for edge swipe Back and Dashboard Exit**

In `GlassNavigationRailViewTest.java`:
```java
@Test
public void testEdgeSwipeOutsideHandleTriggersBackAction() {
    railView.collapse();
    FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
    // Position grabber in the middle: y = 200..304
    grabber.setTranslationY(200f);
    grabber.layout(0, 200, 80, 304);
    railView.layout(0, 0, 800, 480);

    final boolean[] backTriggered = {false};
    railView.setBackActionListenerForTesting(() -> backTriggered[0] = true);

    long downTime = SystemClock.uptimeMillis();
    // Touch near left edge (x=10, y=50) - clearly above grabber handle
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 10f, 50f, 0);
    assertFalse("ACTION_DOWN must not intercept immediately so children receive touch", railView.onInterceptTouchEvent(down));

    // Swipe inward to x=70, y=52 (> 45px and dominant horizontal)
    MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 70f, 52f, 0);
    assertTrue("Inward edge swipe must be intercepted", railView.onInterceptTouchEvent(move));

    // Deliver to onTouchEvent
    railView.onTouchEvent(move);
    MotionEvent up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 70f, 52f, 0);
    railView.onTouchEvent(up);

    assertTrue("Edge swipe outside grabber handle must trigger Back action", backTriggered[0]);
}

@Test
public void testCenterScreenSwipeDoesNotInterceptOrTriggerBack() {
    railView.collapse();
    railView.layout(0, 0, 800, 480);

    final boolean[] backTriggered = {false};
    railView.setBackActionListenerForTesting(() -> backTriggered[0] = true);

    long downTime = SystemClock.uptimeMillis();
    // Touch in center of screen: x=300, y=200
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 300f, 200f, 0);
    assertFalse(railView.onInterceptTouchEvent(down));

    MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 380f, 200f, 0);
    assertFalse("Center screen swipe must NOT be intercepted", railView.onInterceptTouchEvent(move));

    assertFalse("Back must NOT trigger for center screen swipe", backTriggered[0]);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
`cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.ui.glass.rail.GlassNavigationRailViewTest.testEdgeSwipeOutsideHandleTriggersBackAction -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
Expected: FAIL with assertion error.

- [ ] **Step 3: Implement edge gesture interception in `GlassNavigationRailView.java`**

1. Add fields:
   ```java
   private float edgeDownX = 0f;
   private float edgeDownY = 0f;
   private boolean edgeTracking = false;
   private boolean edgeBackIntercepted = false;
   private Runnable backActionListenerForTesting = null;
   ```
2. Implement helper methods:
   - `private boolean isInsideGrabberHandle(float y)`: checks if $y \ge \text{grabberHandle.getTranslationY()}$ and $y \le \text{grabberHandle.getTranslationY()} + \text{grabberHandle.getHeight()}$.
   - `private boolean isAtRailEdge(float x)`:
     `int edgeSlop = GlassDimensions.dpToPx(getContext(), 36);`
     `return !isRight ? (x <= edgeSlop) : (x >= getWidth() - edgeSlop);`
   - `private void performBackAction()`:
     - No haptic feedback.
     - If `backActionListenerForTesting != null`: run it.
     - Else if `activity != null`: call `activity.onBackPressed()`. (Note: `BackNavigationPolicy.handleActivityBack` already automatically pops backstack or calls `finish()` if at root Dashboard).
3. Override `onInterceptTouchEvent(MotionEvent ev)`:
   - If `isOpen || !isCar()`: return `super.onInterceptTouchEvent(ev);`.
   - `ACTION_DOWN`:
     - If `isAtRailEdge(ev.getX()) && !isInsideGrabberHandle(ev.getY())`:
       - `edgeDownX = ev.getX(); edgeDownY = ev.getY(); edgeTracking = true; edgeBackIntercepted = false;`
     - Else `edgeTracking = false;`
     - Return `false;`
   - `ACTION_MOVE`:
     - If `!edgeTracking`: return `false;`
     - `float dx = ev.getX() - edgeDownX; float dy = ev.getY() - edgeDownY;`
     - `int threshold = GlassDimensions.dpToPx(getContext(), 45);`
     - If `Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)`: `edgeTracking = false; return false;` (user is scrolling).
     - If `(!isRight && dx >= threshold) || (isRight && dx <= -threshold)` and `Math.abs(dx) > 1.3f * Math.abs(dy)`:
       - `edgeBackIntercepted = true; edgeTracking = false;`
       - `performBackAction();`
       - Return `true;`
     - Return `false;`
   - `ACTION_UP`, `ACTION_CANCEL`:
     - `edgeTracking = false; edgeBackIntercepted = false;`
     - Return `false;`
4. Override `onTouchEvent(MotionEvent ev)`:
   - If `edgeBackIntercepted`:
     - If `ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL`:
       - `edgeBackIntercepted = false;`
     - Return `true;`
   - Return `super.onTouchEvent(ev);`

- [ ] **Step 4: Run all unit tests**

Run:
`cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest --tests me.aap.fermata.ui.glass.rail.GlassNavigationRailViewTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
Expected: ALL PASS.

Run entire test suite:
`cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:testAutoDebugUnitTest -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
Expected: BUILD SUCCESSFUL (100% passing).

- [ ] **Step 5: Assemble APK & Sideload to test phone `15c36230`**

Commands:
`cmd.exe /c "set ALLOW_LEGACY_TEST_SIGNING=true&& set APP_ID_SFX=.test&& gradlew.bat :fermata:assembleAutoDebug -PAPP_ID_SFX=.test -PALLOW_LEGACY_TEST_SIGNING=true"`
`adb -s 15c36230 install -r -d -g fermata\build\outputs\apk\auto\debug\fermata-2.0.1-me.app.fermataX.auto.test-auto-debug.apk`

- [ ] **Step 6: Verify in DHU**

1. Inject horizontal swipe on the grabber handle via DHU console:
   - Check rail expands smoothly.
2. Inject vertical swipe on the grabber handle:
   - Check handle moves up/down along the edge without expanding.
3. Inject edge swipe outside the grabber handle while in media view:
   - Check Back action triggers and returns to Dashboard.
4. Inject edge swipe outside the grabber handle while at root Dashboard:
   - Check app exits gracefully to Android Auto launcher.
5. Capture screenshot on DHU to confirm.
