package me.aap.fermata.addon.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class ExternalPlaybackRequestTest {
	@Test
	public void preservesMetadataAndRedactsTargetFromToString() {
		ExternalNavigationPolicy policy = uri -> {};
		ExternalPlaybackRequest request = new ExternalPlaybackRequest("catalog:item", "Exact title",
				"https://images.example/private-art.jpg", 98_765L,
				ExternalPlaybackTargetKind.EXTERNAL_HTTP,
				"https://provider.example/watch/private-token", policy);

		assertEquals("catalog:item", request.getContentId());
		assertEquals("Exact title", request.getTitle());
		assertEquals("https://images.example/private-art.jpg", request.getArtworkUri());
		assertEquals(98_765L, request.getDurationMillis());
		assertSame(policy, request.getNavigationPolicy());
		assertFalse(request.toString().contains("catalog:item"));
		assertFalse(request.toString().contains("private-token"));
		assertFalse(request.toString().contains("private-art"));
		assertFalse(request.toString().contains("Exact title"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUnboundExternalHttpTarget() {
		new ExternalPlaybackRequest("id", "title", "", 0L,
				ExternalPlaybackTargetKind.EXTERNAL_HTTP, "https://provider.example/watch");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonHttpExternalTarget() {
		new ExternalPlaybackRequest("id", "title", "", 0L,
				ExternalPlaybackTargetKind.EXTERNAL_HTTP, "file:///private/data");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsYoutubeUrlWhereVideoIdIsRequired() {
		new ExternalPlaybackRequest("id", "title", "", 0L,
				ExternalPlaybackTargetKind.YOUTUBE_ID,
				"https://youtube.com/watch?v=private");
	}
}
