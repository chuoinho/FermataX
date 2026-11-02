package me.aap.fermata.addon.audiobook.remote;

import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;

public record AudiobookshelfBookDetails(AudiobookSource source, AudiobookBook book,
		List<AudiobookChapter> chapters, long remoteProgressUpdatedMs) {

	/**
	 * The chapter of local progress the server has not received (e.g. a push that failed
	 * offline), or null when the server is up to date.
	 */
	@Nullable
	public AudiobookChapter unsyncedChapter(@Nullable AudiobookBook local) {
		if ((local == null) || (local.getProgressChapterId() == null) ||
				(local.getLastPlayedMs() <= remoteProgressUpdatedMs)) return null;
		for (AudiobookChapter c : chapters) {
			if (c.getId().equals(local.getProgressChapterId())) return c;
		}
		return null;
	}
}
