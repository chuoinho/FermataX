package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.fermata.ui.view.VideoView;

/**
 * Contract tests verifying VideoOutputCoordinator surface routing behavior
 * during rapid reverse-camera surface rebind cycles (e.g. R -> D -> R -> D gear changes)
 * ported to baseline FermataX for contract verification.
 */
public class ReverseCameraSurfaceRebindTest {

	@Test
	public void rapidReverseGearCyclingMaintainsBindingContract() throws Exception {
		var coordinator = new VideoOutputCoordinator();
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		long lease = 101L;

		coordinator.bind(engine, true);
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, lease);

		final int cycles = 10;

		for (int i = 0; i < cycles; i++) {
			VideoView carView = view();

			// Shift to Drive: OEM display returns to Android Auto video surface
			coordinator.add(carView, 0, RuntimeHostMode.AA_PROJECTION, lease);
			assertSame("Cycle " + i + " D: carView must be selected", carView, coordinator.getSelected());
			assertTrue("Cycle " + i + " D: carView must be marked selected", coordinator.isSelected(carView));
			assertEquals("Cycle " + i + " D: targets count must be exactly 1", 1, targetCount(coordinator));
			assertEquals("Cycle " + i + " D: decoder engine must be bound to active carView",
					carView, latestBinding(bindings));

			// Shift to Reverse: OEM reverse camera commandeers screen, destroying AA video surface
			coordinator.remove(carView);
			assertNull("Cycle " + i + " R: selected view must be null during backup camera", coordinator.getSelected());
			assertFalse("Cycle " + i + " R: carView must no longer be selected", coordinator.isSelected(carView));
			assertEquals("Cycle " + i + " R: targets count must be 0", 0, targetCount(coordinator));
			assertNull("Cycle " + i + " R: decoder engine must be unbound (null)", latestBinding(bindings));
		}

		// Contract: Exactly 20 bindings events (alternating view_i, null)
		assertEquals(cycles * 2, bindings.size());

		// Final shift to Drive: verify clean final binding to new surface
		VideoView finalCarView = view();
		coordinator.add(finalCarView, 0, RuntimeHostMode.AA_PROJECTION, lease);
		assertSame(finalCarView, coordinator.getSelected());
		assertEquals(1, targetCount(coordinator));
		assertSame(finalCarView, latestBinding(bindings));
	}

	@Test
	public void rapidReverseGearCyclingReusingSameSurfaceInstance() throws Exception {
		var coordinator = new VideoOutputCoordinator();
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		long lease = 102L;
		VideoView carView = view();

		coordinator.bind(engine, true);
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, lease);

		for (int i = 0; i < 10; i++) {
			// Shift to D
			coordinator.add(carView, 0, RuntimeHostMode.AA_PROJECTION, lease);
			assertSame(carView, coordinator.getSelected());
			assertEquals(1, targetCount(coordinator));
			assertSame(carView, latestBinding(bindings));

			// Shift to R
			coordinator.remove(carView);
			assertNull(coordinator.getSelected());
			assertEquals(0, targetCount(coordinator));
			assertNull(latestBinding(bindings));
		}

		assertEquals(20, bindings.size());
		// Final state
		coordinator.add(carView, 0, RuntimeHostMode.AA_PROJECTION, lease);
		assertSame(carView, coordinator.getSelected());
		assertEquals(1, targetCount(coordinator));
		assertSame(carView, latestBinding(bindings));
	}

	@Test
	public void reverseInterruptionWithDualDisplaysDoesNotLeakFramesToPhone() throws Exception {
		var coordinator = new VideoOutputCoordinator();
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		long lease = 201L;

		VideoView phoneView = view();
		VideoView carView = view();

		coordinator.bind(engine, true);

		// Phone view is active on device
		coordinator.add(phoneView, 10, RuntimeHostMode.PHONE);
		coordinator.selectHost(RuntimeHostMode.PHONE);
		assertSame("Phone view should initially be selected on PHONE host", phoneView, coordinator.getSelected());
		assertSame(phoneView, latestBinding(bindings));

		// Android Auto connects and takes projection focus
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, lease);
		coordinator.add(carView, 0, RuntimeHostMode.AA_PROJECTION, lease);
		assertSame("Car view should be selected when AA host active", carView, coordinator.getSelected());
		assertSame(carView, latestBinding(bindings));
		assertEquals(2, targetCount(coordinator));

		// Driver shifts to Reverse: Backup camera commandeers car display. Car surface removed.
		coordinator.remove(carView);

		// CRITICAL CONTRACT: Under AA_PROJECTION host, coordinator MUST NOT fall back
		// or leak video frames to the phone surface!
		assertNull("Under AA_PROJECTION, getSelected() must be null during reverse interruption",
				coordinator.getSelected());
		assertNull("Decoder must be unbound (null), NOT leaked to phoneView",
				latestBinding(bindings));
		assertFalse("Engine must not report bound", coordinator.isBound(engine));
		assertEquals("Phone target must remain registered in pool", 1, targetCount(coordinator));

		// Driver shifts D -> R -> D multiple times while parking
		for (int i = 0; i < 5; i++) {
			VideoView tempCarView = view();
			// D
			coordinator.add(tempCarView, 0, RuntimeHostMode.AA_PROJECTION, lease);
			assertSame(tempCarView, coordinator.getSelected());
			assertSame(tempCarView, latestBinding(bindings));
			assertEquals(2, targetCount(coordinator));

			// R
			coordinator.remove(tempCarView);
			assertNull(coordinator.getSelected());
			assertNull("Never leak frames to phone surface while reversing", latestBinding(bindings));
			assertEquals(1, targetCount(coordinator));
		}

		// User explicitly switches back to PHONE mode
		coordinator.selectHost(RuntimeHostMode.PHONE);
		assertSame("When host switches to PHONE, phone view must be selected",
				phoneView, coordinator.getSelected());
		assertSame("Decoder must be cleanly rebound to phoneView",
				phoneView, latestBinding(bindings));
		assertTrue(coordinator.isBound(engine));

		// User returns to AA projection mode while car surface is recreated
		VideoView restoredCarView = view();
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, lease);
		assertNull(coordinator.getSelected());
		assertNull(latestBinding(bindings));

		// Car surface recreates and attaches
		coordinator.add(restoredCarView, 0, RuntimeHostMode.AA_PROJECTION, lease);
		assertSame("Restored car surface must be bound", restoredCarView, coordinator.getSelected());
		assertSame(restoredCarView, latestBinding(bindings));
		assertEquals(2, targetCount(coordinator));
	}

	@Test
	public void reverseCameraCyclingDuringAudioPlaybackDoesNotBindVideoDecoder() throws Exception {
		var coordinator = new VideoOutputCoordinator();
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = audioEngine(bindings);
		long lease = 301L;

		// Bind as audio (video = false)
		coordinator.bind(engine, false);
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, lease);

		for (int i = 0; i < 5; i++) {
			VideoView carView = view();
			coordinator.add(carView, 0, RuntimeHostMode.AA_PROJECTION, lease);
			assertSame(carView, coordinator.getSelected());
			assertEquals(1, targetCount(coordinator));

			coordinator.remove(carView);
			assertNull(coordinator.getSelected());
			assertEquals(0, targetCount(coordinator));
		}

		// Contract: setVideoView must NEVER be called on the engine for audio items
		assertTrue("Audio-only engine must never have video surfaces bound", bindings.isEmpty());
	}

	@Test
	public void reverseInterruptionWithStaleLeaseRejection() throws Exception {
		var coordinator = new VideoOutputCoordinator();
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		long oldLease = 401L;
		long newLease = 402L;

		coordinator.bind(engine, true);
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, oldLease);

		VideoView oldCar = view();
		coordinator.add(oldCar, 0, RuntimeHostMode.AA_PROJECTION, oldLease);
		assertSame(oldCar, coordinator.getSelected());

		// Reverse camera triggers removal
		coordinator.remove(oldCar);
		assertNull(coordinator.getSelected());

		// Session re-negotiates with new lease while still in reverse
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, newLease);

		// A delayed callback tries to register with the old lease
		VideoView staleCar = view();
		coordinator.add(staleCar, 0, RuntimeHostMode.AA_PROJECTION, oldLease);
		assertNull("Stale lease target must not be selected", coordinator.getSelected());

		// Current car surface registers with the new lease
		VideoView currentCar = view();
		coordinator.add(currentCar, 0, RuntimeHostMode.AA_PROJECTION, newLease);
		assertSame("Target with matching lease must be selected", currentCar, coordinator.getSelected());
		assertSame(currentCar, latestBinding(bindings));
	}

	@Test
	public void idempotentSurfaceOperationsDuringRapidTransitions() throws Exception {
		var coordinator = new VideoOutputCoordinator();
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		long lease = 501L;

		coordinator.bind(engine, true);
		coordinator.selectHost(RuntimeHostMode.AA_PROJECTION, () -> true, lease);

		VideoView carView = view();

		// Add twice
		coordinator.add(carView, 0, RuntimeHostMode.AA_PROJECTION, lease);
		coordinator.add(carView, 0, RuntimeHostMode.AA_PROJECTION, lease);
		assertEquals("Duplicate add must not create duplicate registrations", 1, targetCount(coordinator));
		assertEquals("Binding should only occur once", 1, bindings.size());

		// Remove twice
		coordinator.remove(carView);
		coordinator.remove(carView);
		assertEquals(0, targetCount(coordinator));
		assertNull(coordinator.getSelected());
		assertEquals(2, bindings.size());
		assertNull(latestBinding(bindings));
	}

	private static int targetCount(VideoOutputCoordinator coordinator) {
		try {
			Field field = VideoOutputCoordinator.class.getDeclaredField("targets");
			field.setAccessible(true);
			List<?> list = (List<?>) field.get(coordinator);
			return list.size();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static VideoView latestBinding(List<VideoView> bindings) {
		if (bindings.isEmpty()) return null;
		return bindings.get(bindings.size() - 1);
	}

	private static MediaEngine videoEngine(List<VideoView> bindings) {
		return engine(bindings, true);
	}

	private static MediaEngine audioEngine(List<VideoView> bindings) {
		return engine(bindings, false);
	}

	private static MediaEngine engine(List<VideoView> bindings, boolean video) {
		PlayableItem source = (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "isVideo" -> video;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getSource" -> source;
					case "setVideoView" -> {
						VideoView view = (VideoView) args[0];
						bindings.add(view);
						yield null;
					}
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
	}

	private static VideoView view() throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return (VideoView) unsafeType.getMethod("allocateInstance", Class.class)
				.invoke(unsafe, VideoView.class);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return '\0';
		return null;
	}
}
