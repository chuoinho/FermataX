package me.aap.fermata.addon.stremio.net.http;

import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;

public interface TransportCall extends StremioCall<TransportResponse> {
	CompletableFuture<TransportResponse> response();

	@Override
	default CompletableFuture<TransportResponse> completion() {
		return response();
	}

	void cancel();
}
