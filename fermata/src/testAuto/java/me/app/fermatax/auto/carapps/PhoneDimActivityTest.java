package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class PhoneDimActivityTest {

	@org.junit.Before
	@org.junit.After
	public void resetClient() {
		CarDisplayClient.get().resetForTesting();
	}

	/** The server runs as shell: not exported is a Permission Denial, a phone screen that never appears. */
	@Test
	public void shellCanStartItAndOrdinaryAppsCannot() throws Exception {
		android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
		android.content.pm.ActivityInfo info = ctx.getPackageManager().getActivityInfo(
				new android.content.ComponentName(ctx, PhoneDimActivity.class), 0);
		assertTrue(info.exported);
		org.junit.Assert.assertEquals("android.permission.WRITE_SECURE_SETTINGS", info.permission);
	}

	@Test
	public void rotatingInPanelModeKeepsThePanelOff() {
		CarDisplayClient client = CarDisplayClient.get();
		client.ensureRunning(org.robolectric.RuntimeEnvironment.getApplication());
		CarDisplayClientTest_FakeServer server = new CarDisplayClientTest_FakeServer();
		client.onServerBinderReceived(server);
		client.setPanelOffRequested(true);

		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();
		c.recreate();

		assertTrue(client.isPanelOff());
		org.junit.Assert.assertEquals(Boolean.FALSE, server.lastPower);
	}

	@Test
	public void panelThatCannotTurnOffEndsTheScreenInsteadOfTrappingTouch() {
		CarDisplayClient client = CarDisplayClient.get();
		client.ensureRunning(org.robolectric.RuntimeEnvironment.getApplication());
		CarDisplayClientTest_FakeServer server = new CarDisplayClientTest_FakeServer();
		server.caps = 0;
		client.onServerBinderReceived(server);
		client.setPanelOffRequested(true);

		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();

		assertTrue(c.get().isFinishing());
	}

	@Test
	public void leavingEndsTheDimScreen() {
		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();
		c.pause().stop();
		assertTrue(c.get().isFinishing());
	}

	@Test
	public void rotatingKeepsTheDimScreen() {
		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();
		PhoneDimActivity beforeRotation = c.get();
		c.recreate();
		assertFalse(beforeRotation.isFinishing());
		assertFalse(c.get().isFinishing());
	}

	@Test
	public void panelModeControlsPanelPowerAndSwallowsTouch() {
		CarDisplayClient client = CarDisplayClient.get();
		client.ensureRunning(org.robolectric.RuntimeEnvironment.getApplication());
		CarDisplayClientTest_FakeServer server = new CarDisplayClientTest_FakeServer();
		client.onServerBinderReceived(server);
		client.setPanelOffRequested(true);

		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();
		assertTrue(client.isPanelOff());
		org.junit.Assert.assertEquals(Boolean.FALSE, server.lastPower);

		// Touch on view must not finish activity in panel mode
		((android.view.ViewGroup) c.get().findViewById(android.R.id.content)).getChildAt(0).performClick();
		assertFalse(c.get().isFinishing());

		c.pause().stop();
		assertFalse(client.isPanelOff());
		org.junit.Assert.assertEquals(Boolean.TRUE, server.lastPower);
	}

	@Test
	public void dimModeClickFinishesActivity() {
		CarDisplayClient client = CarDisplayClient.get();
		client.setPanelOffRequested(false);

		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();
		((android.view.ViewGroup) c.get().findViewById(android.R.id.content)).getChildAt(0).performClick();
		assertTrue(c.get().isFinishing());
	}

	private static class CarDisplayClientTest_FakeServer extends android.os.Binder implements ICarDisplayServer {
		Boolean lastPower;
		int caps = ICarDisplayServer.CAP_PANEL_POWER;
		CarDisplayClientTest_FakeServer() {
			attachInterface(this, ICarDisplayServer.DESCRIPTOR);
		}
		@Override public void linkToDeath(DeathRecipient recipient, int flags) {}
		@Override public boolean unlinkToDeath(DeathRecipient recipient, int flags) { return true; }
		@Override public boolean pingBinder() { return true; }
		@Override public int capabilities() { return caps; }
		@Override public int attach(android.view.Surface s, int w, int h, int dpi, android.os.IBinder c) { return 100; }
		@Override public void setSurface(android.view.Surface s, int w, int h, int dpi) {}
		@Override public void inject(android.view.MotionEvent e) {}
		@Override public void injectKey(int keyCode) {}
		@Override public int launch(android.content.ComponentName c, int f) { return 0; }
		@Override public void launchOnPhone(android.content.ComponentName c, int f) {}
		@Override public String closeTopApp(boolean force) { return ""; }
		@Override public void setPanelPower(boolean on) { lastPower = on; }
		@Override public void sleepPhone() {}
		@Override public void release() {}
		@Override public int serverVersion() { return me.aap.fermata.BuildConfig.VERSION_CODE; }
		@Override public android.os.IBinder asBinder() { return this; }
	}
}
