package me.aap.fermata.addon.stremio.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SecretTaintDetectorTest {
	@Test
	public void detectsKnownSecretInArbitraryNestedJsonField() {
		String json = """
				{"futureField":{"unrecognized":[{"value":"member-secret-42"}]}}
				""";
		assertTrue(SecretTaintDetector.isTainted(json, Set.of("member-secret-42")));
	}

	@Test
	public void detectsEncodedKnownSecrets() {
		assertTrue(SecretTaintDetector.isTainted(
				"https://catalog.example.invalid/config/member-secret-42", Set.of("member-secret-42")));
		assertTrue(SecretTaintDetector.isTainted(
				"https://catalog.example.invalid/config?value=member+secret+42", Set.of("member secret 42")));
		assertTrue(SecretTaintDetector.isTainted(
				"bWVtYmVyLXNlY3JldC00Mg==", Set.of("member-secret-42")));
	}

	@Test
	public void detectsSensitiveJsonKeysAndSecretBearingUrls() {
		assertTrue(SecretTaintDetector.isTainted("{\"future_api_token\":\"fictional\"}"));
		assertTrue(SecretTaintDetector.isTainted(
				"{\"logo\":\"https://user:pass@images.example.invalid/a.png\"}"));
		assertTrue(SecretTaintDetector.isTainted(
				"Visit https://images.example.invalid/a.png?size=large for artwork"));
		assertTrue(SecretTaintDetector.isTainted(
				"{\"poster\":\"https://images.example.invalid/token/private/a.png\"}"));
	}

	@Test
	public void detectsCredentialAssignmentsOutsideJsonWithoutRejectingOrdinaryTitles() {
		assertTrue(SecretTaintDetector.isTainted("token=durable-secret"));
		assertTrue(SecretTaintDetector.isTainted(
				"Authorization=Bearer abcdefghijklmnop"));
		assertTrue(SecretTaintDetector.isTainted("use Bearer abcdefghijklmnop"));
		assertFalse(SecretTaintDetector.isTainted("The Secret: A Documentary"));
	}

	@Test
	public void allowsJsonWithoutSecretKeysOrSecretBearingUrls() {
		String safe = """
				{"id":"org.example.catalog","name":"Example","nested":{"enabled":true},
				 "logo":"https://images.example.invalid/logo.png"}
				""";
		assertFalse(SecretTaintDetector.isTainted(safe, Set.of("not-present")));
		assertFalse(SecretTaintDetector.isTainted(
				"https://images.metahub.space/poster/medium/tt0133093/img"));
	}

	@Test
	public void allowsPublicCdnArtworkIdsButStillRejectsCredentialPaths() {
		String publicArtwork =
				"{\"logo\":\"https://play-lh.googleusercontent.com/" +
						"TBRwjS_qfJCSj1m7zZB93FnpJM5fSpMA_wUlFDLxWAb45T9RmwBvQd5cWR5viJJOhkI\"}";
		assertFalse(SecretTaintDetector.isManifestTainted(publicArtwork));
		assertTrue(SecretTaintDetector.isTainted(publicArtwork));
		assertTrue(SecretTaintDetector.isManifestTainted(
				"{\"logo\":\"https://images.example.invalid/token/private/logo.png\"}"));
		assertTrue(SecretTaintDetector.isManifestTainted(
				"{\"endpoint\":\"https://images.example.invalid/" +
						"TBRwjS_qfJCSj1m7zZB93FnpJM5fSpMA_wUlFDLxWAb45T9RmwBvQd5cWR5viJJOhkI\"}"));
	}

	@Test
	public void extractsRawAndDecodedCredentialsFromSensitiveUrlComponents() {
		var secrets = SecretTaintDetector.extractTransportSecrets(
				"https://member%2D42:pa%24%24word@provider.invalid/catalog/" +
				"token/aB12%2Dcd34/key=qR12%2Bxy34/manifest.json?key=zX98%2Dwv76&size=large");

		assertTrue(SecretTaintDetector.isTainted("member-42", secrets));
		assertTrue(SecretTaintDetector.isTainted("pa$$word", secrets));
		assertTrue(SecretTaintDetector.isTainted("aB12-cd34", secrets));
		assertTrue(SecretTaintDetector.isTainted("qR12+xy34", secrets));
		assertTrue(SecretTaintDetector.isTainted("qR12%2Bxy34", secrets));
		assertTrue(SecretTaintDetector.isTainted("zX98-wv76", secrets));
		assertTrue(SecretTaintDetector.isTainted("zX98%2Dwv76", secrets));
	}

	@Test
	public void extractsOpaqueCredentialPathWithoutTreatingOrdinaryUrlWordsAsSecrets() {
		var secrets = SecretTaintDetector.extractTransportSecrets(
				"https://media.example.invalid/catalog/Xy12Za34/manifest.json?size=large");

		assertTrue(SecretTaintDetector.isTainted("Xy12Za34", secrets));
		assertFalse(SecretTaintDetector.isTainted("media.example.invalid", secrets));
		assertFalse(SecretTaintDetector.isTainted("catalog", secrets));
		assertFalse(SecretTaintDetector.isTainted("manifest", secrets));
		assertFalse(SecretTaintDetector.isTainted("large", secrets));

		var ordinary = SecretTaintDetector.extractTransportSecrets(
				"https://media.example.invalid/catalog/release2026/token/manifest.json" +
						"?size=large&type=movie&language=en");
		assertFalse(SecretTaintDetector.isTainted("release2026", ordinary));
		assertFalse(SecretTaintDetector.isTainted("manifest.json", ordinary));
		assertFalse(SecretTaintDetector.isTainted("movie", ordinary));
		assertFalse(SecretTaintDetector.isTainted("en", ordinary));
	}
}
