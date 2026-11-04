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
