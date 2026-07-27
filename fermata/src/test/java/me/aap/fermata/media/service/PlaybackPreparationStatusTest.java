package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import android.support.v4.media.MediaMetadataCompat;

import org.junit.Test;

public class PlaybackPreparationStatusTest {
	@Test
	public void statusSurvivesMetadataReplacementForItsOwner() {
		Object engine = new Object();
		Object item = new Object();
		PlaybackPreparationStatus status = new PlaybackPreparationStatus();
		status.update(engine, item, 7L, "4 peers | 2 MB/s | 80%");

		assertEquals("4 peers | 2 MB/s | 80%",
				status.detailFor(engine, item, 7L));
	}

	@Test
	public void statusNeverLeaksAcrossItemEngineOrRevision() {
		Object engine = new Object();
		Object item = new Object();
		PlaybackPreparationStatus status = new PlaybackPreparationStatus();
		status.update(engine, item, 7L, "Preparing");
		assertEquals("", status.detailFor(engine, new Object(), 7L));
		assertEquals("", status.detailFor(new Object(), item, 7L));
		assertEquals("", status.detailFor(engine, item, 8L));
		status.clear();
		assertEquals("", status.detailFor(engine, item, 7L));
	}

	@Test
	public void firstFrameCompletionRejectsLaterProgressForSameAttempt() {
		Object engine = new Object();
		Object item = new Object();
		PlaybackPreparationStatus status = new PlaybackPreparationStatus();
		status.update(engine, item, 7L, "2 peers | 1 MB/s | 20%");

		status.complete(engine, item, 7L);
		status.update(engine, item, 7L, "Streaming");

		assertEquals("", status.detailFor(engine, item, 7L));
		status.update(engine, item, 8L, "New attempt");
		assertEquals("New attempt", status.detailFor(engine, item, 8L));
	}

	@Test
	public void metadataCleanupRemovesPreparationStatus() {
		MediaMetadataCompat preparing = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Movie")
				.putString(PlaybackSnapshot.METADATA_KEY_PREPARATION_STATUS,
						"2 peers | 10 B/s | 0%")
				.build();
		MediaMetadataCompat cleared = PlaybackPreparationStatus.clearMetadata(preparing);
		assertEquals(null, cleared.getString(PlaybackSnapshot.METADATA_KEY_PREPARATION_STATUS));
	}
}
