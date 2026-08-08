package me.aap.fermata.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import me.aap.utils.log.Log;

/** Base-owned encrypted preferences so dynamic features do not package crypto twice. */
public final class SecurePreferenceStore {
	public static final String REGISTRY_PREFERENCES = "secure_preference_stores";
	private static final String REGISTRY_KEY = "names";
	private static final Set<String> LEGACY_KNOWN_STORES = Set.of(
			"xtream_credentials", "podcast_credentials", "audiobook_credentials",
			"stremio_source_secrets");
	private final SharedPreferences preferences;

	private SecurePreferenceStore(SharedPreferences preferences) {
		this.preferences = preferences;
	}

	@Nullable
	public static SecurePreferenceStore open(Context context, String name) {
		try {
			MasterKey key = new MasterKey.Builder(context)
					.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
					.build();
			SharedPreferences preferences = EncryptedSharedPreferences.create(context, name, key,
					EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
					EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
			register(context, name);
			return new SecurePreferenceStore(preferences);
		} catch (GeneralSecurityException | IOException | RuntimeException ex) {
			Log.e(ex, "Failed to open encrypted preference storage: ", name);
			return null;
		}
	}

	@Nullable
	public String getString(String key) {
		return preferences.getString(key, null);
	}

	public void putString(String key, String value) {
		preferences.edit().putString(key, value).apply();
	}

	public void remove(String key) {
		preferences.edit().remove(key).apply();
	}

	public boolean update(Map<String, String> values, String... removals) {
		SharedPreferences.Editor edit = preferences.edit();
		for (Map.Entry<String, String> value : values.entrySet()) {
			edit.putString(value.getKey(), value.getValue());
		}
		for (String key : removals) edit.remove(key);
		return edit.commit();
	}

	public void remove(String... keys) {
		SharedPreferences.Editor edit = preferences.edit();
		for (String key : keys) edit.remove(key);
		edit.apply();
	}

	/** Returns decrypted logical values; Android Keystore implementation bytes are never exposed. */
	public Map<String, String> snapshot() {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
			if (!(entry.getValue() instanceof String value)) {
				throw new IllegalStateException("Secure preference contains a non-string value");
			}
			result.put(entry.getKey(), value);
		}
		return Map.copyOf(result);
	}

	/** Re-encrypts logical values with the destination installation's Android Keystore key. */
	public boolean replace(Map<String, String> values) {
		SharedPreferences.Editor edit = preferences.edit().clear();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			edit.putString(entry.getKey(), entry.getValue());
		}
		return edit.commit();
	}

	public static Set<String> getRegisteredStoreNames(Context context) {
		Set<String> stored = context.getSharedPreferences(REGISTRY_PREFERENCES,
				Context.MODE_PRIVATE).getStringSet(REGISTRY_KEY, Set.of());
		LinkedHashSet<String> result = new LinkedHashSet<>(LEGACY_KNOWN_STORES);
		if (stored != null) result.addAll(stored);
		return Set.copyOf(result);
	}

	public static boolean isKnownStoreName(String name) {
		return LEGACY_KNOWN_STORES.contains(name);
	}

	private static synchronized void register(Context context, String name) {
		SharedPreferences registry = context.getSharedPreferences(REGISTRY_PREFERENCES,
				Context.MODE_PRIVATE);
		Set<String> current = registry.getStringSet(REGISTRY_KEY, Set.of());
		if ((current != null) && current.contains(name)) return;
		LinkedHashSet<String> updated = new LinkedHashSet<>();
		if (current != null) updated.addAll(current);
		updated.add(name);
		registry.edit().putStringSet(REGISTRY_KEY, updated).commit();
	}
}
