package me.aap.fermata.addon.stremio.net;

import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.CLEARTEXT_NOT_ALLOWED;
import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.DNS_NO_RESULTS;
import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.FORBIDDEN_ADDRESS;
import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.FORBIDDEN_HOST;
import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.LAN_NOT_ALLOWED;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class NetworkPolicy {
	private static final Set<String> FORBIDDEN_HOSTS = Set.of(
			"localhost", "metadata", "metadata.google.internal", "instance-data",
			"instance-data.ec2.internal");

	private NetworkPolicy() {
	}

	public static ValidatedEndpoint validate(
			URI uri, NetworkConsent consent, AddressResolver resolver) throws IOException {
		Objects.requireNonNull(consent, "consent");
		Objects.requireNonNull(resolver, "resolver");
		NormalizedEndpoint endpoint = EndpointNormalizer.normalize(uri);
		if (endpoint.scheme().equals("http") && !consent.allowCleartext()) {
			throw violation(CLEARTEXT_NOT_ALLOWED, "Cleartext HTTP requires explicit consent");
		}
		if (isForbiddenHost(endpoint.host())) {
			throw violation(FORBIDDEN_HOST, "Local and cloud metadata hostnames are forbidden");
		}

		List<InetAddress> addresses = List.copyOf(resolver.resolve(endpoint.host()));
		if (addresses.isEmpty()) throw violation(DNS_NO_RESULTS, "DNS returned no addresses");
		AddressKind firstKind = null;
		for (InetAddress address : addresses) {
			AddressKind kind = AddressClassifier.classify(address);
			if (firstKind == null) firstKind = kind;
			if (kind == AddressKind.PRIVATE) {
				if (!consent.allowLan()) {
					throw violation(LAN_NOT_ALLOWED, "Private network address requires LAN consent");
				}
			} else if (kind != AddressKind.PUBLIC) {
				throw violation(FORBIDDEN_ADDRESS, "Address class is forbidden: " + kind);
			}
		}
		return new ValidatedEndpoint(endpoint, addresses.get(0), firstKind, addresses);
	}

	private static boolean isForbiddenHost(String host) {
		String normalized = host.toLowerCase(Locale.ROOT);
		return FORBIDDEN_HOSTS.contains(normalized) || normalized.endsWith(".localhost") ||
				normalized.endsWith(".metadata.google.internal");
	}

	private static NetworkPolicyViolation violation(
			NetworkPolicyViolation.Reason reason, String message) {
		return new NetworkPolicyViolation(reason, message);
	}
}
