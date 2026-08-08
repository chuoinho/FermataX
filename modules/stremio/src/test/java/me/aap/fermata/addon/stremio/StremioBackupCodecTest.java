package me.aap.fermata.addon.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import org.junit.Test;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;

public class StremioBackupCodecTest {
	@Test
	public void orderedSourceManifestPolicyAndSecretReferenceRoundTrip() throws Exception {
		StremioSourceRecord source = new StremioSourceRecord(
				"8f86f145-bc4e-4ca0-94ea-f4ce97df0280", "fingerprint", "addon.id", "Addon",
				"1.2.3", "https://provider.example/manifest.json", "secret-ref", true, 0,
				"{\"id\":\"addon.id\"}", "etag", "modified", 10, 20, null, 30, 40,
				true, true);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeBoolean(true);
			output.writeInt(1);
			StremioAddon.writeSource(output, source);
		}

		StremioSourceSnapshot restored = StremioAddon.readSources(1, bytes.toByteArray());

		assertTrue(restored.cinemetaInstallHandled());
		assertEquals(1, restored.sources().size());
		assertEquals(source, restored.sources().get(0));
	}
}
