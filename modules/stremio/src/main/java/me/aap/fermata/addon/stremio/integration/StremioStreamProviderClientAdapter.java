package me.aap.fermata.addon.stremio.integration;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.playback.ProviderStreamCall;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.playback.StreamProviderClient;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;
import me.aap.fermata.addon.stremio.protocol.response.StremioResponseParser;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;

/** Parses provider stream responses while preserving transport cancellation. */
public final class StremioStreamProviderClientAdapter implements StreamProviderClient {
	private final StremioProtocolClient client;

	public StremioStreamProviderClientAdapter(StremioProtocolClient client) {
		this.client = Objects.requireNonNull(client, "client");
	}

	@Override
	public ProviderStreamCall fetch(StreamProvider provider, StreamAggregationRequest request) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(request, "request");
		if (!provider.enabled()) return failed();
		StremioRequest protocolRequest = new StremioRequest(
				"stream", request.type(), provider.requestIdOr(request.videoId()));
		StremioProtocolClient.ProtocolCall call = client.fetch(provider.sourceUuid(),
				provider.addonId(), protocolRequest, null);
		CompletableFuture<List<StremioStream>> response = call.response().thenApply(payload ->
				StremioResponseParser.parseStreams(payload.body()).streams()).handle((value, error) -> {
			if (error != null) throw StremioIntegrationException.redactResponseFailure(error);
			return value;
		});
		return new ProviderStreamCall() {
			@Override
			public CompletableFuture<List<StremioStream>> response() {
				return response;
			}

			@Override
			public void cancel() {
				call.cancel();
			}
		};
	}

	private static ProviderStreamCall failed() {
		CompletableFuture<List<StremioStream>> response = StremioFutures.failedFuture(
				new StremioIntegrationException(
						StremioIntegrationException.Code.SOURCE_DISABLED, false));
		return new ProviderStreamCall() {
			@Override
			public CompletableFuture<List<StremioStream>> response() {
				return response;
			}

			@Override
			public void cancel() {
			}
		};
	}
}
