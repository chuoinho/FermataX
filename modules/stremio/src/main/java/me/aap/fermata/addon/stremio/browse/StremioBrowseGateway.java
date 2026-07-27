package me.aap.fermata.addon.stremio.browse;

import java.util.concurrent.CompletionStage;

import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

public interface StremioBrowseGateway {
	CompletionStage<BrowsePayload> get(
			BrowseProvider provider, StremioRequest request, RequestGeneration.Token generation);
}
