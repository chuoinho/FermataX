package me.aap.fermata.addon.stremio.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Test;

public class StremioPresenterTest {
	@Test
	public void staleCompletionCannotReplaceNewerRoute() {
		FakeLoader loader = new FakeLoader();
		List<StremioScreenState> states = new ArrayList<>();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, states::add);

		presenter.start();
		FakeRequest home = loader.requests.remove();
		presenter.navigate(new StremioRoute.Search("matrix"));
		FakeRequest search = loader.requests.remove();
		assertTrue(home.cancelled);

		home.complete(List.of(poster("old", "Old")));
		assertEquals(StremioScreenState.Phase.LOADING, presenter.getState().phase());
		search.complete(List.of(poster("new", "The Matrix")));

		assertEquals(StremioScreenState.Phase.CONTENT, presenter.getState().phase());
		assertEquals("new", presenter.getState().models().get(0).stableKey());
	}

	@Test
	public void backMovesExactlyOneRouteAndReloadsIt() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		loader.requests.remove();
		presenter.navigate(new StremioRoute.Discover("stremio:catalog:popular", "", 0));
		loader.requests.remove();
		presenter.navigate(new StremioRoute.Details("stremio:meta:abc"));
		FakeRequest details = loader.requests.remove();

		assertTrue(presenter.back());
		assertTrue(details.cancelled);
		assertEquals(new StremioRoute.Discover("stremio:catalog:popular", "", 0),
				presenter.getState().route());
		loader.requests.remove();
		assertTrue(presenter.back());
		loader.requests.remove();
		assertFalse(presenter.back());
	}

	@Test
	public void emptyFailureAndCloseHaveExplicitStates() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		FakeRequest home = loader.requests.remove();
		home.complete(List.of());
		assertEquals(StremioScreenState.Phase.EMPTY, presenter.getState().phase());

		presenter.refresh();
		FakeRequest refresh = loader.requests.remove();
		refresh.result.completeExceptionally(new IllegalStateException("secret URL"));
		assertEquals(StremioScreenState.Phase.ERROR, presenter.getState().phase());
		assertEquals("IllegalStateException", presenter.getState().message());

		presenter.refresh();
		FakeRequest closing = loader.requests.remove();
		presenter.close();
		assertTrue(closing.cancelled);
		closing.complete(List.of(poster("late", "Late")));
		assertEquals(StremioScreenState.Phase.LOADING, presenter.getState().phase());
	}

	@Test
	public void refreshAndBackKeepGenerationBoundCachedContent() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		FakeRequest home = loader.requests.remove();
		var homePoster = poster("home", "Home movie");
		home.complete(List.of(homePoster));

		presenter.refresh();
		FakeRequest refresh = loader.requests.remove();
		assertEquals(List.of(homePoster), presenter.getState().models());
		refresh.result.completeExceptionally(new IllegalStateException("offline"));
		assertEquals(StremioScreenState.Phase.ERROR, presenter.getState().phase());
		assertEquals(List.of(homePoster), presenter.getState().models());

		presenter.navigate(new StremioRoute.Search("matrix"));
		loader.requests.remove().complete(List.of(poster("search", "The Matrix")));
		assertTrue(presenter.back());
		loader.requests.remove();
		assertEquals(List.of(homePoster), presenter.getState().models());
	}

	@Test
	public void backRestoresSavedFocusAndShelfOffsets() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		loader.requests.remove().complete(List.of(poster("home", "Home movie")));
		StremioViewportState viewport = new StremioViewportState(
				"home:poster:3", 2, Map.of("section:popular", 3));
		presenter.saveViewport(viewport);

		presenter.navigate(new StremioRoute.Search("matrix"));
		loader.requests.remove().complete(List.of(poster("search", "The Matrix")));
		assertTrue(presenter.back());
		loader.requests.remove();

		assertEquals(viewport, presenter.getState().viewport());
	}

	@Test
	public void replaceDoesNotAddFilterOrSeasonHistory() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		loader.requests.remove();
		presenter.navigate(new StremioRoute.Discover("catalog-a", "", 0));
		loader.requests.remove();
		presenter.replace(new StremioRoute.Discover("catalog-a", "Drama", 0));
		loader.requests.remove();

		assertTrue(presenter.back());
		loader.requests.remove();
		assertTrue(presenter.getState().route() instanceof StremioRoute.Home);
		assertFalse(presenter.back());
	}

	@Test
	public void openPathBuildsCanonicalPlaybackBackStack() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		loader.requests.remove();
		StremioRoute.Details details = new StremioRoute.Details("meta-a", 2);
		StremioRoute.Streams streams = new StremioRoute.Streams("episode-a");
		presenter.openPath(List.of(new StremioRoute.Home(), details, streams));
		loader.requests.remove();

		assertEquals(streams, presenter.getState().route());
		assertTrue(presenter.back());
		loader.requests.remove();
		assertEquals(details, presenter.getState().route());
		assertTrue(presenter.back());
		loader.requests.remove();
		assertTrue(presenter.getState().route() instanceof StremioRoute.Home);
	}

	@Test
	public void incrementalPageIsVisibleButCannotOutliveItsGeneration() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		FakeRequest home = loader.requests.remove();
		home.update(List.of(poster("partial", "Partial")));
		assertEquals(StremioScreenState.Phase.LOADING, presenter.getState().phase());
		assertEquals("partial", presenter.getState().models().get(0).stableKey());

		presenter.navigate(new StremioRoute.Search("new"));
		FakeRequest search = loader.requests.remove();
		home.update(List.of(poster("stale", "Stale")));
		assertTrue(presenter.getState().models().isEmpty());
		search.complete(List.of(poster("new", "New")));
		assertEquals("new", presenter.getState().models().get(0).stableKey());
	}

	@Test
	public void evictedRouteCacheReloadsTheRouteWithoutReusingOldContent() {
		FakeLoader loader = new FakeLoader();
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});
		presenter.start();
		loader.requests.remove().complete(List.of(poster("home", "Home")));

		for (int i = 0; i < 17; i++) {
			presenter.navigate(new StremioRoute.Search("query-" + i));
			loader.requests.remove().complete(List.of(poster("result-" + i,
					"Result " + i)));
		}

		assertTrue(presenter.back());
		loader.requests.remove().complete(List.of(poster("fresh", "Fresh result")));
		assertEquals("fresh", presenter.getState().models().get(0).stableKey());

		while (presenter.back()) {
			FakeRequest request = loader.requests.remove();
			if (presenter.getState().route() instanceof StremioRoute.Home) {
				request.complete(List.of(poster("home-reloaded", "Home reloaded")));
				break;
			}
			request.complete(List.of(poster("route", "Route")));
		}
		assertEquals(new StremioRoute.Home(), presenter.getState().route());
		assertEquals("home-reloaded", presenter.getState().models().get(0).stableKey());
	}

	@Test
	public void requestDeadlineTerminatesSpinnerAndRejectsLateCompletion() throws Exception {
		FakeLoader loader = new FakeLoader();
		ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
		try {
			CountDownLatch failed = new CountDownLatch(1);
			StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
				if (state.phase() == StremioScreenState.Phase.ERROR) failed.countDown();
			}, scheduler, 20L);
			presenter.start();
			FakeRequest request = loader.requests.remove();
			assertTrue(failed.await(2, TimeUnit.SECONDS));
			assertTrue(request.cancelled);
			assertEquals(StremioScreenState.Phase.ERROR, presenter.getState().phase());

			request.complete(List.of(poster("late", "Late")));
			assertEquals(StremioScreenState.Phase.ERROR, presenter.getState().phase());
			presenter.close();
		} finally {
			scheduler.shutdownNow();
		}
	}

	@Test
	public void callbackRegistrationFailureTerminatesLoadingAndCancelsRequest() {
		FakeLoader loader = new FakeLoader();
		loader.failRegistration = true;
		StremioPresenter presenter = new StremioPresenter(loader, Runnable::run, state -> {
		});

		presenter.start();

		FakeRequest request = loader.requests.remove();
		assertTrue(request.cancelled);
		assertEquals(StremioScreenState.Phase.ERROR, presenter.getState().phase());
		presenter.close();
	}

	private static StremioUiModel.Poster poster(String key, String title) {
		return new StremioUiModel.Poster(key, title, "", "", 0f);
	}

	private static final class FakeLoader implements StremioPresenter.Loader {
		private final Queue<FakeRequest> requests = new ArrayDeque<>();
		private boolean failRegistration;

		@Override
		public StremioPresenter.Request load(StremioRoute route) {
			FakeRequest request = new FakeRequest(failRegistration);
			requests.add(request);
			return request;
		}
	}

	private static final class FakeRequest implements StremioPresenter.Request {
		private final CompletableFuture<StremioPresentationPage> result =
				new CompletableFuture<>();
		private boolean cancelled;
		private Consumer<StremioPresentationPage> updateListener;
		private final boolean failRegistration;

		private FakeRequest() {
			this(false);
		}

		private FakeRequest(boolean failRegistration) {
			this.failRegistration = failRegistration;
		}

		@Override
		public CompletableFuture<StremioPresentationPage> result() {
			return result;
		}

		void complete(List<StremioUiModel> models) {
			result.complete(StremioPresentationPage.of(models));
		}

		void update(List<StremioUiModel> models) {
			if (updateListener != null) updateListener.accept(StremioPresentationPage.of(models));
		}

		@Override
		public void onUpdate(Consumer<StremioPresentationPage> listener) {
			if (failRegistration) throw new java.util.concurrent.RejectedExecutionException();
			updateListener = listener;
		}

		@Override
		public void cancel() {
			cancelled = true;
		}
	}
}
