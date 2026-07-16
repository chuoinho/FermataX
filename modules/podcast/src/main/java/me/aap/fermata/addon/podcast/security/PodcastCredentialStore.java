package me.aap.fermata.addon.podcast.security;

import android.content.Context;

import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.security.SecurePreferenceStore;
import me.aap.fermata.addon.podcast.net.PodcastErrorCode;
import me.aap.fermata.addon.podcast.net.PodcastException;

public final class PodcastCredentialStore {
	private static final String PREFS = "podcast_credentials";
	private static final String URL = "url#";
	private static final String USER = "user#";
	private static final String PASSWORD = "password#";
	private final Store store;

	public PodcastCredentialStore(Context context) {
		this(open(context));
	}

	PodcastCredentialStore(@Nullable Store store) {
		this.store = store;
	}

	public boolean isAvailable() {
		return store != null;
	}

	public void save(String reference, PodcastCredential credential) throws PodcastException {
		Store target = requireStore();
		if (!target.put(reference, credential)) {
			throw unavailable();
		}
	}

	@Nullable
	public PodcastCredential load(@Nullable String reference) throws PodcastException {
		if (reference == null) return null;
		Store target = requireStore();
		String url = target.get(URL + reference);
		if (url == null) return null;
		return new PodcastCredential(url, target.get(USER + reference),
				target.get(PASSWORD + reference));
	}

	public void remove(@Nullable String reference) {
		if ((reference != null) && (store != null)) store.remove(reference);
	}

	private Store requireStore() throws PodcastException {
		if (store == null) throw unavailable();
		return store;
	}

	private static PodcastException unavailable() {
		return new PodcastException(PodcastErrorCode.SECURE_STORAGE_UNAVAILABLE,
				"Secure storage is unavailable for this private podcast");
	}

	@Nullable
	private static Store open(Context context) {
		SecurePreferenceStore preferences = SecurePreferenceStore.open(context, PREFS);
		return (preferences == null) ? null : new SharedPreferencesStore(preferences);
	}

	interface Store {
		@Nullable String get(String key);
		boolean put(String reference, PodcastCredential credential);
		void remove(String reference);
	}

	private static final class SharedPreferencesStore implements Store {
		private final SecurePreferenceStore preferences;

		SharedPreferencesStore(SecurePreferenceStore preferences) {
			this.preferences = preferences;
		}

		@Nullable
		@Override
		public String get(String key) {
			return preferences.getString(key);
		}

		@Override
		public boolean put(String reference, PodcastCredential credential) {
			Map<String, String> values = new HashMap<>();
			values.put(URL + reference, credential.getFeedUrl());
			if (credential.hasBasicAuth()) {
				values.put(USER + reference, credential.getUsername());
				values.put(PASSWORD + reference, credential.getPassword());
				return preferences.update(values);
			} else {
				return preferences.update(values, USER + reference, PASSWORD + reference);
			}
		}

		@Override
		public void remove(String reference) {
			preferences.remove(URL + reference, USER + reference, PASSWORD + reference);
		}
	}
}
