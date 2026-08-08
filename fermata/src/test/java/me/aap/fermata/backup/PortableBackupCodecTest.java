package me.aap.fermata.backup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class PortableBackupCodecTest {
	private static final char[] PASSWORD = "correct-password".toCharArray();

	@Test
	public void roundTripPreservesTypedPreferencesSecretsAndSections() throws Exception {
		BackupData original = fixture();
		PortableBackupCodec.Decoded decoded = codec().decode(
				codec().encode(original, PASSWORD, 123_456L, 301, "2.0.1"), PASSWORD);

		assertEquals(123_456L, decoded.createdTimestamp());
		assertEquals(301, decoded.appVersionCode());
		assertEquals("2.0.1", decoded.appVersionName());
		assertEquals(original.preferences(), decoded.data().preferences());
		assertEquals(original.securePreferences(), decoded.data().securePreferences());
		assertEquals(original.sections().keySet(), decoded.data().sections().keySet());
		assertArrayEquals(original.sections().get("tv.sources").data(),
				decoded.data().sections().get("tv.sources").data());
	}

	@Test
	public void encryptedFileContainsNoPlaintextFixtureSecrets() throws Exception {
		byte[] encoded = codec().encode(fixture(), PASSWORD, 1, 301, "2.0.1");

		assertFalse(contains(encoded, "secret-user-issue5".getBytes(UTF_8)));
		assertFalse(contains(encoded, "secret-password-issue5".getBytes(UTF_8)));
		assertFalse(contains(encoded, "secret-token-issue5".getBytes(UTF_8)));
	}

	@Test
	public void wrongPasswordFailsAuthentication() throws Exception {
		byte[] encoded = codec().encode(fixture(), PASSWORD, 1, 301, "2.0.1");

		BackupException failure = assertThrows(BackupException.class,
				() -> codec().decode(encoded, "different-password".toCharArray()));
		assertEquals(BackupException.Code.AUTHENTICATION_FAILED, failure.getCode());
	}

	@Test
	public void ciphertextTamperingFailsAuthentication() throws Exception {
		byte[] encoded = codec().encode(fixture(), PASSWORD, 1, 301, "2.0.1");
		encoded[encoded.length - 1] ^= 0x01;

		BackupException failure = assertThrows(BackupException.class,
				() -> codec().decode(encoded, PASSWORD));
		assertEquals(BackupException.Code.AUTHENTICATION_FAILED, failure.getCode());
	}

	@Test
	public void unsupportedFileVersionFailsBeforeRestore() throws Exception {
		byte[] encoded = codec().encode(fixture(), PASSWORD, 1, 301, "2.0.1");
		int versionOffset = "FermataXBackup\0".getBytes(UTF_8).length;
		encoded[versionOffset + 3] = 2;

		BackupException failure = assertThrows(BackupException.class,
				() -> codec().decode(encoded, PASSWORD));
		assertEquals(BackupException.Code.UNSUPPORTED_VERSION, failure.getCode());
	}

	private static PortableBackupCodec codec() {
		return new PortableBackupCodec(new SecureRandom(), 100_000);
	}

	private static BackupData fixture() {
		Map<String, Object> normal = new LinkedHashMap<>();
		normal.put("boolean", true);
		normal.put("float", 1.25F);
		normal.put("integer", 7);
		normal.put("long", 9L);
		normal.put("string", "value");
		normal.put("set", Set.of("one", "two"));
		return new BackupData(Map.of("fermata", normal), Map.of(
				"xtream_credentials", Map.of(
						"username#1", "secret-user-issue5",
						"password#1", "secret-password-issue5"),
				"stremio_source_secrets", Map.of(
						"configuration_token#1", "secret-token-issue5")),
				Map.of("tv.sources", new BackupData.Section("tv.sources", 1,
						new byte[]{1, 2, 3, 4})));
	}

	private static boolean contains(byte[] source, byte[] target) {
		outer: for (int i = 0; i <= source.length - target.length; i++) {
			for (int j = 0; j < target.length; j++) {
				if (source[i + j] != target[j]) continue outer;
			}
			return true;
		}
		return false;
	}
}
