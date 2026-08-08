package me.aap.fermata.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class AndroidBackupStateStoreTest {
	private Context context;
	private FakeSecureStores secureStores;

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		secureStores = new FakeSecureStores("xtream_credentials");
		context.getSharedPreferences("xtream_credentials", Context.MODE_PRIVATE).edit()
				.putString("implementation-marker", "not-exported").commit();
	}

	@Test
	public void snapshotUsesLogicalSecretsAndExcludesRawSecureAndDiagnosticStores() throws Exception {
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit()
				.putString("theme", "dark").commit();
		context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit()
				.putString("event", "must-not-export").commit();
		secureStores.values.put("username#1", "secret-user-issue5");

		BackupData snapshot = new AndroidBackupStateStore(context, secureStores).snapshot();

		assertEquals("dark", snapshot.preferences().get("fermata").get("theme"));
		assertEquals("secret-user-issue5",
				snapshot.securePreferences().get("xtream_credentials").get("username#1"));
		assertFalse(snapshot.preferences().containsKey("xtream_credentials"));
		assertFalse(snapshot.preferences().containsKey("secure_preference_stores"));
		assertFalse(snapshot.preferences().containsKey("diagnostics"));
	}

	@Test
	public void replaceClearsOldValuesAndReencryptsLogicalSecrets() throws Exception {
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit()
				.putString("old", "value").commit();
		secureStores.values.put("old-secret", "old");
		BackupData replacement = new BackupData(
				Map.of("fermata", Map.of("new", "value")),
				Map.of("xtream_credentials", Map.of("username#7", "new-user")), Map.of());

		new AndroidBackupStateStore(context, secureStores).replace(replacement);

		Map<String, ?> normal = context.getSharedPreferences("fermata", Context.MODE_PRIVATE).getAll();
		assertEquals(Map.of("new", "value"), normal);
		assertEquals(Map.of("username#7", "new-user"), secureStores.values);
	}

	@Test
	public void unavailableSecureStoreFailsValidationBeforeMutation() throws Exception {
		secureStores.available = false;
		AndroidBackupStateStore state = new AndroidBackupStateStore(context, secureStores);
		BackupData replacement = new BackupData(Map.of("fermata", Map.of("new", "value")),
				Map.of("xtream_credentials", Map.of("username#1", "user")), Map.of());

		BackupException failure = assertThrows(BackupException.class,
				() -> state.validate(replacement));

		assertEquals(BackupException.Code.SECURE_STORAGE_UNAVAILABLE, failure.getCode());
		assertFalse(context.getSharedPreferences("fermata", Context.MODE_PRIVATE)
				.contains("new"));
	}

	@Test
	public void legacyLogicalRestoreKeepsDestinationSecureStoreUsable() throws Exception {
		secureStores.values.put("username#1", "existing-user");
		AndroidBackupStateStore state = new AndroidBackupStateStore(context, secureStores);
		BackupData current = state.snapshot();
		BackupData legacy = new BackupData(
				Map.of("fermata", Map.of("legacy-setting", true)), Map.of(), Map.of());
		BackupData safeLegacy = new BackupData(legacy.preferences(),
				current.securePreferences(), Map.of());

		new BackupCoordinator(state, java.util.List.of()).restore(safeLegacy);

		assertEquals("existing-user", secureStores.values.get("username#1"));
		secureStores.values.put("username#2", "new-user-after-legacy-restore");
		assertEquals("new-user-after-legacy-restore", secureStores.values.get("username#2"));
	}

	private static final class FakeSecureStores
			implements AndroidBackupStateStore.SecureStoreAccess,
			AndroidBackupStateStore.LogicalSecureStore {
		final String name;
		final Map<String, String> values = new LinkedHashMap<>();
		boolean available = true;

		FakeSecureStores(String name) {
			this.name = name;
		}

		@Override
		public Set<String> registeredNames() {
			return Set.of(name);
		}

		@Override
		public AndroidBackupStateStore.LogicalSecureStore open(String requestedName) {
			return available && name.equals(requestedName) ? this : null;
		}

		@Override
		public Map<String, String> snapshot() {
			return Map.copyOf(values);
		}

		@Override
		public boolean replace(Map<String, String> replacement) {
			values.clear();
			values.putAll(replacement);
			return true;
		}
	}
}
