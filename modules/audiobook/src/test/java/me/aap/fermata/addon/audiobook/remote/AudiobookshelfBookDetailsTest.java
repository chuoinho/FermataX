package me.aap.fermata.addon.audiobook.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;

public class AudiobookshelfBookDetailsTest {
	private static final List<AudiobookChapter> CHAPTERS = List.of(chapter("c0", 0), chapter("c1", 1));

	@Test
	public void localProgressNewerThanServerIsUnsynced() {
		AudiobookshelfBookDetails details = details(1_000);
		assertEquals("c1", details.unsyncedChapter(local("c1", 2_000)).getId());
	}

	@Test
	public void serverProgressSameOrNewerIsInSync() {
		AudiobookshelfBookDetails details = details(2_000);
		assertNull(details.unsyncedChapter(local("c1", 2_000)));
		assertNull(details.unsyncedChapter(local("c1", 1_000)));
	}

	@Test
	public void noLocalProgressOrUnknownChapterIsNothingToSync() {
		AudiobookshelfBookDetails details = details(0);
		assertNull(details.unsyncedChapter(null));
		assertNull(details.unsyncedChapter(local(null, 2_000)));
		assertNull(details.unsyncedChapter(local("gone", 2_000)));
	}

	private static AudiobookshelfBookDetails details(long remoteUpdatedMs) {
		return new AudiobookshelfBookDetails(null, local(null, 0), CHAPTERS, remoteUpdatedMs);
	}

	private static AudiobookBook local(String chapterId, long lastPlayedMs) {
		return new AudiobookBook("b", "s", "remote-id", "Title", "Author", "", "", "", "en",
				100_000, chapterId, 5_000, lastPlayedMs, false, 1, 2);
	}

	private static AudiobookChapter chapter(String id, int index) {
		return new AudiobookChapter("b", id, index, "Chapter " + index,
				"https://example.test/" + id + ".mp3", "audio/mpeg", 0, index * 50_000L,
				50_000, false, null, 0);
	}
}
