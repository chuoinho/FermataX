package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.app.Activity;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.module.DynamicModuleInstaller;
import me.aap.utils.ui.activity.ActivityBase;

final class AddonModuleController {
	private static final String CHANNEL_ID = "fermata.addon.install";
	private final AddonRuntimeState state;
	private final Function<AddonInfo, FutureSupplier<Boolean>> loadAddon;
	private final Predicate<AddonInfo> retained;
	private final Predicate<String> moduleRetained;
	private final Operations operations;
	private final Map<String, ModuleJob> moduleJobs = new HashMap<>();
	private final Map<String, FutureSupplier<?>> uninstalling = new HashMap<>();
	private final Set<String> restoring = new HashSet<>();
	private final ArrayDeque<PhysicalOperation> physicalQueue = new ArrayDeque<>();
	private boolean physicalOperationActive;

	AddonModuleController(AddonRuntimeState state,
							 Function<AddonInfo, FutureSupplier<Boolean>> loadAddon,
								Predicate<AddonInfo> retained, Predicate<String> moduleRetained) {
		this(state, loadAddon, retained, moduleRetained, new AndroidOperations());
	}

	AddonModuleController(AddonRuntimeState state,
							 Function<AddonInfo, FutureSupplier<Boolean>> loadAddon,
							 Predicate<AddonInfo> retained, Predicate<String> moduleRetained,
							 Operations operations) {
		this.state = state;
		this.loadAddon = loadAddon;
		this.retained = retained;
		this.moduleRetained = moduleRetained;
		this.operations = operations;
	}

	void install(List<AddonInfo> order, AddonInfo root) {
		if (state.isFailed(root) || state.isInstalling(root)) return;
		Promise<Void> ready = new Promise<>();
		if (!state.setInstallingIfAbsent(root, ready)) return;
		watch(root, ready);
		installNext(order, 0, root, ready);
	}

	FutureSupplier<?> getInstalling(AddonInfo info) {
		FutureSupplier<?> task = state.getInstalling(info);
		return (task == null) ? null : task.fork();
	}

	boolean cancelInstall(AddonInfo info) {
		FutureSupplier<?> task = state.getInstalling(info);
		if (task == null) return false;
		task.cancel();
		return true;
	}

	void uninstall(AddonInfo info) {
		ModuleJob job;
		List<Promise<Void>> waiters = null;
		boolean deliveryPending = false;
		synchronized (this) {
			job = moduleJobs.get(info.moduleName);
			if (job != null) {
				job.uninstallRequested = true;
				if (!job.waiters.isEmpty()) {
					waiters = new ArrayList<>(job.waiters.size());
					for (Waiter waiter : job.waiters.values()) waiters.add(waiter.ready);
				}
				if (!job.delivered) {
					deliveryPending = true;
				}
			}
		}
		cancel(waiters);
		if (deliveryPending) return;
		requestModuleUninstall(info);
	}

	private void installNext(List<AddonInfo> order, int index, AddonInfo root,
								 Promise<Void> rootReady) {
		if (!state.isInstalling(root, rootReady)) return;
		if (!retained.test(root)) {
			rootReady.cancel();
			return;
		}
		if (index >= order.size()) {
			rootReady.complete(null);
			return;
		}

		AddonInfo info = order.get(index);
		if (info == root) {
			startAddon(info, rootReady);
			return;
		}

		ensureAddon(info).onCompletion((result, error) -> {
			if (!state.isInstalling(root, rootReady)) return;
			if (error == null) {
				installNext(order, index + 1, root, rootReady);
			} else if (isCancellation(error)) {
				rootReady.cancel();
			} else {
				rootReady.completeExceptionally(error);
			}
		});
	}

	private FutureSupplier<Void> ensureAddon(AddonInfo info) {
		if (!retained.test(info)) return completedNull();
		if (state.isFailed(info))
			return me.aap.utils.async.Completed.failed(
					new IllegalStateException("Addon failed: " + info.className));
		if (state.isLoaded(info)) return completedNull();

		FutureSupplier<?> current = state.getInstalling(info);
		if (current != null) return current.cast();
		Promise<Void> ready = new Promise<>();
		if (!state.setInstallingIfAbsent(info, ready)) {
			current = state.getInstalling(info);
			if (current != null) return current.cast();
			return ensureAddon(info);
		}
		watch(info, ready);
		startAddon(info, ready);
		return ready;
	}

	private void startAddon(AddonInfo info, Promise<Void> ready) {
		if (!state.isInstalling(info, ready)) return;
		if (!retained.test(info)) {
			ready.cancel();
			return;
		}
		if (state.isFailed(info)) {
			ready.completeExceptionally(new IllegalStateException("Addon failed: " + info.className));
			return;
		}
		requestLoad(info, ready, 0);
	}

	private void joinModule(AddonInfo info, Promise<Void> ready) {
		ModuleJob job;
		boolean start = false;
		boolean delivered;
		synchronized (this) {
			if (!state.isInstalling(info, ready)) return;
			job = moduleJobs.get(info.moduleName);
			if (job == null) {
				job = new ModuleJob(info);
				moduleJobs.put(info.moduleName, job);
			}
			// A newly retained waiter supersedes a deferred uninstall requested while delivery ran.
			job.uninstallRequested = false;
			job.waiters.put(info.className, new Waiter(info, ready));
			delivered = job.delivered;
			if (!delivered && !job.starting) {
				job.starting = true;
				start = true;
			}
		}

		if (start) startModule(job);
		else if (delivered) attemptLoad(info, ready, 1);
	}

	private void startModule(ModuleJob job) {
		enqueuePhysical(new PhysicalOperation(
				() -> operations.install(job.representative),
				task -> {
					synchronized (AddonModuleController.this) {
						if (moduleJobs.get(job.representative.moduleName) == job) job.task = task;
					}
				},
				(task, error) -> moduleCompleted(job, task, error)));
	}

	private void moduleCompleted(ModuleJob job, FutureSupplier<?> task, Throwable error) {
		List<Waiter> waiters;
		boolean uninstall;
		synchronized (this) {
			if (moduleJobs.get(job.representative.moduleName) != job) return;
			if ((task != null) && (job.task != task)) return;
			waiters = new ArrayList<>(job.waiters.values());
			if (error != null) {
				moduleJobs.remove(job.representative.moduleName, job);
				job.waiters.clear();
			} else {
				job.delivered = true;
			}
			uninstall = (error == null) &&
					(job.uninstallRequested || !moduleRetained.test(job.representative.moduleName));
		}
		if (error != null) {
			if (!isCancellation(error))
				Log.e(error, "Failed to install module: ", job.representative.moduleName);
			for (Waiter waiter : waiters) completeFailure(waiter.ready, error);
			return;
		}

		Log.i("Module installed: ", job.representative.moduleName);
		if (uninstall) {
			for (Waiter waiter : waiters) waiter.ready.cancel();
			requestModuleUninstall(job.representative);
			return;
		}
		for (Waiter waiter : waiters) attemptLoad(waiter.info, waiter.ready, 1);
	}

	private void attemptLoad(AddonInfo info, Promise<Void> ready, int counter) {
		if (!state.isInstalling(info, ready)) return;
		if (!retained.test(info)) {
			ready.cancel();
			return;
		}
		if (state.isFailed(info)) {
			ready.completeExceptionally(new IllegalStateException("Addon failed: " + info.className));
			return;
		}
		requestLoad(info, ready, counter);
	}

	private void requestLoad(AddonInfo info, Promise<Void> ready, int counter) {
		FutureSupplier<Boolean> activation;
		try {
			activation = loadAddon.apply(info);
			if (activation == null) {
				ready.completeExceptionally(
						new IllegalStateException("Addon loader returned no result: " + info.className));
				return;
			}
		} catch (RuntimeException | LinkageError error) {
			ready.completeExceptionally(error);
			return;
		}

		activation.onCompletion((loaded, error) ->
				onLoadCompleted(info, ready, counter, Boolean.TRUE.equals(loaded), error));
	}

	private void onLoadCompleted(AddonInfo info, Promise<Void> ready, int counter,
									 boolean loaded, Throwable error) {
		if (!state.isInstalling(info, ready)) return;
		if (!retained.test(info)) {
			ready.cancel();
			return;
		}
		if (error != null) {
			completeFailure(ready, error);
			return;
		}
		if (loaded) {
			ready.complete(null);
			return;
		}
		if (state.isFailed(info)) {
			ready.completeExceptionally(new IllegalStateException("Addon failed: " + info.className));
			return;
		}
		if (counter == 0) {
			joinModule(info, ready);
		} else if (counter == 180) {
			Log.e("Failed load addon in 180 seconds: ", info.className);
			ready.completeExceptionally(
					new TimeoutException("Failed to load addon in 180 seconds: " + info.className));
		} else {
			operations.schedule(() -> attemptLoad(info, ready, counter + 1), 1000);
		}
	}

	private void enqueuePhysical(PhysicalOperation operation) {
		boolean start;
		synchronized (this) {
			if (physicalOperationActive) {
				physicalQueue.add(operation);
				start = false;
			} else {
				physicalOperationActive = true;
				start = true;
			}
		}
		if (start) runPhysical(operation);
	}

	private void runPhysical(PhysicalOperation operation) {
		FutureSupplier<?> task;
		try {
			task = operation.start.get();
			operation.started.accept(task);
		} catch (RuntimeException | LinkageError error) {
			try {
				operation.completed.accept(null, error);
			} finally {
				finishPhysical();
			}
			return;
		}
		task.onCompletion((result, error) -> {
			try {
				operation.completed.accept(task, error);
			} finally {
				finishPhysical();
			}
		});
	}

	private void finishPhysical() {
		PhysicalOperation next;
		synchronized (this) {
			next = physicalQueue.poll();
			if (next == null) physicalOperationActive = false;
		}
		if (next != null) runPhysical(next);
	}

	private void watch(AddonInfo info, Promise<Void> ready) {
		ready.onCompletion((result, error) -> {
			state.removeInstalling(info, ready);
			onReadyCompleted(info, ready);
		});
	}

	private void onReadyCompleted(AddonInfo info, Promise<Void> ready) {
		AddonInfo uninstall = null;
		synchronized (this) {
			ModuleJob job = moduleJobs.get(info.moduleName);
			if (job == null) return;
			Waiter waiter = job.waiters.get(info.className);
			if ((waiter == null) || (waiter.ready != ready)) return;
			job.waiters.remove(info.className);
			if (!job.waiters.isEmpty() || !job.delivered) return;
			moduleJobs.remove(info.moduleName, job);
			if (job.uninstallRequested || !moduleRetained.test(info.moduleName))
				uninstall = job.representative;
		}
		if (uninstall != null) requestModuleUninstall(uninstall);
	}

	private void requestModuleUninstall(AddonInfo info) {
		if (moduleRetained.test(info.moduleName)) return;
		Promise<Void> reservation = new Promise<>();
		synchronized (this) {
			if (uninstalling.putIfAbsent(info.moduleName, reservation) != null) return;
		}

		enqueuePhysical(new PhysicalOperation(
				() -> operations.uninstall(info, () -> !moduleRetained.test(info.moduleName)),
				task -> {
					synchronized (AddonModuleController.this) {
						if (uninstalling.get(info.moduleName) == reservation)
							uninstalling.put(info.moduleName, task);
					}
				},
				(task, error) -> {
					synchronized (AddonModuleController.this) {
						FutureSupplier<?> current = uninstalling.get(info.moduleName);
						if ((current == reservation) || (current == task))
							uninstalling.remove(info.moduleName);
					}
					if ((error != null) && !isCancellation(error))
						Log.e(error, "Failed to uninstall module: ", info.moduleName);
					else if ((error == null) && moduleRetained.test(info.moduleName))
						restoreRetainedModule(info);
				}));
	}

	private void restoreRetainedModule(AddonInfo info) {
		synchronized (this) {
			if ((moduleJobs.get(info.moduleName) != null) || !restoring.add(info.moduleName)) return;
		}
		enqueuePhysical(new PhysicalOperation(
				() -> {
					synchronized (AddonModuleController.this) {
						if ((moduleJobs.get(info.moduleName) != null) ||
								!moduleRetained.test(info.moduleName)) return completedNull();
					}
					return operations.install(info);
				},
				task -> {
				},
				(task, error) -> {
					synchronized (AddonModuleController.this) {
						restoring.remove(info.moduleName);
					}
					if ((error != null) && !isCancellation(error))
						Log.e(error, "Failed to restore retained module: ", info.moduleName);
				}));
	}

	private static void completeFailure(Promise<Void> ready, Throwable error) {
		if (isCancellation(error)) ready.cancel();
		else ready.completeExceptionally(error);
	}

	private static void cancel(List<Promise<Void>> tasks) {
		if (tasks == null) return;
		for (Promise<Void> task : tasks) task.cancel();
	}

	interface Operations {
		FutureSupplier<?> install(AddonInfo info);

		FutureSupplier<?> uninstall(AddonInfo info, BooleanSupplier shouldUninstall);

		void schedule(Runnable task, long delayMillis);
	}

	private static final class AndroidOperations implements Operations {
		@Nullable
		private FutureSupplier<MainActivity> pendingActivity;

		@Override
		public FutureSupplier<?> install(AddonInfo info) {
			return acquireActivity(info)
					.then(activity -> createInstaller(activity, info).install(info.moduleName));
		}

		@Override
		public FutureSupplier<?> uninstall(AddonInfo info, BooleanSupplier shouldUninstall) {
			return acquireActivity(info).then(activity -> {
				if (!shouldUninstall.getAsBoolean()) return completedNull();
				return createInstaller(activity, info).uninstall(info.moduleName)
						.onSuccess(result -> Log.i("Module uninstalled: ", info.moduleName));
			});
		}

		@Override
		public void schedule(Runnable task, long delayMillis) {
			App.get().getHandler().postDelayed(task, delayMillis);
		}

		private synchronized FutureSupplier<MainActivity> acquireActivity(AddonInfo info) {
			FutureSupplier<MainActivity> pending = pendingActivity;
			if ((pending != null) && !pending.isDone()) return pending;
			FutureSupplier<MainActivity> created = ActivityBase.create(App.get(), CHANNEL_ID,
					info.moduleName, info.icon, info.moduleName, null, MainActivity.class);
			if (!created.isDone()) {
				pendingActivity = created;
				created.onCompletion((result, error) -> {
					synchronized (AndroidOperations.this) {
						if (pendingActivity == created) pendingActivity = null;
					}
				});
			}
			return created;
		}
	}

	private static DynamicModuleInstaller createInstaller(Activity activity, AddonInfo info) {
		DynamicModuleInstaller installer = new DynamicModuleInstaller(activity);
		String name = activity.getString(info.addonName);
		installer.setSmallIcon(R.drawable.notification);
		installer.setTitle(activity.getString(R.string.module_installation, name));
		installer.setNotificationChannel(CHANNEL_ID,
				activity.getString(R.string.installing, name));
		installer.setPendingMessage(activity.getString(R.string.install_pending, name));
		installer.setDownloadingMessage(activity.getString(R.string.downloading, name));
		installer.setInstallingMessage(activity.getString(R.string.installing, name));
		return installer;
	}

	private static final class ModuleJob {
		final AddonInfo representative;
		final Map<String, Waiter> waiters = new LinkedHashMap<>();
		FutureSupplier<?> task;
		boolean starting;
		boolean delivered;
		boolean uninstallRequested;

		ModuleJob(AddonInfo representative) {
			this.representative = representative;
		}
	}

	private record Waiter(AddonInfo info, Promise<Void> ready) {
	}

	private record PhysicalOperation(Supplier<FutureSupplier<?>> start,
										 Consumer<FutureSupplier<?>> started,
										 BiConsumer<FutureSupplier<?>, Throwable> completed) {
	}
}
