package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import org.junit.Test;

public class DiagnosticSourceOperationTest {
	@Test
	public void terminalOutcomesAreClassifiedAcrossWrappedCauses() {
		assertEquals("request_completed", DiagnosticSourceOperation.terminalEvent(null));
		assertEquals("request_cancelled", DiagnosticSourceOperation.terminalEvent(
				new CompletionException(new CancellationException())));
		assertEquals("request_timed_out", DiagnosticSourceOperation.terminalEvent(
				new CompletionException(new SocketTimeoutException())));
		assertEquals("request_failed", DiagnosticSourceOperation.terminalEvent(
				new IllegalStateException()));
	}

	@Test
	public void cancellationDoesNotEscalateAsAnError() {
		CancellationException cancellation = new CancellationException();
		assertNull(DiagnosticSourceOperation.reportableError("request_cancelled", cancellation));
		IllegalStateException failure = new IllegalStateException();
		assertSame(failure,
				DiagnosticSourceOperation.reportableError("request_failed", failure));
	}
}
