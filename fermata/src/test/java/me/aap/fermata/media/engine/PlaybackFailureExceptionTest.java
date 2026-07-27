package me.aap.fermata.media.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletionException;

import org.junit.Test;

public class PlaybackFailureExceptionTest {
	@Test
	public void findsTypedFailureThroughAsyncAndPlayerWrappers() {
		var failure = new PlaybackFailureException(
				PlaybackFailureException.Reason.P2P_NO_PEERS);
		Throwable wrapped = new RuntimeException("Source error", new CompletionException(failure));

		assertEquals(failure, PlaybackFailureException.find(wrapped));
	}

	@Test
	public void ignoresOrdinaryAndCyclicFailures() {
		assertNull(PlaybackFailureException.find(new IOExceptionWithoutSafeReason()));
		CyclicFailure cyclic = new CyclicFailure();
		assertNull(PlaybackFailureException.find(cyclic));
	}

	@Test
	public void p2pTransportFailuresPreventDecoderRetry() {
		for (PlaybackFailureException.Reason reason : PlaybackFailureException.Reason.values()) {
			assertTrue(new PlaybackFailureException(reason).preventsEngineFallback());
		}
	}

	private static final class IOExceptionWithoutSafeReason extends java.io.IOException {
	}

	private static final class CyclicFailure extends RuntimeException {
		@Override
		public synchronized Throwable getCause() {
			return this;
		}
	}
}
