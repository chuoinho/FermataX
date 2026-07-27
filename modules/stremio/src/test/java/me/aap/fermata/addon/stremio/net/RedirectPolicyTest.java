package me.aap.fermata.addon.stremio.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RedirectPolicyTest {
	@Test
	public void revalidatesEveryRedirectAndCatchesDnsRebinding() throws Exception {
		var resolver = new SequenceResolver(
				List.of(address("8.8.8.8")),
				List.of(address("127.0.0.1")));
		var initial = NetworkPolicy.validate(URI.create("https://provider.example.invalid/start"),
				NetworkConsent.STRICT, resolver);

		var error = assertThrows(NetworkPolicyViolation.class, () -> RedirectPolicy.follow(
				initial, URI.create("/next"), 0, NetworkConsent.STRICT, resolver, Map.of()));
		assertEquals(NetworkPolicyViolation.Reason.FORBIDDEN_ADDRESS, error.reason());
		assertEquals(2, resolver.calls);
		assertEquals("8.8.8.8", initial.pinnedAddress().getHostAddress());
	}

	@Test
	public void stripsSensitiveHeadersOnCrossOriginRedirect() throws Exception {
		var initial = NetworkPolicy.validate(URI.create("https://one.example.invalid/start"),
				NetworkConsent.STRICT, fixed("8.8.8.8"));
		var headers = new LinkedHashMap<String, String>();
		headers.put("Authorization", "Bearer secret");
		headers.put("cookie", "session=secret");
		headers.put("X-Api-Key", "secret");
		headers.put("Referer", "https://one.example.invalid/start");
		headers.put("Accept", "application/json");

		var decision = RedirectPolicy.follow(initial, URI.create("https://two.example.invalid/next"), 0,
				NetworkConsent.STRICT, fixed("1.1.1.1"), headers);

		assertEquals(Map.of("Accept", "application/json"), decision.requestHeaders());
	}

	@Test
	public void keepsHeadersOnSameOriginRedirect() throws Exception {
		var initial = NetworkPolicy.validate(URI.create("https://one.example.invalid/start"),
				NetworkConsent.STRICT, fixed("8.8.8.8"));
		var headers = Map.of("Authorization", "Bearer secret", "Accept", "application/json");

		var decision = RedirectPolicy.follow(initial, URI.create("/next"), 0,
				NetworkConsent.STRICT, fixed("8.8.4.4"), headers);

		assertEquals(headers, decision.requestHeaders());
		assertEquals("https://one.example.invalid/next", decision.target().endpoint().uri().toString());
	}

	@Test
	public void enforcesRedirectAndDowngradePolicies() throws Exception {
		var initial = NetworkPolicy.validate(URI.create("https://one.example.invalid/start"),
				NetworkConsent.STRICT, fixed("8.8.8.8"));
		var limitError = assertThrows(NetworkPolicyViolation.class, () -> RedirectPolicy.follow(
				initial, URI.create("/next"), NetworkLimits.MAX_REDIRECTS,
				NetworkConsent.STRICT, fixed("8.8.4.4"), Map.of()));
		assertEquals(NetworkPolicyViolation.Reason.TOO_MANY_REDIRECTS, limitError.reason());

		var cleartextError = assertThrows(NetworkPolicyViolation.class, () -> RedirectPolicy.follow(
				initial, URI.create("http://two.example.invalid/next"), 0,
				NetworkConsent.STRICT, fixed("1.1.1.1"), Map.of()));
		assertEquals(NetworkPolicyViolation.Reason.CLEARTEXT_NOT_ALLOWED, cleartextError.reason());
	}

	@Test
	public void identifiesSensitiveNamesCaseInsensitively() {
		assertTrue(SensitiveHeaderPolicy.isSensitive("AUTHORIZATION"));
		assertTrue(SensitiveHeaderPolicy.isSensitive("Cookie"));
		assertFalse(SensitiveHeaderPolicy.isSensitive("Accept-Language"));
	}

	private static AddressResolver fixed(String value) {
		return host -> List.of(address(value));
	}

	private static InetAddress address(String value) {
		try {
			return InetAddress.getByName(value);
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	private static final class SequenceResolver implements AddressResolver {
		private final ArrayDeque<List<InetAddress>> answers = new ArrayDeque<>();
		private int calls;

		@SafeVarargs
		private SequenceResolver(List<InetAddress>... answers) {
			for (List<InetAddress> answer : answers) this.answers.add(answer);
		}

		@Override
		public List<InetAddress> resolve(String host) {
			calls++;
			return answers.removeFirst();
		}
	}
}
