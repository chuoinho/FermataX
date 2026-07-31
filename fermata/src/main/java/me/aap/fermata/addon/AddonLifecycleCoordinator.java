package me.aap.fermata.addon;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.AsyncOperationController.DiagnosticsObserver;
import me.aap.utils.log.Log;

final class AddonLifecycleCoordinator {
	private final List<AddonLifecycle> addons = new ArrayList<>();
	private final Map<FermataAddon, AddonLifecycle> active = new IdentityHashMap<>();
	private final ArrayDeque<Runnable> events = new ArrayDeque<>();
	private final Executor executor;
	private long generation;
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
			if (activityCreated && (this.activity == activity)) return;
			this.activity = activity;
			activityCreated = true;
			activityResumed = false;
			List<AddonLifecycle> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (AddonLifecycle addon : snapshot) activityCreate(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onActivityResume(MainActivityDelegate activity) {
		boolean dispatch;
		synchronized (this) {
			if (!activityCreated || (this.activity != activity) || activityResumed) return;
			this.activity = activity;
			activityResumed = true;
			List<AddonLifecycle> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (AddonLifecycle addon : snapshot) activityResume(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onActivityPause(MainActivityDelegate activity) {
		boolean dispatch;
		synchronized (this) {
			if (!activityCreated || (this.activity != activity) || !activityResumed) return;
			activityResumed = false;
			List<AddonLifecycle> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (AddonLifecycle addon : snapshot) activityPause(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onActivityDestroy(MainActivityDelegate activity) {
		boolean dispatch;
		synchronized (this) {
			if (!activityCreated || (this.activity != activity)) return;
			this.activity = null;
			activityCreated = false;
			activityResumed = false;
			List<AddonLifecycle> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (AddonLifecycle addon : snapshot) activityDestroy(addon, activity);
			});
		}
		dispatchNow(dispatch);
	}

	void onServiceCreate(MediaSessionCallback service) {
		boolean dispatch;
		synchronized (this) {
			if (serviceCreated && (this.service == service)) return;
			this.service = service;
			serviceCreated = true;
			List<AddonLifecycle> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (AddonLifecycle addon : snapshot) serviceCreate(addon, service);
			});
		}
		dispatchNow(dispatch);
	}

	void onServiceDestroy(MediaSessionCallback service) {
		boolean dispatch;
		synchronized (this) {
			if (!serviceCreated || (this.service != service)) return;
			this.service = null;
			serviceCreated = false;
			List<AddonLifecycle> snapshot = snapshot();
			dispatch = enqueue(() -> {
				for (AddonLifecycle addon : snapshot) serviceDestroy(addon, service);
			});
		}
		dispatchNow(dispatch);
	}

	LifecycleToken onAddonLoaded(FermataAddon addon) {
		return onAddonLoaded(addon, () -> {
		});
	}

	LifecycleToken onAddonLoaded(FermataAddon addon, Runnable afterReplay) {
		boolean dispatch;
		boolean replay;
		AddonLifecycle lifecycle;
		 synchronized (this) {
			AddonLifecycle current = active.get(addon);
			if (current != null) {
				DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.TOKEN_REUSED,
						addonId(addon), current.token.generation(), 0L, null);
				return current.token;
			}
			lifecycle = new AddonLifecycle(new LifecycleToken(++generation, addon));
			active.put(addon, lifecycle);
			addons.add(lifecycle);
			MediaSessionCallback service = this.service;
			MainActivityDelegate activity = this.activity;
			boolean createService = serviceCreated;
			boolean createActivity = activityCreated;
			boolean resumeActivity = activityResumed;
			replay = createService || createActivity || resumeActivity;
			dispatch = enqueue(() -> {
				try {
					if (createService) serviceCreate(lifecycle, service);
					if (createActivity) activityCreate(lifecycle, activity);
					if (resumeActivity) activityResume(lifecycle, activity);
				} finally {
					if (isActive(lifecycle.token)) afterReplay.run();
				}
			});
		}
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.TOKEN_CREATED, addonId(addon),
				lifecycle.token.generation(), 0L, null);
		if (replay) dispatchAsync(dispatch);
		else dispatchNow(dispatch);
		return lifecycle.token;
	}

	void onAddonUnloading(FermataAddon addon) {
		onAddonUnloading(addon, () -> {
		});
	}

	void onAddonUnloading(FermataAddon addon, Runnable afterTeardown) {
		boolean dispatch;
		boolean teardown;
		AddonLifecycle lifecycle;
		synchronized (this) {
			lifecycle = active.remove(addon);
			if (lifecycle == null) return;
			addons.remove(lifecycle);
			teardown = lifecycle.activityCreated || lifecycle.activityResumed ||
					lifecycle.serviceCreated;
			dispatch = enqueue(() -> {
				try {
					teardown(lifecycle);
				} finally {
					afterTeardown.run();
				}
			});
		}
		DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.TOKEN_INVALIDATED, addonId(addon),
				lifecycle.token.generation(), 0L, null);
		if (teardown) dispatchAsync(dispatch);
		else dispatchNow(dispatch);
	}

	@Nullable
	synchronized LifecycleToken getToken(FermataAddon addon) {
		AddonLifecycle lifecycle = active.get(addon);
		return (lifecycle == null) ? null : lifecycle.token;
	}

	synchronized boolean isActive(LifecycleToken token) {
		AddonLifecycle lifecycle = active.get(token.addon);
		return (lifecycle != null) && (lifecycle.token == token);
	}

	private List<AddonLifecycle> snapshot() {
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

	private void activityCreate(AddonLifecycle lifecycle,
			@Nullable MainActivityDelegate activity) {
		synchronized (this) {
			if (!isActiveLocked(lifecycle) ||
					(lifecycle.activityCreated && (lifecycle.activity == activity))) return;
			lifecycle.activity = activity;
			lifecycle.activityCreated = true;
			lifecycle.activityResumed = false;
		}
		FermataAddon addon = lifecycle.token.addon;
		if (addon instanceof FermataActivityAddon activityAddon) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.ACTIVITY_CREATE,
					addonId(addon), lifecycle.token.generation(), 0L, null);
			invoke(addon, "activity create", () -> activityAddon.onActivityCreate(activity));
		}
	}

	private void activityResume(AddonLifecycle lifecycle,
			@Nullable MainActivityDelegate activity) {
		synchronized (this) {
			if (!isActiveLocked(lifecycle) || !lifecycle.activityCreated ||
					(lifecycle.activity != activity) || lifecycle.activityResumed) return;
			lifecycle.activityResumed = true;
		}
		FermataAddon addon = lifecycle.token.addon;
		if (addon instanceof FermataActivityAddon activityAddon) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.ACTIVITY_RESUME,
					addonId(addon), lifecycle.token.generation(), 0L, null);
			invoke(addon, "activity resume", () -> activityAddon.onActivityResume(activity));
		}
	}

	private void activityPause(AddonLifecycle lifecycle,
			@Nullable MainActivityDelegate activity) {
		synchronized (this) {
			if (!isActiveLocked(lifecycle) || !lifecycle.activityResumed ||
					(lifecycle.activity != activity)) return;
			lifecycle.activityResumed = false;
		}
		FermataAddon addon = lifecycle.token.addon;
		if (addon instanceof FermataActivityAddon) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.ACTIVITY_PAUSE,
					addonId(addon), lifecycle.token.generation(), 0L, null);
		}
		activityPause(addon, activity);
	}

	private void activityDestroy(AddonLifecycle lifecycle,
			@Nullable MainActivityDelegate activity) {
		synchronized (this) {
			if (!isActiveLocked(lifecycle) || !lifecycle.activityCreated ||
					(lifecycle.activity != activity)) return;
			lifecycle.activityCreated = false;
			lifecycle.activityResumed = false;
			lifecycle.activity = null;
		}
		FermataAddon addon = lifecycle.token.addon;
		if (addon instanceof FermataActivityAddon) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.ACTIVITY_DESTROY,
					addonId(addon), lifecycle.token.generation(), 0L, null);
		}
		activityDestroy(addon, activity);
	}

	private void serviceCreate(AddonLifecycle lifecycle,
			@Nullable MediaSessionCallback service) {
		synchronized (this) {
			if (!isActiveLocked(lifecycle) ||
					(lifecycle.serviceCreated && (lifecycle.service == service))) return;
			lifecycle.service = service;
			lifecycle.serviceCreated = true;
		}
		FermataAddon addon = lifecycle.token.addon;
		if (addon instanceof FermataMediaServiceAddon serviceAddon) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.SERVICE_CREATE,
					addonId(addon), lifecycle.token.generation(), 0L, null);
			invoke(addon, "service create", () -> serviceAddon.onServiceCreate(service));
		}
	}

	private void serviceDestroy(AddonLifecycle lifecycle,
			@Nullable MediaSessionCallback service) {
		synchronized (this) {
			if (!isActiveLocked(lifecycle) || !lifecycle.serviceCreated ||
					(lifecycle.service != service)) return;
			lifecycle.serviceCreated = false;
			lifecycle.service = null;
		}
		FermataAddon addon = lifecycle.token.addon;
		if (addon instanceof FermataMediaServiceAddon) {
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.SERVICE_DESTROY,
					addonId(addon), lifecycle.token.generation(), 0L, null);
		}
		serviceDestroy(addon, service);
	}

	private void teardown(AddonLifecycle lifecycle) {
		MainActivityDelegate activity;
		MediaSessionCallback service;
		boolean pause;
		boolean destroyActivity;
		boolean destroyService;
		synchronized (this) {
			activity = lifecycle.activity;
			service = lifecycle.service;
			pause = lifecycle.activityResumed;
			destroyActivity = lifecycle.activityCreated;
			destroyService = lifecycle.serviceCreated;
			lifecycle.activityResumed = false;
			lifecycle.activityCreated = false;
			lifecycle.serviceCreated = false;
			lifecycle.activity = null;
			lifecycle.service = null;
		}
		FermataAddon addon = lifecycle.token.addon;
		if (pause) activityPause(addon, activity);
		if (destroyActivity) activityDestroy(addon, activity);
		if (destroyService) serviceDestroy(addon, service);
	}

	private boolean isActiveLocked(AddonLifecycle lifecycle) {
		return active.get(lifecycle.token.addon) == lifecycle;
	}

	private void activityPause(FermataAddon addon, @Nullable MainActivityDelegate activity) {
		if (addon instanceof FermataActivityAddon activityAddon)
			invoke(addon, "activity pause", () -> activityAddon.onActivityPause(activity));
	}

	private void activityDestroy(FermataAddon addon, @Nullable MainActivityDelegate activity) {
		if (addon instanceof FermataActivityAddon activityAddon)
			invoke(addon, "activity destroy", () -> activityAddon.onActivityDestroy(activity));
	}

	private void serviceDestroy(FermataAddon addon, @Nullable MediaSessionCallback service) {
		if (addon instanceof FermataMediaServiceAddon serviceAddon)
			invoke(addon, "service destroy", () -> serviceAddon.onServiceDestroy(service));
	}

	private void invoke(FermataAddon addon, String event, Runnable callback) {
		try {
			callback.run();
		} catch (RuntimeException | LinkageError error) {
			LifecycleToken token = getToken(addon);
			DiagnosticsObserver.addon(DiagnosticsObserver.AddonEvent.CALLBACK_FAILED,
					addonId(addon), (token == null) ? 0L : token.generation(), 0L, error);
			Log.e(error, "Addon ", event, " failed: ", addon.getClass().getName());
		}
	}

	private static int addonId(FermataAddon addon) {
		try {
			return addon.getAddonId();
		} catch (RuntimeException | LinkageError ignored) {
			return 0;
		}
	}

	static record LifecycleToken(long generation, FermataAddon addon) {
	}

	private static final class AddonLifecycle {
		private final LifecycleToken token;
		@Nullable
		private MainActivityDelegate activity;
		@Nullable
		private MediaSessionCallback service;
		private boolean activityCreated;
		private boolean activityResumed;
		private boolean serviceCreated;

		private AddonLifecycle(LifecycleToken token) {
			this.token = token;
		}
	}
}
