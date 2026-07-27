package me.aap.fermata.addon.stremio.subtitle;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;
import me.aap.fermata.addon.stremio.net.RequestGeneration;

public interface SubtitleProvider {
	String providerKey();

	SubtitleProviderCall load(RequestGeneration.Token generation);

	interface SubtitleProviderCall extends StremioCall<List<SubtitleCandidate>> {
		CompletableFuture<List<SubtitleCandidate>> response();

		@Override
		default CompletableFuture<List<SubtitleCandidate>> completion() {
			return response();
		}

		void cancel();
	}
}
