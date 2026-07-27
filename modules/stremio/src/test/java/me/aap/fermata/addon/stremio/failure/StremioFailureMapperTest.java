package me.aap.fermata.addon.stremio.failure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletionException;

import org.junit.Test;

import me.aap.fermata.addon.stremio.failure.StremioFailure.Code;
import me.aap.fermata.addon.stremio.failure.StremioFailure.Phase;
import me.aap.fermata.addon.stremio.net.http.HttpFailure;
import me.aap.fermata.addon.stremio.source.StremioSourceException;
import me.aap.fermata.media.engine.PlaybackFailureException;

public class StremioFailureMapperTest {
	@Test
	public void mapsTransportTimeoutWithoutExposingProviderIdentity() {
		var failure = StremioFailureMapper.map(new CompletionException(new HttpFailure(
				HttpFailure.Code.HEADER_TIMEOUT, "secret provider URL")),
				Phase.STREAM, "provider-secret-key");

		assertEquals(Code.HEADER_TIMEOUT, failure.code());
		assertTrue(failure.retryable());
		assertEquals(StremioRecovery.RETRY, failure.recovery());
		assertFalse(failure.toString().contains("provider-secret-key"));
		assertFalse(failure.toString().contains("secret provider URL"));
	}

	@Test
	public void mapsSourceAndP2pFailuresToActionableRecovery() {
		var source = StremioFailureMapper.map(
				new StremioSourceException(StremioSourceException.Code.INVALID_MANIFEST),
				Phase.MANIFEST, "provider");
		var peers = StremioFailureMapper.map(new PlaybackFailureException(
				PlaybackFailureException.Reason.P2P_NO_PEERS), Phase.P2P, "provider");
		var storage = StremioFailureMapper.map(new PlaybackFailureException(
				PlaybackFailureException.Reason.P2P_LOW_STORAGE), Phase.P2P, "provider");

		assertEquals(Code.MALFORMED_MANIFEST, source.code());
		assertEquals(StremioRecovery.MANAGE_ADDON, source.recovery());
		assertEquals(Code.P2P_NO_PEERS, peers.code());
		assertEquals(StremioRecovery.SELECT_SOURCE, peers.recovery());
		assertEquals(Code.P2P_LOW_STORAGE, storage.code());
		assertEquals(StremioRecovery.FREE_STORAGE, storage.recovery());
	}

	@Test
	public void cancellationIsTerminalAndNotPresentedAsRetry() {
		var failure = StremioFailureMapper.map(
				new java.util.concurrent.CancellationException(), Phase.LIFECYCLE, null);

		assertEquals(Code.CANCELLED, failure.code());
		assertTrue(failure.isCancellation());
		assertFalse(failure.retryable());
		assertEquals(StremioRecovery.NONE, failure.recovery());
	}
}
