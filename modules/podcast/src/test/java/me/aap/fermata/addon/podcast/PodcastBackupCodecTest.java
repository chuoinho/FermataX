package me.aap.fermata.addon.podcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.podcast.model.PodcastSubscription;

public class PodcastBackupCodecTest {
	@Test
	public void portableSubscriptionFieldsRoundTripAndRefreshCacheIsReset() throws Exception {
		PodcastSubscription source = new PodcastSubscription("feed-key", "https://redacted/feed",
				"feed-secret-ref", "Title", "Author", "Description", "https://redacted/art",
				"art-secret-ref", "https://site", "vi", true, "etag", "modified",
				10, 11, 12);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(1);
			PodcastAddon.writeSubscription(output, source);
		}

		List<PodcastSubscription> restored = PodcastAddon.readSubscriptions(1, bytes.toByteArray());
		PodcastSubscription actual = restored.get(0);

		assertEquals(1, restored.size());
		assertEquals(source.getFeedKey(), actual.getFeedKey());
		assertEquals(source.getCanonicalUrl(), actual.getCanonicalUrl());
		assertEquals(source.getCredentialRef(), actual.getCredentialRef());
		assertEquals(source.getTitle(), actual.getTitle());
		assertEquals(source.getAuthor(), actual.getAuthor());
		assertEquals(source.getDescription(), actual.getDescription());
		assertEquals(source.getArtworkUrl(), actual.getArtworkUrl());
		assertEquals(source.getArtworkCredentialRef(), actual.getArtworkCredentialRef());
		assertEquals(source.getWebsiteUrl(), actual.getWebsiteUrl());
		assertEquals(source.getLanguage(), actual.getLanguage());
		assertEquals(source.isExplicit(), actual.isExplicit());
		assertEquals(source.getSubscribedMs(), actual.getSubscribedMs());
		assertNull(actual.getEtag());
		assertNull(actual.getLastModified());
		assertEquals(0, actual.getLastCheckedMs());
		assertEquals(0, actual.getLastSuccessMs());
	}
}
