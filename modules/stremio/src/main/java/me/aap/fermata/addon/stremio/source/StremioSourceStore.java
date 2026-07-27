package me.aap.fermata.addon.stremio.source;

import java.util.concurrent.CompletableFuture;

/** Atomic persistence boundary used by the source-management domain. */
public interface StremioSourceStore {
	CompletableFuture<StremioSourceSnapshot> load();

	CompletableFuture<Void> commit(
			StremioSourceSnapshot expected, StremioSourceSnapshot replacement);
}
