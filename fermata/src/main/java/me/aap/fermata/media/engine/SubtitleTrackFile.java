package me.aap.fermata.media.engine;

import androidx.annotation.Nullable;

import me.aap.utils.vfs.VirtualFile;

/** Optional metadata contract for virtual subtitle files with stable provider identities. */
public interface SubtitleTrackFile extends VirtualFile {
	long getSubtitleTrackId();

	@Nullable
	String getSubtitleLanguage();

	@Nullable
	String getSubtitleDescription();
}
