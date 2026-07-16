package me.aap.fermata.addon;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.Test;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;

public class AddonLifecycleCoordinatorTest {
	@Test
	public void lateLoadedAddonReceivesActiveLifecycleInOrder() {
		AddonLifecycleCoordinator coordinator = new AddonLifecycleCoordinator();
		RecordingAddon addon = new RecordingAddon();
		coordinator.onServiceCreate(null);
		coordinator.onActivityCreate(null);
		coordinator.onActivityResume(null);

		coordinator.onAddonLoaded(addon);
		assertEquals(List.of("service-create", "activity-create", "activity-resume"), addon.events);

		coordinator.onAddonUnloading(addon);
		assertEquals(List.of("service-create", "activity-create", "activity-resume",
				"activity-pause", "activity-destroy", "service-destroy"), addon.events);
	}

	@Test
	public void addonLoadedWhilePausedOnlyReceivesActivityCreate() {
		AddonLifecycleCoordinator coordinator = new AddonLifecycleCoordinator();
		RecordingAddon addon = new RecordingAddon();
		coordinator.onActivityCreate(null);
		coordinator.onActivityResume(null);
		coordinator.onActivityPause(null);

		coordinator.onAddonLoaded(addon);

		assertEquals(List.of("activity-create"), addon.events);
	}

	@Test
	public void failingAddonDoesNotBlockUnrelatedLifecycleDelivery() {
		AddonLifecycleCoordinator coordinator = new AddonLifecycleCoordinator();
		RecordingAddon healthy = new RecordingAddon();
		RecordingAddon failing = new RecordingAddon() {
			@Override
			public void onActivityCreate(MainActivityDelegate activity) {
				throw new IllegalStateException("expected");
			}
		};

		coordinator.onAddonLoaded(failing);
		coordinator.onAddonLoaded(healthy);
		coordinator.onActivityCreate(null);

		assertEquals(List.of("activity-create"), healthy.events);
	}

	@Test
	public void reentrantLoadDuringDestroyDoesNotReplayDyingActivity() {
		AddonLifecycleCoordinator coordinator = new AddonLifecycleCoordinator();
		RecordingAddon late = new RecordingAddon();
		RecordingAddon unloading = new RecordingAddon() {
			@Override
			public void onActivityDestroy(MainActivityDelegate activity) {
				coordinator.onAddonLoaded(late);
			}
		};
		coordinator.onAddonLoaded(unloading);
		coordinator.onActivityCreate(null);

		coordinator.onActivityDestroy(null);

		assertEquals(Collections.emptyList(), late.events);
	}

	@Test(timeout = 3000)
	public void concurrentLoadQueuesWithoutDeadlockingLifecycleCallback() {
		AddonLifecycleCoordinator coordinator = new AddonLifecycleCoordinator();
		RecordingAddon late = new RecordingAddon();
		RecordingAddon blocker = new RecordingAddon() {
			@Override
			public void onActivityCreate(MainActivityDelegate activity) {
				Thread loader = new Thread(() -> coordinator.onAddonLoaded(late));
				loader.start();
				try {
					loader.join(1000);
				} catch (InterruptedException error) {
					throw new AssertionError(error);
				}
				if (loader.isAlive()) throw new AssertionError("Lifecycle load deadlocked");
			}
		};
		coordinator.onAddonLoaded(blocker);

		coordinator.onActivityCreate(null);

		assertEquals(List.of("activity-create"), late.events);
	}

	@Test
	public void replayAndTeardownWrapCommitCallbacks() {
		Queue<Runnable> scheduled = new ArrayDeque<>();
		AddonLifecycleCoordinator coordinator = new AddonLifecycleCoordinator(scheduled::add);
		RecordingAddon addon = new RecordingAddon();
		coordinator.onActivityCreate(null);

		coordinator.onAddonLoaded(addon, () -> addon.events.add("commit"));
		assertEquals(Collections.emptyList(), addon.events);
		scheduled.remove().run();
		assertEquals(List.of("activity-create", "commit"), addon.events);

		coordinator.onAddonUnloading(addon, () -> addon.events.add("uninstall"));
		assertEquals(List.of("activity-create", "commit"), addon.events);
		scheduled.remove().run();
		assertEquals(List.of("activity-create", "commit", "activity-destroy", "uninstall"),
				addon.events);
	}

	private static class RecordingAddon implements FermataActivityAddon, FermataMediaServiceAddon {
		final List<String> events = new ArrayList<>();
		private final AddonInfo info = new AddonInfo("test", getClass().getName(), 1, 1, 1,
				1, false, false, false, false, "");

		@Override
		public int getAddonId() {
			return 1;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}

		@Override
		public void onActivityCreate(MainActivityDelegate activity) {
			events.add("activity-create");
		}

		@Override
		public void onActivityResume(MainActivityDelegate activity) {
			events.add("activity-resume");
		}

		@Override
		public void onActivityPause(MainActivityDelegate activity) {
			events.add("activity-pause");
		}

		@Override
		public void onActivityDestroy(MainActivityDelegate activity) {
			events.add("activity-destroy");
		}

		@Override
		public void onServiceCreate(MediaSessionCallback callback) {
			events.add("service-create");
		}

		@Override
		public void onServiceDestroy(MediaSessionCallback callback) {
			events.add("service-destroy");
		}
	}
}
