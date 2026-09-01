package me.aap.fermata.addon.tv.stalker;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.content.Context;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import me.aap.fermata.addon.tv.R;

final class StalkerErrorMapper {
	private final StalkerAccount account;
	private final Context context;

	StalkerErrorMapper(StalkerAccount account, Context context) {
		this.account = account;
		this.context = context;
	}

	IOException map(Throwable error) {
		Throwable root = error;
		while ((root.getCause() != null) && (root.getCause() != root)) root = root.getCause();
		String message;
		if (root instanceof UnknownHostException) {
			message = text(R.string.stalker_error_host_not_found,
					"Unable to find Stalker portal %1$s.", account.getPortal());
		} else if (root instanceof ConnectException) {
			message = text(R.string.stalker_error_connect,
					"Unable to connect to Stalker portal %1$s.", account.getPortal());
		} else if (root instanceof SocketTimeoutException) {
			message = text(R.string.stalker_error_timeout,
					"Stalker portal did not respond in time: %1$s.", account.getPortal());
		} else if (root instanceof HttpStatusException status) {
			if ((status.status == HttpURLConnection.HTTP_UNAUTHORIZED) ||
					(status.status == HttpURLConnection.HTTP_FORBIDDEN)) {
				message = text(R.string.stalker_error_account_rejected,
						"Stalker portal rejected this MAC address (HTTP %1$d).", status.status);
			} else if (status.status == HttpURLConnection.HTTP_NOT_FOUND) {
				message = text(R.string.stalker_error_api_not_found,
						"Stalker API was not found. Check the portal URL.");
			} else {
				message = "Stalker request failed (HTTP " + status.status + ").";
			}
		} else {
			message = account.redact(error.getLocalizedMessage());
			if (isNullOrBlank(message)) message = account.redact(root.getLocalizedMessage());
			if (isNullOrBlank(message)) message = "Stalker portal request failed";
		}
		return new IOException(message, error);
	}

	private String text(int resource, String fallback, Object... args) {
		return (context == null) ? String.format(Locale.ROOT, fallback, args) :
				context.getString(resource, args);
	}

	static final class HttpStatusException extends IOException {
		final int status;

		HttpStatusException(int status, String reason) {
			super("HTTP " + status + (isNullOrBlank(reason) ? "" : " " + reason));
			this.status = status;
		}
	}
}
