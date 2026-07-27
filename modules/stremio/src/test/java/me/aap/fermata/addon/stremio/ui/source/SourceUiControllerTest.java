package me.aap.fermata.addon.stremio.ui.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.Test;

import me.aap.fermata.addon.stremio.ui.source.SourceUiController.EditorRequest;
import me.aap.fermata.addon.stremio.ui.source.SourceUiController.SourceUiState;

public class SourceUiControllerTest {
	@Test
	public void loadAndCommittedObserverSnapshotsRenderDeterministically() {
		FakeGateway gateway = new FakeGateway(snapshot(1, item("a", 0, true)));
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);

		controller.start();
		assertEquals("a", controller.state().snapshot().sources().get(0).sourceUuid());
		gateway.publish(snapshot(2, item("b", 0, false)));

		assertEquals(2, controller.state().snapshot().revision());
		assertEquals("b", controller.state().snapshot().sources().get(0).sourceUuid());
		assertFalse(controller.state().initialLoading());
		controller.close();
		assertTrue(gateway.observerClosed);
	}

	@Test
	public void invalidFormNeverCrossesGatewayBoundary() {
		FakeGateway gateway = new FakeGateway(SourceUiSnapshot.empty());
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);
		controller.start();
		EditorRequest request = new EditorRequest(null,
				new SourceUiDraft("", "", SourceUiConsent.STRICT));

		boolean submitted = controller.submit(request,
				new SourceUiDraft("http://example.com/manifest.json", "secret",
						SourceUiConsent.STRICT));

		assertFalse(submitted);
		assertEquals(SourceUiError.CLEARTEXT_CONSENT_REQUIRED, listener.lastError);
		assertEquals(0, gateway.operationCalls);
		controller.close();
	}

	@Test
	public void submitAfterControllerCloseNeverCrossesGatewayBoundary() {
		FakeGateway gateway = new FakeGateway(SourceUiSnapshot.empty());
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);
		controller.start();
		controller.close();

		boolean submitted = controller.submit(new EditorRequest(null,
				new SourceUiDraft("", "", SourceUiConsent.STRICT)),
				new SourceUiDraft("https://example.com/manifest.json", "",
						SourceUiConsent.STRICT));

		assertFalse(submitted);
		assertEquals(0, gateway.operationCalls);
	}

	@Test
	public void mutationShowsCancellableStateAndAppliesOnlyCompletionSnapshot() {
		FakeGateway gateway = new FakeGateway(snapshot(1, item("a", 0, true)));
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);
		controller.start();

		controller.refresh("a");
		assertEquals(SourceUiController.Action.REFRESH,
				controller.state().pending().action());
		assertEquals(1, controller.state().snapshot().revision());
		gateway.pending.complete(SourceUiResult.changed(
				snapshot(2, item("a", 0, true))));

		assertNull(controller.state().pending());
		assertEquals(2, controller.state().snapshot().revision());
		controller.close();
	}

	@Test
	public void cancelIsForwardedAndLateCompletionAfterCloseIsIgnored() {
		FakeGateway gateway = new FakeGateway(snapshot(1, item("a", 0, true)));
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);
		controller.start();
		controller.refresh("a");

		controller.cancelPending();
		assertEquals(1, gateway.cancelCalls);
		int renders = listener.states.size();
		controller.close();
		gateway.pending.complete(SourceUiResult.changed(
				snapshot(9, item("late", 0, true))));
		assertEquals(renders, listener.states.size());
	}

	@Test
	public void editDraftAndReorderUseStableSourceIds() {
		FakeGateway gateway = new FakeGateway(snapshot(1,
				item("a", 0, true), item("b", 1, true)));
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);
		controller.start();
		controller.requestEdit("a");
		assertTrue(listener.editor.isEdit());
		assertFalse(listener.editor.toString().contains("secret-a"));

		controller.reorder(List.of("b", "a"));
		assertEquals(List.of("b", "a"), gateway.lastOrder);
		controller.close();
	}

	@Test
	public void olderLoadCannotOverwriteNewerObservedRevision() {
		FakeGateway gateway = new FakeGateway(snapshot(1, item("old", 0, true)));
		gateway.loadFuture = new CompletableFuture<>();
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);
		controller.start();

		gateway.publish(snapshot(3, item("new", 0, true)));
		gateway.loadFuture.complete(snapshot(1, item("old", 0, true)));

		assertEquals(3, controller.state().snapshot().revision());
		assertEquals("new", controller.state().snapshot().sources().get(0).sourceUuid());
		controller.close();
	}

	@Test
	public void safeGatewayFailureIsNormalizedWithoutMessageInspection() {
		FakeGateway gateway = new FakeGateway(SourceUiSnapshot.empty());
		gateway.loadFuture = CompletableFuture.failedFuture(
				new SourceUiFailure(SourceUiError.SECURE_STORAGE));
		RecordingListener listener = new RecordingListener();
		SourceUiController controller = controller(gateway, listener);

		controller.start();

		assertEquals(SourceUiError.SECURE_STORAGE, listener.lastError);
		controller.close();
	}

	@Test
	public void initialLoadDeadlineTerminatesSpinnerAndRejectsLateSnapshot() throws Exception {
		FakeGateway gateway = new FakeGateway(SourceUiSnapshot.empty());
		gateway.loadFuture = new CompletableFuture<>();
		RecordingListener listener = new RecordingListener();
		ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
		try {
			SourceUiController controller = new SourceUiController(
					gateway, listener, Runnable::run, scheduler, 20L);
			controller.start();
			assertTrue(controller.state().initialLoading());

			await(() -> !controller.state().initialLoading());
			assertEquals(SourceUiError.UNKNOWN, listener.lastError);
			assertTrue(gateway.loadFuture.isCancelled());
			assertFalse(gateway.loadFuture.complete(snapshot(9, item("late", 0, true))));
			assertTrue(controller.state().snapshot().sources().isEmpty());
			controller.close();
		} finally {
			scheduler.shutdownNow();
		}
	}

	@Test
	public void mutationDeadlineClearsPendingCancelsGatewayAndRejectsLateResult()
			throws Exception {
		FakeGateway gateway = new FakeGateway(snapshot(1, item("a", 0, true)));
		RecordingListener listener = new RecordingListener();
		ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
		try {
			SourceUiController controller = new SourceUiController(
					gateway, listener, Runnable::run, scheduler, 20L);
			controller.start();
			controller.refresh("a");

			await(() -> controller.state().pending() == null);
			assertEquals(1, gateway.cancelCalls);
			assertEquals(SourceUiError.UNKNOWN, listener.lastError);
			assertFalse(gateway.pending.complete(SourceUiResult.changed(
					snapshot(9, item("late", 0, true)))));
			assertEquals(1, controller.state().snapshot().revision());
			controller.close();
		} finally {
			scheduler.shutdownNow();
		}
	}

	@Test
	public void closeCancelsInitialLoadAndPreventsDeadlinePublication() throws Exception {
		FakeGateway gateway = new FakeGateway(SourceUiSnapshot.empty());
		gateway.loadFuture = new CompletableFuture<>();
		RecordingListener listener = new RecordingListener();
		ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
		try {
			SourceUiController controller = new SourceUiController(
					gateway, listener, Runnable::run, scheduler, 40L);
			controller.start();
			controller.close();
			TimeUnit.MILLISECONDS.sleep(100L);

			assertTrue(gateway.loadFuture.isCancelled());
			assertNull(listener.lastError);
			assertFalse(controller.state().initialLoading());
		} finally {
			scheduler.shutdownNow();
		}
	}

	private static SourceUiController controller(FakeGateway gateway,
			RecordingListener listener) {
		return new SourceUiController(gateway, listener, Runnable::run);
	}

	private static SourceUiSnapshot snapshot(long revision, SourceUiItem... sources) {
		return new SourceUiSnapshot(revision, List.of(sources));
	}

	private static SourceUiItem item(String id, int position, boolean enabled) {
		return new SourceUiItem(id, "Provider " + id, "1.0", "https://example.com/manifest.json",
				enabled, position, null, false, SourceUiConsent.STRICT);
	}

	private static void await(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() >= deadline) throw new AssertionError("condition timed out");
			TimeUnit.MILLISECONDS.sleep(5L);
		}
	}

	private static final class RecordingListener implements SourceUiController.Listener {
		private final List<SourceUiState> states = new ArrayList<>();
		private SourceUiError lastError;
		private EditorRequest editor;

		@Override
		public void render(SourceUiState state) {
			states.add(state);
		}

		@Override
		public void showEditor(EditorRequest request) {
			editor = request;
		}

		@Override
		public void confirmRemove(SourceUiItem source) {
		}

		@Override
		public void showError(SourceUiError error) {
			lastError = error;
		}
	}

	private static final class FakeGateway implements SourceUiGateway {
		private final SourceUiSnapshot loaded;
		private CompletableFuture<SourceUiSnapshot> loadFuture;
		private Consumer<SourceUiSnapshot> observer;
		private CompletableFuture<SourceUiResult> pending;
		private int operationCalls;
		private int cancelCalls;
		private boolean observerClosed;
		private List<String> lastOrder;

		private FakeGateway(SourceUiSnapshot loaded) {
			this.loaded = loaded;
		}

		@Override
		public CompletableFuture<SourceUiSnapshot> load() {
			return (loadFuture != null) ? loadFuture : CompletableFuture.completedFuture(loaded);
		}

		@Override
		public CompletableFuture<SourceUiDraft> loadDraft(String sourceUuid) {
			return CompletableFuture.completedFuture(new SourceUiDraft(
					"https://example.com/" + sourceUuid + "/manifest.json",
					"secret-" + sourceUuid, SourceUiConsent.STRICT));
		}

		@Override
		public CompletableFuture<me.aap.fermata.addon.stremio.ui.config.StremioConfigLaunch>
				loadConfiguration(String sourceUuid) {
			return CompletableFuture.failedFuture(new UnsupportedOperationException());
		}

		@Override
		public AutoCloseable observe(Consumer<SourceUiSnapshot> observer) {
			this.observer = observer;
			return () -> observerClosed = true;
		}

		@Override
		public SourceUiOperation add(SourceUiDraft draft) {
			return operation();
		}

		@Override
		public SourceUiOperation edit(String sourceUuid, SourceUiDraft draft) {
			return operation();
		}

		@Override
		public SourceUiOperation configure(String sourceUuid,
				me.aap.fermata.addon.stremio.ui.config.StremioConfigResult result) {
			return operation();
		}

		@Override
		public SourceUiOperation setEnabled(String sourceUuid, boolean enabled) {
			return operation();
		}

		@Override
		public SourceUiOperation refresh(String sourceUuid) {
			return operation();
		}

		@Override
		public SourceUiOperation remove(String sourceUuid) {
			return operation();
		}

		@Override
		public SourceUiOperation reorder(List<String> orderedSourceUuids) {
			lastOrder = List.copyOf(orderedSourceUuids);
			return operation();
		}

		private SourceUiOperation operation() {
			operationCalls++;
			pending = new CompletableFuture<>();
			return SourceUiOperation.of(pending, () -> cancelCalls++);
		}

		private void publish(SourceUiSnapshot snapshot) {
			observer.accept(snapshot);
		}
	}
}
