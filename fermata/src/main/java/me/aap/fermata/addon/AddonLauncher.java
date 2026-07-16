package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import me.aap.fermata.FermataApplication;
import me.aap.utils.async.FutureSupplier;

final class AddonLauncher {
	private final AddonManager manager;

	AddonLauncher(AddonManager manager) {
		this.manager = manager;
	}

	FutureSupplier<FermataAddon> getOrInstallAddon(String moduleOrClassName) {
		FermataAddon addon = manager.getAddon(moduleOrClassName);
		if (addon != null) return completed(addon);

		AddonInfo info = FermataAddon.findAddonInfo(moduleOrClassName);
		var prefs = FermataApplication.get().getPreferenceStore();
		if (!prefs.getBooleanPref(info.enabledPref)) prefs.applyBooleanPref(info.enabledPref, true);
		manager.installAddon(info);

		FutureSupplier<?> pending = manager.getInstallingTask(info);
		if (pending == null) {
			addon = manager.getAddon(moduleOrClassName);
			return addon != null ? completed(addon) :
					failed(new RuntimeException("Failed to install addon: " + moduleOrClassName));
		}

		return pending.then(v -> manager.getOrInstallAddon(info.className));
	}
}
