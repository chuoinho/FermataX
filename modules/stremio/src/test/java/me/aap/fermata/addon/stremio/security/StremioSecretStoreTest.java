package me.aap.fermata.addon.stremio.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class StremioSecretStoreTest {
	private static final String SOURCE = "123e4567-e89b-12d3-a456-426614174000";

	@Test
	public void roundTripsEditsAndRemovesEncryptedSourceMaterial() {
		MemoryStore memory = new MemoryStore();
		StremioSecretStore store = new StremioSecretStore(memory);
		StremioSourceSecret first = new StremioSourceSecret(
				"https://catalog.example.invalid/manifest.json?token=first", "first-token");

		store.save(SOURCE, first);
		assertEquals(first.transportUrl(), store.load(SOURCE).transportUrl());
		assertEquals("first-token", store.load(SOURCE).configurationToken());

		StremioSourceSecret edited = new StremioSourceSecret(
				"https://catalog.example.invalid/v2/manifest.json?token=second", null);
		store.save(SOURCE, edited);
		assertEquals(edited.transportUrl(), store.load(SOURCE).transportUrl());
		assertNull(store.load(SOURCE).configurationToken());

		store.remove(SOURCE);
		assertNull(store.load(SOURCE));
	}

	@Test
	public void unavailableEncryptionFailsClosedForReadWriteAndRemove() {
		StremioSecretStore store = new StremioSecretStore((StremioSecretStore.Store) null);
		assertFalse(store.isAvailable());
		assertThrows(SecureStorageUnavailableException.class, () -> store.load(SOURCE));
		assertThrows(SecureStorageUnavailableException.class, () -> store.save(SOURCE,
				new StremioSourceSecret("https://catalog.example.invalid/manifest.json", null)));
		assertThrows(SecureStorageUnavailableException.class, () -> store.remove(SOURCE));
	}

	@Test
	public void failedEncryptedCommitDoesNotReportSuccess() {
		MemoryStore memory = new MemoryStore();
		memory.acceptWrites = false;
		StremioSecretStore store = new StremioSecretStore(memory);
		assertThrows(SecureStorageUnavailableException.class, () -> store.save(SOURCE,
				new StremioSourceSecret("https://catalog.example.invalid/manifest.json", null)));
	}

	@Test
	public void secretObjectNeverRendersItsValues() {
		StremioSourceSecret secret = new StremioSourceSecret(
				"https://catalog.example.invalid/manifest.json?token=fictional", "fictional-token");
		assertEquals("StremioSourceSecret[redacted]", secret.toString());
		assertFalse(secret.toString().contains("fictional"));
	}

	private static final class MemoryStore implements StremioSecretStore.Store {
		private final Map<String, String> values = new HashMap<>();
		private boolean acceptWrites = true;

		@Override
		public String get(String key) {
			return values.get(key);
		}

		@Override
		public boolean put(String sourceUuid, StremioSourceSecret secret) {
			if (!acceptWrites) return false;
			values.put("transport_url#" + sourceUuid, secret.transportUrl());
			if (secret.configurationToken() == null) {
				values.remove("configuration_token#" + sourceUuid);
			} else {
				values.put("configuration_token#" + sourceUuid, secret.configurationToken());
			}
			return true;
		}

		@Override
		public boolean remove(String sourceUuid) {
			values.remove("transport_url#" + sourceUuid);
			values.remove("configuration_token#" + sourceUuid);
			return true;
		}
	}
}
