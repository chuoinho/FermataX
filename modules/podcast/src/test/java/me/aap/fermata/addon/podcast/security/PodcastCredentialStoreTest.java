package me.aap.fermata.addon.podcast.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import me.aap.fermata.addon.podcast.net.PodcastErrorCode;
import me.aap.fermata.addon.podcast.net.PodcastException;

public class PodcastCredentialStoreTest {
	@Test
	public void roundTripsAndRemovesPrivateCredential() throws Exception {
		MemoryStore memory = new MemoryStore();
		PodcastCredentialStore store = new PodcastCredentialStore(memory);
		PodcastCredential credential = new PodcastCredential(
				"https://example.test/feed?token=secret", "driver", "password");

		store.save("feed:key", credential);
		PodcastCredential restored = store.load("feed:key");

		assertEquals(credential.getFeedUrl(), restored.getFeedUrl());
		assertEquals("driver", restored.getUsername());
		assertEquals("password", restored.getPassword());
		store.remove("feed:key");
		assertNull(store.load("feed:key"));
	}

	@Test
	public void unavailableStoreFailsClosedForPrivateData() {
		PodcastCredentialStore store = new PodcastCredentialStore((PodcastCredentialStore.Store) null);
		assertFalse(store.isAvailable());
		PodcastException error = assertThrows(PodcastException.class,
				() -> store.save("feed:key", new PodcastCredential("https://example.test", null, null)));
		assertEquals(PodcastErrorCode.SECURE_STORAGE_UNAVAILABLE, error.getCode());
	}

	private static final class MemoryStore implements PodcastCredentialStore.Store {
		private final Map<String, String> values = new HashMap<>();

		@Override
		public String get(String key) {
			return values.get(key);
		}

		@Override
		public boolean put(String reference, PodcastCredential credential) {
			values.put("url#" + reference, credential.getFeedUrl());
			if (credential.hasBasicAuth()) {
				values.put("user#" + reference, credential.getUsername());
				values.put("password#" + reference, credential.getPassword());
			}
			return true;
		}

		@Override
		public void remove(String reference) {
			values.remove("url#" + reference);
			values.remove("user#" + reference);
			values.remove("password#" + reference);
		}
	}
}
