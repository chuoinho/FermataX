package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.app.fermatax.auto.CarService;
import me.app.fermatax.auto.LauncherActivity;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class CarAppsFragmentTest {

	private Context context;
	private CarDisplayClient client;
	private TestServerBinder serverBinder;
	private final List<Surface> testSurfaces = new ArrayList<>();

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		client = CarDisplayClient.get();
		client.resetForTesting();
		serverBinder = new TestServerBinder();
		client.onServerBinderReceived(serverBinder);
	}

	@After
	public void tearDown() throws Exception {
		for (Surface s : testSurfaces) {
			try {
				s.release();
			} catch (Throwable ignored) {
			}
		}
		testSurfaces.clear();
		client.resetForTesting();
		CarAppsFeature.setState(RootState.ABSENT);
		setCarServiceActive(false);
	}

	private static void setCarServiceActive(boolean active) {
		try {
			java.lang.reflect.Field f = CarService.class.getDeclaredField("active");
			f.setAccessible(true);
			f.setBoolean(null, active);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private CarAppsFragment createAndAttachFragment() {
		FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
		CarAppsFragment fragment = new CarAppsFragment(client);
		activity.getSupportFragmentManager().beginTransaction()
				.add(fragment, "car_apps")
				.commitNow();
		return fragment;
	}

	private Surface createTestSurface() {
		Surface s = new Surface(new android.graphics.SurfaceTexture(0));
		testSurfaces.add(s);
		return s;
	}

	@Test
	public void nullSurfaceDoesNotRecreateDisplay() {
		CarAppsFragment fragment = createAndAttachFragment();
		SurfaceHolder.Callback callback = fragment.getSurfaceHolderCallback();

		Surface surface1 = createTestSurface();
		FakeSurfaceHolder holder1 = new FakeSurfaceHolder(surface1, 1024, 600);

		// 1. Initial surface creation: calls attach
		callback.surfaceCreated(holder1);
		assertEquals(1, serverBinder.attachCount.get());
		assertEquals(surface1, serverBinder.lastSurface);

		// 2. Surface destruction: calls setSurface(null, 0, 0, 0)
		callback.surfaceDestroyed(holder1);
		assertEquals(1, serverBinder.attachCount.get());
		assertEquals(null, serverBinder.lastSurface);

		// 3. Subsequent surface creation: calls setSurface instead of attach
		Surface surface2 = createTestSurface();
		FakeSurfaceHolder holder2 = new FakeSurfaceHolder(surface2, 1024, 600);
		callback.surfaceCreated(holder2);
		assertEquals("VirtualDisplay must not be recreated (attach count stays 1)",
				1, serverBinder.attachCount.get());
		assertEquals(surface2, serverBinder.lastSurface);
	}

	@Test
	public void surfaceResizeCallsSetSurfaceNotAttach() {
		CarAppsFragment fragment = createAndAttachFragment();
		SurfaceHolder.Callback callback = fragment.getSurfaceHolderCallback();

		Surface surface = createTestSurface();
		FakeSurfaceHolder holder = new FakeSurfaceHolder(surface, 1024, 600);

		callback.surfaceCreated(holder);
		assertEquals(1, serverBinder.attachCount.get());

		// Resize event calls setSurface
		callback.surfaceChanged(holder, 0, 1280, 720);
		assertEquals("Surface resize must call setSurface without re-attaching",
				1, serverBinder.attachCount.get());
		assertEquals(1280, serverBinder.lastWidth);
		assertEquals(720, serverBinder.lastHeight);
	}

	@Test
	public void backPressedOnDrawerReturnsFalse() {
		CarAppsFragment fragment = createAndAttachFragment();

		// Screen defaults to DRAWER
		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());
		assertFalse("onBackPressed on DRAWER screen must return false", fragment.onBackPressed());
		assertEquals(-1, serverBinder.lastKeyCode.get());

		// Switch to APP
		fragment.setScreen(CarAppsNavModel.Screen.APP);
		assertEquals(CarAppsNavModel.Screen.APP, fragment.getScreen());
		assertTrue("onBackPressed on APP screen must return true", fragment.onBackPressed());
		assertEquals(KeyEvent.KEYCODE_BACK, serverBinder.lastKeyCode.get());
	}

	@Test
	public void barHiddenWhenBarsHidden() {
		CarAppsFragment fragment = createAndAttachFragment();
		View bottomBar = fragment.getBottomBar();
		assertEquals(View.VISIBLE, bottomBar.getVisibility());

		fragment.setBarsHidden(true);
		assertTrue(fragment.isBarsHidden());
		assertEquals(View.GONE, bottomBar.getVisibility());

		fragment.setBarsHidden(false);
		assertFalse(fragment.isBarsHidden());
		assertEquals(View.VISIBLE, bottomBar.getVisibility());
	}

	@Test
	public void rootButtonHiddenWhenCarServiceActive() {
		CarAppsFeature.setState(RootState.AVAILABLE);
		setCarServiceActive(true);

		CarAppsFragment fragment = createAndAttachFragment();
		assertEquals(CarAppsUiState.State.NEEDS_ROOT, fragment.getUiState());
		assertEquals("Root request button must be hidden when CarService is active",
				View.GONE, fragment.getGrantRootButton().getVisibility());

		setCarServiceActive(false);
		fragment.updateUiState();
		assertEquals("Root request button must be visible when CarService is not active",
				View.VISIBLE, fragment.getGrantRootButton().getVisibility());
	}

	@Test
	public void launcherVisibilityChangesScreenState() {
		CarAppsFragment fragment = createAndAttachFragment();
		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());

		// Launcher becomes invisible (app opened)
		LauncherActivity.notifyVisibility(false);
		org.robolectric.shadows.ShadowLooper.idleMainLooper();
		assertEquals(CarAppsNavModel.Screen.APP, fragment.getScreen());
		assertTrue("Back button must be enabled in APP screen", fragment.getBackButton().isEnabled());
		assertTrue("Close button must be enabled in APP screen", fragment.getCloseButton().isEnabled());

		// Back button click in APP screen injects KEYCODE_BACK
		fragment.getBackButton().performClick();
		assertEquals(KeyEvent.KEYCODE_BACK, serverBinder.lastKeyCode.get());

		// Launcher becomes visible again (returned to drawer)
		LauncherActivity.notifyVisibility(true);
		org.robolectric.shadows.ShadowLooper.idleMainLooper();
		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());
		assertFalse("Back button must be disabled in DRAWER screen", fragment.getBackButton().isEnabled());
		assertFalse("Close button must be disabled in DRAWER screen", fragment.getCloseButton().isEnabled());
	}

	@Test
	public void drawerButtonLaunchesLauncherActivity() {
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.setScreen(CarAppsNavModel.Screen.APP);

		fragment.getDrawerButton().performClick();

		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());
		assertEquals(LauncherActivity.class.getName(), serverBinder.lastLaunchedComponent.getClassName());
	}

	@Test
	public void closeButtonClosesTopApp() {
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.setScreen(CarAppsNavModel.Screen.APP);

		// Click calls closeTopApp(false)
		fragment.getCloseButton().performClick();
		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());
		assertFalse(serverBinder.lastForceClose.get());

		fragment.setScreen(CarAppsNavModel.Screen.APP);
		// Long click calls closeTopApp(true)
		fragment.getCloseButton().performLongClick();
		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());
		assertTrue(serverBinder.lastForceClose.get());
	}

	@Test
	public void bottomBarHasExactlyThreeButtons() {
		CarAppsFragment fragment = createAndAttachFragment();
		View bottomBar = fragment.getBottomBar();
		assertTrue(bottomBar instanceof ViewGroup);
		ViewGroup bar = (ViewGroup) bottomBar;
		assertEquals("Bottom bar must have exactly 3 buttons per approved design", 3, bar.getChildCount());
		assertSame(fragment.getBackButton(), bar.getChildAt(0));
		assertSame(fragment.getDrawerButton(), bar.getChildAt(1));
		assertSame(fragment.getCloseButton(), bar.getChildAt(2));
	}

	@Test
	public void bottomBarIsCompact() {
		CarAppsFragment fragment = createAndAttachFragment();
		int maxPx = (int) Math.ceil(52 * context.getResources().getDisplayMetrics().density);
		assertTrue("Bottom bar must be at most 52 dp", fragment.getBottomBar().getLayoutParams().height <= maxPx);
	}

	@Test
	public void bottomBarIconsFollowThemeTint() {
		CarAppsFragment fragment = createAndAttachFragment();
		for (ImageButton b : new ImageButton[]{fragment.getBackButton(), fragment.getDrawerButton(), fragment.getCloseButton()}) {
			assertTrue("Icon must be tinted from the active theme", b.getImageTintList() != null);
		}
	}

	private String nextStartedActivityClass() {
		android.content.Intent i = org.robolectric.Shadows.shadowOf(
				(Application) context.getApplicationContext()).getNextStartedActivity();
		return i == null || i.getComponent() == null ? null : i.getComponent().getClassName();
	}

	private void setDimPref(boolean on) {
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit()
				.putBoolean(CarAppsFeature.PREF_PANEL_OFF, on).commit();
	}

	@Test
	public void phoneDimScreenShownOnAttach() {
		CarAppsFeature.setState(RootState.GRANTED);
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.getSurfaceHolderCallback().surfaceCreated(new FakeSurfaceHolder(createTestSurface(), 1024, 600));
		assertEquals(PhoneDimActivity.class.getName(), nextStartedActivityClass());
	}

	@Test
	public void phoneDimScreenShownWhenAttachedFromSurfaceChanged() {
		CarAppsFeature.setState(RootState.GRANTED);
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.getSurfaceHolderCallback().surfaceChanged(new FakeSurfaceHolder(createTestSurface(), 1024, 600), 0, 1024, 600);
		assertEquals(1, serverBinder.attachCount.get());
		assertEquals(PhoneDimActivity.class.getName(), nextStartedActivityClass());
	}

	@Test
	public void phoneDimScreenNotShownWhenOptionOff() {
		setDimPref(false);
		CarAppsFeature.setState(RootState.GRANTED);
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.getSurfaceHolderCallback().surfaceCreated(new FakeSurfaceHolder(createTestSurface(), 1024, 600));
		assertEquals(null, nextStartedActivityClass());
	}

	@Test
	public void phoneDimScreenNotReshownAfterUserDismissedIt() {
		CarAppsFeature.setState(RootState.GRANTED);
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.getSurfaceHolderCallback().surfaceCreated(new FakeSurfaceHolder(createTestSurface(), 1024, 600));
		assertEquals(PhoneDimActivity.class.getName(), nextStartedActivityClass());
		fragment.onStart(); // any later UI refresh must not bring the black screen back
		assertEquals(null, nextStartedActivityClass());
	}

	private static MotionEvent touch(long down, int action, float x, float y) {
		return MotionEvent.obtain(down, down, action, x, y, 0);
	}

	@Test
	public void losingSurfaceMidTouchCancelsTheGesture() {
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.forwardTouch(touch(1000, MotionEvent.ACTION_DOWN, 40, 50));

		fragment.getSurfaceHolderCallback().surfaceDestroyed(new FakeSurfaceHolder(createTestSurface(), 1024, 600));

		MotionEvent last = serverBinder.injected.get(serverBinder.injected.size() - 1);
		assertEquals(MotionEvent.ACTION_CANCEL, last.getActionMasked());
		assertEquals(1000, last.getDownTime());
		assertEquals(40f, last.getX(), 0f);
		assertEquals(50f, last.getY(), 0f);
	}

	@Test
	public void stoppingMidTouchCancelsTheGesture() {
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.forwardTouch(touch(2000, MotionEvent.ACTION_DOWN, 10, 20));
		fragment.forwardTouch(touch(2000, MotionEvent.ACTION_MOVE, 15, 25));

		fragment.onStop();

		assertEquals(MotionEvent.ACTION_CANCEL,
				serverBinder.injected.get(serverBinder.injected.size() - 1).getActionMasked());
	}

	@Test
	public void finishedTouchIsNotCancelledAgain() {
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.forwardTouch(touch(3000, MotionEvent.ACTION_DOWN, 10, 20));
		fragment.forwardTouch(touch(3000, MotionEvent.ACTION_UP, 10, 20));

		fragment.getSurfaceHolderCallback().surfaceDestroyed(new FakeSurfaceHolder(createTestSurface(), 1024, 600));

		assertEquals(2, serverBinder.injected.size());
	}

	@Test
	public void rootGrantedAfterFailureRestartsServer() {
		CarAppsFeature.setState(RootState.AVAILABLE);
		CarAppsFragment fragment = createAndAttachFragment();
		fragment.onStart();
		for (int i = 0; i < 20 && client.state() != CarDisplayClient.State.FAILED; i++) {
			client.retryConnectionForTesting();
		}
		assertEquals(CarDisplayClient.State.FAILED, client.state());
		java.util.List<String> cmds = new java.util.ArrayList<>();
		client.setCommandRunnerForTest(cmds::add);
		CarAppsFeature.setState(RootState.GRANTED);
		assertEquals("Granting root must restart the failed server", 1, cmds.size());
		fragment.onStop();
	}

	private static class FakeSurfaceHolder implements SurfaceHolder {
		private final Surface surface;
		private final Rect frame;

		FakeSurfaceHolder(Surface surface, int width, int height) {
			this.surface = surface;
			this.frame = new Rect(0, 0, width, height);
		}

		@Override
		public void addCallback(Callback callback) {}

		@Override
		public void removeCallback(Callback callback) {}

		@Override
		public boolean isCreating() { return false; }

		@Override
		public void setType(int type) {}

		@Override
		public void setFixedSize(int width, int height) {}

		@Override
		public void setSizeFromLayout() {}

		@Override
		public void setFormat(int format) {}

		@Override
		public void setKeepScreenOn(boolean screenOn) {}

		@Override
		public Canvas lockCanvas() { return null; }

		@Override
		public Canvas lockCanvas(Rect dirty) { return null; }

		@Override
		public void unlockCanvasAndPost(Canvas canvas) {}

		@Override
		public Rect getSurfaceFrame() { return frame; }

		@Override
		public Surface getSurface() { return surface; }
	}

	private static class TestServerBinder extends Binder implements ICarDisplayServer {
		final AtomicInteger attachCount = new AtomicInteger(0);
		final AtomicInteger lastKeyCode = new AtomicInteger(-1);
		final AtomicBoolean lastForceClose = new AtomicBoolean(false);
		Surface lastSurface;
		int lastWidth;
		int lastHeight;
		ComponentName lastLaunchedComponent;
		int caps = 0x1f;
		final List<MotionEvent> injected = new ArrayList<>();

		TestServerBinder() {
			attachInterface(this, ICarDisplayServer.DESCRIPTOR);
		}

		@Override
		public int capabilities() { return caps; }

		@Override
		public int attach(Surface s, int w, int h, int dpi, IBinder client) {
			attachCount.incrementAndGet();
			lastSurface = s;
			lastWidth = w;
			lastHeight = h;
			return 100;
		}

		@Override
		public void setSurface(Surface s, int w, int h, int dpi) {
			lastSurface = s;
			lastWidth = w;
			lastHeight = h;
		}

		@Override
		public void inject(MotionEvent e) {
			injected.add(MotionEvent.obtain(e)); // the caller recycles e right after this call
		}

		@Override
		public void injectKey(int keyCode) {
			lastKeyCode.set(keyCode);
		}

		@Override
		public int launch(ComponentName c, int intentFlags) {
			lastLaunchedComponent = c;
			return 0;
		}

		@Override
		public String closeTopApp(boolean force) {
			lastForceClose.set(force);
			return "closed";
		}

		@Override
		public void release() {}

		@Override
		public int serverVersion() {
			return BuildConfig.VERSION_CODE;
		}

		@Override
		public IBinder asBinder() { return this; }
	}
}
