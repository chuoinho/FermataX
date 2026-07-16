package me.aap.fermata.addon.audiobook.download;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.utils.async.FutureSupplier;

public interface AudiobookDownloadStore {
	FutureSupplier<List<AudiobookChapter>> listChapters(String bookId);

	FutureSupplier<Void> updateDownload(String bookId, String chapterId, int state,
			String localPath);

	FutureSupplier<Void> clearDownloads(String bookId);
}
