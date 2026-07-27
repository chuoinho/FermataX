package me.aap.fermata.addon.stremio.net;

import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.INVALID_URL;
import static me.aap.fermata.addon.stremio.net.NetworkPolicyViolation.Reason.TOO_MANY_REDIRECTS;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

public final class RedirectPolicy {
	private RedirectPolicy() {
	}

	public static RedirectDecision follow(
			ValidatedEndpoint current,
			URI location,
			int redirectsFollowed,
			NetworkConsent consent,
			AddressResolver resolver,
			Map<String, String> requestHeaders) throws IOException {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(location, "location");
		if (redirectsFollowed < 0) {
			throw new NetworkPolicyViolation(INVALID_URL, "Redirect count cannot be negative");
		}
		if (redirectsFollowed >= NetworkLimits.MAX_REDIRECTS) {
			throw new NetworkPolicyViolation(TOO_MANY_REDIRECTS, "Redirect limit exceeded");
		}
		URI targetUri = current.endpoint().uri().resolve(location);
		ValidatedEndpoint target = NetworkPolicy.validate(targetUri, consent, resolver);
		Map<String, String> headers = SensitiveHeaderPolicy.forRedirect(
				current.endpoint(), target.endpoint(), requestHeaders);
		return new RedirectDecision(target, headers);
	}
}
