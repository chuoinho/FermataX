package me.aap.fermata.addon.stremio.source;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;

public class StremioSecretReferenceTest {
	private static final String SOURCE_ID = "10000000-0000-0000-0000-000000000001";
	private static final String SECRET_ID = "20000000-0000-0000-0000-000000000002";

	@Test
	public void canonicalReferenceRoundTrips() {
		String reference = StremioSecretReference.create(SECRET_ID);
		assertEquals("secure:stremio-source:" + SECRET_ID, reference);
		assertEquals(SECRET_ID, StremioSecretReference.resolve(source(reference)));
	}

	@Test
	public void legacyReferenceStillResolves() {
		assertEquals(SECRET_ID,
				StremioSecretReference.resolve(source("secure:" + SECRET_ID)));
	}

	@Test
	public void absentOrOpaqueReferenceFallsBackToSourceIdentity() {
		assertEquals(SOURCE_ID, StremioSecretReference.resolve(source(null)));
		assertEquals(SOURCE_ID, StremioSecretReference.resolve(source("legacy-secret")));
	}

	private static StremioSourceRecord source(String secretRef) {
		return new StremioSourceRecord(SOURCE_ID, "fingerprint", "addon", "Provider", "1.0",
				"https://provider.example.invalid/manifest.json", secretRef, true, 0, "{}",
				null, null, 1L, 1L, null, 1L, 1L, false, false);
	}
}
