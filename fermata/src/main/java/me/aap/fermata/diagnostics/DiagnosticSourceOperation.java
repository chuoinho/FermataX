package me.aap.fermata.diagnostics;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

/** Consistent correlation and terminal classification for source/network requests. */
public final class DiagnosticSourceOperation {
	private static final int MAX_CAUSE_DEPTH = 8;

	private DiagnosticSourceOperation() {
	}

	public static <T> FutureSupplier<T> observe(FutureSupplier<T> future, String category,
			String requestType) {
		String operationId = DiagnosticIds.next();
		App.get().onDiagnosticEvent(category, "request_started", operationId,
				Map.of("request_type", requestType), null);
		future.onCompletion((value, error) -> {
			String event = terminalEvent(error);
			App.get().onDiagnosticEvent(category, event, operationId,
					Map.of("request_type", requestType), reportableError(event, error));
		});
		return future;
	}

	public static String terminalEvent(Throwable error) {
		if (error == null) return "request_completed";
		int depth = 0;
		for (Throwable current = error; (current != null) && (depth++ < MAX_CAUSE_DEPTH); ) {
			if (current instanceof CancellationException) return "request_cancelled";
			if ((current instanceof SocketTimeoutException) || (current instanceof TimeoutException)) {
				return "request_timed_out";
			}
			Throwable cause = current.getCause();
			if (cause == current) break;
			current = cause;
		}
		return "request_failed";
	}

	public static Throwable reportableError(String event, Throwable error) {
		return "request_cancelled".equals(event) ? null : error;
	}
}
