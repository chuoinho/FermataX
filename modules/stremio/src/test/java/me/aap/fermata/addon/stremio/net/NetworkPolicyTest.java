package me.aap.fermata.addon.stremio.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.List;

import org.junit.Test;

public class NetworkPolicyTest {
	@Test
	public void requiresExplicitCleartextConsent() throws Exception {
		var resolver = fixed("8.8.8.8");
		assertReason(NetworkPolicyViolation.Reason.CLEARTEXT_NOT_ALLOWED,
				() -> NetworkPolicy.validate(URI.create("http://provider.example.invalid/manifest.json"),
						NetworkConsent.STRICT, resolver));

		var endpoint = NetworkPolicy.validate(URI.create("http://provider.example.invalid/manifest.json"),
				new NetworkConsent(true, false), resolver);
		assertEquals("http", endpoint.endpoint().scheme());
	}

	@Test
	public void requiresLanConsentButNeverAllowsForbiddenAddressClasses() throws Exception {
		assertReason(NetworkPolicyViolation.Reason.LAN_NOT_ALLOWED,
				() -> validate("https://provider.example.invalid", NetworkConsent.STRICT, "192.168.1.10"));
		assertEquals(AddressKind.PRIVATE,
				validate("https://provider.example.invalid", new NetworkConsent(false, true), "192.168.1.10")
						.pinnedAddressKind());

		for (String address : List.of("127.0.0.1", "169.254.1.1", "169.254.169.254",
				"0.0.0.0", "100.64.0.1", "100.127.255.254", "192.0.2.1", "198.18.0.1",
				"240.0.0.1", "239.1.1.1", "::1", "fe80::1", "100::1", "100:0:0:1::1",
				"2001:2::1", "2001:db8::1", "3fff::1", "5f00::1", "ff02::1", "::")) {
			assertReason(NetworkPolicyViolation.Reason.FORBIDDEN_ADDRESS,
					() -> validate("https://provider.example.invalid", new NetworkConsent(false, true), address));
		}
	}

	@Test
	public void rejectsKnownMetadataAndLocalNamesBeforeDns() {
		var resolver = new CountingResolver(List.of(address("8.8.8.8")));
		assertReason(NetworkPolicyViolation.Reason.FORBIDDEN_HOST,
				() -> NetworkPolicy.validate(URI.create("https://metadata.google.internal/a"),
						new NetworkConsent(false, true), resolver));
		assertEquals(0, resolver.calls);
	}

	@Test
	public void rejectsMixedPublicAndPrivateDnsAnswersWithoutLanConsent() {
		var resolver = new CountingResolver(List.of(address("8.8.8.8"), address("10.0.0.2")));
		assertReason(NetworkPolicyViolation.Reason.LAN_NOT_ALLOWED,
				() -> NetworkPolicy.validate(URI.create("https://provider.example.invalid"),
						NetworkConsent.STRICT, resolver));
	}

	@Test
	public void validationResultPinsAddressAndDoesNotResolveAgain() throws Exception {
		var resolver = new SequenceResolver(
				List.of(address("8.8.8.8")),
				List.of(address("127.0.0.1")));
		var endpoint = NetworkPolicy.validate(URI.create("https://provider.example.invalid/a"),
				NetworkConsent.STRICT, resolver);

		assertEquals(1, resolver.calls);
		assertSame(resolver.firstAddress, endpoint.pinnedAddress());
		assertEquals("8.8.8.8", endpoint.pinnedAddress().getHostAddress());
		assertEquals(1, resolver.calls);
	}

	private static ValidatedEndpoint validate(
			String uri, NetworkConsent consent, String address) throws Exception {
		return NetworkPolicy.validate(URI.create(uri), consent, fixed(address));
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

	private static void assertReason(
			NetworkPolicyViolation.Reason expected, ThrowingRunnable action) {
		var error = assertThrows(NetworkPolicyViolation.class, action::run);
		assertEquals(expected, error.reason());
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static class CountingResolver implements AddressResolver {
		private final List<InetAddress> addresses;
		protected int calls;

		private CountingResolver(List<InetAddress> addresses) {
			this.addresses = addresses;
		}

		@Override
		public List<InetAddress> resolve(String host) {
			calls++;
			return addresses;
		}
	}

	private static final class SequenceResolver extends CountingResolver {
		private final ArrayDeque<List<InetAddress>> answers = new ArrayDeque<>();
		private final InetAddress firstAddress;

		@SafeVarargs
		private SequenceResolver(List<InetAddress>... answers) {
			super(List.of());
			for (List<InetAddress> answer : answers) this.answers.add(answer);
			firstAddress = answers[0].get(0);
		}

		@Override
		public List<InetAddress> resolve(String host) {
			super.calls++;
			return answers.removeFirst();
		}
	}
}
