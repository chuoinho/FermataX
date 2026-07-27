package me.aap.fermata.addon.stremio.playback;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;

public interface ProviderStreamCall extends StremioCall<List<StremioStream>> {
	CompletableFuture<List<StremioStream>> response();

	@Override
	default CompletableFuture<List<StremioStream>> completion() {
		return response();
	}

	void cancel();
}
