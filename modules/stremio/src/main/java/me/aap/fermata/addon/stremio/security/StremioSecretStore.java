package me.aap.fermata.addon.stremio.security;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import me.aap.fermata.security.SecurePreferenceStore;

/** Encrypted source-secret store. Unavailable encryption always fails closed. */
public final class StremioSecretStore {
	private static final String PREFS = "stremio_source_secrets";
	private static final String URL = "transport_url#";
	private static final String TOKEN = "configuration_token#";
	private final Store store;

	public StremioSecretStore(Context context) {
		this(open(context));
	}

	StremioSecretStore(@Nullable Store store) {
		this.store = store;
	}

	public boolean isAvailable() {
		return store != null;
	}

	public void save(String sourceUuid, StremioSourceSecret secret) {
		String id = requireUuid(sourceUuid);
		if (!requireStore().put(id, Objects.requireNonNull(secret, "secret"))) throw unavailable();
	}

	@Nullable
	public StremioSourceSecret load(String sourceUuid) {
		String id = requireUuid(sourceUuid);
		Store target = requireStore();
		String url = target.get(URL + id);
		return (url == null) ? null : new StremioSourceSecret(url, target.get(TOKEN + id));
	}

	public void remove(String sourceUuid) {
		if (!requireStore().remove(requireUuid(sourceUuid))) throw unavailable();
	}

	private Store requireStore() {
		if (store == null) throw unavailable();
		return store;
	}

	private static SecureStorageUnavailableException unavailable() {
		return new SecureStorageUnavailableException();
	}

	private static String requireUuid(String value) {
		try {
			String canonical = UUID.fromString(value).toString();
			if (!canonical.equals(value)) throw new IllegalArgumentException();
			return canonical;
		} catch (NullPointerException | IllegalArgumentException ex) {
			throw new IllegalArgumentException("sourceUuid must be a canonical UUID", ex);
		}
	}

	@Nullable
	private static Store open(Context context) {
		SecurePreferenceStore preferences = SecurePreferenceStore.open(context, PREFS);
		return (preferences == null) ? null : new EncryptedPreferencesStore(preferences);
	}

	interface Store {
		@Nullable String get(String key);

		boolean put(String sourceUuid, StremioSourceSecret secret);

		boolean remove(String sourceUuid);
	}

	private static final class EncryptedPreferencesStore implements Store {
		private final SecurePreferenceStore preferences;

		EncryptedPreferencesStore(SecurePreferenceStore preferences) {
			this.preferences = preferences;
		}

		@Override
		public String get(String key) {
			return preferences.getString(key);
		}

		@Override
		public boolean put(String sourceUuid, StremioSourceSecret secret) {
			Map<String, String> values = new HashMap<>();
			values.put(URL + sourceUuid, secret.transportUrl());
			String token = secret.configurationToken();
			return (token == null) ? preferences.update(values, TOKEN + sourceUuid) :
					preferences.update(withToken(values, sourceUuid, token));
		}

		@Override
		public boolean remove(String sourceUuid) {
			return preferences.update(Map.of(), URL + sourceUuid, TOKEN + sourceUuid);
		}

		private static Map<String, String> withToken(
				Map<String, String> values, String sourceUuid, String token) {
			values.put(TOKEN + sourceUuid, token);
			return values;
		}
	}
}
