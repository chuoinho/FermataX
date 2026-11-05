package me.app.fermatax.auto.carapps.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CarDisplayServerImplTest {

	@org.junit.After
	public void cleanPanel() {
		HiddenTest.resetPanel();
		org.robolectric.util.ReflectionHelpers.setStaticField(Hidden.class, "initialized", false);
	}

	@Test
	public void panelPowerRefusesAStrangerUid() throws Exception {
		HiddenTest.usePanelFake();
		new CarDisplayServerImpl(android.os.Binder.getCallingUid() + 1, 1, "p").setPanelPower(false);
		assertTrue(HiddenTest.FakePanel.modes.isEmpty());
	}

	@Test
	public void clientLeavingRestoresAPanelItTurnedOff() throws Exception {
		HiddenTest.usePanelFake();
		CarDisplayServerImpl server = new CarDisplayServerImpl(android.os.Binder.getCallingUid(), 1, "p");
		server.setPanelPower(false);
		assertEquals(java.util.Arrays.asList(0, 0), HiddenTest.FakePanel.modes);

		server.releaseInternal();
		assertEquals(java.util.Arrays.asList(0, 0, 2, 2), HiddenTest.FakePanel.modes);
	}

	@Test
	public void clientLeavingDoesNotLightASleepingPhone() throws Exception {
		HiddenTest.usePanelFake();
		new CarDisplayServerImpl(android.os.Binder.getCallingUid(), 1, "p").releaseInternal();
		assertTrue(HiddenTest.FakePanel.modes.isEmpty());
	}

	/** Android 10: a display resized to 0 kills system_server, divide by zero in configureDisplayPolicy. */
	@Test
	public void aLostSurfaceDoesNotResizeTheDisplay() {
		assertFalse(CarDisplayServerImpl.canResize(0, 0, 0));
		assertFalse(CarDisplayServerImpl.canResize(100, 100, 0));
		assertFalse(CarDisplayServerImpl.canResize(100, 0, 160));
		assertFalse(CarDisplayServerImpl.canResize(-1, 100, 160));
		assertTrue(CarDisplayServerImpl.canResize(100, 100, 160));
	}

	@Test
	public void probeReleasesTheKeySoNoneStaysHeldOnTheDisplay() {
		List<KeyEvent> sent = new ArrayList<>();
		assertTrue(CarDisplayServerImpl.injectionAccepted(10, e -> sent.add(e)));

		assertEquals(2, sent.size());
		assertEquals(KeyEvent.ACTION_DOWN, sent.get(0).getAction());
		assertEquals(KeyEvent.ACTION_UP, sent.get(1).getAction());
		assertEquals(KeyEvent.KEYCODE_UNKNOWN, sent.get(0).getKeyCode());
		assertEquals(KeyEvent.KEYCODE_UNKNOWN, sent.get(1).getKeyCode());
	}

	@Test
	public void probeFailsWhenEitherEventIsRefused() {
		assertFalse(CarDisplayServerImpl.injectionAccepted(10, e -> false));
		assertFalse(CarDisplayServerImpl.injectionAccepted(10, e -> e.getAction() == KeyEvent.ACTION_DOWN));
		assertFalse(CarDisplayServerImpl.injectionAccepted(10, e -> e.getAction() == KeyEvent.ACTION_UP));
	}
}
