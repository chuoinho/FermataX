package me.aap.fermata.addon.podcast.download;

import java.io.File;

public record PodcastDownloadResult(File file, long bytes, long totalBytes,
		String etag, String lastModified) {
}
