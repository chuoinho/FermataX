package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.external.ExternalPlaybackHandler;
import me.aap.fermata.addon.external.ExternalPlaybackRequest;
import me.aap.fermata.addon.external.ExternalPlaybackRouter;
import me.aap.fermata.addon.external.ExternalPlaybackTargetKind;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

public class ExternalPlaybackRoutingTest {
	private static final ExternalPlaybackRequest REQUEST = new ExternalPlaybackRequest(
			"movie:one", "Exact title", "https://images.example/one.jpg", 12_345L,
			ExternalPlaybackTargetKind.YOUTUBE_ID, "video_one");

	@Test
	public void missingHandlerReturnsClearUnavailableResult() {
		assertNull(ExternalPlaybackRouter.route(List.of(), null, REQUEST).getOrThrow());
	}

	@Test
	public void disabledHandlerIsNeverInvoked() {
		List<String> calls = new ArrayList<>();
		Handler disabled = new Handler("disabled", 0, calls, playable("disabled"), null);

		List<ExternalPlaybackHandler> selected = AddonManager.selectExternalPlaybackHandlers(
				List.of(disabled), addon -> false, REQUEST);

		assertNull(ExternalPlaybackRouter.route(selected, null, REQUEST).getOrThrow());
		assertEquals(List.of(), calls);
	}

	@Test
	public void throwingAndFailedHandlersDoNotBlockNextHandler() {
		List<String> calls = new ArrayList<>();
		PlayableItem expected = playable("expected");
		Handler throwing = new Handler("a.throwing", 0, calls, null,
				new IllegalStateException("sync"));
		Handler failed = new Handler("b.failed", 1, calls, null,
				new AsyncFailure("async"));
		Handler following = new Handler("c.following", 2, calls, expected, null);

		List<ExternalPlaybackHandler> selected = AddonManager.selectExternalPlaybackHandlers(
				List.of(following, failed, throwing), addon -> true, REQUEST);
		PlayableItem result = ExternalPlaybackRouter.route(selected, null, REQUEST).getOrThrow();

		assertSame(expected, result);
		assertEquals(List.of("a.throwing", "b.failed", "c.following"), calls);
	}

	@Test
	public void capabilityTargetAndPriorityProduceDeterministicOrder() {
		List<String> calls = new ArrayList<>();
		Handler laterByName = new Handler("z.handler", 10, calls, null, null);
		Handler firstByName = new Handler("a.handler", 10, calls, null, null);
		Handler firstByPriority = new Handler("p.handler", 1, calls, null, null);
		Handler wrongCapability = new Handler("web.handler", 0, calls, null, null,
				ExternalPlaybackTargetKind.EXTERNAL_HTTP, "web");

		List<ExternalPlaybackHandler> selected = AddonManager.selectExternalPlaybackHandlers(
				List.of(laterByName, wrongCapability, firstByName, firstByPriority),
				addon -> true, REQUEST);
		ExternalPlaybackRouter.route(selected, null, REQUEST).getOrThrow();

		assertEquals(List.of("p.handler", "a.handler", "z.handler"), calls);
	}

	private static PlayableItem playable(String id) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getId", "getName", "getOrigId", "toString" -> id;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> null;
				});
	}

	private static final class Handler implements ExternalPlaybackHandler {
		private final AddonInfo info;
		private final int priority;
		private final List<String> calls;
		private final PlayableItem result;
		private final RuntimeException failure;
		private final ExternalPlaybackTargetKind kind;

		private Handler(String name, int priority, List<String> calls, PlayableItem result,
				RuntimeException failure) {
			this(name, priority, calls, result, failure,
					ExternalPlaybackTargetKind.YOUTUBE_ID, "youtube");
		}

		private Handler(String name, int priority, List<String> calls, PlayableItem result,
				RuntimeException failure, ExternalPlaybackTargetKind kind, String capability) {
			this.info = new AddonInfo("test", name, 0, 0, 0, priority, false, false,
					true, false, "", capability);
			this.priority = priority;
			this.calls = calls;
			this.result = result;
			this.failure = failure;
			this.kind = kind;
		}

		@Override
		public ExternalPlaybackTargetKind getExternalPlaybackTargetKind() {
			return kind;
		}

		@Override
		public int getExternalPlaybackPriority() {
			return priority;
		}

		@Override
		public FutureSupplier<PlayableItem> createExternalPlaybackItem(DefaultMediaLib lib,
				ExternalPlaybackRequest request) {
			calls.add(info.className);
			if ((failure != null) && !(failure instanceof AsyncFailure)) throw failure;
			if (failure != null) return failed(failure);
			return completed(result);
		}

		@Override
		public int getAddonId() {
			return 0;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}
	}

	private static final class AsyncFailure extends RuntimeException {
		private AsyncFailure(String message) {
			super(message);
		}
	}
}
