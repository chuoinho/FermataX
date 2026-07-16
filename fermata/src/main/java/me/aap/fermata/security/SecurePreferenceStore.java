package me.aap.fermata.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

import me.aap.utils.log.Log;

/** Base-owned encrypted preferences so dynamic features do not package crypto twice. */
public final class SecurePreferenceStore {
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
}
