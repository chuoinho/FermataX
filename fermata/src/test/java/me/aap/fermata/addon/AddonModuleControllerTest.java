package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.Test;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class AddonModuleControllerTest {
	@Test
	public void readinessWaitsForLifecycleReplayAndCommit() {
		Queue<Runnable> lifecycleTasks = new ArrayDeque<>();
		List<String> events = new ArrayList<>();
		AddonInfo info = info("lifecycle", ReplayAddon.class.getName(), 10);
		ReplayAddon.info = info;
		ReplayAddon.events = events;
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{info}));
		AddonLifecycleCoordinator lifecycle = new AddonLifecycleCoordinator(lifecycleTasks::add);
		lifecycle.onActivityCreate(null);
		AddonLoader loader = new AddonLoader(state, lifecycle);
		Promise<Boolean> activation = new Promise<>();
		AddonModuleController controller = new AddonModuleController(state, candidate -> {
			FermataAddon loaded = loader.load(candidate, () -> true, addon -> {
				events.add("commit");
				state.activate(candidate, addon);
				events.add("broadcast");
				activation.complete(true);
			});
			if (loaded == null) activation.complete(false);
			return activation;
		}, candidate -> true, module -> true, new FakeOperations());

		controller.install(List.of(info), info);
		FutureSupplier<?> ready = state.getInstalling(info);
		assertNotNull(ready);
		ready.onCompletion((result, error) -> events.add("ready"));

		assertNotNull(state.getRegistered(info.className));
		assertFalse(state.isLoaded(info));
		assertFalse(ready.isDone());
		assertEquals(1, lifecycleTasks.size());

		lifecycleTasks.remove().run();

		assertTrue(state.isLoaded(info));
		assertTrue(ready.isDoneNotFailed());
		assertEquals(List.of("replay", "commit", "broadcast", "ready"), events);
	}

	@Test
	public void dependencyActivationCompletesBeforeRootActivationStarts() {
		AddonInfo dependency = info("dependency", "test.AsyncDependency", 1);
		AddonInfo root = info("root", "test.AsyncRoot", 2);
		Map<String, Promise<Boolean>> activations = new HashMap<>();
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{dependency, root}));
		AddonModuleController controller = new AddonModuleController(state,
				candidate -> activations.computeIfAbsent(candidate.className, key -> new Promise<>()),
				candidate -> true, module -> true, new FakeOperations());

		controller.install(List.of(dependency, root), root);
		FutureSupplier<?> rootReady = state.getInstalling(root);
		assertNotNull(rootReady);
		assertTrue(activations.containsKey(dependency.className));
		assertFalse(activations.containsKey(root.className));
		assertFalse(rootReady.isDone());

		activations.get(dependency.className).complete(true);
		assertTrue(activations.containsKey(root.className));
		assertFalse(rootReady.isDone());

		activations.get(root.className).complete(true);
		assertTrue(rootReady.isDoneNotFailed());
	}

	@Test
	public void sharedModuleUsesOnePhysicalInstallAndCompletesBothAddons() {
		AddonInfo first = info("shared", "test.First", 1);
		AddonInfo second = info("shared", "test.Second", 2);
		Harness harness = new Harness(first, second);

		harness.controller.install(List.of(first), first);
		FutureSupplier<?> firstReady = harness.state.getInstalling(first);
		harness.controller.install(List.of(second), second);
		FutureSupplier<?> secondReady = harness.state.getInstalling(second);

		assertNotNull(firstReady);
		assertNotNull(secondReady);
		assertEquals(1, harness.operations.installCount("shared"));
		harness.visible.put(first.className, true);
		harness.visible.put(second.className, true);
		harness.operations.completeInstall("shared");

		assertTrue(firstReady.isDoneNotFailed());
		assertTrue(secondReady.isDoneNotFailed());
		assertFalse(firstReady.isCancelled());
		assertFalse(secondReady.isCancelled());
	}

	@Test
	public void differentModulesSerializePhysicalActivityAcquisition() {
		AddonInfo first = info("first", "test.First", 1);
		AddonInfo second = info("second", "test.Second", 2);
		Harness harness = new Harness(first, second);

		harness.controller.install(List.of(first), first);
		harness.controller.install(List.of(second), second);

		assertEquals(1, harness.operations.installCount("first"));
		assertEquals(0, harness.operations.installCount("second"));
		harness.visible.put(first.className, true);
		harness.operations.completeInstall("first");
		assertEquals(1, harness.operations.installCount("second"));
	}

	@Test
	public void cancellingOneObserverDoesNotCancelSharedActivation() {
		AddonInfo info = info("module", "test.SharedActivation", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		FutureSupplier<?> first = harness.controller.getInstalling(info);
		FutureSupplier<?> second = harness.controller.getInstalling(info);

		first.cancel();
		harness.visible.put(info.className, true);
		harness.operations.completeInstall("module");

		assertTrue(first.isCancelled());
		assertTrue(second.isDoneNotFailed());
		assertFalse(second.isCancelled());
	}

	@Test
	public void delayedClassVisibilityCompletesReadinessSuccessfully() {
		AddonInfo info = info("module", "test.Delayed", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		FutureSupplier<?> ready = harness.state.getInstalling(info);

		harness.operations.completeInstall("module");
		assertFalse(ready.isDone());
		assertEquals(1, harness.operations.scheduled.size());
		harness.controller.install(List.of(info), info);
		assertTrue(ready == harness.state.getInstalling(info));

		harness.visible.put(info.className, true);
		harness.operations.runNextScheduled();

		assertTrue(ready.isDoneNotFailed());
		assertFalse(ready.isCancelled());
	}

	@Test
	public void disableBeforeDeliveryDefersModuleUninstall() {
		AddonInfo info = info("module", "test.Pending", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);

		harness.retained.put(info.className, false);
		assertTrue(harness.controller.cancelInstall(info));
		harness.controller.uninstall(info);
		assertEquals(0, harness.operations.uninstallCount("module"));

		harness.operations.completeInstall("module");

		assertEquals(1, harness.operations.uninstallCount("module"));
	}

	@Test
	public void reenableBeforeDeliveryCancelsDeferredUninstall() {
		AddonInfo info = info("module", "test.Pending", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		harness.retained.put(info.className, false);
		harness.controller.cancelInstall(info);
		harness.controller.uninstall(info);

		harness.retained.put(info.className, true);
		harness.controller.install(List.of(info), info);
		FutureSupplier<?> ready = harness.state.getInstalling(info);
		harness.visible.put(info.className, true);
		harness.operations.completeInstall("module");

		assertTrue(ready.isDoneNotFailed());
		assertEquals(0, harness.operations.uninstallCount("module"));
	}

	@Test
	public void reenableAfterDeferredUninstallRequestRestoresModule() {
		AddonInfo info = info("module", "test.Restored", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		harness.visible.put(info.className, true);
		harness.operations.completeInstall("module");
		harness.operations.deferUninstall = true;

		harness.retained.put(info.className, false);
		harness.controller.uninstall(info);
		assertEquals(1, harness.operations.uninstallCount("module"));
		harness.retained.put(info.className, true);
		harness.operations.completeUninstall();

		assertEquals(2, harness.operations.installCount("module"));
	}

	@Test
	public void deferredRestoreWaitsBehindActivePhysicalOperation() {
		AddonInfo restored = info("restored", "test.Restored", 1);
		AddonInfo other = info("other", "test.Other", 2);
		Harness harness = new Harness(restored, other);
		harness.controller.install(List.of(restored), restored);
		harness.visible.put(restored.className, true);
		harness.operations.completeInstall("restored");
		harness.operations.deferUninstall = true;
		harness.retained.put(restored.className, false);
		harness.controller.uninstall(restored);
		harness.controller.install(List.of(other), other);

		harness.retained.put(restored.className, true);
		harness.operations.completeUninstall();
		assertEquals(1, harness.operations.installCount("other"));
		assertEquals(1, harness.operations.installCount("restored"));

		harness.visible.put(other.className, true);
		harness.operations.completeInstall("other");
		assertEquals(2, harness.operations.installCount("restored"));
	}

	@Test
	public void dependencyBecomesReadyBeforeRootInstallStarts() {
		AddonInfo dependency = info("dependency", "test.Dependency", 1);
		AddonInfo root = info("root", "test.Root", 2);
		Harness harness = new Harness(dependency, root);

		harness.controller.install(List.of(dependency, root), root);
		assertEquals(1, harness.operations.installCount("dependency"));
		assertEquals(0, harness.operations.installCount("root"));

		harness.visible.put(dependency.className, true);
		harness.operations.completeInstall("dependency");

		assertEquals(1, harness.operations.installCount("root"));
	}

	@Test
	public void visibilityTimeoutIsFailureRatherThanCancellation() {
		AddonInfo info = info("module", "test.Timeout", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		FutureSupplier<?> ready = harness.state.getInstalling(info);
		harness.operations.completeInstall("module");

		while (!harness.operations.scheduled.isEmpty()) harness.operations.runNextScheduled();

		assertTrue(ready.isFailed());
		assertFalse(ready.isCancelled());
	}

	@Test
	public void loadFailureDoesNotStartPhysicalModuleDelivery() {
		AddonInfo info = info("module", "test.Broken", 1);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{info}));
		FakeOperations operations = new FakeOperations();
		AddonModuleController controller = new AddonModuleController(state, candidate -> {
			state.markFailed(candidate);
			return completed(false);
		}, candidate -> true, module -> true, operations);

		controller.install(List.of(info), info);

		assertTrue(state.isFailed(info));
		assertEquals(0, operations.installCount("module"));
	}

	private static AddonInfo info(String module, String className, int id) {
		return new AddonInfo(module, className, id, id, id, id,
				false, false, false, false, "");
	}

	public static final class ReplayAddon implements FermataActivityAddon {
		private static AddonInfo info;
		private static List<String> events;

		public ReplayAddon() {
		}

		@Override
		public int getAddonId() {
			return info.addonId;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}

		@Override
		public void onActivityCreate(
				me.aap.fermata.ui.activity.MainActivityDelegate activity) {
			events.add("replay");
		}
	}

	private static final class Harness {
		final Map<String, Boolean> retained = new HashMap<>();
		final Map<String, Boolean> visible = new HashMap<>();
		final FakeOperations operations = new FakeOperations();
		final AddonRuntimeState state;
		final AddonModuleController controller;

		Harness(AddonInfo... infos) {
			state = new AddonRuntimeState(new AddonRegistry(infos));
			for (AddonInfo info : infos) retained.put(info.className, true);
			controller = new AddonModuleController(state,
					info -> completed(visible.getOrDefault(info.className, false)),
					info -> retained.getOrDefault(info.className, false),
					module -> {
						for (AddonInfo info : infos) {
							if (module.equals(info.moduleName) &&
									retained.getOrDefault(info.className, false)) return true;
						}
						return false;
					}, operations);
		}
	}

	private static final class FakeOperations implements AddonModuleController.Operations {
		final Map<String, Integer> installs = new HashMap<>();
		final Map<String, Integer> uninstalls = new HashMap<>();
		final Map<String, Promise<Void>> installTasks = new HashMap<>();
		final Queue<Runnable> scheduled = new ArrayDeque<>();
		boolean deferUninstall;
		Promise<Void> uninstallTask;

		@Override
		public FutureSupplier<?> install(AddonInfo info) {
			installs.merge(info.moduleName, 1, Integer::sum);
			Promise<Void> task = new Promise<>();
			installTasks.put(info.moduleName, task);
			return task;
		}

		@Override
		public FutureSupplier<?> uninstall(AddonInfo info,
													java.util.function.BooleanSupplier shouldUninstall) {
			if (shouldUninstall.getAsBoolean())
				uninstalls.merge(info.moduleName, 1, Integer::sum);
			if (!deferUninstall) return completedNull();
			uninstallTask = new Promise<>();
			return uninstallTask;
		}

		@Override
		public void schedule(Runnable task, long delayMillis) {
			scheduled.add(task);
		}

		int installCount(String module) {
			return installs.getOrDefault(module, 0);
		}

		int uninstallCount(String module) {
			return uninstalls.getOrDefault(module, 0);
		}

		void completeInstall(String module) {
			installTasks.get(module).complete(null);
		}

		void runNextScheduled() {
			scheduled.remove().run();
		}

		void completeUninstall() {
			uninstallTask.complete(null);
		}
	}
}
