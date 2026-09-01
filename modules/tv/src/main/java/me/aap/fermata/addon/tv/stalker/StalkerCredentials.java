package me.aap.fermata.addon.tv.stalker;

import android.content.Context;

import androidx.annotation.Nullable;

import me.aap.fermata.security.SecurePreferenceStore;
import me.aap.utils.app.App;
import me.aap.utils.pref.PreferenceStore;

final class StalkerCredentials {
	private static final String PREFS = "stalker_credentials";
	private static final String MAC_PREFIX = "mac#";
	private static final String SERIAL_PREFIX = "serial#";
	private static final String DEVICE_ID_PREFIX = "device_id#";
	private static volatile Store store;

	private StalkerCredentials() {
	}

	@Nullable
	static Identity load(int sourceId) {
		Store s = store();
		if (s == null) return null;
		String mac = s.getString(MAC_PREFIX + sourceId);
		if (mac == null) return null;
		return new Identity(mac, s.getString(SERIAL_PREFIX + sourceId),
				s.getString(DEVICE_ID_PREFIX + sourceId));
	}

	static void requireAvailable() {
		if (store() == null) {
			throw new IllegalStateException("Encrypted Stalker identity storage is unavailable");
		}
	}

	static void save(PreferenceStore.Edit edit, int sourceId, String mac, @Nullable String serial,
			@Nullable String deviceId) {
		Store s = store();
		if (s == null) {
			throw new IllegalStateException("Encrypted Stalker identity storage is unavailable");
		}
		s.putString(MAC_PREFIX + sourceId, mac);
		putOrRemove(s, SERIAL_PREFIX + sourceId, serial);
		putOrRemove(s, DEVICE_ID_PREFIX + sourceId, deviceId);
		edit.removePref(StalkerAccount.macPref(sourceId));
		edit.removePref(StalkerAccount.serialPref(sourceId));
		edit.removePref(StalkerAccount.deviceIdPref(sourceId));
	}

	static void remove(PreferenceStore.Edit edit, int sourceId) {
		Store s = store();
		if (s != null) {
			s.remove(MAC_PREFIX + sourceId);
			s.remove(SERIAL_PREFIX + sourceId);
			s.remove(DEVICE_ID_PREFIX + sourceId);
		}
		edit.removePref(StalkerAccount.macPref(sourceId));
		edit.removePref(StalkerAccount.serialPref(sourceId));
		edit.removePref(StalkerAccount.deviceIdPref(sourceId));
	}

	private static void putOrRemove(Store store, String key, @Nullable String value) {
		if ((value == null) || value.isBlank()) store.remove(key);
		else store.putString(key, value);
	}

	@Nullable
	private static Store store() {
		Store current = store;
		if (current != null) return current;
		synchronized (StalkerCredentials.class) {
			current = store;
			if (current != null) return current;
			Context context = App.get();
			SecurePreferenceStore preferences = SecurePreferenceStore.open(context, PREFS);
			return (preferences == null) ? null : (store = new SharedPrefsStore(preferences));
		}
	}

	interface Store {
		@Nullable String getString(String key);
		void putString(String key, String value);
		void remove(String key);
	}

	static final class Identity {
		final String mac;
		final String serial;
		final String deviceId;

		Identity(String mac, String serial, String deviceId) {
			this.mac = mac;
			this.serial = serial;
			this.deviceId = deviceId;
		}
	}

	private static final class SharedPrefsStore implements Store {
		private final SecurePreferenceStore preferences;

		SharedPrefsStore(SecurePreferenceStore preferences) {
			this.preferences = preferences;
		}

		@Override
		public String getString(String key) {
			return preferences.getString(key);
		}

		@Override
		public void putString(String key, String value) {
			preferences.putString(key, value);
		}

		@Override
		public void remove(String key) {
			preferences.remove(key);
		}
	}
}
