package me.aap.fermata.ui.carapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.function.Predicate;

/**
 * Passive root detector for the Car Apps feature.
 * <p>
 * NEVER executes su or any process; detection is purely passive via file
 * checks, package manager queries, and recorded preferences.
 */
public final class RootProbe {

	public static final String PREF_ROOT_GRANTED = "CAR_APPS_ROOT_GRANTED";
	public static final String PREF_ADB_PAIRED = "CAR_APPS_ADB_PAIRED";

	public static final String[] SU_PATHS = {
			"/system/bin/su",
			"/system/xbin/su",
			"/sbin/su",
			"/product/bin/su",
			"/vendor/bin/su",
			"/system_ext/bin/su",
			"/debug_ramdisk/su"
	};

	public static final String[] MANAGER_PACKAGES = {
			"com.topjohnwu.magisk",
			"me.weishu.kernelsu",
			"me.bmax.apatch"
	};

	private RootProbe() {
	}

	public static RootState detect(@Nullable Predicate<String> fileExists,
	                               @Nullable Predicate<String> packageInstalled,
	                               boolean grantedBefore) {
		// A grant that worked is proof even with root hidden; a later failed start clears it (CarDisplayClient)
		if (grantedBefore) return RootState.GRANTED;
		boolean rootDetected = false;
		if (fileExists != null) {
			for (String path : SU_PATHS) {
				if (fileExists.test(path)) {
					rootDetected = true;
					break;
				}
			}
		}
		if (!rootDetected && packageInstalled != null) {
			for (String pkg : MANAGER_PACKAGES) {
				if (packageInstalled.test(pkg)) {
					rootDetected = true;
					break;
				}
			}
		}
		return rootDetected ? RootState.AVAILABLE : RootState.ABSENT;
	}

	public static void refresh(@Nullable Context ctx) {
		if (ctx == null) {
			CarAppsFeature.setState(RootState.ABSENT);
			return;
		}
		SharedPreferences prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		boolean grantedBefore = (prefs != null) && prefs.getBoolean(PREF_ROOT_GRANTED, false);

		PackageManager pm = ctx.getPackageManager();
		Predicate<String> packageInstalled = pkg -> {
			if (pm == null) {
				return false;
			}
			try {
				pm.getPackageInfo(pkg, 0);
				return true;
			} catch (PackageManager.NameNotFoundException ignored) {
				return false;
			} catch (Throwable ignored) {
				return false;
			}
		};

		Predicate<String> fileExists = path -> {
			try {
				return new File(path).exists();
			} catch (Throwable ignored) {
				return false;
			}
		};

		RootState state = detect(fileExists, packageInstalled, grantedBefore);
		CarAppsFeature.setState(state);
	}

	public static void recordGranted(@Nullable Context ctx, boolean granted) {
		if (ctx == null) {
			return;
		}
		SharedPreferences prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		if (prefs != null) {
			prefs.edit().putBoolean(PREF_ROOT_GRANTED, granted).apply();
		}
		refresh(ctx);
	}

	/** Remembers that this phone is paired for the local ADB channel, or that the authorization was revoked. */
	public static void recordAdbPaired(@Nullable Context ctx, boolean paired) {
		if (ctx != null) {
			ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().putBoolean(PREF_ADB_PAIRED, paired).apply();
		}
		CarAppsFeature.setAdbPaired(paired);
	}

	/** Restores the paired flag at startup; the in-memory flag alone would be lost with the process. */
	public static void restoreAdbPaired(@Nullable Context ctx) {
		if (ctx == null) return;
		CarAppsFeature.setAdbPaired(ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE)
				.getBoolean(PREF_ADB_PAIRED, false));
	}
}
