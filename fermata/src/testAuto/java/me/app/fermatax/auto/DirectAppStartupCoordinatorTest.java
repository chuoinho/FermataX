package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.utils.async.Promise;

public class DirectAppStartupCoordinatorTest {
	@Test
	public void concurrentActivitiesShareOneConnectionAndRejectStaleUi() throws Exception {
		AtomicInteger connects = new AtomicInteger();
		Promise<TestConnection> attempt = new Promise<>();
		DirectAppStartupCoordinator<TestConnection> coordinator = coordinator();

		DirectAppStartupCoordinator.Startup<TestConnection> first =
				coordinator.begin(() -> {
					connects.incrementAndGet();
					return attempt;
				});
		DirectAppStartupCoordinator.Startup<TestConnection> second =
				coordinator.begin(() -> {
					connects.incrementAndGet();
					return new Promise<>();
				});

		assertEquals(1, connects.get());
		assertEquals(DirectAppStartupCoordinator.State.SERVICE_CONNECTING, coordinator.getState());

		TestConnection connection = new TestConnection();
		attempt.complete(connection);
		assertSame(connection, first.getConnection().get());
		assertSame(connection, second.getConnection().get());
		assertFalse(coordinator.beginUi(first.getGeneration(), connection));
		assertTrue(coordinator.beginUi(second.getGeneration(), connection));
		coordinator.uiReady(second.getGeneration());
		assertEquals(DirectAppStartupCoordinator.State.UI_READY, coordinator.getState());
	}

	@Test
	public void failedConnectionRetriesOnceWithoutParallelBind() throws Exception {
		AtomicInteger connects = new AtomicInteger();
		Promise<TestConnection> firstAttempt = new Promise<>();
		Promise<TestConnection> secondAttempt = new Promise<>();
		DirectAppStartupCoordinator<TestConnection> coordinator = coordinator();

		DirectAppStartupCoordinator.Startup<TestConnection> startup = coordinator.begin(() ->
				(connects.getAndIncrement() == 0) ? firstAttempt : secondAttempt);
		firstAttempt.completeExceptionally(new IllegalStateException("first failure"));
		assertEquals(2, connects.get());
		assertFalse(startup.getConnection().isDone());

		TestConnection connection = new TestConnection();
		secondAttempt.complete(connection);
		assertSame(connection, startup.getConnection().get());
		assertEquals(DirectAppStartupCoordinator.State.SERVICE_READY, coordinator.getState());
	}

	@Test
	public void shutdownCancelsPendingAttemptAndInvalidatesGeneration() {
		Promise<TestConnection> attempt = new Promise<>();
		DirectAppStartupCoordinator<TestConnection> coordinator = coordinator();
		DirectAppStartupCoordinator.Startup<TestConnection> startup =
				coordinator.begin(() -> attempt);

		assertEquals(null, coordinator.shutdown());
		assertTrue(attempt.isCancelled());
		assertTrue(startup.getConnection().isCancelled());
		assertFalse(coordinator.isCurrent(startup.getGeneration()));
		assertEquals(DirectAppStartupCoordinator.State.IDLE, coordinator.getState());
	}

	@Test
	public void deadCachedConnectionIsDisposedBeforeReconnect() throws Exception {
		DirectAppStartupCoordinator<TestConnection> coordinator = coordinator();
		TestConnection firstConnection = new TestConnection();
		DirectAppStartupCoordinator.Startup<TestConnection> first =
				coordinator.begin(() -> completed(firstConnection));
		assertSame(firstConnection, first.getConnection().get());

		firstConnection.connected = false;
		TestConnection secondConnection = new TestConnection();
		DirectAppStartupCoordinator.Startup<TestConnection> second =
				coordinator.begin(() -> completed(secondConnection));

		assertEquals(1, firstConnection.disconnects);
		assertSame(secondConnection, second.getConnection().get());
	}

	private static DirectAppStartupCoordinator<TestConnection> coordinator() {
		return new DirectAppStartupCoordinator<>(connection -> connection.connected,
				TestConnection::disconnect);
	}

	private static Promise<TestConnection> completed(TestConnection connection) {
		Promise<TestConnection> result = new Promise<>();
		result.complete(connection);
		return result;
	}

	private static final class TestConnection {
		private boolean connected = true;
		private int disconnects;

		private void disconnect() {
			disconnects++;
			connected = false;
		}
	}
}
