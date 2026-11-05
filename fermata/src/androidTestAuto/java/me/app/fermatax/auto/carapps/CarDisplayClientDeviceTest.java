package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static me.app.fermatax.auto.carapps.TestDevice.waitUntil;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The real client with the real server on a device, no root and no Android Auto: the server is started as
 * shell, which is what su does, and an ImageReader stands in for the car surface, which a sleeping phone
 * does not touch. Tests that lock the phone set a PIN, so they run only on emulators.
 */
@RunWith(AndroidJUnit4.class)
public class CarDisplayClientDeviceTest {
	private static final ComponentName SETTINGS =
			new ComponentName("com.android.settings", "com.android.settings.Settings");
	private static final Pattern DISPLAY_LOCK =
			Pattern.compile("(?m)^ +SCREEN_\\S+ +'fermatax:cardisplay'");
	private static final Pattern DIM_LOCK = Pattern.compile("(?m)^ +SCREEN_\\S+ +'fermatax:carapps'");
	/** Android 10 writes the number. */
	private static final Pattern PANEL_OFF = Pattern.compile("powerMode=(Off|0)\\b");
	private static final int W = 1280;
	private static final int H = 720;
	private static final int DPI = 160;

	private final AtomicInteger frames = new AtomicInteger();
	private final AtomicInteger litFrames = new AtomicInteger();
	private TestDevice device;
	private Context ctx;
	private CarDisplayClient client;
	private HandlerThread thread;
	private ImageReader reader;
	private Surface surface;
	private boolean pinSet;
	private String timeout;

	@Before
	public void setUp() {
		device = new TestDevice(InstrumentationRegistry.getInstrumentation());
		ctx = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
		client = CarDisplayClient.get();
		client.release();
		client.setCommandRunnerForTest(device::startServer);
		thread = new HandlerThread("car-frames");
		thread.start();
		reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3);
		reader.setOnImageAvailableListener(r -> {
			try (Image image = r.acquireLatestImage()) {
				if (image == null) return;
				frames.incrementAndGet();
				if (isLit(image)) litFrames.incrementAndGet();
			}
		}, new Handler(thread.getLooper()));
		surface = reader.getSurface();
	}

	@After
	public void tearDown() {
		client.release();
		client.setCommandRunnerForTest(null);
		device.killServer();
		device.shell("am force-stop " + SETTINGS.getPackageName());
		if (pinSet) device.unlockAndClearPin();
		if (timeout != null) device.shell("settings put system screen_off_timeout " + timeout);
		reader.close();
		thread.quitSafely();
	}

	/** Runs on any phone and changes nothing there: it never locks it and never enters a PIN. */
	@Test
	public void connectsAttachesAndReleasesInAnyPhoneState() {
		connect();
		boolean lockedAtStart = CarDisplayClient.keyguardHoldsPhone(Build.VERSION.SDK_INT,
				device.keyguard().isKeyguardSecure(), device.keyguard().isKeyguardLocked());

		int id = client.attach(surface, W, H, DPI);

		assertTrue("attach=" + id, id >= 0);
		assertEquals("own power group", hasOwnGroup(), client.hasOwnPowerGroup());
		assertEquals(lockedAtStart, client.isPhoneLocked());
		assertFalse(client.isPhoneAsleep());

		if (!lockedAtStart) {
			assertLocksDuringSession(); // a locked phone is not kept awake
			assertCarShows(id, SETTINGS);
		}

		client.release();
		assertEquals(CarDisplayClient.State.IDLE, client.state());
		waitUntil("the car display to go", 10_000, () -> !device.shell("dumpsys display").contains("fermatax-car"));
		assertFalse("display lock left", hasDisplayLock());
	}

	@Test
	public void powerKeyLeavesTheCarRunningWhileThePhoneIsLocked() {
		startUnlockedWithPin();
		int id = attach();
		assertFalse(client.isPhoneLocked());
		assertCarShows(id, SETTINGS);

		powerKeyOff();

		assertPhoneOffAndLocked();
		assertFalse("car still open", client.isPhoneLocked());
		assertFalse("phone sleep is not reported", client.isPhoneAsleep());

		// the power key sleeps only the phone's group: the car group needs its lock once the timeout passes
		timeout = device.shell("settings get system screen_off_timeout").trim();
		device.shell("settings put system screen_off_timeout 15000");
		SystemClock.sleep(25_000);

		assertCarShows(id, otherApp());
		assertPhoneOffAndLocked();
	}

	@Test
	public void screenTimeoutLeavesTheCarRunning() {
		startUnlockedWithPin();
		int id = attach();
		assertCarShows(id, SETTINGS);
		timeout = device.shell("settings get system screen_off_timeout").trim();
		device.shell("settings put system screen_off_timeout 15000");

		waitUntil("the screen to time out", 60_000, () -> !isPhoneScreenOn());

		assertFalse(client.isPhoneLocked());
		assertCarShows(id, otherApp());
	}

	@Test
	public void lockedPhoneShowsNothingUntilItIsUnlockedOnce() {
		startUnlockedWithPin();
		powerKeyOff();
		assertPhoneOffAndLocked();

		int id = attach();

		assertTrue("car waits for unlock", client.isPhoneLocked());
		client.launch(SETTINGS, Intent.FLAG_ACTIVITY_NEW_TASK);
		SystemClock.sleep(5000);
		assertEquals("no frame may reach the car", 0, frames.get());
		assertPhoneOffAndLocked();

		device.unlock();
		waitUntil("the car to open", 15_000, () -> !client.isPhoneLocked());
		waitUntil("the app opened while locked to appear", 20_000, () -> litFrames.get() > 0);

		powerKeyOff();
		assertPhoneOffAndLocked();
		assertFalse("unlocked once, open to the end of the session", client.isPhoneLocked());
		assertCarShows(id, otherApp());
	}

	@Test
	public void nextSessionAsksForUnlockAgainAndLeavesNoLock() {
		startUnlockedWithPin();
		int id = attach();
		assertCarShows(id, SETTINGS);
		powerKeyOff();

		client.release();
		waitUntil("the car display to go", 10_000, () -> !device.shell("dumpsys display").contains("fermatax-car"));
		assertFalse("display lock left", hasDisplayLock());

		frames.set(0);
		connect();
		attach();
		assertTrue("a new session must not stay open", client.isPhoneLocked());
		SystemClock.sleep(3000);
		assertEquals(0, frames.get());
	}

	@Test
	public void killedServerComesBackWhileThePhoneIsLocked() {
		startUnlockedWithPin();
		int id = attach();
		assertCarShows(id, SETTINGS);
		powerKeyOff();
		assertPhoneOffAndLocked();

		device.killServer();
		waitUntil("the client to notice", 10_000, () -> client.state() != CarDisplayClient.State.READY);
		waitUntil("the server to come back", 30_000, () -> client.state() == CarDisplayClient.State.READY);

		int newId = client.attach(surface, W, H, DPI); // the fragment does this on READY
		assertTrue("attach=" + newId, newId >= 0);
		assertFalse("the session stays open", client.isPhoneLocked());
		assertLocksDuringSession();
		assertCarShows(newId, SETTINGS);
		assertPhoneOffAndLocked();
	}

	@Test
	public void carSurfaceLostAndBackWhileThePhoneIsLocked() {
		startUnlockedWithPin();
		int id = attach();
		assertCarShows(id, SETTINGS);
		powerKeyOff();
		assertPhoneOffAndLocked();

		client.setSurface(null, 0, 0, 0);
		SystemClock.sleep(2000);
		client.setSurface(surface, W, H, DPI);

		assertFalse(client.isPhoneLocked());
		assertCarShows(id, otherApp());
		assertPhoneOffAndLocked();
	}

	@Test
	public void panelOffKeepsTheCarRunningAndThePowerKeyBringsItBack() {
		int id = startWithPanelOff();
		assertCarShows(id, SETTINGS);
		assertTrue("panel stays off", isPanelOff());

		device.shell("input keyevent KEYCODE_POWER");

		waitUntil("the panel to come back", 15_000, () -> !isPanelOff());
		waitUntil("the phone to wake", 15_000, this::isPhoneScreenOn);
		// not dumpsys: its insets section keeps naming the window after the activity is gone
		waitUntil("the black screen to end", 15_000, () -> !PhoneDimActivity.isShowing());
		assertFalse(client.isPanelOff());
		if (hasOwnGroup()) assertCarShows(id, otherApp());
		else waitUntil("the car to ask for an unlock", 15_000, client::isPhoneLocked);
	}

	@Test public void swipeLockTellsTheCarWhenAppsStop() {
		assumeTrue("Android 11-12, emulators only", TestDevice.isEmulator() && Build.VERSION.SDK_INT < 33);
		assumeFalse("needs a lock without a PIN", device.keyguard().isKeyguardSecure());
		device.wakeAndOpen();
		connect();
		int id = attach();
		assertCarShows(id, SETTINGS);
		powerKeyOff();
		device.shell("input keyevent KEYCODE_WAKEUP");
		waitUntil("the car to ask for an unlock", 15_000, client::isPhoneLocked);
	}

	@Test public void sleepPhoneOnlySleepsAScreenThatIsOn() {
		assumeTrue("emulators only", TestDevice.isEmulator());
		device.wakeAndOpen();
		connect();
		int id = attach();
		client.sleepPhone();
		waitUntil("the screen to go off", 15_000, () -> !isPhoneScreenOn());
		if (hasOwnGroup()) assertCarShows(id, SETTINGS);
		SystemClock.sleep(2100);
		client.sleepPhone();
		SystemClock.sleep(3000);
		assertFalse("a second press must not wake it", isPhoneScreenOn());
	}

	@Test public void panelOffThenPowerKeyAsksToUnlock() {
		assumeTrue("Android 11-12, emulators only", TestDevice.isEmulator() && Build.VERSION.SDK_INT < 33);
		device.wakeAndOpen();
		device.setPin();
		pinSet = true;
		connect();
		attach();
		client.setPanelOffRequested(true);
		client.launchOnPhone(new ComponentName(ctx, PhoneDimActivity.class),
				Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
		waitUntil("the panel to turn off", 20_000, this::isPanelOff);
		device.shell("input keyevent KEYCODE_POWER");
		waitUntil("the keyguard", 15_000, () -> device.keyguard().isKeyguardLocked());
		waitUntil("the car to ask for an unlock", 15_000, client::isPhoneLocked);
	}

	@Test
	public void killedServerBringsThePanelBack() {
		startWithPanelOff();
		device.killServer();
		waitUntil("the server to come back", 30_000, () -> client.state() == CarDisplayClient.State.READY);
		waitUntil("the panel to come back", 15_000, () -> !isPanelOff());
	}

	@Test
	public void serverThatNeverComesBackLeavesOnePowerPressToBringThePanelBack() {
		startWithPanelOff();
		client.setCommandRunnerForTest(command -> { });
		device.killServer();
		waitUntil("the client to notice", 10_000, () -> client.state() != CarDisplayClient.State.READY);
		assertTrue("nothing can light it yet", isPanelOff());

		device.shell("input keyevent KEYCODE_POWER");

		waitUntil("the panel to come back", 15_000, () -> !isPanelOff() && isPhoneScreenOn());
	}

	@Test
	public void serverThatNeverComesBackLetsTheScreenTimeoutBringThePanelBack() {
		startWithPanelOff();
		client.setCommandRunnerForTest(command -> { });
		timeout = device.shell("settings get system screen_off_timeout").trim();
		device.shell("settings put system screen_off_timeout 15000");
		// the 11 and 12 images keep the screen on while plugged in, and there no timeout ever comes
		String stayOn = device.shell("settings get global stay_on_while_plugged_in").trim();
		device.shell("settings put global stay_on_while_plugged_in 0");
		try {
			device.killServer();
			waitUntil("the panel to come back", 60_000, () -> !isPanelOff() && isPhoneScreenOn());
		} finally {
			device.shell("settings put global stay_on_while_plugged_in " + stayOn);
		}
	}

	@Test
	public void releaseBringsThePanelBack() {
		startWithPanelOff();
		client.release();
		waitUntil("the panel to come back", 15_000, () -> !isPanelOff());
	}

	@Test public void panelTurnsOffAgainAfterThePowerKeyBroughtItBack() {
		startWithPanelOff();
		device.shell("input keyevent KEYCODE_POWER");
		waitUntil("the panel to come back", 15_000, () -> !isPanelOff());
		waitUntil("the black screen to end", 15_000, () -> !PhoneDimActivity.isShowing());
		device.wakeAndOpen();
		client.setPanelOffRequested(true);
		client.launchOnPhone(new ComponentName(ctx, PhoneDimActivity.class),
				Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
		waitUntil("the panel to turn off again", 20_000, this::isPanelOff);
	}

	private int startWithPanelOff() {
		assumeTrue("Android 11-15, emulators only", TestDevice.isEmulator() && PanelPowerPolicy.sdkSupported(Build.VERSION.SDK_INT));
		device.wakeAndOpen();
		connect();
		int id = attach();
		assertTrue("CAP_PANEL_POWER", client.hasCapability(ICarDisplayServer.CAP_PANEL_POWER));
		client.setPanelOffRequested(true);
		client.launchOnPhone(new ComponentName(ctx, PhoneDimActivity.class),
				Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
		waitUntil("the panel to turn off", 20_000, this::isPanelOff);
		return id;
	}

	private boolean isPanelOff() {
		return PANEL_OFF.matcher(device.shell("dumpsys SurfaceFlinger")).find();
	}

	private void startUnlockedWithPin() {
		assumeTrue("sets a PIN: emulators only", TestDevice.isEmulator());
		assumeTrue("the car runs on while the phone sleeps: own power group only", hasOwnGroup());
		device.wakeAndOpen();
		device.setPin();
		pinSet = true;
		connect();
	}

	private void connect() {
		client.ensureRunning(ctx);
		waitUntil("the server to connect", 30_000, () -> client.state() == CarDisplayClient.State.READY);
	}

	private int attach() {
		int id = client.attach(surface, W, H, DPI);
		assertTrue("attach=" + id, id >= 0);
		assertEquals("own power group", hasOwnGroup(), client.hasOwnPowerGroup());
		assertLocksDuringSession();
		return id;
	}

	/** Before Android 13 the display shares the phone's group: see VirtualDisplayFlags. */
	private static boolean hasOwnGroup() {
		return Build.VERSION.SDK_INT >= 33;
	}

	/** Android 16 needs no lock: a VirtualDevice gives the display its own timeout. A shared group needs the dim lock. */
	private void assertLocksDuringSession() {
		assertEquals("dim lock", !hasOwnGroup(), DIM_LOCK.matcher(device.shell("dumpsys power")).find());
		assertEquals("display lock", hasOwnGroup() && !VirtualDisplayFlags.usesVirtualDevice(Build.VERSION.SDK_INT), hasDisplayLock());
	}

	private boolean hasDisplayLock() {
		return DISPLAY_LOCK.matcher(device.shell("dumpsys power")).find();
	}

	/** The app is resumed on the car display and its picture, not a black screen, reaches the surface. */
	private void assertCarShows(int displayId, ComponentName app) {
		int lit = litFrames.get();
		assertEquals(0, client.launch(app, Intent.FLAG_ACTIVITY_NEW_TASK));
		waitUntil(app.getPackageName() + " to resume on display " + displayId, 20_000,
				() -> app.getPackageName().equals(device.resumedOn(displayId)));
		waitUntil("a picture of " + app.getPackageName(), 20_000, () -> litFrames.get() > lit);
	}

	private ComponentName otherApp() {
		Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
		for (ResolveInfo ri : ctx.getPackageManager().queryIntentActivities(main, 0)) {
			ActivityInfo a = ri.activityInfo;
			// a camera's preview can stay black on an emulator, which reads as no picture
			if (!a.packageName.equals(SETTINGS.getPackageName()) && !a.packageName.equals(ctx.getPackageName())
					&& !a.packageName.contains("camera")) {
				return new ComponentName(a.packageName, a.name);
			}
		}
		throw new AssertionError("no other launcher app");
	}

	private void assertPhoneOffAndLocked() {
		assertFalse("phone screen on", isPhoneScreenOn());
		assertTrue("phone not locked", device.keyguard().isKeyguardLocked());
	}

	private void powerKeyOff() {
		if (isPhoneScreenOn()) device.shell("input keyevent KEYCODE_POWER");
		waitUntil("the screen to go off", 15_000, () -> !isPhoneScreenOn());
		waitUntil("the keyguard", 15_000, () -> device.keyguard().isKeyguardLocked());
	}

	private boolean isPhoneScreenOn() {
		DisplayManager dm = ctx.getSystemService(DisplayManager.class);
		return dm.getDisplay(Display.DEFAULT_DISPLAY).getState() == Display.STATE_ON;
	}

	private static boolean isLit(Image image) {
		Image.Plane plane = image.getPlanes()[0];
		ByteBuffer pixels = plane.getBuffer();
		for (int y = 0; y < image.getHeight(); y += 16) {
			int row = y * plane.getRowStride();
			for (int x = 0; x < image.getWidth(); x += 16) {
				int p = row + x * plane.getPixelStride();
				if ((pixels.get(p) | pixels.get(p + 1) | pixels.get(p + 2)) != 0) return true;
			}
		}
		return false;
	}
}
