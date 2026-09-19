package me.aap.fermata.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static me.aap.utils.async.Completed.completed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.Test;

import me.aap.utils.async.Promise;
import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;

public class AutomotiveNavigationControllerTest {
	@Test
	public void readinessTracksNavigatorRegistrationAndRawConnection() {
		AutomotiveConnectionState connection = new AutomotiveConnectionState();
		AutomotiveNavigationController controller = new AutomotiveNavigationController(connection);
		List<String> events = new ArrayList<>();
		controller.addReadinessListener((available, epoch) ->
				events.add(available + ":" + epoch));
		AutomotiveNavigationController.Navigator navigator = (id, current) ->
				completed(OpenResult.OPENED);

		connection.connectionChanged(true);
		controller.register(navigator);
		connection.connectionChanged(false);
		controller.unregister(navigator);

		assertEquals(List.of("false:0", "false:1", "true:1", "false:2"), events);
		assertFalse(controller.getOpenOnCarMode().isAvailable());
		assertFalse(controller.getOpenOnCarMode().isEnabled());
	}

	@Test
	public void openIsAcceptedOnlyByTheRegisteredCarNavigator() {
		AutomotiveNavigationController controller = new AutomotiveNavigationController();
		List<Integer> destinations = new ArrayList<>();
		Object host = new Object();
		AutomotiveConnectionState.get().connectionChanged(true);
		AutomotiveConnectionState.get().appVisibilityChanged(host, true);
		AutomotiveNavigationController.Navigator navigator = (destination, current) -> {
			destinations.add(destination);
			return completed(current.getAsBoolean() ? OpenResult.OPENED : OpenResult.CANCELLED);
		};

		assertEquals(OpenResult.NOT_READY, controller.open(41).peek());
		controller.register(navigator);
		assertEquals(OpenResult.OPENED, controller.open(42).peek());
		controller.unregister(navigator);
		assertEquals(OpenResult.NOT_READY, controller.open(43).peek());
		assertEquals(List.of(42), destinations);
		AutomotiveConnectionState.get().appVisibilityChanged(host, false);
		AutomotiveConnectionState.get().connectionChanged(false);
	}

	@Test
	public void staleNavigatorCannotUnregisterItsReplacement() {
		AutomotiveNavigationController controller = new AutomotiveNavigationController();
		List<Integer> destinations = new ArrayList<>();
		Object host = new Object();
		AutomotiveConnectionState.get().connectionChanged(true);
		AutomotiveConnectionState.get().appVisibilityChanged(host, true);
		AutomotiveNavigationController.Navigator stale = (destination, guard) -> completed(OpenResult.FAILED);
		AutomotiveNavigationController.Navigator current = (destination, guard) -> {
			destinations.add(destination);
			return completed(OpenResult.OPENED);
		};

		controller.register(stale);
		controller.register(current);
		controller.unregister(stale);

		assertEquals(OpenResult.OPENED, controller.open(7).peek());
		assertEquals(List.of(7), destinations);
		AutomotiveConnectionState.get().appVisibilityChanged(host, false);
		AutomotiveConnectionState.get().connectionChanged(false);
	}

	@Test
	public void completedWorkFromUnregisteredHostIsCancelled() {
		AutomotiveConnectionState connection = AutomotiveConnectionState.get();
		Object host = new Object();
		connection.connectionChanged(true);
		connection.appVisibilityChanged(host, true);
		try {
			AutomotiveNavigationController controller = new AutomotiveNavigationController();
			Promise<OpenResult> pending = new Promise<>();
			AtomicReference<BooleanSupplier> guard = new AtomicReference<>();
			AutomotiveNavigationController.Navigator navigator = (id, current) -> {
				guard.set(current);
				return pending;
			};
			controller.register(navigator);
			var result = controller.open(42);
			controller.unregister(navigator);
			assertFalse(guard.get().getAsBoolean());
			pending.complete(OpenResult.OPENED);
			assertEquals(OpenResult.CANCELLED, result.peek());
		} finally {
			connection.appVisibilityChanged(host, false);
			connection.connectionChanged(false);
		}
	}

	@Test
	public void newerRequestSupersedesPendingRequest() {
		AutomotiveConnectionState connection = AutomotiveConnectionState.get();
		Object host = new Object();
		connection.connectionChanged(true);
		connection.appVisibilityChanged(host, true);
		try {
			AutomotiveNavigationController controller = new AutomotiveNavigationController();
			List<BooleanSupplier> guards = new ArrayList<>();
			List<Promise<OpenResult>> pending = new ArrayList<>();
			controller.register((id, guard) -> {
				guards.add(guard);
				Promise<OpenResult> promise = new Promise<>();
				pending.add(promise);
				return promise;
			});
			var first = controller.open(1);
			var second = controller.open(2);
			assertFalse(guards.get(0).getAsBoolean());
			assertTrue(guards.get(1).getAsBoolean());
			pending.get(0).complete(OpenResult.OPENED);
			pending.get(1).complete(OpenResult.OPENED);
			assertEquals(OpenResult.CANCELLED, first.peek());
			assertEquals(OpenResult.OPENED, second.peek());
		} finally {
			connection.appVisibilityChanged(host, false);
			connection.connectionChanged(false);
		}
	}
}
