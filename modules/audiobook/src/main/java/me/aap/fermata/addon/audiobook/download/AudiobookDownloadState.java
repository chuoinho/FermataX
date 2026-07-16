package me.aap.fermata.addon.audiobook.download;

public final class AudiobookDownloadState {
	public static final int NONE = 0;
	public static final int DOWNLOADING = 1;
	public static final int COMPLETE = 2;
	public static final int FAILED = 3;

	private AudiobookDownloadState() {
	}
}
