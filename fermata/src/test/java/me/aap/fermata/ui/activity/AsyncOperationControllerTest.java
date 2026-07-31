package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import me.aap.fermata.ui.activity.AsyncOperationController.Operation;
import me.aap.fermata.ui.activity.AsyncOperationController.OperationType;
import me.aap.fermata.ui.activity.AsyncOperationController.Snapshot;
import me.aap.fermata.ui.activity.AsyncOperationController.State;
import me.aap.utils.app.App;
import me.aap.utils.async.Promise;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.os.OsUtils;

public class AsyncOperationControllerTest {
	private static App app;

	@BeforeClass
	public static void setUpClass() {
		TestUtils.enableTestMode();
		OsUtils.isAndroid();
		app = new TestApp();
		app.onCreate();
	}

	@AfterClass
	public static void tearDownClass() {
		app.onTerminate();
		app = null;
	}

	@Test
	public void replacementCancelsOnlyThePreviousOperation() {
		List<Snapshot> states = new ArrayList<>();
		AsyncOperationController controller = new AsyncOperationController(states::add);
		Promise<String> first = new Promise<>();
		Promise<String> second = new Promise<>();

		Operation<String> firstOperation = controller.start("first", OperationType.LEGACY, first);
		Operation<String> secondOperation = controller.start("second", OperationType.LEGACY, second);

		assertTrue(first.isCancelled());
		assertFalse(second.isCancelled());
		assertFalse(controller.isActive(firstOperation.token()));
		assertTrue(controller.isActive(secondOperation.token()));
		assertEquals(List.of(State.RUNNING, State.CANCELLED, State.RUNNING),
				states.stream().map(Snapshot::state).toList());
	}

	@Test
	public void staleCompletionCannotFinishReplacement() {
		List<Snapshot> states = new ArrayList<>();
		AsyncOperationController controller = new AsyncOperationController(states::add);
		Promise<String> first = new Promise<>();
		Promise<String> second = new Promise<>();
		controller.start("first", OperationType.LEGACY, first);
		Operation<String> active = controller.start("second", OperationType.LEGACY, second);

		first.complete("stale");

		assertTrue(controller.isActive(active.token()));
		assertEquals(State.RUNNING, controller.getSnapshot().state());
		second.complete("current");
		assertEquals(State.SUCCESS, controller.getSnapshot().state());
	}

	@Test
	public void explicitCancelPropagatesToUpstream() {
		AsyncOperationController controller = new AsyncOperationController(state -> {
		});
		Promise<String> source = new Promise<>();
		Operation<String> operation = controller.start(this, OperationType.LEGACY, source);

		assertTrue(controller.cancel(operation.future()));
		assertTrue(source.isCancelled());
		assertEquals(State.CANCELLED, controller.getSnapshot().state());
		assertFalse(controller.cancel(operation.future()));
	}

	@Test
	public void ownerAndOperationTypeRemainAttachedToToken() {
		AsyncOperationController controller = new AsyncOperationController(state -> {
		});
		Object owner = new Object();
		Operation<String> operation = controller.start(owner, OperationType.LEGACY, new Promise<>());

		assertSame(owner, operation.token().owner());
		assertEquals(OperationType.LEGACY, operation.token().type());
		assertEquals(30_000L, OperationType.SEARCH.timeoutMillis());
	}

	@Test
	public void timeoutFailureHasASeparateTerminalState() {
		Promise<String> source = new Promise<>();

		assertEquals(State.TIMED_OUT,
				AsyncOperationController.completionState(source, new TimeoutException()));
	}

	@Test
	public void providerFailureIsTerminalAndNextOperationCanStartCleanly() {
		AsyncOperationController controller = new AsyncOperationController(state -> {
		});
		Promise<String> provider = new Promise<>();
		controller.start("provider", OperationType.LEGACY, provider);
		provider.completeExceptionally(new IOException("network unavailable"));
		assertEquals(State.ERROR, controller.getSnapshot().state());

		Promise<String> refresh = new Promise<>();
		Operation<String> replacement = controller.start("source", OperationType.LEGACY, refresh);
		assertTrue(controller.isActive(replacement.token()));
		refresh.complete("ok");
		assertEquals(State.SUCCESS, controller.getSnapshot().state());
	}

	@Test
	public void nestedSlowOperationTimeoutIsRecognized() {
		Promise<String> source = new Promise<>();
		IOException wrapper = new IOException("provider timed out", new TimeoutException("slow"));

		assertEquals(State.TIMED_OUT,
				AsyncOperationController.completionState(source, wrapper));
		assertEquals(60_000L, OperationType.REFRESH.timeoutMillis());
		assertEquals(120_000L, OperationType.STREAM_PREPARE.timeoutMillis());
	}

	@Test
	public void typedTimeoutCancelsUpstreamAndPublishesTimedOut() throws Exception {
		CountDownLatch completed = new CountDownLatch(1);
		AsyncOperationController controller = new AsyncOperationController(state -> {
			if (state.state() == State.TIMED_OUT) completed.countDown();
		}, type -> 1L);
		TrackingPromise<String> source = new TrackingPromise<>();

		controller.start(this, OperationType.SEARCH, source);

		assertTrue(completed.await(5, TimeUnit.SECONDS));
		assertTrue(source.cancelled.await(5, TimeUnit.SECONDS));
		assertTrue(source.isCancelled());
		assertEquals(State.TIMED_OUT, controller.getSnapshot().state());
		assertEquals(OperationType.SEARCH, controller.getSnapshot().token().type());
	}

	private static final class TrackingPromise<T> extends Promise<T> {
		private final CountDownLatch cancelled = new CountDownLatch(1);

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			boolean result = super.cancel(mayInterruptIfRunning);
			if (result) cancelled.countDown();
			return result;
		}
	}

	private static final class TestApp extends App {
		@Override
		public String getLogTag() {
			return "AsyncOperationControllerTest";
		}
	}
}
