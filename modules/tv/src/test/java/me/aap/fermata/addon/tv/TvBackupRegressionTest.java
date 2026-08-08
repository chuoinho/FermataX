package me.aap.fermata.addon.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.tv.xtream.XtreamAccount;
import me.aap.fermata.backup.BackupIO;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;

@RunWith(RobolectricTestRunner.class)
@Config(application = FermataApplication.class)
public class TvBackupRegressionTest {
	private final Map<String, String> credentials = new LinkedHashMap<>();
	private Field credentialStore;
	private TvSourceRepository repository;

	@Before
	public void setUp() throws Exception {
		FermataApplication app = FermataApplication.get();
		app.getSharedPreferences("medialib", Context.MODE_PRIVATE).edit().clear().commit();
		Class<?> credentialsClass = Class.forName(
				"me.aap.fermata.addon.tv.xtream.XtreamCredentials");
		Class<?> storeClass = Class.forName(
				"me.aap.fermata.addon.tv.xtream.XtreamCredentials$Store");
		Object store = Proxy.newProxyInstance(storeClass.getClassLoader(),
				new Class<?>[]{storeClass}, (proxy, method, arguments) -> switch (method.getName()) {
					case "getString" -> credentials.get(arguments[0]);
					case "putString" -> credentials.put((String) arguments[0], (String) arguments[1]);
					case "remove" -> credentials.remove(arguments[0]);
					case "toString" -> "FixtureCredentialStore";
					default -> throw new UnsupportedOperationException(method.getName());
				});
		credentialStore = credentialsClass.getDeclaredField("store");
		credentialStore.setAccessible(true);
		credentialStore.set(null, store);
		repository = new TvSourceRepository(SharedPreferenceStore.create(app, "medialib"));
	}

	@After
	public void tearDown() throws Exception {
		credentialStore.set(null, null);
		FermataApplication.get().getSharedPreferences("medialib", Context.MODE_PRIVATE)
				.edit().clear().commit();
		credentials.clear();
	}

	@Test
	public void m3uAndTwoXtreamAccountsRestoreAndNextSourceRemainsUsable() throws Exception {
		XtreamAccount first = account(2, "First", "first-user", "first-password");
		XtreamAccount second = account(3, "Second", "second-user", "second-password");
		try (PreferenceStore.Edit edit = repository.getStore().editPreferenceStore()) {
			repository.saveM3uSource(edit, 1, "m3u-source-id");
			repository.saveXtreamSource(edit, 2, first);
			repository.saveXtreamSource(edit, 3, second);
		}
		repository.setSourceIds(new int[]{1, 2, 3});
		TvAddon addon = new TvAddon();
		byte[] backup = addon.exportBackup();

		FermataApplication.get().getSharedPreferences("medialib", Context.MODE_PRIVATE)
				.edit().clear().commit();
		credentials.clear();
		addon.restoreBackup(addon.getBackupVersion(), backup);
		addon.verifyRestore(addon.getBackupVersion(), backup);

		assertArrayEquals(new int[]{1, 2, 3}, repository.getSourceIds());
		assertEquals(3, repository.getSourceCounter());
		assertEquals("m3u-source-id", repository.getM3uId(1));
		assertAccount(first, XtreamAccount.load(repository.getStore(), 2));
		assertAccount(second, XtreamAccount.load(repository.getStore(), 3));
		int nextId = repository.nextSourceId();
		assertEquals(4, nextId);
		assertFalse(repository.hasSource(nextId));

		XtreamAccount third = account(nextId, "Third", "third-user", "third-password");
		try (PreferenceStore.Edit edit = repository.getStore().editPreferenceStore()) {
			repository.saveXtreamSource(edit, nextId, third);
		}
		repository.setSourceIds(new int[]{1, 2, 3, nextId});

		assertAccount(third, XtreamAccount.load(repository.getStore(), nextId));
		assertEquals(5, repository.nextSourceId());
	}

	@Test
	public void missingM3uReferenceIsRejectedDuringValidation() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(1);
			output.writeInt(1);
			output.writeInt(1);
			BackupIO.writeString(output, TvSourceItem.TYPE_M3U);
			BackupIO.writeNullableString(output, null);
		}

		assertThrows(IllegalArgumentException.class,
				() -> new TvAddon().validateRestore(1, bytes.toByteArray()));
	}

	private static XtreamAccount account(int id, String name, String user, String password) {
		return new XtreamAccount(id, name, 1, "portal.example.invalid", 443, user, password,
				1, "FermataX", 45);
	}

	private static void assertAccount(XtreamAccount expected, XtreamAccount actual) {
		assertNotNull(actual);
		assertEquals(expected.getSourceId(), actual.getSourceId());
		assertEquals(expected.getRawName(), actual.getRawName());
		assertEquals(expected.getSchemeIndex(), actual.getSchemeIndex());
		assertEquals(expected.getHost(), actual.getHost());
		assertEquals(expected.getPort(), actual.getPort());
		assertEquals(expected.getUsername(), actual.getUsername());
		assertEquals(expected.getPassword(), actual.getPassword());
		assertEquals(expected.getOutputIndex(), actual.getOutputIndex());
		assertEquals(expected.getUserAgent(), actual.getUserAgent());
		assertEquals(expected.getResponseTimeout(), actual.getResponseTimeout());
	}
}
