package me.aap.fermata.backup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LegacyBackupReaderTest {
	@Test
	public void restoresNormalXmlAndSkipsKnownAndDetectedEncryptedStores() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes, UTF_8)) {
			entry(zip, "fermata.xml", "<map><boolean name=\"enabled\" value=\"true\"/>" +
					"<string name=\"title\">FermataX</string></map>");
			entry(zip, "xtream_credentials.xml", "<map><string name=\"cipher\">secret</string></map>");
			entry(zip, "future_secure_store.xml", "<map><string name=\"" +
					"__androidx_security_crypto_encrypted_prefs_key_keyset__\">keyset</string></map>");
		}

		LegacyBackupReader.Result result = new LegacyBackupReader().read(
				new ByteArrayInputStream(bytes.toByteArray()));

		assertEquals(2, result.skippedSecureStores());
		assertEquals(true, result.data().preferences().get("fermata").get("enabled"));
		assertEquals("FermataX", result.data().preferences().get("fermata").get("title"));
		assertFalse(result.data().preferences().containsKey("xtream_credentials"));
		assertFalse(result.data().preferences().containsKey("future_secure_store"));
	}

	@Test
	public void encryptedPreferenceMarkersAreRecognizedWithoutStoreNameKnowledge() {
		assertTrue(AndroidBackupStateStore.containsEncryptedPreferenceKeyset(
				("<string name=\"__androidx_security_crypto_encrypted_prefs_value_keyset__\">" +
						"value</string>").getBytes(UTF_8)));
	}

	private static void entry(ZipOutputStream zip, String name, String value) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(value.getBytes(UTF_8));
		zip.closeEntry();
	}
}
