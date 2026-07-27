package me.aap.fermata.media.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RemotePlaybackProgressTest {
	@Test
	public void bufferingIsBoundedUntilPlayerIsReady() {
		assertEquals(99, RemotePlaybackProgress.buffering(
				4, 2, 1_000, 900, 1_000, 100).percent());
		assertEquals(100, RemotePlaybackProgress.ready(
				4, 2, 1_000, 1_000, 1_000).percent());
	}

	@Test
	public void streamingStatesDoNotCarryAStalePercentage() {
		assertEquals(RemotePlaybackProgress.Phase.STREAMING,
				RemotePlaybackProgress.streaming(7, 2, 2_400_000).phase());
		assertEquals(-1, RemotePlaybackProgress.streaming(7, 2, 2_400_000).percent());
		assertEquals(RemotePlaybackProgress.Phase.REBUFFERING,
				RemotePlaybackProgress.rebuffering(7, 2, 2_400_000).phase());
		assertNull(RemotePlaybackProgress.rebuffering(7, 2, 2_400_000).failure());
	}

	@Test
	public void failedProgressCarriesAnActionableReason() {
		var progress = RemotePlaybackProgress.failed(
				RemotePlaybackProgress.Failure.NO_PEERS);
		assertEquals(RemotePlaybackProgress.Phase.FAILED, progress.phase());
		assertEquals(RemotePlaybackProgress.Failure.NO_PEERS, progress.failure());
	}

	@Test
	public void rejectsInvalidTelemetry() {
		assertThrows(IllegalArgumentException.class, () -> new RemotePlaybackProgress(
				RemotePlaybackProgress.Phase.BUFFERING, -1, 0, 0, 0, 1, 0));
	}
}
