package me.aap.fermata.addon.audiobook;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import me.aap.fermata.addon.audiobook.data.AudiobookRepository;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

final class AudiobookChapterItem extends ExtPlayable implements AudiobookItem,
		PlaybackProgressItem {
	private final AudiobookBook book;
	private final AudiobookChapter chapter;
	private final AudiobookRepository repository;
	private final AudiobookRootItem root;
	private final Map<String, String> requestHeaders;
	@Nullable private final String nextChapterId;
	private volatile long resumePosition;

	AudiobookChapterItem(BrowsableItem parent, AudiobookBook book, AudiobookChapter chapter,
			VirtualResource resource, AudiobookRepository repository,
			AudiobookRootItem root, Map<String, String> requestHeaders,
			@Nullable String nextChapterId) {
		super(AudiobookRootItem.chapterId(book.getId(), chapter.getId()), parent, resource);
		this.book = book;
		this.chapter = chapter;
		this.repository = repository;
		this.root = root;
		this.requestHeaders = requestHeaders;
		this.nextChapterId = nextChapterId;
		resumePosition = chapter.getId().equals(book.getProgressChapterId()) ?
				Math.max(book.getProgressMs(), 0) : 0;
	}

	AudiobookBook getBook() {
		return book;
	}

	AudiobookChapter getChapter() {
		return chapter;
	}

	@NonNull
	@Override
	public String getName() {
		return chapter.getTitle();
	}

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	public boolean isExternal() {
		// Participate in Fermata's bookmark/favorite/playerbar lifecycle. Durable resume still
		// comes from PlaybackProgressItem, so common preferences remain only a fallback.
		return false;
	}

	@Override
	public long getOffset() {
		return chapter.getOffsetMs();
	}

	@Override
	public boolean isTimerRequired() {
		return chapter.isSegment();
	}

	@NonNull
	@Override
	public Map<String, String> getRequestHeaders() {
		return requestHeaders;
	}

	@Override
	public long getResumePosition() {
		return resumePosition;
	}

	@Override
	public FutureSupplier<Void> savePlaybackProgress(long position, boolean completed) {
		resumePosition = completed ? 0 : Math.max(position, 0);
		String targetChapter = (completed && (nextChapterId != null)) ? nextChapterId :
				chapter.getId();
		boolean bookFinished = completed && (nextChapterId == null);
		FutureSupplier<Void> saved = repository.updateProgress(book.getId(), targetChapter,
				resumePosition, bookFinished, System.currentTimeMillis());
		saved.onSuccess(ignored -> repository.getChapter(book.getId(), targetChapter)
				.onSuccess(target -> {
					if (target != null) root.syncProgress(book, target, resumePosition, bookFinished);
				}));
		return saved;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		MetadataBuilder metadata = new MetadataBuilder();
		metadata.putString(METADATA_KEY_TITLE, chapter.getTitle());
		metadata.putString(METADATA_KEY_ALBUM, book.getTitle());
		if (!book.getAuthor().isEmpty()) metadata.putString(METADATA_KEY_ARTIST, book.getAuthor());
		if (chapter.getDurationMs() > 0) metadata.putLong(METADATA_KEY_DURATION,
				chapter.getDurationMs());
		if (!book.getArtworkUrl().isEmpty()) metadata.setImageUri(book.getArtworkUrl());
		return buildMeta(metadata);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat metadata, SharedTextBuilder text) {
		if (!book.getAuthor().isEmpty()) text.append(book.getAuthor());
		else text.append(book.getTitle());
		return text.toString();
	}
}
