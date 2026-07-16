package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeoutException;

final class XtreamErrorMapper {
	private final XtreamAccount account;

	XtreamErrorMapper(XtreamAccount account) {
		this.account = account;
	}

	Throwable map(Throwable error) {
		Throwable root = rootCause(error);
		String message = describe(root);
		if (isNullOrBlank(message)) message = account.redact(error.getLocalizedMessage());
		if (isNullOrBlank(message)) message = account.redact(root.getLocalizedMessage());
		return new IOException(message, error);
	}

	HttpStatusException httpStatus(int status, String reason) {
		return new HttpStatusException(status, reason);
	}

	private Throwable rootCause(Throwable error) {
		Throwable root = error;
		while ((root.getCause() != null) && (root.getCause() != root)) root = root.getCause();
		return root;
	}

	private String describe(Throwable error) {
		String host = account.getHost();

		if ((error instanceof UnresolvedAddressException) || (error instanceof UnknownHostException)) {
			return "Unable to find Xtream server " + host + ". Check the host and port.";
		} else if (error instanceof ConnectException) {
			return "Unable to connect to Xtream server " + host + ". Check the host, port and network.";
		} else if ((error instanceof TimeoutException) || (error instanceof SocketTimeoutException)) {
			return "Xtream server did not respond in time: " + host +
					". Try again or increase the timeout.";
		} else if (error instanceof HttpStatusException status) {
			return describeHttpStatus(status);
		}

		String message = error.getLocalizedMessage();
		if (!isNullOrBlank(message) && message.contains("expected JSON, got HTML")) {
			return "Xtream server returned an HTML error page instead of JSON. " +
					"Check the portal URL and account.";
		} else if (!isNullOrBlank(message) && message.contains("expected JSON")) {
			return "Xtream server returned an invalid response. Check the portal URL and account.";
		}

		return null;
	}

	private String describeHttpStatus(HttpStatusException error) {
		int status = error.status;
		String reason = isNullOrBlank(error.reason) ? "" : " " + error.reason;

		if ((status == HttpURLConnection.HTTP_UNAUTHORIZED) ||
				(status == HttpURLConnection.HTTP_FORBIDDEN)) {
			return "Xtream server rejected the request (HTTP " + status +
					"). Check username, password, expiry, or connection slots.";
		} else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
			return "Xtream API was not found on this server (HTTP 404). " +
					"Check the portal URL, host and port.";
		} else if (status >= 500) {
			return "Xtream server error (HTTP " + status + reason + "). Try again later.";
		}

		return "Xtream request failed (HTTP " + status + reason + ").";
	}

	static final class HttpStatusException extends IOException {
		final int status;
		final String reason;

		HttpStatusException(int status, String reason) {
			super("HTTP " + status + (isNullOrBlank(reason) ? "" : " " + reason));
			this.status = status;
			this.reason = reason;
		}
	}
}
