package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static me.app.fermatax.auto.carapps.TestDevice.waitUntil;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.app.fermatax.auto.LauncherActivity;

/**
 * The real fragment in the real main activity, with real touches through its SurfaceView, on an emulator.
 * The activity sits on the phone display instead of Android Auto, so the phone stays on here: a locked phone is
 * shown with the activity over the lock screen. Sleeping with the car running is CarDisplayClientDeviceTest's.
 */
@RunWith(AndroidJUnit4.class)
public class CarAppsFragmentDeviceTest {
	private static final int DRAWER = 1;
	private static final int CLOSE = 2;

	private Instrumentation instrumentation;
	private TestDevice device;
	private CarDisplayClient client;
	private MainActivity activity;
	private boolean pinSet;

	@Before
	public void setUp() {
		assumeTrue("drives the phone: emulators only", TestDevice.isEmulator());
		instrumentation = InstrumentationRegistry.getInstrumentation();
		device = new TestDevice(instrumentation);
		Context ctx = instrumentation.getTargetContext().getApplicationContext();
		client = CarDisplayClient.get();
		client.release();
		client.setCommandRunnerForTest(device::shell);
		device.wakeAndOpen();
		instrumentation.runOnMainSync(() -> CarAppsFeature.setState(RootState.GRANTED));
		Intent main = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		activity = (MainActivity) instrumentation.startActivitySync(main);
		waitUntil("the main activity's delegate", 30_000, () -> activity.getActivityDelegate().isDone());
	}

	@After
	public void tearDown() {
		if (client == null) return;
		client.release();
		client.setCommandRunnerForTest(null);
		device.killServer();
		if (pinSet) device.unlockAndClearPin();
		if (activity != null) activity.finishAndRemoveTask();
	}

	@Test
	public void touchesAndButtonsDriveTheCarDisplay() {
		CarAppsFragment fragment = showFragment();
		int car = waitForLauncherOnCar();
		assertSurfaceShowsAPicture(fragment);
		assertTrue("own power group", client.hasOwnPowerGroup());
		assertFalse("no black phone screen", device.shell("dumpsys activity activities").contains("PhoneDimActivity"));
		assertEquals(View.VISIBLE, fragment.getBottomBar().getVisibility());

		String app = openFirstAppByTouch(fragment, car);
		assertSurfaceShowsAPicture(fragment);

		tapButton(fragment, DRAWER);
		assertEquals(car, waitForLauncherOnCar());
		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());

		assertEquals(app, openFirstAppByTouch(fragment, car));
		assertEquals(CarAppsNavModel.Screen.APP, fragment.getScreen());
		SystemClock.sleep(4000); // Chrome starts its first-run screen just after opening

		tapButton(fragment, CLOSE);
		waitUntil(app + " to leave the car", 15_000, () -> !app.equals(device.resumedOn(car)));
		assertEquals(CarAppsNavModel.Screen.DRAWER, fragment.getScreen());
	}

	@Test
	public void hidingTheFragmentStopsThePictureAndShowingItBringsItBack() {
		CarAppsFragment fragment = showFragment();
		int car = waitForLauncherOnCar();
		assertSurfaceShowsAPicture(fragment);

		instrumentation.runOnMainSync(() -> delegate().showFragment(R.id.settings_fragment));
		waitUntil("the surface to go", 10_000, () -> !fragment.getSurfaceView().getHolder().getSurface().isValid());

		instrumentation.runOnMainSync(() -> delegate().showFragment(R.id.car_apps_fragment));
		waitUntil("the surface to return", 10_000, () -> fragment.getSurfaceView().getHolder().getSurface().isValid());
		assertSurfaceShowsAPicture(fragment);
		assertEquals(car, waitForLauncherOnCar());
	}

	@Test
	public void lockedPhoneCoversTheCarUntilItIsUnlocked() {
		instrumentation.runOnMainSync(() -> {
			activity.setShowWhenLocked(true);
			activity.setTurnScreenOn(true);
		});
		device.setPin();
		pinSet = true;
		device.shell("input keyevent KEYCODE_POWER");
		waitUntil("the keyguard", 15_000, () -> device.keyguard().isKeyguardLocked());
		device.shell("input keyevent KEYCODE_WAKEUP");
		waitUntil("the activity over the lock screen", 15_000, () -> MainActivity.getActiveInstance() == activity);

		CarAppsFragment fragment = showFragment();
		waitUntil("the car to wait for the unlock", 30_000, () -> client.isPhoneLocked());

		waitUntil("the lock overlay", 10_000, () -> fragment.getBottomBar().getVisibility() == View.INVISIBLE);
		assertTrue("lock overlay", isTopOverlayShown(fragment));

		device.unlock();
		waitUntil("the car to open", 15_000, () -> !client.isPhoneLocked());
		waitUntil("the bottom bar", 10_000, () -> fragment.getBottomBar().getVisibility() == View.VISIBLE);
		waitForLauncherOnCar();
		assertSurfaceShowsAPicture(fragment);
		assertFalse("lock overlay", isTopOverlayShown(fragment));
	}

	private CarAppsFragment showFragment() {
		AtomicReference<CarAppsFragment> f = new AtomicReference<>();
		instrumentation.runOnMainSync(() ->
				f.set((CarAppsFragment) delegate().showFragment(R.id.car_apps_fragment)));
		assertNotNull(f.get());
		waitUntil("the fragment's view", 15_000, () -> f.get().getView() != null);
		return f.get();
	}

	private MainActivityDelegate delegate() {
		return activity.getActivityDelegate().peek();
	}

	private int waitForLauncherOnCar() {
		waitUntil("the app drawer on the car", 30_000, () -> {
			LauncherActivity l = LauncherActivity.getActiveInstance();
			return (l != null) && l.isVirtualDisplay();
		});
		int id = LauncherActivity.getActiveInstance().getDisplay().getDisplayId();
		assertNotEquals(Display.DEFAULT_DISPLAY, id);
		return id;
	}

	/** Taps through the phone's SurfaceView, whose coordinates are the car display's. */
	private String openFirstAppByTouch(CarAppsFragment fragment, int car) {
		LauncherActivity l = LauncherActivity.getActiveInstance();
		ViewGroup content = l.findViewById(android.R.id.content);
		RecyclerView list = (RecyclerView) content.getChildAt(0);
		waitUntil("the app icons", 10_000, () -> list.getChildCount() > 1);
		// the first one is this app, whose main activity is already on the phone
		View icon = list.getChildAt(1);
		int[] item = new int[2];
		int[] surface = new int[2];
		SurfaceView sv = fragment.getSurfaceView();
		instrumentation.runOnMainSync(() -> {
			icon.getLocationOnScreen(item);
			sv.getLocationOnScreen(surface);
		});
		String launcher = device.resumedOn(car);
		device.shell("input -d 0 tap " + (surface[0] + item[0] + (icon.getWidth() / 2)) + " "
				+ (surface[1] + item[1] + (icon.getHeight() / 2)));
		AtomicReference<String> app = new AtomicReference<>();
		waitUntil("the app to open from a touch", 20_000, () -> {
			app.set(device.resumedOn(car));
			return (app.get() != null) && !app.get().equals(launcher);
		});
		waitUntil("the fragment to see the app", 5000, () -> fragment.getScreen() == CarAppsNavModel.Screen.APP);
		return app.get();
	}

	private void tapButton(CarAppsFragment fragment, int index) {
		View b = ((ViewGroup) fragment.getBottomBar()).getChildAt(index);
		int[] p = new int[2];
		instrumentation.runOnMainSync(() -> b.getLocationOnScreen(p));
		device.shell("input -d 0 tap " + (p[0] + (b.getWidth() / 2)) + " " + (p[1] + (b.getHeight() / 2)));
		SystemClock.sleep(1000);
	}

	private void assertSurfaceShowsAPicture(CarAppsFragment fragment) {
		waitUntil("a picture in the SurfaceView", 20_000, () -> isSurfaceLit(fragment.getSurfaceView()));
	}

	/** The lock overlay is added last to the viewport, above the surface and the other overlays. */
	private boolean isTopOverlayShown(CarAppsFragment fragment) {
		ViewGroup viewport = (ViewGroup) fragment.getSurfaceView().getParent();
		return viewport.getChildAt(viewport.getChildCount() - 1).getVisibility() == View.VISIBLE;
	}

	private boolean isSurfaceLit(SurfaceView view) {
		if (!view.getHolder().getSurface().isValid() || (view.getWidth() == 0)) return false;
		Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
		HandlerThread t = new HandlerThread("pixel-copy");
		t.start();
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicInteger result = new AtomicInteger(-1);
			PixelCopy.request(view, bmp, r -> {
				result.set(r);
				done.countDown();
			}, new Handler(t.getLooper()));
			if (!done.await(5, TimeUnit.SECONDS) || (result.get() != PixelCopy.SUCCESS)) return false;
			for (int y = 0; y < bmp.getHeight(); y += 16) {
				for (int x = 0; x < bmp.getWidth(); x += 16) {
					if ((bmp.getPixel(x, y) & 0xFFFFFF) != 0) return true;
				}
			}
			return false;
		} catch (InterruptedException | IllegalArgumentException e) {
			return false;
		} finally {
			bmp.recycle();
			t.quitSafely();
		}
	}
}
