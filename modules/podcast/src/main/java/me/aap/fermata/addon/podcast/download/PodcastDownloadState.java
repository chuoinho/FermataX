package me.aap.fermata.addon.podcast.download;

public final class PodcastDownloadState {
	public static final int NONE = 0;
	public static final int DOWNLOADING = 1;
	public static final int COMPLETE = 2;
	public static final int FAILED = 3;

	private PodcastDownloadState() {
	}
}
