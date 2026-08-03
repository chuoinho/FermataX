package me.aap.utils.net.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URL;

import org.junit.jupiter.api.Test;

import me.aap.utils.net.TlsTrustPolicy;

class HttpTlsPolicyTest {
	@Test
	void defaultIsStrictAndCannotBeChangedWithoutAuthorizedOrigin() throws Exception {
		HttpConnection.Opts opts = new HttpConnection.Opts();
		URL source = new URL("https://iptv.example.test/list.m3u");
		assertEquals(TlsTrustPolicy.STRICT, opts.trustPolicyFor(source));

		opts.tlsTrustPolicy = TlsTrustPolicy.TRUST_ALL_USER_SOURCE;
		assertEquals(TlsTrustPolicy.STRICT, opts.trustPolicyFor(source));
	}

	@Test
	void userSourceAuthorizationSurvivesOnlySameOriginRedirects() throws Exception {
		HttpConnection.Opts opts = new HttpConnection.Opts();
		URL source = new URL("https://iptv.example.test:8443/list.m3u");
		opts.trustAllUserSourceOrigin(source);

		assertEquals(TlsTrustPolicy.TRUST_ALL_USER_SOURCE,
				opts.trustPolicyFor(new URL("https://IPTV.example.test:8443/redirected.m3u")));
		assertEquals(TlsTrustPolicy.STRICT,
				opts.trustPolicyFor(new URL("https://cdn.example.test:8443/list.m3u")));
		assertEquals(TlsTrustPolicy.STRICT,
				opts.trustPolicyFor(new URL("https://iptv.example.test/list.m3u")));
	}

	@Test
	void connectionCacheIdentityIncludesTrustPolicy() throws Exception {
		URL url = new URL("https://same.example.test/resource");
		var strict = new HttpConnection.ConnectionId(url, TlsTrustPolicy.STRICT);
		var userSource = new HttpConnection.ConnectionId(
				url, TlsTrustPolicy.TRUST_ALL_USER_SOURCE);

		assertNotEquals(strict, userSource);
		assertNotEquals(strict.hashCode(), userSource.hashCode());
	}
}
