package me.aap.fermata.ui.policy;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Pure policy for Android background limits and known OEM startup managers. */
public final class BackgroundRunPolicy {
	private BackgroundRunPolicy() {
	}

	public static boolean isUnrestricted(boolean ignoringBatteryOptimizations,
			boolean backgroundRestricted) {
		return ignoringBatteryOptimizations && !backgroundRestricted;
	}

	public static boolean shouldOfferInitialWarning(boolean unrestricted, boolean warningAsked) {
		return !unrestricted && !warningAsked;
	}

	public static boolean shouldExplainBackgroundRestriction(boolean stale, boolean alwaysLocation,
			boolean unrestricted) {
		return stale && alwaysLocation && !unrestricted;
	}

	public static ManagerTarget selectVendorTarget(String manufacturer,
			Predicate<ManagerTarget> isResolvable) {
		Vendor vendor = vendorFor(manufacturer);
		if (vendor == null) return null;
		for (ManagerTarget target : vendor.targets()) {
			if (isResolvable.test(target)) return target;
		}
		return ManagerTarget.APP_DETAILS;
	}

	public static Vendor vendorFor(String manufacturer) {
		if (manufacturer == null) return null;
		String value = manufacturer.toLowerCase(Locale.ROOT);
		if (value.contains("honor") || value.contains("huawei")) return Vendor.HUAWEI_HONOR;
		if (value.contains("xiaomi") || value.contains("redmi") || value.contains("poco")) {
			return Vendor.XIAOMI;
		}
		if (value.contains("oppo") || value.contains("realme") || value.contains("oneplus")) {
			return Vendor.OPPO_REALME_ONEPLUS;
		}
		if (value.contains("vivo") || value.contains("iqoo")) return Vendor.VIVO;
		return null;
	}

	public enum Vendor {
		HUAWEI_HONOR(List.of(
				new ManagerTarget("com.huawei.systemmanager",
						"com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
				new ManagerTarget("com.hihonor.systemmanager",
						"com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
				new ManagerTarget("com.huawei.systemmanager",
						"com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))),
		XIAOMI(List.of(new ManagerTarget("com.miui.securitycenter",
				"com.miui.permcenter.autostart.AutoStartManagementActivity"))),
		OPPO_REALME_ONEPLUS(List.of(
				new ManagerTarget("com.coloros.safecenter",
						"com.coloros.safecenter.startupapp.StartupAppListActivity"),
				new ManagerTarget("com.oplus.safecenter",
						"com.oplus.safecenter.startupapp.StartupAppListActivity"),
				new ManagerTarget("com.oneplus.security",
						"com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))),
		VIVO(List.of(
				new ManagerTarget("com.iqoo.secure",
						"com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
				new ManagerTarget("com.vivo.permissionmanager",
						"com.vivo.permissionmanager.activity.BgStartUpManagerActivity")));

		private final List<ManagerTarget> targets;

		Vendor(List<ManagerTarget> targets) {
			this.targets = targets;
		}

		public List<ManagerTarget> targets() {
			return targets;
		}
	}

	public record ManagerTarget(String packageName, String className, boolean appDetailsFallback) {
		public static final ManagerTarget APP_DETAILS = new ManagerTarget("", "", true);

		public ManagerTarget(String packageName, String className) {
			this(packageName, className, false);
		}
	}
}
