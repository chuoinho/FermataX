package me.aap.fermata.backup;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import me.aap.fermata.security.SecurePreferenceStore;
import me.aap.utils.pref.PrefUtils;

/** Captures and replaces logical preference state without copying implementation XML files. */
public final class AndroidBackupStateStore implements BackupStateStore {
	private static final int DETECTION_BYTES = 128 * 1024;
	private static final String KEY_KEYSET =
			"__androidx_security_crypto_encrypted_prefs_key_keyset__";
	private static final String VALUE_KEYSET =
			"__androidx_security_crypto_encrypted_prefs_value_keyset__";
	private static final Set<String> EXCLUDED_NORMAL_STORES = Set.of(
			"image-cache", "diagnostics", SecurePreferenceStore.REGISTRY_PREFERENCES);
	private final Context context;
	private final SecureStoreAccess secureStores;

	public AndroidBackupStateStore(Context context) {
		this(context, null);
	}

	AndroidBackupStateStore(Context context, SecureStoreAccess secureStores) {
		this.context = context.getApplicationContext();
		this.secureStores = (secureStores != null) ? secureStores :
				new AndroidSecureStoreAccess(this.context);
	}

	@Override
	public BackupData snapshot() throws BackupException {
		Map<String, Map<String, Object>> normal = new LinkedHashMap<>();
		Map<String, Map<String, String>> secure = new LinkedHashMap<>();
		Set<String> secureNames = secureStoreNames();

		for (String name : preferenceFileNames()) {
			if (EXCLUDED_NORMAL_STORES.contains(name) || secureNames.contains(name)) continue;
			try {
				normal.put(name, copySupported(context.getSharedPreferences(name,
						Context.MODE_PRIVATE).getAll()));
			} catch (RuntimeException ex) {
				throw new BackupException(BackupException.Code.INVALID_FORMAT,
						"Unable to read application preferences", ex);
			}
		}

		for (String name : secureNames) {
			LogicalSecureStore store = secureStores.open(name);
			if (store == null) throw secureUnavailable();
			try {
				secure.put(name, store.snapshot());
			} catch (RuntimeException ex) {
				throw new BackupException(BackupException.Code.SECURE_STORAGE_UNAVAILABLE,
						"Secure storage is unavailable", ex);
			}
		}

		return new BackupData(normal, secure, Map.of());
	}

	@Override
	public void validate(BackupData data) throws BackupException {
		for (String name : data.preferences().keySet()) validateName(name);
		for (String name : data.securePreferences().keySet()) {
			validateName(name);
			if (secureStores.open(name) == null) throw secureUnavailable();
		}
	}

	@Override
	public void replace(BackupData data) throws BackupException {
		validate(data);
		Set<String> secureNames = secureStoreNames();
		secureNames.addAll(data.securePreferences().keySet());

		Set<String> normalNames = preferenceFileNames();
		normalNames.addAll(data.preferences().keySet());
		normalNames.removeAll(EXCLUDED_NORMAL_STORES);
		normalNames.removeAll(secureNames);
		for (String name : normalNames) {
			Map<String, Object> values = data.preferences().getOrDefault(name, Map.of());
			if (!replace(context.getSharedPreferences(name, Context.MODE_PRIVATE), values)) {
				throw new BackupException(BackupException.Code.RESTORE_FAILED,
						"Unable to restore application preferences");
			}
		}

		for (String name : secureNames) {
			LogicalSecureStore store = secureStores.open(name);
			if (store == null) throw secureUnavailable();
			Map<String, String> values = data.securePreferences().getOrDefault(name, Map.of());
			if (!store.replace(values)) throw secureUnavailable();
		}
	}

	public Set<String> secureStoreNames() throws BackupException {
		Set<String> names = new LinkedHashSet<>();
		Set<String> registered = secureStores.registeredNames();
		for (File file : preferenceFiles()) {
			String name = preferenceName(file);
			if (registered.contains(name) || isEncryptedPreferenceFile(file)) names.add(name);
		}
		return names;
	}

	public static boolean containsEncryptedPreferenceKeyset(byte[] bytes) {
		String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		return text.contains(KEY_KEYSET) || text.contains(VALUE_KEYSET);
	}

	private boolean isEncryptedPreferenceFile(File file) throws BackupException {
		byte[] bytes = new byte[(int) Math.min(file.length(), DETECTION_BYTES)];
		try (FileInputStream input = new FileInputStream(file)) {
			int offset = 0;
			while (offset < bytes.length) {
				int read = input.read(bytes, offset, bytes.length - offset);
				if (read < 0) break;
				offset += read;
			}
			return containsEncryptedPreferenceKeyset(
					(offset == bytes.length) ? bytes : Arrays.copyOf(bytes, offset));
		} catch (IOException ex) {
			throw new BackupException(BackupException.Code.INVALID_FORMAT,
					"Unable to inspect application preferences", ex);
		}
	}

	private Set<String> preferenceFileNames() {
		Set<String> names = new LinkedHashSet<>();
		for (File file : preferenceFiles()) names.add(preferenceName(file));
		return names;
	}

	private File[] preferenceFiles() {
		File parent = PrefUtils.getSharedPrefsFile(context, "fermata").getParentFile();
		File[] files = (parent == null) ? null : parent.listFiles(file ->
				file.isFile() && file.getName().endsWith(".xml"));
		return (files == null) ? new File[0] : files;
	}

	private static String preferenceName(File file) {
		String name = file.getName();
		return name.substring(0, name.length() - 4);
	}

	private static Map<String, Object> copySupported(Map<String, ?> source)
			throws BackupException {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, ?> entry : source.entrySet()) {
			Object value = entry.getValue();
			if ((value instanceof Boolean) || (value instanceof Float) ||
					(value instanceof Integer) || (value instanceof Long) ||
					(value instanceof String)) {
				result.put(entry.getKey(), value);
			} else if (value instanceof Set<?> set) {
				LinkedHashSet<String> strings = new LinkedHashSet<>();
				for (Object item : set) {
					if (!(item instanceof String text)) throw unsupported();
					strings.add(text);
				}
				result.put(entry.getKey(), Set.copyOf(strings));
			} else {
				throw unsupported();
			}
		}
		return Map.copyOf(result);
	}

	@SuppressWarnings("unchecked")
	private static boolean replace(SharedPreferences preferences, Map<String, Object> values)
			throws BackupException {
		SharedPreferences.Editor edit = preferences.edit().clear();
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Boolean v) edit.putBoolean(key, v);
			else if (value instanceof Float v) edit.putFloat(key, v);
			else if (value instanceof Integer v) edit.putInt(key, v);
			else if (value instanceof Long v) edit.putLong(key, v);
			else if (value instanceof String v) edit.putString(key, v);
			else if (value instanceof Set<?> v) edit.putStringSet(key, (Set<String>) v);
			else throw unsupported();
		}
		return edit.commit();
	}

	private static void validateName(String name) throws BackupException {
		if ((name == null) || !name.matches("[A-Za-z0-9_.-]{1,160}")) {
			throw new BackupException(BackupException.Code.INVALID_FORMAT,
					"Invalid preference store name");
		}
	}

	private static BackupException unsupported() {
		return new BackupException(BackupException.Code.INVALID_FORMAT,
				"Unsupported preference value");
	}

	private static BackupException secureUnavailable() {
		return new BackupException(BackupException.Code.SECURE_STORAGE_UNAVAILABLE,
				"Secure storage is unavailable");
	}

	interface SecureStoreAccess {
		Set<String> registeredNames();

		LogicalSecureStore open(String name);
	}

	interface LogicalSecureStore {
		Map<String, String> snapshot();

		boolean replace(Map<String, String> values);
	}

	private static final class AndroidSecureStoreAccess implements SecureStoreAccess {
		private final Context context;

		AndroidSecureStoreAccess(Context context) {
			this.context = context;
		}

		@Override
		public Set<String> registeredNames() {
			return SecurePreferenceStore.getRegisteredStoreNames(context);
		}

		@Override
		public LogicalSecureStore open(String name) {
			SecurePreferenceStore store = SecurePreferenceStore.open(context, name);
			if (store == null) return null;
			return new LogicalSecureStore() {
				@Override
				public Map<String, String> snapshot() {
					return store.snapshot();
				}

				@Override
				public boolean replace(Map<String, String> values) {
					return store.replace(values);
				}
			};
		}
	}
}
