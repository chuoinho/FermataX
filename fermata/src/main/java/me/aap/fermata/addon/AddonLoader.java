package me.aap.fermata.addon;

import androidx.annotation.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import me.aap.utils.log.Log;

final class AddonLoader {
	private final AddonRuntimeState state;
	private final AddonLifecycleCoordinator lifecycle;

	AddonLoader(AddonRuntimeState state, AddonLifecycleCoordinator lifecycle) {
		this.state = state;
		this.lifecycle = lifecycle;
	}

	boolean isClassAvailable(AddonInfo info) {
		try {
			Class.forName(info.className, false, AddonLoader.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException error) {
			return false;
		} catch (LinkageError error) {
			return true;
		}
	}

	@Nullable
	FermataAddon load(AddonInfo info, BooleanSupplier shouldCommit) {
		return load(info, shouldCommit, addon -> {
		});
	}

	@Nullable
	FermataAddon load(AddonInfo info, BooleanSupplier shouldCommit,
							 Consumer<FermataAddon> afterReplay) {
		return load(info, (addon, commit) -> {
			if (!shouldCommit.getAsBoolean()) return false;
			commit.run();
			return true;
		}, afterReplay);
	}

	@Nullable
	FermataAddon load(AddonInfo info, Committer committer,
							 Consumer<FermataAddon> afterReplay) {
		return load(info, committer, () -> state.markFailed(info), afterReplay);
	}

	@Nullable
	FermataAddon load(AddonInfo info, Committer committer, Runnable markFailed,
							 Consumer<FermataAddon> afterReplay) {
		FermataAddon addon = null;
		boolean installInvoked = false;
		try {
			addon = (FermataAddon) Class.forName(info.className)
					.getDeclaredConstructor().newInstance();
			if (!info.className.equals(addon.getInfo().className)) {
				throw new AddonRuntimeState.RegistrationException(
						"Loaded addon metadata does not match " + info.className);
			}
			installInvoked = true;
			addon.install();
			FermataAddon loaded = addon;
			if (!committer.commit(loaded, () -> {
				state.add(loaded);
				lifecycle.onAddonLoaded(loaded, () -> afterReplay.accept(loaded));
			})) {
				uninstallAfterFailedLoad(addon, info);
				return null;
			}
			Log.i("Addon loaded: ", info.className);
			return addon;
		} catch (Exception | LinkageError ex) {
			if (ex instanceof ClassNotFoundException) return null;
			// Block reentrant preference callbacks during cleanup, then restore the failure marker
			// in case cleanup itself changed the addon's enabled preference.
			markFailed.run();
			if (installInvoked) uninstallAfterFailedLoad(addon, info);
			markFailed.run();
			Log.e(ex, "Failed to load addon: ", info.className);
			return null;
		}
	}

	@FunctionalInterface
	interface Committer {
		boolean commit(FermataAddon addon, Runnable commit);
	}

	@Nullable
	FermataAddon unload(AddonInfo info) {
		return unload(info, () -> {
		});
	}

	@Nullable
	FermataAddon unload(AddonInfo info, Runnable afterUninstall) {
		FermataAddon addon = state.remove(info);
		if (addon == null) return null;

		lifecycle.onAddonUnloading(addon, () -> {
			try {
				addon.uninstall();
			} catch (RuntimeException | LinkageError ex) {
				Log.e(ex, "Failed to uninstall addon: ", info.className);
			} finally {
				afterUninstall.run();
			}
		});
		return addon;
	}

	private void uninstallAfterFailedLoad(@Nullable FermataAddon addon, AddonInfo info) {
		if (addon == null) return;
		try {
			addon.uninstall();
		} catch (RuntimeException | LinkageError ex) {
			Log.e(ex, "Failed to clean up addon after load failure: ", info.className);
		}
	}
}
