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
import me.aap.utils.async.FutureSupplier;
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

	@Test
	public void typedRequestIsStampedAndNewerRequestCancelsOlderCompletion() {
		AutomotiveConnectionState connection = AutomotiveConnectionState.get();
		Object host = new Object();
		connection.connectionChanged(true);
		connection.appVisibilityChanged(host, true);
		try {
			AutomotiveNavigationController controller = new AutomotiveNavigationController();
			List<OpenOnCarRequest> received = new ArrayList<>();
			List<BooleanSupplier> guards = new ArrayList<>();
			List<Promise<OpenResult>> pending = new ArrayList<>();
			controller.register(new AutomotiveNavigationController.Navigator() {
				@Override
				public FutureSupplier<OpenResult> open(int id, BooleanSupplier guard) {
					return completed(OpenResult.FAILED);
				}

				@Override
				public FutureSupplier<OpenResult> open(OpenOnCarRequest request,
						BooleanSupplier guard) {
					received.add(request);
					guards.add(guard);
					Promise<OpenResult> result = new Promise<>();
					pending.add(result);
					return result;
				}
			});
			long modeRevision = controller.getOpenOnCarMode().revision();
			OpenOnCarRequest firstRequest = new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 71,
					"https://first.example", new OpenOnCarToken(0, 0, modeRevision, 9, 0));
			OpenOnCarRequest secondRequest = new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 71,
					"https://second.example", new OpenOnCarToken(0, 0, modeRevision, 10, 0));

			var first = controller.open(firstRequest);
			var second = controller.open(secondRequest);

			assertFalse(guards.get(0).getAsBoolean());
			assertTrue(guards.get(1).getAsBoolean());
			assertEquals(connection.connectionEpoch(), received.get(1).token().connectionEpoch());
			assertEquals(10, received.get(1).token().sourceGeneration());
			assertTrue(received.get(1).token().requestId() > received.get(0).token().requestId());
			pending.get(0).complete(OpenResult.LOAD_DISPATCHED);
			pending.get(1).complete(OpenResult.LOAD_DISPATCHED);
			assertEquals(OpenResult.CANCELLED, first.peek());
			assertEquals(OpenResult.LOAD_DISPATCHED, second.peek());
		} finally {
			connection.appVisibilityChanged(host, false);
			connection.connectionChanged(false);
		}
	}

	@Test
	public void typedRequestIsCancelledWhenModeChangesBeforeReceiverCompletes() {
		AutomotiveConnectionState connection = AutomotiveConnectionState.get();
		Object host = new Object();
		connection.connectionChanged(true);
		connection.appVisibilityChanged(host, true);
		try {
			AutomotiveNavigationController controller = new AutomotiveNavigationController();
			Promise<OpenResult> pending = new Promise<>();
			AtomicReference<BooleanSupplier> guard = new AtomicReference<>();
			controller.register(new AutomotiveNavigationController.Navigator() {
				@Override
				public FutureSupplier<OpenResult> open(int id, BooleanSupplier current) {
					return completed(OpenResult.FAILED);
				}

				@Override
				public FutureSupplier<OpenResult> open(OpenOnCarRequest request,
						BooleanSupplier current) {
					guard.set(current);
					return pending;
				}
			});
			long revision = controller.getOpenOnCarMode().revision();
			var result = controller.open(new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 71,
					"https://example.test", new OpenOnCarToken(0, 0, revision, 4, 0)));

			controller.getOpenOnCarMode().setEnabled(true);
			assertFalse(guard.get().getAsBoolean());
			pending.complete(OpenResult.LOAD_DISPATCHED);
			assertEquals(OpenResult.CANCELLED, result.peek());
		} finally {
			connection.appVisibilityChanged(host, false);
			connection.connectionChanged(false);
		}
	}

	@Test
	public void typedWebRequestDoesNotFallBackToLegacyAddonNavigation() {
		AutomotiveConnectionState connection = AutomotiveConnectionState.get();
		Object host = new Object();
		connection.connectionChanged(true);
		connection.appVisibilityChanged(host, true);
		try {
			AutomotiveNavigationController controller = new AutomotiveNavigationController();
			List<Integer> opened = new ArrayList<>();
			controller.register((id, guard) -> {
				opened.add(id);
				return completed(OpenResult.OPENED);
			});
			long revision = controller.getOpenOnCarMode().revision();

			OpenResult result = controller.open(new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 71,
					"https://example.test", new OpenOnCarToken(0, 0, revision, 1, 0))).peek();

			assertEquals(OpenResult.NOT_READY, result);
			assertTrue(opened.isEmpty());
		} finally {
			connection.appVisibilityChanged(host, false);
			connection.connectionChanged(false);
		}
	}

	@Test
	public void typedRequestDoesNotReplayAfterReconnect() {
		AutomotiveConnectionState connection = AutomotiveConnectionState.get();
		Object host = new Object();
		connection.connectionChanged(true);
		connection.appVisibilityChanged(host, true);
		try {
			AutomotiveNavigationController controller = new AutomotiveNavigationController();
			Promise<OpenResult> pending = new Promise<>();
			List<BooleanSupplier> guards = new ArrayList<>();
			controller.register(new AutomotiveNavigationController.Navigator() {
				@Override
				public FutureSupplier<OpenResult> open(int id, BooleanSupplier current) {
					return completed(OpenResult.FAILED);
				}

				@Override
				public FutureSupplier<OpenResult> open(OpenOnCarRequest request,
						BooleanSupplier current) {
					guards.add(current);
					return pending;
				}
			});
			long revision = controller.getOpenOnCarMode().revision();
			var result = controller.open(new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 71,
					"https://example.test", new OpenOnCarToken(0, 0, revision, 2, 0)));

			connection.connectionChanged(false);
			connection.connectionChanged(true);
			assertFalse(guards.get(0).getAsBoolean());
			assertEquals(1, guards.size());
			pending.complete(OpenResult.LOAD_DISPATCHED);
			assertEquals(OpenResult.CANCELLED, result.peek());
		} finally {
			connection.appVisibilityChanged(host, false);
			connection.connectionChanged(false);
		}
	}
}
