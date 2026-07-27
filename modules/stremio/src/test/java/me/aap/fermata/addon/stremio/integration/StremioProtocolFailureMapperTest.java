package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import org.junit.Test;

import me.aap.fermata.addon.stremio.net.http.HttpFailure;

public class StremioProtocolFailureMapperTest {
	@Test
	public void preservesEveryHttpFailureMappingAndRetryability() {
		Map<HttpFailure.Code, StremioIntegrationException.Code> expected =
				new EnumMap<>(HttpFailure.Code.class);
		expected.put(HttpFailure.Code.CANCELLED, StremioIntegrationException.Code.CANCELLED);
		expected.put(HttpFailure.Code.CONNECT_TIMEOUT,
				StremioIntegrationException.Code.NETWORK_TIMEOUT);
		expected.put(HttpFailure.Code.HEADER_TIMEOUT,
				StremioIntegrationException.Code.NETWORK_TIMEOUT);
		expected.put(HttpFailure.Code.BODY_TIMEOUT,
				StremioIntegrationException.Code.NETWORK_TIMEOUT);
		expected.put(HttpFailure.Code.CALL_TIMEOUT,
				StremioIntegrationException.Code.NETWORK_TIMEOUT);
		expected.put(HttpFailure.Code.BODY_TOO_LARGE,
				StremioIntegrationException.Code.RESPONSE_TOO_LARGE);
		expected.put(HttpFailure.Code.INVALID_REDIRECT,
				StremioIntegrationException.Code.NETWORK);
		expected.put(HttpFailure.Code.TRANSPORT, StremioIntegrationException.Code.NETWORK);
		expected.put(HttpFailure.Code.HTTP_STATUS, StremioIntegrationException.Code.HTTP_STATUS);

		for (var entry : expected.entrySet()) {
			var mapped = StremioProtocolFailureMapper.map(
					new CompletionException(new HttpFailure(entry.getKey(), "private detail")));
			assertEquals(entry.getValue(), mapped.code());
			assertEquals(entry.getValue() == StremioIntegrationException.Code.NETWORK_TIMEOUT ||
					entry.getValue() == StremioIntegrationException.Code.NETWORK ||
					entry.getValue() == StremioIntegrationException.Code.HTTP_STATUS,
					mapped.retryable());
			assertFalse(mapped.toString().contains("private detail"));
		}
	}

	@Test
	public void preservesNonHttpMappingsAndExistingIntegrationFailure() {
		assertEquals(StremioIntegrationException.Code.CANCELLED,
				StremioProtocolFailureMapper.map(new CancellationException()).code());
		assertEquals(StremioIntegrationException.Code.INVALID_RESPONSE,
				StremioProtocolFailureMapper.map(new IllegalArgumentException()).code());
		assertEquals(StremioIntegrationException.Code.NETWORK,
				StremioProtocolFailureMapper.map(new IllegalStateException()).code());

		var existing = StremioProtocolFailureMapper.failure(
				StremioIntegrationException.Code.SOURCE_CHANGED);
		assertSame(existing, StremioProtocolFailureMapper.map(existing));
		assertTrue(StremioProtocolFailureMapper.failure(
				StremioIntegrationException.Code.HTTP_STATUS).retryable());
	}
}
