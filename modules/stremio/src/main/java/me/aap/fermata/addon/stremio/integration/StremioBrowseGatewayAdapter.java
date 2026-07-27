package me.aap.fermata.addon.stremio.integration;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

import me.aap.fermata.addon.stremio.browse.BrowseGatewayException;
import me.aap.fermata.addon.stremio.browse.BrowsePayload;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.StremioBrowseGateway;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

/** Browse adapter that never passes provider transport material into the browse domain. */
public final class StremioBrowseGatewayAdapter implements StremioBrowseGateway {
	private final StremioProtocolClient client;

	public StremioBrowseGatewayAdapter(StremioProtocolClient client) {
		this.client = Objects.requireNonNull(client, "client");
	}

	@Override
	public CompletionStage<BrowsePayload> get(BrowseProvider provider,
			StremioRequest request, RequestGeneration.Token generation) {
		Objects.requireNonNull(provider, "provider");
		StremioProtocolClient.ProtocolCall call = client.fetch(provider.sourceUuid(),
				provider.manifest().id(), request, generation);
		return call.response().handle((payload, error) -> {
			if (error != null) throw browseFailure(error);
			return new BrowsePayload(payload.body(), payload.stale());
		});
	}

	private static BrowseGatewayException browseFailure(Throwable error) {
		Throwable cause = unwrap(error);
		if (cause instanceof StremioIntegrationException integration) {
			return new BrowseGatewayException(
					"Stremio browse failed: " + integration.code(), integration.retryable());
		}
		return new BrowseGatewayException("Stremio browse failed", true);
	}

	private static Throwable unwrap(Throwable error) {
		while ((error instanceof java.util.concurrent.CompletionException ||
				error instanceof java.util.concurrent.ExecutionException) &&
				error.getCause() != null) error = error.getCause();
		return error;
	}
}
