package me.aap.fermata.addon.podcast.download;

import android.content.Context;

import java.io.File;

import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;

public final class PodcastDownloadFiles {
	private PodcastDownloadFiles() {
	}

	public static File directory(Context context) {
		return new File(context.getFilesDir(), "podcast/downloads");
	}

	public static File complete(Context context, PodcastEpisodeRecord episode) {
		return complete(directory(context), episode);
	}

	static File complete(File directory, PodcastEpisodeRecord episode) {
		return file(directory, episode, ".media");
	}

	static File partial(File directory, PodcastEpisodeRecord episode) {
		return file(directory, episode, ".partial");
	}

	private static File file(File directory, PodcastEpisodeRecord episode, String suffix) {
		return new File(new File(directory, episode.getFeedKey()),
				episode.getEpisodeKey() + suffix);
	}
}
