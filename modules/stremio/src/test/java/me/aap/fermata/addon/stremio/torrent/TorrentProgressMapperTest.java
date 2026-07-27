package me.aap.fermata.addon.stremio.torrent;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.engine.PlaybackFailureException;
import me.aap.fermata.media.net.RemotePlaybackProgress;

public class TorrentProgressMapperTest {
	@Test
	public void mapsPreparationFailuresToStableRemoteFailures() {
		assertEquals(RemotePlaybackProgress.Failure.METADATA_UNAVAILABLE,
				failure(PlaybackFailureException.Reason.P2P_METADATA_UNAVAILABLE));
		assertEquals(RemotePlaybackProgress.Failure.NO_PEERS,
				failure(PlaybackFailureException.Reason.P2P_NO_PEERS));
		assertEquals(RemotePlaybackProgress.Failure.DATA_TIMEOUT,
				failure(PlaybackFailureException.Reason.P2P_DATA_TIMEOUT));
		assertEquals(RemotePlaybackProgress.Failure.ENGINE_UNAVAILABLE,
				failure(PlaybackFailureException.Reason.P2P_ENGINE_UNAVAILABLE));
		assertEquals(RemotePlaybackProgress.Failure.FILE_UNAVAILABLE,
				failure(PlaybackFailureException.Reason.P2P_FILE_UNAVAILABLE));
		assertEquals(RemotePlaybackProgress.Failure.LOW_STORAGE,
				failure(PlaybackFailureException.Reason.P2P_LOW_STORAGE));
	}

	@Test
	public void failureRemainsTerminalForLaterProgress() {
		List<RemotePlaybackProgress.Phase> phases = new ArrayList<>();
		TorrentProgressMapper.Publisher publisher = new TorrentProgressMapper.Publisher();
		publisher.observe(value -> phases.add(value.phase()));
		publisher.fail(RemotePlaybackProgress.Failure.DATA_TIMEOUT);
		publisher.publish(RemotePlaybackProgress.findingPeers(), true);

		assertEquals(List.of(RemotePlaybackProgress.Phase.FINDING_PEERS,
				RemotePlaybackProgress.Phase.FAILED), phases);
	}

	private static RemotePlaybackProgress.Failure failure(
			PlaybackFailureException.Reason reason) {
		return TorrentProgressMapper.failure(new PlaybackFailureException(reason)).failure();
	}
}
