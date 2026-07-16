package me.aap.fermata.addon.tv.xtream;

import android.content.Context;

import androidx.annotation.Nullable;

import me.aap.utils.app.App;
import me.aap.utils.pref.PreferenceStore;
import me.aap.fermata.security.SecurePreferenceStore;

/**
 * @author Andrey Pavlenko
 */
final class XtreamCredentials {
	private static final String PREFS = "xtream_credentials";
	private static final String USERNAME_PREFIX = "username#";
	private static final String PASSWORD_PREFIX = "password#";
	private static volatile Store store;

	private XtreamCredentials() {
	}

	static Credentials load(int sourceId) {
		return load(sourceId, store());
	}

	static void requireAvailable() {
		if (store() == null) {
			throw new IllegalStateException("Encrypted Xtream credential storage is unavailable");
		}
	}

	static Credentials load(int sourceId, Store s) {
		if (s == null) return null;
		String username = s.getString(usernameKey(sourceId));
		String password = s.getString(passwordKey(sourceId));
		return ((username == null) || (password == null)) ? null : new Credentials(username, password);
	}

	static void save(PreferenceStore.Edit edit, int sourceId, String username, String password) {
		save(edit, sourceId, username, password, store());
	}

	static void save(PreferenceStore.Edit edit, int sourceId, String username, String password,
									 Store s) {
		if (s == null) {
			throw new IllegalStateException("Encrypted Xtream credential storage is unavailable");
		}
		s.putString(usernameKey(sourceId), username);
		s.putString(passwordKey(sourceId), password);
		edit.removePref(XtreamAccount.usernamePref(sourceId));
		edit.removePref(XtreamAccount.passwordPref(sourceId));
	}

	static void remove(PreferenceStore.Edit edit, int sourceId) {
		remove(edit, sourceId, store());
	}

	static void remove(PreferenceStore.Edit edit, int sourceId, @Nullable Store s) {
		if (s != null) {
			s.remove(usernameKey(sourceId));
			s.remove(passwordKey(sourceId));
		}

		edit.removePref(XtreamAccount.usernamePref(sourceId));
		edit.removePref(XtreamAccount.passwordPref(sourceId));
	}

	static String usernameKey(int sourceId) {
		return USERNAME_PREFIX + sourceId;
	}

	static String passwordKey(int sourceId) {
		return PASSWORD_PREFIX + sourceId;
	}

	@Nullable
	private static Store store() {
		Store s = store;
		if (s != null) return s;

		synchronized (XtreamCredentials.class) {
			s = store;
			if (s != null) return s;

			Context ctx = App.get();
			SecurePreferenceStore preferences = SecurePreferenceStore.open(ctx, PREFS);
			return (preferences == null) ? null : (store = new SharedPrefsStore(preferences));
		}
	}

	interface Store {
		@Nullable
		String getString(String key);

		void putString(String key, String value);

		void remove(String key);
	}

	static final class Credentials {
		final String username;
		final String password;

		Credentials(String username, String password) {
			this.username = username;
			this.password = password;
		}
	}

	private static final class SharedPrefsStore implements Store {
		private final SecurePreferenceStore prefs;

		SharedPrefsStore(SecurePreferenceStore prefs) {
			this.prefs = prefs;
		}

		@Nullable
		@Override
		public String getString(String key) {
			return prefs.getString(key);
		}

		@Override
		public void putString(String key, String value) {
			prefs.putString(key, value);
		}

		@Override
		public void remove(String key) {
			prefs.remove(key);
		}
	}
}
