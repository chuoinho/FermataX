package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.os.OsUtils;

public class AddonModuleControllerTest {
	private static TestApp app;

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

	@Before
	public void clearPhysicalTimeouts() {
		app.clearTimeouts();
	}

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
	public void physicalTimeoutUnblocksNextQueuedOperation() {
		AddonInfo stuck = info("stuck", "test.Stuck", 1);
		AddonInfo next = info("next", "test.Next", 2);
		Harness harness = new Harness(stuck, next);

		harness.controller.install(List.of(stuck), stuck);
		harness.controller.install(List.of(next), next);
		assertEquals(1, harness.operations.installCount("stuck"));
		assertEquals(0, harness.operations.installCount("next"));

		app.runNextTimeout();

		assertNull(harness.state.getInstalling(stuck));
		assertTrue(harness.operations.installTasks.get("stuck").isCancelled());
		assertEquals(1, harness.operations.installCount("next"));
	}

	@Test
	public void fastPhysicalOperationIsUnaffectedByTimeout() {
		AddonInfo info = info("fast", "test.Fast", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		FutureSupplier<?> ready = harness.state.getInstalling(info);

		harness.visible.put(info.className, true);
		harness.operations.completeInstall("fast");

		assertTrue(ready.isDoneNotFailed());
		assertFalse(ready.isCancelled());
		assertNull(ready.getFailure());
		assertEquals(0, app.pendingTimeoutCount());
	}

	@Test
	public void physicalTimeoutCompletesOperationWithTimeoutException() {
		AddonInfo info = info("stuck", "test.Stuck", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		FutureSupplier<?> ready = harness.state.getInstalling(info);

		app.runNextTimeout();

		assertTrue(ready.isFailed());
		assertTrue(ready.getFailure() instanceof TimeoutException);
		assertFalse(ready.isCancelled());
	}

	@Test
	public void physicalQueueOrderingIsPreservedAfterTimeout() {
		AddonInfo first = info("first", "test.First", 1);
		AddonInfo second = info("second", "test.Second", 2);
		AddonInfo third = info("third", "test.Third", 3);
		Harness harness = new Harness(first, second, third);

		harness.controller.install(List.of(first), first);
		harness.controller.install(List.of(second), second);
		harness.controller.install(List.of(third), third);
		FutureSupplier<?> firstReady = harness.state.getInstalling(first);
		FutureSupplier<?> secondReady = harness.state.getInstalling(second);
		FutureSupplier<?> thirdReady = harness.state.getInstalling(third);
		assertEquals(List.of("first"), harness.operations.installOrder);

		app.runNextTimeout();
		assertTrue(firstReady.isFailed());
		assertFalse(secondReady.isDone());
		assertFalse(thirdReady.isDone());
		assertEquals(List.of("first", "second"), harness.operations.installOrder);

		// A late completion from the timed-out source must neither finish the queue twice nor
		// replace/remove the ownership state of the operation that is now active.
		harness.operations.completeInstall("first");
		assertEquals(List.of("first", "second"), harness.operations.installOrder);
		assertNull(harness.state.getInstalling(first));
		assertSame(secondReady, harness.state.getInstalling(second));
		assertSame(thirdReady, harness.state.getInstalling(third));
		assertFalse(secondReady.isDone());
		assertFalse(thirdReady.isDone());

		harness.visible.put(second.className, true);
		harness.operations.completeInstall("second");
		assertTrue(secondReady.isDoneNotFailed());
		assertFalse(thirdReady.isDone());
		assertEquals(List.of("first", "second", "third"), harness.operations.installOrder);
		assertEquals(1, harness.operations.installCount("first"));
		assertEquals(1, harness.operations.installCount("second"));
		assertEquals(1, harness.operations.installCount("third"));
	}

	@Test
	public void uninstallTimeoutReleasesReservationAndUnblocksQueue() {
		AddonInfo stuck = info("stuck", "test.Stuck", 1);
		AddonInfo next = info("next", "test.Next", 2);
		Harness harness = new Harness(stuck, next);
		harness.controller.install(List.of(stuck), stuck);
		harness.visible.put(stuck.className, true);
		harness.operations.completeInstall("stuck");

		harness.retained.put(stuck.className, false);
		harness.operations.deferUninstall = true;
		harness.controller.uninstall(stuck);
		harness.controller.install(List.of(next), next);
		assertEquals(1, harness.operations.uninstallCount("stuck"));
		assertEquals(0, harness.operations.installCount("next"));

		app.runNextTimeout();

		assertTrue(harness.operations.uninstallTask.isCancelled());
		assertEquals(1, harness.operations.installCount("next"));
		harness.visible.put(next.className, true);
		harness.operations.completeInstall("next");

		// A second request must not be blocked by the timed-out operation's reservation.
		harness.operations.deferUninstall = false;
		harness.controller.uninstall(stuck);
		assertEquals(2, harness.operations.uninstallCount("stuck"));
	}

	@Test
	public void fastPhysicalUninstallIsUnaffectedByTimeout() {
		AddonInfo info = info("fast", "test.Fast", 1);
		Harness harness = new Harness(info);
		harness.controller.install(List.of(info), info);
		harness.visible.put(info.className, true);
		harness.operations.completeInstall("fast");

		harness.retained.put(info.className, false);
		harness.operations.deferUninstall = true;
		harness.controller.uninstall(info);
		FutureSupplier<?> uninstall = harness.operations.uninstallTask;
		harness.operations.completeUninstall();

		assertTrue(uninstall.isDoneNotFailed());
		assertFalse(uninstall.isCancelled());
		assertEquals(0, app.pendingTimeoutCount());
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
		final List<String> installOrder = new ArrayList<>();
		final Queue<Runnable> scheduled = new ArrayDeque<>();
		boolean deferUninstall;
		Promise<Void> uninstallTask;

		@Override
		public FutureSupplier<?> install(AddonInfo info) {
			installs.merge(info.moduleName, 1, Integer::sum);
			installOrder.add(info.moduleName);
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

	private static final class TestApp extends App {
		private final ScheduledThreadPoolExecutor scheduler =
				new ScheduledThreadPoolExecutor(1);

		TestApp() {
			scheduler.setRemoveOnCancelPolicy(true);
		}

		@Override
		public String getLogTag() {
			return "AddonModuleControllerTest";
		}

		@Override
		public ScheduledExecutorService getScheduler() {
			return scheduler;
		}

		@Override
		public void onTerminate() {
			scheduler.shutdownNow();
			super.onTerminate();
		}

		void runNextTimeout() {
			assertFalse(scheduler.getQueue().isEmpty());
			Runnable timeout = scheduler.getQueue().iterator().next();
			assertTrue(scheduler.remove(timeout));
			timeout.run();
		}

		int pendingTimeoutCount() {
			return scheduler.getQueue().size();
		}

		void clearTimeouts() {
			for (Runnable timeout : new ArrayList<>(scheduler.getQueue())) {
				scheduler.remove(timeout);
			}
		}
	}
}
