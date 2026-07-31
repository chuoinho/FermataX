package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Test;

import me.aap.fermata.media.net.RemotePlaybackLifecycleItem;

public class RemotePlaybackLifecycleControllerTest {
	@Test
	public void staleFailureCannotEscapeAfterOwnerSwitch() {
		Queue<Runnable> main = new ArrayDeque<>();
		RemotePlaybackLifecycleController controller =
				new RemotePlaybackLifecycleController(main::add);
		RecordingLifecycle first = new RecordingLifecycle();
		RecordingLifecycle second = new RecordingLifecycle();
		AtomicInteger failures = new AtomicInteger();

		controller.activate(new Object(), first, 1L, error -> failures.incrementAndGet());
		controller.activate(new Object(), second, 2L, error -> failures.incrementAndGet());
		first.fail(new IllegalStateException("stale"));
		second.fail(new IllegalStateException("current"));
		while (!main.isEmpty()) main.remove().run();

		assertEquals(1, failures.get());
		assertEquals(List.of("activate:1", "cancel:1"), first.events);
		assertEquals(List.of("activate:2"), second.events);
	}

	@Test
	public void sameOwnerAndRevisionAreIdempotent() {
		RemotePlaybackLifecycleController controller =
				new RemotePlaybackLifecycleController(Runnable::run);
		RecordingLifecycle lifecycle = new RecordingLifecycle();
		Object owner = new Object();

		controller.activate(owner, lifecycle, 4L, error -> {});
		controller.activate(owner, lifecycle, 4L, error -> {});
		controller.notifyActive((value, revision) -> lifecycle.events.add("notify:" + revision));
		controller.cancel();

		assertEquals(List.of("activate:4", "notify:4", "cancel:4"), lifecycle.events);
	}

	@Test
	public void fallbackDefaultsToAllowedAndDelegatesToActiveLifecycle() {
		RemotePlaybackLifecycleController controller =
				new RemotePlaybackLifecycleController(Runnable::run);
		assertEquals(true, controller.allowsFallback());

		RecordingLifecycle lifecycle = new RecordingLifecycle();
		lifecycle.allowFallback = false;
		controller.activate(new Object(), lifecycle, 7L, error -> {});
		assertEquals(false, controller.allowsFallback());
		assertEquals(List.of("activate:7", "fallback:7"), lifecycle.events);
	}

	private static final class RecordingLifecycle implements RemotePlaybackLifecycleItem {
		private final List<String> events = new ArrayList<>();
		private Consumer<Throwable> failureHandler;
		private boolean allowFallback = true;

		@Override
		public void onPlaybackAttemptActivated(long revision, Consumer<Throwable> failureHandler) {
			events.add("activate:" + revision);
			this.failureHandler = failureHandler;
		}

		@Override
		public void onPlaybackAttemptCancelled(long revision) {
			events.add("cancel:" + revision);
		}

		@Override
		public boolean onPlaybackAttemptFallback(long revision) {
			events.add("fallback:" + revision);
			return allowFallback;
		}

		private void fail(Throwable error) {
			failureHandler.accept(error);
		}
	}
}
