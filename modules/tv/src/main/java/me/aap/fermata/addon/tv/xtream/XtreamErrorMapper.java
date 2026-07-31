package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.content.Context;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import me.aap.fermata.addon.tv.R;

final class XtreamErrorMapper {
	private final XtreamAccount account;
	private final Context context;

	XtreamErrorMapper(XtreamAccount account) {
		this(account, null);
	}

	XtreamErrorMapper(XtreamAccount account, Context context) {
		this.account = account;
		this.context = context;
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
			return message(R.string.xtream_error_host_not_found,
					"Unable to find Xtream server %1$s. Check the host and port.", host);
		} else if (error instanceof ConnectException) {
			return message(R.string.xtream_error_connect,
					"Unable to connect to Xtream server %1$s. Check the host, port and network.", host);
		} else if ((error instanceof TimeoutException) || (error instanceof SocketTimeoutException)) {
			return message(R.string.xtream_error_timeout,
					"Xtream server did not respond in time: %1$s. Try again or increase the timeout.",
					host);
		} else if (error instanceof HttpStatusException status) {
			return describeHttpStatus(status);
		}

		String message = error.getLocalizedMessage();
		if (!isNullOrBlank(message) && message.contains("expected JSON, got HTML")) {
			return message(R.string.xtream_error_html_response,
					"Xtream server returned an HTML error page instead of JSON. " +
							"Check the portal URL and account.");
		} else if (!isNullOrBlank(message) && message.contains("expected JSON")) {
			return message(R.string.xtream_error_invalid_response,
					"Xtream server returned an invalid response. Check the portal URL and account.");
		}

		return null;
	}

	private String describeHttpStatus(HttpStatusException error) {
		int status = error.status;
		String reason = isNullOrBlank(error.reason) ? "" : " " + error.reason;

		if ((status == HttpURLConnection.HTTP_UNAUTHORIZED) ||
				(status == HttpURLConnection.HTTP_FORBIDDEN)) {
			return message(R.string.xtream_error_account_rejected,
					"Xtream server rejected the request (HTTP %1$d). " +
							"Check username, password, expiry, or connection slots.", status);
		} else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
			return message(R.string.xtream_error_api_not_found,
					"Xtream API was not found on this server (HTTP 404). " +
							"Check the portal URL, host and port.");
		} else if (status >= 500) {
			return message(R.string.xtream_error_server,
					"Xtream server error (HTTP %1$d%2$s). Try again later.", status, reason);
		}

		return message(R.string.xtream_error_http,
				"Xtream request failed (HTTP %1$d%2$s).", status, reason);
	}

	private String message(int resource, String fallback, Object... args) {
		return (context == null) ? String.format(Locale.ROOT, fallback, args) :
				context.getString(resource, args);
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
