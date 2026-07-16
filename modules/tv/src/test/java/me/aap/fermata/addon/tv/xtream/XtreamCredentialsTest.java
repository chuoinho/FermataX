package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;

public class XtreamCredentialsTest extends Assert {

	@Test
	public void saveMovesCredentialsOutOfRootPreferences() {
		BasicPreferenceStore root = new BasicPreferenceStore();
		MapStore encrypted = new MapStore();

		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			e.setStringPref(XtreamAccount.usernamePref(7), "plain-user");
			e.setStringPref(XtreamAccount.passwordPref(7), "plain-pass");
		}

		try (PreferenceStore.Edit e = root.editPreferenceStore()) {
			XtreamCredentials.save(e, 7, "secure-user", "secure-pass", encrypted);
		}

		assertFalse(root.hasPref(XtreamAccount.usernamePref(7), false));
		assertFalse(root.hasPref(XtreamAccount.passwordPref(7), false));
		assertEquals("secure-user", encrypted.getString(XtreamCredentials.usernameKey(7)));
		assertEquals("secure-pass", encrypted.getString(XtreamCredentials.passwordKey(7)));
	}

	@Test
	public void loadReturnsNullWhenEitherSecretIsMissing() {
		MapStore encrypted = new MapStore();
		encrypted.putString(XtreamCredentials.usernameKey(9), "user");

		assertNull(XtreamCredentials.load(9, encrypted));
	}

	private static final class MapStore implements XtreamCredentials.Store {
		private final Map<String, String> values = new HashMap<>();

		@Override
		public String getString(String key) {
			return values.get(key);
		}

		@Override
		public void putString(String key, String value) {
			values.put(key, value);
		}

		@Override
		public void remove(String key) {
			values.remove(key);
		}
	}
}
