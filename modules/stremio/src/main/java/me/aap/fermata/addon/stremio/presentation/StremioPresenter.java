package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;
import me.aap.fermata.addon.stremio.lifecycle.StremioDeadlineScheduler;
import me.aap.utils.log.Log;

/** Owns the Stremio UI route stack and rejects every stale asynchronous result. */
public final class StremioPresenter implements AutoCloseable {
	private static final int MAX_CACHED_ROUTES = 16;
	private static final long DEFAULT_LOAD_TIMEOUT_MILLIS = 30_000L;
	private final Loader loader;
	private final Executor callbackExecutor;
	private final Listener listener;
	private final ScheduledExecutorService scheduler;
	private final long loadTimeoutMillis;
	private final Deque<StremioRoute> routes = new ArrayDeque<>();
	private final Map<StremioRoute, StremioScreenState> routeCache =
			new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<StremioRoute, StremioScreenState> eldest) {
					return size() > MAX_CACHED_ROUTES;
				}
			};
	private final Map<StremioRoute, StremioViewportState> routeViewports =
			new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<StremioRoute, StremioViewportState> eldest) {
					return size() > MAX_CACHED_ROUTES;
				}
			};
	private Request active;
	private ScheduledFuture<?> activeDeadline;
	private StremioScreenState state;
	private long generation;
	private boolean closed;

	public StremioPresenter(Loader loader, Executor callbackExecutor, Listener listener) {
		this(loader, callbackExecutor, listener, StremioDeadlineScheduler.get(),
				DEFAULT_LOAD_TIMEOUT_MILLIS);
	}

	StremioPresenter(Loader loader, Executor callbackExecutor, Listener listener,
			ScheduledExecutorService scheduler, long loadTimeoutMillis) {
		this.loader = Objects.requireNonNull(loader, "loader");
		this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
		this.listener = Objects.requireNonNull(listener, "listener");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		if (loadTimeoutMillis <= 0L) throw new IllegalArgumentException(
				"loadTimeoutMillis must be positive");
		this.loadTimeoutMillis = loadTimeoutMillis;
	}

	public synchronized void start() {
		if (closed) return;
		routes.clear();
		routeCache.clear();
		routeViewports.clear();
		routes.addLast(new StremioRoute.Home());
		load(routes.getLast(), false);
	}

	public synchronized void navigate(StremioRoute route) {
		if (closed) return;
		route = Objects.requireNonNull(route, "route");
		if (route.equals(routes.peekLast())) return;
		routes.addLast(route);
		load(route, false);
	}

	public synchronized void replace(StremioRoute route) {
		replace(route, false);
	}

	public synchronized void replace(StremioRoute route, boolean preserveViewport) {
		if (closed) return;
		route = Objects.requireNonNull(route, "route");
		if (route.equals(routes.peekLast())) return;
		StremioRoute previous = routes.peekLast();
		StremioViewportState viewport = preserveViewport && (previous != null) ?
				routeViewports.get(previous) : null;
		if (!routes.isEmpty()) routes.removeLast();
		if (routes.isEmpty()) routes.addLast(new StremioRoute.Home());
		if (!(route instanceof StremioRoute.Home)) routes.addLast(route);
		if (viewport != null) routeViewports.put(route, viewport);
		load(routes.getLast(), false);
	}

	public synchronized void openPath(List<? extends StremioRoute> path) {
		if (closed) return;
		Objects.requireNonNull(path, "path");
		if (path.isEmpty() || !(path.get(0) instanceof StremioRoute.Home)) {
			throw new IllegalArgumentException("Stremio path must start at Home");
		}
		cancelActive();
		routes.clear();
		for (StremioRoute route : path) {
			if ((routes.peekLast() == null) || !routes.peekLast().equals(route)) {
				routes.addLast(Objects.requireNonNull(route, "route"));
			}
		}
		load(routes.getLast(), true);
	}

	public synchronized boolean back() {
		if (closed || (routes.size() <= 1)) return false;
		routes.removeLast();
		load(routes.getLast(), true);
		return true;
	}

	public synchronized void refresh() {
		if (!closed && !routes.isEmpty()) load(routes.getLast(), true);
	}

	public synchronized StremioScreenState getState() {
		return state;
	}

	public synchronized void saveViewport(StremioViewportState viewport) {
		if (closed || routes.isEmpty()) return;
		StremioRoute route = routes.getLast();
		routeViewports.put(route, Objects.requireNonNull(viewport, "viewport"));
		if ((state != null) && state.route().equals(route)) {
			state = new StremioScreenState(state.generation(), state.route(), state.phase(),
					state.models(), state.selections(), state.message(), viewport);
		}
	}

	private void load(StremioRoute route, boolean preserveCachedContent) {
		cancelActive();
		long expected = ++generation;
		StremioScreenState cached = preserveCachedContent ? routeCache.get(route) : null;
		List<StremioUiModel> models = (cached == null) ? List.of() : cached.models();
		Map<String, StremioSelection> selections =
				(cached == null) ? Map.of() : cached.selections();
		StremioViewportState viewport = routeViewports.getOrDefault(
				route, StremioViewportState.empty());
		publish(new StremioScreenState(expected, route,
				StremioScreenState.Phase.LOADING, models, selections, "", viewport));
		Request request;
		try {
			request = Objects.requireNonNull(loader.load(route), "request");
		} catch (RuntimeException error) {
			failStart(expected, route, error);
			return;
		}
		active = request;
		try {
			activeDeadline = scheduler.schedule(() -> callbackExecutor.execute(
					() -> timeout(expected, route, request)), loadTimeoutMillis,
					TimeUnit.MILLISECONDS);
			request.onUpdate(page -> callbackExecutor.execute(
					() -> update(expected, route, request, page)));
			Objects.requireNonNull(request.result(), "request result")
					.whenComplete((page, failure) -> callbackExecutor.execute(
							() -> complete(expected, route, request, page, failure)));
		} catch (RuntimeException failure) {
			if (active == request) {
				cancelActive();
				failStart(expected, route, failure);
			}
		}
	}

	private synchronized void update(long expected, StremioRoute route,
			Request request, StremioPresentationPage page) {
		if (closed || (expected != generation) || !route.equals(routes.peekLast()) ||
				(active != request) || (page == null)) return;
		StremioScreenState next = new StremioScreenState(expected, route,
				StremioScreenState.Phase.LOADING, page.models(), page.selections(), "",
				routeViewports.getOrDefault(route, StremioViewportState.empty()));
		routeCache.put(route, next);
		publish(next);
	}

	private synchronized void complete(long expected, StremioRoute route,
			Request request, StremioPresentationPage page, Throwable failure) {
		if (closed || (expected != generation) || !route.equals(routes.peekLast()) ||
				(active != request)) return;
		active = null;
		cancelDeadline();
		if (failure != null) {
			Log.w("Stremio screen load failed [", routeCode(route), "]: ",
					failureCode(failure));
			StremioScreenState cached = routeCache.get(route);
			StremioViewportState viewport = routeViewports.getOrDefault(
					route, StremioViewportState.empty());
			publish(new StremioScreenState(expected, route,
					StremioScreenState.Phase.ERROR,
					(cached == null) ? List.of() : cached.models(),
					(cached == null) ? Map.of() : cached.selections(), safeMessage(failure),
					viewport));
			return;
		}
		StremioPresentationPage contentPage = Objects.requireNonNullElseGet(page,
				() -> StremioPresentationPage.of(List.of()));
		List<StremioUiModel> content = contentPage.models();
		StremioScreenState.Phase phase = content.isEmpty() ?
				StremioScreenState.Phase.EMPTY : StremioScreenState.Phase.CONTENT;
		StremioScreenState next = new StremioScreenState(expected, route, phase, content,
				contentPage.selections(), "", routeViewports.getOrDefault(
						route, StremioViewportState.empty()));
		routeCache.put(route, next);
		publish(next);
	}

	private void timeout(long expected, StremioRoute route, Request request) {
		synchronized (this) {
			if (closed || (expected != generation) || !route.equals(routes.peekLast()) ||
					(active != request)) return;
		}
		complete(expected, route, request, null,
				new TimeoutException("Stremio page load timed out"));
		safeCancel(request);
	}

	private synchronized void failStart(long expected, StremioRoute route, Throwable failure) {
		if (closed || (expected != generation) || !route.equals(routes.peekLast()) ||
				(active != null)) return;
		Log.w("Stremio screen load failed [", routeCode(route), "]: ",
				failureCode(failure));
		StremioScreenState cached = routeCache.get(route);
		publish(new StremioScreenState(expected, route, StremioScreenState.Phase.ERROR,
				(cached == null) ? List.of() : cached.models(),
				(cached == null) ? Map.of() : cached.selections(), safeMessage(failure),
				routeViewports.getOrDefault(route, StremioViewportState.empty())));
	}

	private void publish(StremioScreenState next) {
		state = next;
		listener.onStateChanged(next);
	}

	private void cancelActive() {
		cancelDeadline();
		Request request = active;
		active = null;
		if (request != null) safeCancel(request);
	}

	private static void safeCancel(Request request) {
		try {
			request.cancel();
		} catch (RuntimeException failure) {
			Log.w("Stremio request cancellation failed: ", failureCode(failure));
		}
	}

	private void cancelDeadline() {
		ScheduledFuture<?> deadline = activeDeadline;
		activeDeadline = null;
		if (deadline != null) deadline.cancel(false);
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		generation++;
		cancelActive();
		routes.clear();
		routeCache.clear();
		routeViewports.clear();
	}

	private static String safeMessage(Throwable failure) {
		String type = failure.getClass().getSimpleName();
		return type.isEmpty() ? "Load failed" : type;
	}

	private static String routeCode(StremioRoute route) {
		String type = route.getClass().getSimpleName();
		return type.isEmpty() ? "unknown" : type;
	}

	private static String failureCode(Throwable failure) {
		StringBuilder result = new StringBuilder();
		Throwable cause = failure;
		for (int depth = 0; (cause != null) && (depth < 6); depth++) {
			if (depth != 0) result.append('/');
			String type = cause.getClass().getSimpleName();
			result.append(type.isEmpty() ? "unknown" : type);
			cause = (cause.getCause() == cause) ? null : cause.getCause();
		}
		return result.toString();
	}

	public interface Loader {
		Request load(StremioRoute route);
	}

	public interface Request extends StremioCall<StremioPresentationPage> {
		CompletionStage<StremioPresentationPage> result();

		@Override
		default CompletionStage<StremioPresentationPage> completion() {
			return result();
		}

		default void onUpdate(Consumer<StremioPresentationPage> listener) {
		}

		void cancel();
	}

	public interface Listener {
		void onStateChanged(StremioScreenState state);
	}
}
