package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class XtreamErrorMapperTest extends Assert {
	private final XtreamErrorMapper mapper = new XtreamErrorMapper(account());

	@Test
	public void networkFailuresKeepActionableProviderMessages() {
		assertMessage(new UnknownHostException(), "Unable to find Xtream server example.com");
		assertMessage(new ConnectException(), "Unable to connect to Xtream server example.com");
		assertMessage(new SocketTimeoutException(), "did not respond in time: example.com");
	}

	@Test
	public void httpFailuresKeepAuthNotFoundAndServerClassification() {
		assertMessage(mapper.httpStatus(401, "Unauthorized"), "Check username, password, expiry");
		assertMessage(mapper.httpStatus(404, "Not Found"), "Xtream API was not found");
		assertMessage(mapper.httpStatus(503, "Unavailable"), "Xtream server error (HTTP 503");
	}

	@Test
	public void invalidProviderPayloadsKeepClearMessages() {
		assertMessage(new IOException("Invalid Xtream response: expected JSON, got HTML"),
				"returned an HTML error page instead of JSON");
		assertMessage(new IOException("Invalid Xtream response: expected JSON"),
				"returned an invalid response");
	}

	@Test
	public void fallbackErrorMessageRedactsCredentials() {
		Throwable mapped = mapper.map(new IOException(
				"request failed for username=secret-user&password=secret-pass"));
		assertFalse(mapped.getMessage().contains("secret-user"));
		assertFalse(mapped.getMessage().contains("secret-pass"));
	}

	private void assertMessage(Throwable error, String expected) {
		assertTrue(mapper.map(error).getMessage().contains(expected));
	}

	private static XtreamAccount account() {
		return new XtreamAccount(0, "Test", 0, "example.com", 80,
				"secret-user", "secret-pass", 0, null, 0);
	}
}
