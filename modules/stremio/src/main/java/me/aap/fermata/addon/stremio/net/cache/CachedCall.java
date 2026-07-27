package me.aap.fermata.addon.stremio.net.cache;

import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;

public interface CachedCall extends StremioCall<CachedResponse> {
	CompletableFuture<CachedResponse> response();

	@Override
	default CompletableFuture<CachedResponse> completion() {
		return response();
	}

	void cancel();
}
