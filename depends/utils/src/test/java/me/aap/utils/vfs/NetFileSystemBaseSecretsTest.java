package me.aap.utils.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

class NetFileSystemBaseSecretsTest {
	private static final Pref<Supplier<String[]>> ROOTS = Pref.sa("TEST_ROOTS", new String[0]);
	private static final Pref<Supplier<String>> LEGACY_PASSWORD = Pref.s("smb://user@host#P", () -> null);
	private static String createdWithPassword;

	@AfterEach
	void uninstallSecrets() {
		NetFileSystemBase.setSecrets(null);
	}

	@Test
	void passwordSavedBeforeTheEncryptedStoreMovesIntoIt() {
		PreferenceStore ps = new BasicPreferenceStore();
		new TestFs(ps).saveCredentials("smb", "user", "host", 445, "secret", null, null);
		assertNotNull(ps.getStringPref(LEGACY_PASSWORD)); // the old hex form

		MapSecrets secrets = new MapSecrets();
		NetFileSystemBase.setSecrets(secrets);
		ps.applyStringArrayPref(ROOTS, new String[]{"smb://user@host/share"});
		new TestFs(ps);

		assertEquals("secret", createdWithPassword);
		assertEquals("secret", secrets.values.get("smb://user@host#P"));
		assertNull(ps.getStringPref(LEGACY_PASSWORD));
	}

	@Test
	void newPasswordGoesOnlyToTheEncryptedStore() {
		PreferenceStore ps = new BasicPreferenceStore();
		MapSecrets secrets = new MapSecrets();
		NetFileSystemBase.setSecrets(secrets);

		new TestFs(ps).saveCredentials("smb", "user", "host", 445, "secret", null, "key-pass");

		assertNull(ps.getStringPref(LEGACY_PASSWORD));
		assertEquals("secret", secrets.values.get("smb://user@host#P"));
		assertEquals("key-pass", secrets.values.get("smb://user@host#K"));
	}

	@Test
	void unavailableStoreNeverFallsBackToThePreferenceStore() {
		PreferenceStore ps = new BasicPreferenceStore();
		MapSecrets secrets = new MapSecrets();
		secrets.available = false;
		NetFileSystemBase.setSecrets(secrets);

		new TestFs(ps).saveCredentials("smb", "user", "host", 445, "secret", null, null);

		assertNull(ps.getStringPref(LEGACY_PASSWORD));
	}

	private static final class MapSecrets implements NetFileSystemBase.Secrets {
		final Map<String, String> values = new HashMap<>();
		boolean available = true;

		@Nullable
		@Override
		public String get(String key) {
			return values.get(key);
		}

		@Override
		public boolean put(String key, String value) {
			if (available) values.put(key, value);
			return available;
		}

		@Override
		public void remove(String key) {
			values.remove(key);
		}
	}

	private static final class TestFs extends NetFileSystemBase {
		TestFs(PreferenceStore ps) {
			super(new Provider() {
				@NonNull
				@Override
				public Set<String> getSupportedSchemes() {
					return Set.of("smb");
				}

				@NonNull
				@Override
				public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
					throw new UnsupportedOperationException();
				}
			}, ps);
		}

		@Override
		protected VirtualFolder createRoot(@Nullable String user, @NonNull String host, int port,
				@Nullable String path, @Nullable String password, @Nullable String keyFile,
				@Nullable String keyPass) {
			createdWithPassword = password;
			return null;
		}

		@Override
		protected FutureSupplier<VirtualFolder> createConnectedRoot(@Nullable String user,
				@NonNull String host, int port, @Nullable String path, @Nullable String password,
				@Nullable String keyFile, @Nullable String keyPass) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected FutureSupplier<VirtualResource> createResource(VirtualResource root, String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected VirtualFile createFile(VirtualResource root, String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected VirtualFolder createFolder(VirtualResource root, String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected int getDefaultPort() {
			return 445;
		}

		@Override
		protected Pref<Supplier<String[]>> getRootsPref() {
			return ROOTS;
		}
	}
}
