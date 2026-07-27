package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.List;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptorFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackHeaderRegistry;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ProxyHeaders;
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;

public class StremioItemGatewayAdapterIdentityTest {
	@Test
	public void stableIdentitySelectsExactSourceRegardlessOfOrderOrEnabledState() {
		StreamAggregationRequest request = request("source-b");
		List<StremioSourceRecord> reordered = List.of(
				source("source-c", true, 0),
				source("source-b", false, 1),
				source("source-a", true, 2));

		assertEquals("source-b", StremioItemGatewayAdapter.findSourceUuid(
				request, reordered));
	}

	@Test
	public void unknownStableIdentityNeverFallsBackToAnotherProvider() {
		assertNull(StremioItemGatewayAdapter.findSourceUuid(request("removed-source"),
				List.of(source("source-a", true, 0), source("source-b", true, 1))));
	}

	@Test
	public void refreshKeepsSemanticChoiceAndNeverSilentlySwitchesQuality() {
		StreamAggregationRequest request = request("source-b");
		StreamProvider provider = new StreamProvider(
				"source-b", "fixture", "Fixture", 0, true);
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(
				new PlaybackHeaderRegistry.HeaderStore());
		PlaybackDescriptor selected = factory.create(request, provider,
				stream("1080p", "https://cdn.invalid/old.m3u8?token=old"), 1_000L);
		PlaybackDescriptor refreshed720 = factory.create(request, provider,
				stream("720p", "https://cdn.invalid/720.m3u8?token=new"), 2_000L);
		PlaybackDescriptor refreshed1080 = factory.create(request, provider,
				stream("1080p", "https://cdn.invalid/1080.m3u8?token=new"), 2_000L);

		assertEquals(refreshed1080, StremioItemGatewayAdapter.selectRefreshedDescriptor(
				List.of(refreshed720, refreshed1080), selected.refreshRequest()));
		assertThrows(IllegalStateException.class,
				() -> StremioItemGatewayAdapter.selectRefreshedDescriptor(
						List.of(refreshed720), selected.refreshRequest()));
	}

	private static StremioStream stream(String name, String url) {
		return new StremioStream(name, "Fixture", null, new DirectStreamTarget(url),
				new StreamBehaviorHints(false, null, null, null, ProxyHeaders.EMPTY));
	}

	private static StreamAggregationRequest request(String sourceUuid) {
		return new StreamAggregationRequest(StremioPlaybackIdentity.scoped(
				sourceUuid, "movie", "movie-a", "video-a"), "movie", "movie-a", "video-a",
				new StremioPlaybackMetadata("Movie A", null, 100_000L));
	}

	private static StremioSourceRecord source(String sourceUuid, boolean enabled, int position) {
		return new StremioSourceRecord(sourceUuid, "fingerprint-" + sourceUuid,
				"org.fixture." + sourceUuid, sourceUuid, "1.0", "/manifest.json", null,
				enabled, position, "{}", null, null, 0L, 0L, null, 0L, 0L);
	}
}
