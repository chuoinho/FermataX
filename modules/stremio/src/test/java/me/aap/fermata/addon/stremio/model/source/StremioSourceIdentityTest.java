package me.aap.fermata.addon.stremio.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class StremioSourceIdentityTest {
	@Test
	public void createsIndependentRandomSourceUuids() {
		StremioSourceIdentity first = StremioSourceIdentity.create(
				"https://catalog.example.invalid/manifest.json?member=alpha");
		StremioSourceIdentity second = StremioSourceIdentity.create(
				"https://catalog.example.invalid/manifest.json?member=alpha");

		assertNotEquals(first.sourceUuid(), second.sourceUuid());
		assertEquals(first.transportFingerprint(), second.transportFingerprint());
	}

	@Test
	public void urlAndTokenEditsPreserveSourceUuid() {
		StremioSourceIdentity source = StremioSourceIdentity.create(
				"https://catalog.example.invalid/old/manifest.json?token=first");
		StremioSourceIdentity edited = source.withTransport(
				"https://catalog.example.invalid/new/manifest.json?token=second");

		assertEquals(source.sourceUuid(), edited.sourceUuid());
		assertNotEquals(source.transportFingerprint(), edited.transportFingerprint());
	}

	@Test
	public void fingerprintNormalizesHostDefaultPortAndFragment() {
		String first = TransportFingerprint.create(
				" HTTPS://Catalog.Example.Invalid:443/addon/../manifest.json?member=alpha#screen ");
		String second = TransportFingerprint.create(
				"https://catalog.example.invalid/manifest.json?member=alpha");
		assertEquals(first, second);
	}

	@Test
	public void rejectsNonCanonicalOrInvalidRestoredIdentity() {
		String fingerprint = TransportFingerprint.create("https://catalog.example.invalid/manifest.json");
		assertThrows(IllegalArgumentException.class,
				() -> StremioSourceIdentity.restore("NOT-A-UUID", fingerprint));
		assertThrows(IllegalArgumentException.class,
				() -> StremioSourceIdentity.restore(
						"123E4567-E89B-12D3-A456-426614174000", fingerprint));
		assertThrows(IllegalArgumentException.class,
				() -> StremioSourceIdentity.restore(
						"123e4567-e89b-12d3-a456-426614174000", "raw-url-is-not-a-fingerprint"));
	}
}
