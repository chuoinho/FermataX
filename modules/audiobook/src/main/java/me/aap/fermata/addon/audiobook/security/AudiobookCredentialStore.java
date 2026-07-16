package me.aap.fermata.addon.audiobook.security;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.security.SecurePreferenceStore;

public final class AudiobookCredentialStore {
	private static final String ENDPOINT = "endpoint#";
	private static final String USERNAME = "username#";
	private static final String ACCESS = "access#";
	private static final String REFRESH = "refresh#";
	private static final String PASSWORD = "password#";
	@Nullable private final SecurePreferenceStore store;

	public AudiobookCredentialStore(Context context) {
		store = SecurePreferenceStore.open(context, "audiobook_credentials");
	}

	public boolean isAvailable() {
		return store != null;
	}

	public boolean save(String reference, AudiobookCredential credential) {
		if (store == null) return false;
		Map<String, String> values = new HashMap<>();
		values.put(ENDPOINT + reference, credential.endpoint());
		values.put(USERNAME + reference, credential.username());
		values.put(ACCESS + reference, credential.accessToken());
		if (credential.password() != null) values.put(PASSWORD + reference, credential.password());
		java.util.List<String> removals = new java.util.ArrayList<>(2);
		if (credential.password() == null) removals.add(PASSWORD + reference);
		if (credential.refreshToken() == null) removals.add(REFRESH + reference);
		else values.put(REFRESH + reference, credential.refreshToken());
		return store.update(values, removals.toArray(String[]::new));
	}

	@Nullable
	public AudiobookCredential load(@Nullable String reference) {
		if ((store == null) || (reference == null)) return null;
		String endpoint = store.getString(ENDPOINT + reference);
		String access = value(store.getString(ACCESS + reference));
		String password = store.getString(PASSWORD + reference);
		if ((endpoint == null) || (access.isEmpty() && (password == null))) return null;
		return new AudiobookCredential(endpoint, value(store.getString(USERNAME + reference)),
				access, store.getString(REFRESH + reference), password);
	}

	public void remove(@Nullable String reference) {
		if ((store == null) || (reference == null)) return;
		store.remove(ENDPOINT + reference, USERNAME + reference, ACCESS + reference,
				REFRESH + reference, PASSWORD + reference);
	}

	private static String value(String value) {
		return (value == null) ? "" : value;
	}
}
