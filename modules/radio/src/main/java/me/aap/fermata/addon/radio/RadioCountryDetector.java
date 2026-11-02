package me.aap.fermata.addon.radio;

import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Pure helper for determining the ISO 3166-1 alpha-2 country code for the "Near You" folder.
 * Priority: TelephonyManager.getNetworkCountryIso() -> TelephonyManager.getSimCountryIso() -> Locale.getDefault().getCountry().
 * No location permission required.
 */
public final class RadioCountryDetector {

	private RadioCountryDetector() {
	}

	@Nullable
	public static String detectCountryCode(@NonNull Context context) {
		String networkIso = null;
		String simIso = null;
		try {
			TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			if (tm != null) {
				networkIso = tm.getNetworkCountryIso();
				simIso = tm.getSimCountryIso();
			}
		} catch (Throwable ignored) {
		}
		String localeIso = Locale.getDefault().getCountry();
		return resolveCountryCode(networkIso, simIso, localeIso);
	}

	@Nullable
	public static String resolveCountryCode(@Nullable String networkIso, @Nullable String simIso, @Nullable String localeIso) {
		if (isValidIso(networkIso)) return networkIso.trim().toUpperCase(Locale.ROOT);
		if (isValidIso(simIso)) return simIso.trim().toUpperCase(Locale.ROOT);
		if (isValidIso(localeIso)) return localeIso.trim().toUpperCase(Locale.ROOT);
		return null;
	}

	private static boolean isValidIso(@Nullable String iso) {
		return iso != null && !iso.trim().isEmpty();
	}
}
