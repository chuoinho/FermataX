package me.aap.fermata.addon.radio;

import static me.aap.utils.net.http.HttpContentDecoder.decodeBuffered;

import android.util.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import me.aap.utils.app.App;
import me.aap.fermata.diagnostics.DiagnosticIds;
import me.aap.fermata.diagnostics.DiagnosticSourceOperation;

final class RadioHttp {
	private RadioHttp() {
	}

	static <T> T request(HttpURLConnection connection, StatusExceptionFactory statusException,
					 Parser<T> parser) throws IOException {
		String operationId = DiagnosticIds.next();
		diagnostic("request_started", operationId, Map.of("phase", "request"), null);
		try {
			int code = connection.getResponseCode();
			if ((code < 200) || (code >= 300)) throw statusException.create(code);

			try (InputStream in = decodeBuffered(connection, connection.getInputStream());
					 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
					 JsonReader json = new JsonReader(reader)) {
				T result = parser.parse(json);
				diagnostic("request_completed", operationId, Map.of("error_code", code), null);
				return result;
			}
		} catch (IOException | RuntimeException error) {
			String event = DiagnosticSourceOperation.terminalEvent(error);
			diagnostic(event, operationId, Map.of("phase", "request"),
					DiagnosticSourceOperation.reportableError(event, error));
			throw error;
		} finally {
			connection.disconnect();
		}
	}

	private static void diagnostic(String event, String operationId, Map<String, ?> attributes,
			Throwable error) {
		try {
			App.get().onDiagnosticEvent("radio_source", event, operationId, attributes, error);
		} catch (RuntimeException ignored) {
			// Tests and non-Fermata hosts may not install an App singleton.
		}
	}

	interface Parser<T> {
		T parse(JsonReader reader) throws IOException;
	}

	interface StatusExceptionFactory {
		IOException create(int statusCode);
	}
}
