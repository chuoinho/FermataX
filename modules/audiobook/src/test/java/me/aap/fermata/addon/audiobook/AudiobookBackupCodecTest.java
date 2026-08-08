package me.aap.fermata.addon.audiobook;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;

public class AudiobookBackupCodecTest {
	@Test
	public void portableSourceFieldsRoundTrip() throws Exception {
		AudiobookSource source = new AudiobookSource("source-id",
				AudiobookSourceType.AUDIOBOOKSHELF, "Books", "https://books.example",
				"credential-ref", 10, 20);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(1);
			AudiobookAddon.writeSource(output, source);
		}

		List<AudiobookSource> restored = AudiobookAddon.readSources(1, bytes.toByteArray());
		AudiobookSource actual = restored.get(0);

		assertEquals(1, restored.size());
		assertEquals(source.getId(), actual.getId());
		assertEquals(source.getType(), actual.getType());
		assertEquals(source.getName(), actual.getName());
		assertEquals(source.getEndpoint(), actual.getEndpoint());
		assertEquals(source.getCredentialRef(), actual.getCredentialRef());
		assertEquals(source.getCreatedMs(), actual.getCreatedMs());
		assertEquals(source.getUpdatedMs(), actual.getUpdatedMs());
	}
}
