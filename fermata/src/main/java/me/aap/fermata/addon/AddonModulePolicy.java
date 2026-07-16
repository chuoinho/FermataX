package me.aap.fermata.addon;

import java.util.function.Predicate;

final class AddonModulePolicy {
	private AddonModulePolicy() {
	}

	static boolean shouldUninstall(AddonInfo removed, Iterable<AddonInfo> infos,
											 Predicate<AddonInfo> retained) {
		for (AddonInfo info : infos) {
			if ((info != removed) && info.moduleName.equals(removed.moduleName) && retained.test(info))
				return false;
		}
		return true;
	}
}
