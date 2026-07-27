package me.aap.fermata.engine.exoplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.media.engine.PlaybackFailureException.Reason;

public class ProfileHttpDataSourceTest {
	@Test
	public void p2pHttpStatusesHaveStableSafeReasons() {
		assertEquals(Reason.P2P_ENGINE_UNAVAILABLE,
				ProfileHttpDataSource.p2pFailureReason(502));
		assertEquals(Reason.P2P_NO_PEERS,
				ProfileHttpDataSource.p2pFailureReason(503));
		assertEquals(Reason.P2P_DATA_TIMEOUT,
				ProfileHttpDataSource.p2pFailureReason(504));
		assertEquals(null, ProfileHttpDataSource.p2pFailureReason(404));
	}
}
