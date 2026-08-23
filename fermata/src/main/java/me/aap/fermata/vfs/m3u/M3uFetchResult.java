package me.aap.fermata.vfs.m3u;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.net.http.HttpFileDownloader.Status;

public record M3uFetchResult(@NonNull State state, @NonNull Status status,
		@Nullable Throwable fallbackFailure) {
	public enum State {
		FRESH_CACHE,
		NOT_MODIFIED,
		UPDATED,
		STALE_FALLBACK
	}
}
