package me.aap.fermata.pref;

import static java.util.concurrent.TimeUnit.HOURS;

import android.content.Context;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;

public final class DiagnosticsPreferences {
	public static final long DETAILED_DURATION_MILLIS = HOURS.toMillis(48);
	public static final Pref<BooleanSupplier> DETAILED_ENABLED =
			Pref.b("DETAILED_ENABLED", false);
	public static final Pref<LongSupplier> DETAILED_ENABLED_AT =
			Pref.l("DETAILED_ENABLED_AT", 0L);
	public static final Pref<LongSupplier> DETAILED_EXPIRES_AT =
			Pref.l("DETAILED_EXPIRES_AT", 0L);
	public static final Pref<LongSupplier> DETAILED_ENABLED_ELAPSED =
			Pref.l("DETAILED_ENABLED_ELAPSED", 0L);

	private final SharedPreferenceStore store;
	private final AtomicLong generation = new AtomicLong();

	public DiagnosticsPreferences(Context context) {
		store = SharedPreferenceStore.create(
				context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE));
	}

	public SharedPreferenceStore getStore() {
		return store;
	}

	public long getGeneration() {
		return generation.get();
	}

	public boolean isDetailedEnabled(long now) {
		boolean enabled = store.getBooleanPref(DETAILED_ENABLED);
		long enabledAt = store.getLongPref(DETAILED_ENABLED_AT);
		long expiresAt = store.getLongPref(DETAILED_EXPIRES_AT);
		long enabledElapsed = store.getLongPref(DETAILED_ENABLED_ELAPSED);
		long elapsedNow = SystemClock.elapsedRealtime();
		if ((enabledElapsed > 0L) && (elapsedNow >= enabledElapsed)) {
			return enabled && (enabledAt > 0L) && (expiresAt > enabledAt) &&
					(now < expiresAt) &&
					((elapsedNow - enabledElapsed) < DETAILED_DURATION_MILLIS);
		}
		return evaluate(enabled, enabledAt, expiresAt, now);
	}

	public long getDetailedExpiresAt() {
		return store.getLongPref(DETAILED_EXPIRES_AT);
	}

	public long getRemainingMillis(long now) {
		if (!isDetailedEnabled(now)) return 0L;
		long remaining = Math.max(0L, getDetailedExpiresAt() - now);
		long enabledElapsed = store.getLongPref(DETAILED_ENABLED_ELAPSED);
		long elapsedNow = SystemClock.elapsedRealtime();
		if ((enabledElapsed > 0L) && (elapsedNow >= enabledElapsed)) {
			remaining = Math.min(remaining, Math.max(0L,
					DETAILED_DURATION_MILLIS - (elapsedNow - enabledElapsed)));
		}
		return remaining;
	}

	public void setDetailedEnabled(boolean enabled, long now) {
		try (var edit = store.editPreferenceStore(false)) {
			edit.setBooleanPref(DETAILED_ENABLED, enabled);
			edit.setLongPref(DETAILED_ENABLED_AT, enabled ? now : 0L);
			edit.setLongPref(DETAILED_EXPIRES_AT,
					enabled ? saturatingAdd(now, DETAILED_DURATION_MILLIS) : 0L);
			edit.setLongPref(DETAILED_ENABLED_ELAPSED,
					enabled ? SystemClock.elapsedRealtime() : 0L);
		}
		generation.incrementAndGet();
	}

	public boolean disableIfExpired(long now) {
		if (!store.getBooleanPref(DETAILED_ENABLED)) return false;
		if (isDetailedEnabled(now)) return false;
		setDetailedEnabled(false, now);
		return true;
	}

	static boolean evaluate(boolean enabled, long enabledAt, long expiresAt, long now) {
		if (!enabled || (enabledAt <= 0L) || (expiresAt <= enabledAt)) return false;
		if ((now < enabledAt) || (now >= expiresAt)) return false;
		return (expiresAt - enabledAt) <= DETAILED_DURATION_MILLIS;
	}

	private static long saturatingAdd(long value, long increment) {
		if (value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
		return value + increment;
	}
}
