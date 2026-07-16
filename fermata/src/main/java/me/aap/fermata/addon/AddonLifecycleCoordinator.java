package me.aap.fermata.addon;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

final class AddonLifecycleCoordinator {
	private final Set<FermataAddon> addons = new LinkedHashSet<>();
	private final ArrayDeque<Runnable> events = new ArrayDeque<>();
	private final Executor executor;
	@Nullable
	private MainActivityDelegate activity;
	@Nullable
	private MediaSessionCallback service;
	private boolean activityCreated;
	private boolean activityResumed;
	private boolean serviceCreated;
	private boolean dispatching;

	AddonLifecycleCoordinator() {
		this(Runnable::run);
	}

	AddonLifecycleCoordinator(Executor executor) {
		this.executor = executor;
	}

	void onActivityCreate(MainActivityDelegate activity) {
		boolean dispatch;
		synchronized (this) {
			this.activity = activity;
			activityCreated = true;
			activityResumed = false;
			List<FermataAddon> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (FermataAddon addon : snapshot) activityCreate(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onActivityResume(MainActivityDelegate activity) {
		boolean dispatch;
		synchronized (this) {
			this.activity = activity;
			activityResumed = true;
			List<FermataAddon> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (FermataAddon addon : snapshot) activityResume(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onActivityPause(MainActivityDelegate activity) {
		boolean dispatch;
		synchronized (this) {
			if (this.activity == activity) activityResumed = false;
			List<FermataAddon> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (FermataAddon addon : snapshot) activityPause(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onActivityDestroy(MainActivityDelegate activity) {
		boolean dispatch;
		synchronized (this) {
			if (this.activity == activity) {
				this.activity = null;
				activityCreated = false;
				activityResumed = false;
			}
			List<FermataAddon> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (FermataAddon addon : snapshot) activityDestroy(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onServiceCreate(MediaSessionCallback service) {
		boolean dispatch;
		synchronized (this) {
			this.service = service;
			serviceCreated = true;
			List<FermataAddon> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (FermataAddon addon : snapshot) serviceCreate(addon, service);
			});
		}
		dispatchNow(dispatch);
	}

	void onServiceDestroy(MediaSessionCallback service) {
		boolean dispatch;
		synchronized (this) {
			if (this.service == service) {
				this.service = null;
				serviceCreated = false;
			}
			List<FermataAddon> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (FermataAddon addon : snapshot) serviceDestroy(addon, service);
			});
		}
		dispatchNow(dispatch);
	}

	void onAddonLoaded(FermataAddon addon) {
		onAddonLoaded(addon, () -> {
		});
	}

	void onAddonLoaded(FermataAddon addon, Runnable afterReplay) {
		boolean dispatch;
		boolean replay;
		synchronized (this) {
			if (!addons.add(addon)) return;
			MediaSessionCallback service = this.service;
			MainActivityDelegate activity = this.activity;
			boolean createService = serviceCreated;
			boolean createActivity = activityCreated;
			boolean resumeActivity = activityResumed;
			replay = createService || createActivity || resumeActivity;
			dispatch = enqueue(() -> {
				try {
					if (createService) serviceCreate(addon, service);
					if (createActivity) activityCreate(addon, activity);
					if (resumeActivity) activityResume(addon, activity);
				} finally {
					afterReplay.run();
				}
			});
		}
		if (replay) dispatchAsync(dispatch);
		else dispatchNow(dispatch);
	}

	void onAddonUnloading(FermataAddon addon) {
		onAddonUnloading(addon, () -> {
		});
	}

	void onAddonUnloading(FermataAddon addon, Runnable afterTeardown) {
		boolean dispatch;
		boolean teardown;
		synchronized (this) {
			if (!addons.remove(addon)) return;
			MainActivityDelegate activity = this.activity;
			MediaSessionCallback service = this.service;
			boolean pauseActivity = activityResumed;
			boolean destroyActivity = activityCreated;
			boolean destroyService = serviceCreated;
			teardown = pauseActivity || destroyActivity || destroyService;
			dispatch = enqueue(() -> {
				try {
					if (pauseActivity) activityPause(addon, activity);
					if (destroyActivity) activityDestroy(addon, activity);
					if (destroyService) serviceDestroy(addon, service);
				} finally {
					afterTeardown.run();
				}
			});
		}
		if (teardown) dispatchAsync(dispatch);
		else dispatchNow(dispatch);
	}

	private List<FermataAddon> snapshot() {
		return new ArrayList<>(addons);
	}

	private boolean enqueue(Runnable event) {
		events.add(event);
		if (dispatching) return false;
		dispatching = true;
		return true;
	}

	private void dispatchNow(boolean required) {
		if (required) drainEvents();
	}

	private void dispatchAsync(boolean required) {
		if (!required) return;
		try {
			executor.execute(this::drainEvents);
		} catch (RuntimeException error) {
			synchronized (this) {
				dispatching = false;
			}
			throw error;
		}
	}

	private void drainEvents() {
		while (true) {
			Runnable event;
			synchronized (this) {
				event = events.poll();
				if (event == null) {
					dispatching = false;
					return;
				}
			}
			try {
				event.run();
			} catch (RuntimeException | LinkageError error) {
				Log.e(error, "Addon lifecycle dispatch failed");
			}
		}
	}

	private void activityCreate(FermataAddon addon, @Nullable MainActivityDelegate activity) {
		if (addon instanceof FermataActivityAddon activityAddon)
			invoke(addon, "activity create", () -> activityAddon.onActivityCreate(activity));
	}

	private void activityResume(FermataAddon addon, @Nullable MainActivityDelegate activity) {
		if (addon instanceof FermataActivityAddon activityAddon)
			invoke(addon, "activity resume", () -> activityAddon.onActivityResume(activity));
	}

	private void activityPause(FermataAddon addon, @Nullable MainActivityDelegate activity) {
		if (addon instanceof FermataActivityAddon activityAddon)
			invoke(addon, "activity pause", () -> activityAddon.onActivityPause(activity));
	}

	private void activityDestroy(FermataAddon addon, @Nullable MainActivityDelegate activity) {
		if (addon instanceof FermataActivityAddon activityAddon)
			invoke(addon, "activity destroy", () -> activityAddon.onActivityDestroy(activity));
	}

	private void serviceCreate(FermataAddon addon, @Nullable MediaSessionCallback service) {
		if (addon instanceof FermataMediaServiceAddon serviceAddon)
			invoke(addon, "service create", () -> serviceAddon.onServiceCreate(service));
	}

	private void serviceDestroy(FermataAddon addon, @Nullable MediaSessionCallback service) {
		if (addon instanceof FermataMediaServiceAddon serviceAddon)
			invoke(addon, "service destroy", () -> serviceAddon.onServiceDestroy(service));
	}

	private void invoke(FermataAddon addon, String event, Runnable callback) {
		try {
			callback.run();
		} catch (RuntimeException | LinkageError error) {
			Log.e(error, "Addon ", event, " failed: ", addon.getClass().getName());
		}
	}
}
