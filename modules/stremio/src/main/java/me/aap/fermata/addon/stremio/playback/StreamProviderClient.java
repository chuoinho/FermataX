package me.aap.fermata.addon.stremio.playback;

/** Starts a non-blocking provider request. Network policy remains the client's responsibility. */
@FunctionalInterface
public interface StreamProviderClient {
	ProviderStreamCall fetch(StreamProvider provider, StreamAggregationRequest request);
}
