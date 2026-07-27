package me.aap.fermata.addon.stremio.net.http;

import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;

public interface HttpCall extends StremioCall<HttpResponseData> {
	CompletableFuture<HttpResponseData> response();

	@Override
	default CompletableFuture<HttpResponseData> completion() {
		return response();
	}

	void cancel();
}
